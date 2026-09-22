package dev.skomlach.biometric.compat.impl

import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession
import java.util.concurrent.atomic.AtomicBoolean

internal fun canPrepareSoftwareAlongsideNative(
    enroll: Boolean,
    silent: Boolean,
    confirmation: BiometricConfirmation,
    hasNativeRoute: Boolean,
    usesNativeImplementation: Boolean
): Boolean = !enroll && !silent && confirmation == BiometricConfirmation.ANY &&
    hasNativeRoute && usesNativeImplementation

/** The compat dialog and engine routing must see the same admitted set as native completion. */
internal fun applyDeferredSoftwareAdmission(
    availableTypes: Set<BiometricType>,
    nativeTypes: Set<BiometricType>,
    admittedTypes: Set<BiometricType>,
    isSoftware: (BiometricType) -> Boolean,
    disableForFlow: (BiometricType) -> Unit
) {
    availableTypes.filter { it !in nativeTypes && it !in admittedTypes && isSoftware(it) }
        .forEach(disableForFlow)
}

/** One optional branch. Timeout revokes this flow's admission, never the provider's initial state. */
internal class DeferredSoftwarePreparation(
    private val ownsFlow: () -> Boolean,
    private val unscheduleTimeout: () -> Unit
) {
    private val session = SoftwareBiometricWorkSession()
    private val started = AtomicBoolean(false)
    val isPending: Boolean get() = session.isActive
    val canContinue: Boolean get() = isPending && ownsFlow()

    fun start(
        startNative: () -> Unit,
        scheduleTimeout: (() -> Unit) -> Unit,
        prepare: (() -> Unit) -> Unit,
        onFinished: (Boolean) -> Unit
    ) {
        if (!canContinue || !started.compareAndSet(false, true)) return
        fun finish(ready: Boolean) {
            if (!session.complete()) return
            unscheduleTimeout()
            if (ownsFlow()) onFinished(ready)
        }
        startNative()
        if (!canContinue) return
        scheduleTimeout { finish(false) }
        try { prepare { finish(true) } }
        catch (_: Exception) { finish(false) }
        catch (_: LinkageError) { finish(false) }
    }

    fun cancel() {
        session.cancel()
        unscheduleTimeout()
    }
}
