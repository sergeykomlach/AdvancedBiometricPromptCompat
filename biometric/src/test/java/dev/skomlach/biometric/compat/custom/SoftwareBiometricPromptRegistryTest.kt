package dev.skomlach.biometric.compat.custom

import android.content.Context
import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SoftwareBiometricPromptRegistryTest {
    @Test
    fun resolveReturnsFactoryForRequestedBiometricType() {
        val factory = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val provider = object : SoftwareBiometricProvider() {
            override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
                error("unused")
            }

            override fun getPromptFactory(): SoftwareBiometricPromptFactory = factory
        }

        val resolved = SoftwareBiometricPromptRegistry.resolve(
            BiometricType.BIOMETRIC_VOICE,
            listOf(provider)
        )

        assertSame(factory, resolved)
    }

    @Test
    fun resolveReturnsNullWhenNoFactoryMatchesType() {
        val provider = object : SoftwareBiometricProvider() {
            override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
                error("unused")
            }
        }

        val resolved = SoftwareBiometricPromptRegistry.resolve(
            BiometricType.BIOMETRIC_BEHAVIOR,
            listOf(provider)
        )

        assertNull(resolved)
    }

    private class FakeFactory(
        override val biometricType: BiometricType
    ) : SoftwareBiometricPromptFactory {
        override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate? = null
    }
}
