package dev.skomlach.biometric.compat.custom

import android.content.Context
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean

private val SOFTWARE_RUNTIME_ORDER = compareByDescending<SoftwareBiometricRuntime> { it.priority }
    .thenByDescending { it.promptFactoryPriority }.thenBy { it.moduleId }

/**
 * Discovers software providers once and keeps their manager/prompt pair together.
 *
 * A prompt must be created from the same runtime that was used for availability and legacy
 * authentication. Loading providers independently for those operations can otherwise select a
 * different implementation, or create two SDK sessions with different lifecycle owners.
 */
internal object SoftwareBiometricPromptRegistry {
    private val lock = Any()
    private var enabled = true
    private var discoveryFailures = emptyList<Throwable>()

    @Volatile
    private var cachedRuntimes: List<SoftwareBiometricRuntime>? = null

    @Volatile
    private var cachedSelection: SoftwareBiometricRuntimeSelection? = null

    private fun discover(context: Context): List<SoftwareBiometricRuntime> {
        cachedRuntimes?.let { return it }
        val failures = mutableListOf<Throwable>()
        val runtimes = mutableListOf<SoftwareBiometricRuntime>()
        val complete = load(context, runtimes, failures)
        discoveryFailures = failures.toList()
        // A traversal that could not complete must not become a cached partial provider set.
        if (!complete) {
            runtimes.forEach { runCatching(it::close).onFailure { error -> BiometricLoggerImpl.e(error) } }
            return emptyList()
        }
        cachedRuntimes = runtimes
        return runtimes
    }

    fun retireRuntimes(): List<SoftwareBiometricRuntime> = synchronized(lock) {
        enabled = false
        cachedSelection = null
        val retired = cachedRuntimes.orEmpty()
        cachedRuntimes = null
        discoveryFailures = emptyList()
        retired.onEach(SoftwareBiometricRuntime::retire)
    }

    private fun selection(context: Context): SoftwareBiometricRuntimeSelection? =
        synchronized(lock) {
            if (!enabled) return null
            cachedSelection ?: SoftwareBiometricRuntimeSelection(discover(context))
                .also { if (cachedRuntimes != null) cachedSelection = it }
        }

    fun registeredRuntimes(context: Context): List<SoftwareBiometricRuntime> = synchronized(lock) {
        enabled = true
        selection(context)?.runtimes.orEmpty()
    }

    // Run against every installed provider, including inactive alternatives sharing a modality.
    // LegacyBiometric serializes maintenance with load/unload.
    fun maintain(
        context: Context,
        onlyIfDiscovered: Boolean,
        action: (AbstractSoftwareBiometricManager) -> Unit
    ): Boolean {
        val (known, initialFailures) = synchronized(lock) { cachedRuntimes to discoveryFailures }
        if (known == null && onlyIfDiscovered) return false
        val failures = if (known == null) mutableListOf() else initialFailures.toMutableList()
        val temporary = mutableListOf<SoftwareBiometricRuntime>()
        if (known == null) load(context, temporary, failures)
        (known ?: temporary).forEach { runtime ->
            attemptMaintenance(failures) { action(runtime.manager) }
        }
        temporary.forEach { runtime -> attemptMaintenance(failures, runtime::close) }
        if (failures.isNotEmpty()) {
            val error = IllegalStateException("Software biometric maintenance failed for one or more providers")
            failures.forEach(error::addSuppressed)
            throw error
        }
        return true
    }

    private inline fun attemptMaintenance(failures: MutableList<Throwable>, action: () -> Unit) {
        try { action() }
        catch (error: Exception) { failures.add(error) }
        catch (error: LinkageError) { failures.add(error) }
    }

    fun prepareInitial(
        type: BiometricType,
        context: Context,
        isActive: () -> Boolean,
        onComplete: () -> Unit
    ) {
        val selection = selection(context)
        if (selection != null) selection.prepareInitial(type, isActive, onComplete)
        else if (isActive()) onComplete()
    }

    fun resolve(type: BiometricType, context: Context): SoftwareBiometricRuntime? =
        selection(context)?.resolve(type)

    internal fun select(
        type: BiometricType,
        runtimes: Iterable<SoftwareBiometricRuntime>,
        requirePromptFactory: Boolean,
        allowUnavailable: Boolean,
        allowLockedOut: Boolean = false
    ): SoftwareBiometricRuntime? {
        val matches = runtimes
            .filter { runtime ->
                runtime.manager.biometricType == type &&
                    (!requirePromptFactory || runtime.promptFactory?.biometricType == type)
            }
            .sortedWith(SOFTWARE_RUNTIME_ORDER)

        if (matches.isEmpty()) return null
        return matches.firstOrNull { isAvailable(it.manager, allowLockedOut) }
            ?: matches.firstOrNull().takeIf { allowUnavailable }
    }

    private fun load(
        context: Context,
        result: MutableList<SoftwareBiometricRuntime>,
        failures: MutableList<Throwable>
    ): Boolean {
        val onError = { error: Throwable ->
            failures.add(error)
            BiometricLoggerImpl.e(error, "SoftwareBiometricPromptRegistry.provider")
        }
        return try {
            SoftwareBiometricServices.collect(
                ServiceLoader.load(SoftwareBiometricProvider::class.java), onError
            ) { provider ->
                result.add(provider.createRuntime(context))
                Unit
            }
            true
        } catch (error: java.util.ServiceConfigurationError) {
            onError(error)
            false
        }
    }

    internal fun isAvailable(manager: AbstractSoftwareBiometricManager, allowLockedOut: Boolean = false): Boolean =
        try {
            manager.isHardwareDetected() &&
                (allowLockedOut || manager.getLockoutError() != CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT)
        } catch (error: Throwable) {
            BiometricLoggerImpl.e(error, "SoftwareBiometricPromptRegistry.availability")
            false
        }

}

/**
 * Pin both halves once initial preparation succeeds. Deferred candidates retain lower-priority
 * standbys until a confirmed initial failure; NEW/PREPARING and lockout never authorize fallback.
 * After READY, availability may suppress the runtime but cannot swap its enrollment namespace.
 */
internal class SoftwareBiometricRuntimeSelection(candidates: Iterable<SoftwareBiometricRuntime>) {
    private val byType = candidates.groupBy { it.manager.biometricType }.mapValues { (_, all) ->
        InitialRuntimeChoice(all)
    }

    val runtimes: List<SoftwareBiometricRuntime> = byType.values.flatMap { choice ->
        choice.registered.onEach { runtime ->
            runtime.selectionIsActive = { choice.current() === runtime }
        }
    }

    fun resolve(type: BiometricType): SoftwareBiometricRuntime? = byType[type]?.current()?.takeIf {
        !it.isRetired && it.initialState() != SoftwareBiometricInitializationState.FAILED &&
            it.promptFactory?.biometricType == type && SoftwareBiometricPromptRegistry.isAvailable(it.manager)
    }

    /** Resolve initial readiness before enrollment-based routing can hide an unprepared provider. */
    fun prepareInitial(type: BiometricType, isActive: () -> Boolean, onComplete: () -> Unit) {
        val choices = byType.filterKeys { type == BiometricType.BIOMETRIC_ANY || it == type }.values.toList()
        fun prepareNext(index: Int) {
            if (!isActive()) return
            if (index == choices.size) { onComplete(); return }
            val choice = choices[index]
            val runtime = choice.current()
            if (runtime.isRetired || !runtime.needsInitialPreparation() || !canPrepareInitial(runtime)) {
                prepareNext(index + 1)
                return
            }
            val completed = AtomicBoolean(false)
            val finish = {
                if (completed.compareAndSet(false, true) && isActive()) {
                    // Only the provider's confirmed FAILED state can authorize another runtime.
                    prepareNext(if (!runtime.isRetired && choice.current() !== runtime) index else index + 1)
                }
            }
            try {
                runtime.manager.prepareForAuthentication(object : AbstractSoftwareBiometricManager.PreparationCallback() {
                    override fun onPrepared() = finish()
                    override fun onPreparationError(errMsgId: Int, errString: CharSequence?) = finish()
                    override fun onPreparationCanceled() = finish()
                })
            } catch (_: Exception) { finish() }
            catch (_: LinkageError) { finish() }
        }
        prepareNext(0)
    }
}

private fun SoftwareBiometricRuntime.needsInitialPreparation(): Boolean =
    initialState() == SoftwareBiometricInitializationState.NEW ||
        initialState() == SoftwareBiometricInitializationState.PREPARING

private fun canPrepareInitial(runtime: SoftwareBiometricRuntime): Boolean =
    try {
        // Enrollment and lockout can live in different protected stores. An unreadable
        // enrollment must not authorize preparation followed by a namespace downgrade.
        runtime.manager.getEnrollmentSnapshot().requireReadable()
        runtime.manager.getLockoutError() == null
    }
    catch (_: Exception) { false }
    catch (_: LinkageError) { false }

private class InitialRuntimeChoice(
    candidates: List<SoftwareBiometricRuntime>
) {
    private val ordered = candidates.sortedWith(SOFTWARE_RUNTIME_ORDER)
    private var selected = choose(ordered)!!
    private var pinned = selected.initialState() == SoftwareBiometricInitializationState.READY
    val registered = if (pinned) listOf(selected) else ordered.drop(ordered.indexOf(selected))

    @Synchronized
    fun current(): SoftwareBiometricRuntime {
        while (!pinned) {
            when (selected.initialState()) {
                SoftwareBiometricInitializationState.READY -> pinned = true
                SoftwareBiometricInitializationState.FAILED -> {
                    // Storage errors and any stored lockout must not become a downgrade path.
                    if (!canFallback(selected)) return selected
                    val next = choose(registered.drop(registered.indexOf(selected) + 1))
                        ?: return selected
                    selected = next
                }
                SoftwareBiometricInitializationState.NEW,
                SoftwareBiometricInitializationState.PREPARING -> return selected
            }
        }
        return selected
    }

    private fun choose(candidates: List<SoftwareBiometricRuntime>): SoftwareBiometricRuntime? =
        candidates.firstOrNull {
            if (it.initialState() == SoftwareBiometricInitializationState.FAILED) !canFallback(it)
            else SoftwareBiometricPromptRegistry.isAvailable(it.manager, allowLockedOut = true)
        } ?: candidates.firstOrNull()

    private fun canFallback(runtime: SoftwareBiometricRuntime): Boolean =
        canPrepareInitial(runtime)
}

private fun SoftwareBiometricRuntime.initialState(): SoftwareBiometricInitializationState =
    (manager as? SoftwareBiometricDeferredInitialization)?.initializationState
        ?: SoftwareBiometricInitializationState.READY
