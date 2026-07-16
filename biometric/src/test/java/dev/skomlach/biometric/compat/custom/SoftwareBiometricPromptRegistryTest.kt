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

    @Test
    fun resolveRejectsAmbiguousTopPriorityFactories() {
        val first = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val second = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val providers = listOf(
            fakeProvider(first, priority = 10),
            fakeProvider(second, priority = 10)
        )

        assertNull(
            SoftwareBiometricPromptRegistry.resolve(
                BiometricType.BIOMETRIC_VOICE,
                providers
            )
        )
    }

    @Test
    fun resolveSelectsHighestPriorityFactoryDeterministically() {
        val low = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val high = FakeFactory(BiometricType.BIOMETRIC_VOICE)

        assertSame(
            high,
            SoftwareBiometricPromptRegistry.resolve(
                BiometricType.BIOMETRIC_VOICE,
                listOf(fakeProvider(low, 1), fakeProvider(high, 2))
            )
        )
    }

    private fun fakeProvider(
        factory: FakeFactory,
        priority: Int
    ): SoftwareBiometricProvider = object : SoftwareBiometricProvider() {
        override val promptFactoryPriority: Int = priority

        override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
            error("unused")
        }

        override fun getPromptFactory(): SoftwareBiometricPromptFactory = factory
    }

    private class FakeFactory(
        override val biometricType: BiometricType
    ) : SoftwareBiometricPromptFactory {
        override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate? = null
    }
}
