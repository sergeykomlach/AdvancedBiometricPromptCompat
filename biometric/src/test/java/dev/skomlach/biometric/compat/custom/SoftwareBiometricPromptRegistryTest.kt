package dev.skomlach.biometric.compat.custom

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SoftwareBiometricPromptRegistryTest {
    @Test
    fun resolveReturnsRuntimeForRequestedBiometricType() {
        val factory = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val runtime = fakeRuntime(factory, managerPriority = -100)

        val resolved = SoftwareBiometricPromptRegistry.select(
            BiometricType.BIOMETRIC_VOICE,
            listOf(runtime),
            requirePromptFactory = true,
            allowUnavailable = false
        )

        assertSame(runtime, resolved)
        assertSame(factory, resolved?.promptFactory)
    }

    @Test
    fun resolveReturnsNullWhenNoFactoryMatchesType() {
        val runtime = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE))

        val resolved = SoftwareBiometricPromptRegistry.select(
            BiometricType.BIOMETRIC_BEHAVIOR,
            listOf(runtime),
            requirePromptFactory = true,
            allowUnavailable = false
        )

        assertNull(resolved)
    }

    @Test
    fun resolveUsesStableModuleIdForEqualManagerPriority() {
        val first = fakeRuntime(
            FakeFactory(BiometricType.BIOMETRIC_VOICE),
            managerPriority = -100
        )
        val second = fakeRuntime(
            FakeFactory(BiometricType.BIOMETRIC_VOICE),
            managerPriority = -100,
            moduleId = 2
        )

        assertSame(
            first.promptFactory,
            SoftwareBiometricPromptRegistry.select(
                BiometricType.BIOMETRIC_VOICE,
                listOf(first, second),
                requirePromptFactory = true,
                allowUnavailable = false
            )?.promptFactory
        )
    }

    @Test
    fun resolveUsesManagerPriorityBeforePromptFactoryPriority() {
        val voiceFactory = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val sherpaFactory = FakeFactory(BiometricType.BIOMETRIC_VOICE)

        val resolved = SoftwareBiometricPromptRegistry.select(
            BiometricType.BIOMETRIC_VOICE,
            listOf(
                fakeRuntime(
                    factory = voiceFactory,
                    managerPriority = -100,
                    promptFactoryPriority = 100,
                    moduleId = 1
                ),
                fakeRuntime(
                    factory = sherpaFactory,
                    managerPriority = -99,
                    promptFactoryPriority = 0,
                    moduleId = 2
                )
            ),
            requirePromptFactory = true,
            allowUnavailable = false
        )

        assertSame(sherpaFactory, resolved?.promptFactory)
    }

    @Test
    fun resolveFallsBackWhenTheHigherPriorityManagerIsUnavailable() {
        val voiceFactory = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val sherpaFactory = FakeFactory(BiometricType.BIOMETRIC_VOICE)

        val resolved = SoftwareBiometricPromptRegistry.select(
            BiometricType.BIOMETRIC_VOICE,
            listOf(
                fakeRuntime(
                    factory = voiceFactory,
                    managerPriority = -100,
                    moduleId = 1
                ),
                fakeRuntime(
                    factory = sherpaFactory,
                    managerPriority = -99,
                    hardwareDetected = false,
                    moduleId = 2
                )
            ),
            requirePromptFactory = true,
            allowUnavailable = false
        )

        assertSame(voiceFactory, resolved?.promptFactory)
    }

    private fun fakeRuntime(
        factory: FakeFactory,
        managerPriority: Int = 0,
        promptFactoryPriority: Int = 0,
        hardwareDetected: Boolean = true,
        moduleId: Int = 1
    ): SoftwareBiometricRuntime = SoftwareBiometricRuntime(
        moduleId = moduleId,
        manager = FakeManager(
            biometricType = factory.biometricType,
            priority = managerPriority,
            hardwareDetected = hardwareDetected
        ),
        promptFactory = factory,
        promptFactoryPriority = promptFactoryPriority
    )

    private class FakeFactory(
        override val biometricType: BiometricType
    ) : SoftwareBiometricPromptFactory {
        override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate? = null
    }

    private class FakeManager(
        override val biometricType: BiometricType,
        override val priority: Int,
        private val hardwareDetected: Boolean
    ) : AbstractSoftwareBiometricManager() {
        override fun getTimeoutMessage(): CharSequence? = null

        override fun resetLockOut() = Unit

        override fun resetPermanentLockOut() = Unit

        override fun getPermissions(): List<String> = emptyList()

        override fun isHardwareDetected(): Boolean = hardwareDetected

        override fun hasEnrolledBiometric(): Boolean = true

        override fun getManagers(): Set<Any> = emptySet()

        override fun remove(extra: Bundle?) = Unit

        override fun getEnrollBundle(name: String?): Bundle = Bundle()

        override fun getEnrolls(): Collection<String> = emptyList()

        override fun authenticate(
            crypto: CryptoObject?,
            flags: Int,
            cancel: CancellationSignal?,
            callback: AuthenticationCallback?,
            handler: Handler?,
            extra: Bundle?
        ) = Unit
    }
}
