package dev.skomlach.biometric.compat.custom

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.ServiceLoader

internal object SoftwareBiometricPromptRegistry {
    fun resolve(type: BiometricType): SoftwareBiometricPromptFactory? {
        return try {
            val providers = ServiceLoader.load(SoftwareBiometricProvider::class.java)
            resolve(type, providers)
        } catch (t: Throwable) {
            BiometricLoggerImpl.e(t, "SoftwareBiometricPromptRegistry.resolve")
            null
        }
    }

    internal fun resolve(
        type: BiometricType,
        providers: Iterable<SoftwareBiometricProvider>
    ): SoftwareBiometricPromptFactory? {
        val matches = mutableListOf<Pair<Int, SoftwareBiometricPromptFactory>>()
        providers.forEach { provider ->
            try {
                val factory = provider.getPromptFactory()
                if (factory?.biometricType == type) {
                    matches += provider.promptFactoryPriority to factory
                }
            } catch (t: Throwable) {
                BiometricLoggerImpl.e(t, "SoftwareBiometricPromptRegistry.provider")
            }
        }
        if (matches.isEmpty()) return null
        val sorted = matches.sortedByDescending { it.first }
        if (sorted.size > 1 && sorted[0].first == sorted[1].first) return null
        return sorted.first().second
    }
}
