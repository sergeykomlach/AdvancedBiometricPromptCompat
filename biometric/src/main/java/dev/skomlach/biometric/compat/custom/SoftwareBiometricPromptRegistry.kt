package dev.skomlach.biometric.compat.custom

import android.content.Context
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.ServiceLoader

/**
 * Discovers software providers once and keeps their manager/prompt pair together.
 *
 * A prompt must be created from the same runtime that was used for availability and legacy
 * authentication. Loading providers independently for those operations can otherwise select a
 * different implementation, or create two SDK sessions with different lifecycle owners.
 */
internal object SoftwareBiometricPromptRegistry {
    private val lock = Any()

    @Volatile
    private var cachedRuntimes: List<SoftwareBiometricRuntime>? = null

    fun discover(context: Context): List<SoftwareBiometricRuntime> {
        cachedRuntimes?.let { return it }
        return synchronized(lock) {
            cachedRuntimes ?: load(context).also { cachedRuntimes = it }
        }
    }

    fun reset() {
        synchronized(lock) {
            cachedRuntimes = null
        }
    }

    fun resolve(type: BiometricType, context: Context): SoftwareBiometricRuntime? =
        select(type, discover(context), requirePromptFactory = true, allowUnavailable = false)

    internal fun select(
        type: BiometricType,
        runtimes: Iterable<SoftwareBiometricRuntime>,
        requirePromptFactory: Boolean,
        allowUnavailable: Boolean
    ): SoftwareBiometricRuntime? {
        val matches = runtimes
            .filter { runtime ->
                runtime.manager.biometricType == type &&
                    (!requirePromptFactory || runtime.promptFactory?.biometricType == type)
            }
            .sortedWith(
                compareByDescending<SoftwareBiometricRuntime> { it.priority }
                    .thenByDescending { it.promptFactoryPriority }
                    .thenBy { it.moduleId }
            )

        if (matches.isEmpty()) return null
        return matches.firstOrNull { isAvailable(it.manager) }
            ?: matches.firstOrNull().takeIf { allowUnavailable }
    }

    private fun load(context: Context): List<SoftwareBiometricRuntime> {
        val runtimes = mutableListOf<SoftwareBiometricRuntime>()
        try {
            ServiceLoader.load(SoftwareBiometricProvider::class.java).forEach { provider ->
                try {
                    runtimes += provider.createRuntime(context)
                } catch (error: Throwable) {
                    BiometricLoggerImpl.e(error, "SoftwareBiometricPromptRegistry.provider")
                }
            }
        } catch (error: Throwable) {
            BiometricLoggerImpl.e(error, "SoftwareBiometricPromptRegistry.load")
        }
        return runtimes
    }

    private fun isAvailable(manager: AbstractSoftwareBiometricManager): Boolean =
        try {
            manager.isHardwareDetected() &&
                manager.getLockoutError() != CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        } catch (error: Throwable) {
            BiometricLoggerImpl.e(error, "SoftwareBiometricPromptRegistry.availability")
            false
        }

}
