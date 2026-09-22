package dev.skomlach.biometric.compat.custom

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareBiometricPromptRegistryTest {
    @Test fun unreadableEnrollmentDoesNotStartPreparationEvenWhenLockoutStoreIsReadable() {
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE))
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        manager.snapshotValue = SoftwareBiometricEnrollment.Unavailable()
        val selection = SoftwareBiometricRuntimeSelection(listOf(sherpa))
        var completed = false
        selection.prepareInitial(BiometricType.BIOMETRIC_VOICE, { true }) { completed = true }
        assertTrue(completed)
        assertEquals(0, manager.preparationCalls)
        manager.enrollmentReadFailure = IllegalStateException("template store unreadable")
        selection.prepareInitial(BiometricType.BIOMETRIC_VOICE, { true }) {}
        assertEquals(0, manager.preparationCalls)
    }

    @Test fun enrollmentReadFailureDuringPreparationCannotAuthorizeFallback() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        manager.prepare = { callback ->
            manager.snapshotValue = SoftwareBiometricEnrollment.Unavailable()
            manager.initializationState = SoftwareBiometricInitializationState.FAILED
            manager.hardwareDetected = false
            callback.onPreparationError(AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE, null)
        }
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        selection.prepareInitial(BiometricType.BIOMETRIC_VOICE, { true }) {}
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
        manager.enrollmentReadFailure = IllegalStateException("template store unreadable")
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
    }

    @Test fun failureKnownBeforeDiscoveryCannotBypassUnreadableEnrollment() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99,
            hardwareDetected = false, moduleId = 2)
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.FAILED
        manager.snapshotValue = SoftwareBiometricEnrollment.Unavailable()
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
    }

    @Test fun initialPreparationFindsEnrolledFallbackEvenWhenPreferredStoreIsEmpty() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        val manager = sherpa.manager as FakeManager
        manager.enrolled = false
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        manager.prepare = { callback ->
            manager.initializationState = SoftwareBiometricInitializationState.FAILED
            manager.hardwareDetected = false
            callback.onPreparationError(AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE, null)
        }
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        assertFalse(selection.resolve(BiometricType.BIOMETRIC_VOICE)!!.manager.hasEnrolledBiometric())
        var ready = false
        selection.prepareInitial(BiometricType.BIOMETRIC_ANY, { true }) { ready = true }
        assertTrue(ready)
        assertEquals(1, manager.preparationCalls)
        assertSame(voice, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertTrue(selection.resolve(BiometricType.BIOMETRIC_VOICE)!!.manager.hasEnrolledBiometric())
    }

    @Test fun readyUnenrolledProviderDoesNotBorrowFallbackEnrollment() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        val manager = sherpa.manager as FakeManager
        manager.enrolled = false
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        manager.prepare = { callback ->
            manager.initializationState = SoftwareBiometricInitializationState.READY
            callback.onPrepared()
        }
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        selection.prepareInitial(BiometricType.BIOMETRIC_VOICE, { true }) {}
        assertSame(sherpa, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(manager.hasEnrolledBiometric())
        assertFalse(voice.isSelected)
    }

    @Test fun lateInitialPreparationCannotContinueCanceledFlowOrDeliverTwice() {
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE))
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        var pending: AbstractSoftwareBiometricManager.PreparationCallback? = null
        manager.prepare = { pending = it }
        val selection = SoftwareBiometricRuntimeSelection(listOf(sherpa))
        var active = true
        var completed = 0
        selection.prepareInitial(BiometricType.BIOMETRIC_FACE, { active }) { completed++ }
        assertEquals(0, manager.preparationCalls)
        selection.prepareInitial(BiometricType.BIOMETRIC_VOICE, { active }) { completed++ }
        active = false
        manager.initializationState = SoftwareBiometricInitializationState.READY
        pending!!.onPrepared()
        pending!!.onPrepared()
        assertEquals(1, completed) // Only the unrelated FACE request completed.
    }

    @Test fun initialPreparationDoesNotBypassLockoutOrUnreadableStorage() {
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE))
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        val selection = SoftwareBiometricRuntimeSelection(listOf(sherpa))
        manager.currentLockoutError = AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT
        selection.prepareInitial(BiometricType.BIOMETRIC_VOICE, { true }) {}
        manager.currentLockoutError = null
        manager.lockoutReadFailure = IllegalStateException("unreadable store")
        selection.prepareInitial(BiometricType.BIOMETRIC_VOICE, { true }) {}
        assertEquals(0, manager.preparationCalls)
    }

    @Test fun pendingInitializationRetainsPriorityAndRegistersAFallback() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        (sherpa.manager as FakeManager).initializationState = SoftwareBiometricInitializationState.NEW
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        assertEquals(setOf(sherpa, voice), selection.runtimes.toSet())
        assertSame(sherpa, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertTrue(sherpa.isSelected)
        assertFalse(voice.isSelected)
        sherpa.manager.initializationState = SoftwareBiometricInitializationState.PREPARING
        assertSame(sherpa, selection.resolve(BiometricType.BIOMETRIC_VOICE))
    }

    @Test fun failedDeferredInitializationSwitchesManagerAndPromptTogether() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        manager.initializationState = SoftwareBiometricInitializationState.FAILED
        manager.hardwareDetected = false
        assertSame(voice, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertTrue(voice.isSelected)
        assertFalse(sherpa.isSelected)
        manager.hardwareDetected = true
        assertSame(voice, selection.resolve(BiometricType.BIOMETRIC_VOICE))
    }

    @Test fun successfulDeferredInitializationPinsSelectionAcrossLaterRuntimeFailure() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        manager.initializationState = SoftwareBiometricInitializationState.READY
        assertSame(sherpa, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        manager.hardwareDetected = false
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
    }

    @Test fun failedInitializationCannotBypassStoredLockout() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.NEW
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        manager.initializationState = SoftwareBiometricInitializationState.FAILED
        manager.hardwareDetected = false
        manager.currentLockoutError = AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
        manager.currentLockoutError = AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
        manager.currentLockoutError = AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
        manager.currentLockoutError = null
        manager.lockoutReadFailure = IllegalStateException("storage unavailable")
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
    }

    @Test fun fallbackDeferredProviderMustFinishItsOwnInitialization() {
        val factory = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val first = fakeRuntime(factory, -98, moduleId = 1)
        val second = fakeRuntime(factory, -99, moduleId = 2)
        val voice = fakeRuntime(factory, -100, moduleId = 3)
        val firstManager = first.manager as FakeManager
        val secondManager = second.manager as FakeManager
        firstManager.initializationState = SoftwareBiometricInitializationState.NEW
        secondManager.initializationState = SoftwareBiometricInitializationState.NEW
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, second, first))
        firstManager.initializationState = SoftwareBiometricInitializationState.FAILED
        firstManager.hardwareDetected = false
        assertSame(second, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertTrue(second.isSelected)
        assertFalse(voice.isSelected)
        secondManager.initializationState = SoftwareBiometricInitializationState.PREPARING
        assertSame(second, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        secondManager.initializationState = SoftwareBiometricInitializationState.FAILED
        secondManager.hardwareDetected = false
        assertSame(voice, selection.resolve(BiometricType.BIOMETRIC_VOICE))
    }

    @Test fun failureKnownBeforeDiscoveryCannotBypassStoredLockout() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99,
            hardwareDetected = false, moduleId = 2)
        val manager = sherpa.manager as FakeManager
        manager.initializationState = SoftwareBiometricInitializationState.FAILED
        manager.currentLockoutError = AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
    }

    @Test fun fallbackChainCannotSkipAStoredLockoutOnAFailedStandby() {
        val factory = FakeFactory(BiometricType.BIOMETRIC_VOICE)
        val first = fakeRuntime(factory, -98, moduleId = 1)
        val second = fakeRuntime(factory, -99, hardwareDetected = false, moduleId = 2)
        val voice = fakeRuntime(factory, -100, moduleId = 3)
        val firstManager = first.manager as FakeManager
        val secondManager = second.manager as FakeManager
        firstManager.initializationState = SoftwareBiometricInitializationState.NEW
        secondManager.initializationState = SoftwareBiometricInitializationState.FAILED
        secondManager.currentLockoutError = AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, second, first))
        firstManager.initializationState = SoftwareBiometricInitializationState.FAILED
        firstManager.hardwareDetected = false
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertFalse(voice.isSelected)
    }

    @Test fun promptCannotSwitchAwayFromTheRegisteredManagerAfterInitialization() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        assertSame(sherpa, selection.runtimes.single())
        assertSame(sherpa, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        (sherpa.manager as FakeManager).hardwareDetected = false
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertSame(sherpa, selection.runtimes.single())
    }

    @Test fun initialSherpaFailureSelectsVoiceForBothManagerAndPrompt() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99,
            hardwareDetected = false, moduleId = 2)
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        assertSame(voice, selection.runtimes.single())
        (sherpa.manager as FakeManager).hardwareDetected = true
        assertSame(voice, selection.resolve(BiometricType.BIOMETRIC_VOICE))
        // A new discovery generation may select the now-available engine, never half of a pair.
        assertSame(sherpa, SoftwareBiometricRuntimeSelection(listOf(voice, sherpa)).runtimes.single())
    }

    @Test fun permanentLockoutCannotSilentlyDowngradeAnExistingSelection() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        (sherpa.manager as FakeManager).currentLockoutError =
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
        assertSame(sherpa, selection.runtimes.single())
    }

    @Test fun restartingDiscoveryDoesNotTurnPermanentLockoutIntoFallback() {
        val voice = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -100, moduleId = 1)
        val sherpa = fakeRuntime(FakeFactory(BiometricType.BIOMETRIC_VOICE), -99, moduleId = 2)
        (sherpa.manager as FakeManager).currentLockoutError =
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        val selection = SoftwareBiometricRuntimeSelection(listOf(voice, sherpa))
        assertSame(sherpa, selection.runtimes.single())
        assertNull(selection.resolve(BiometricType.BIOMETRIC_VOICE))
    }
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
        var hardwareDetected: Boolean
    ) : AbstractSoftwareBiometricManager(), SoftwareBiometricDeferredInitialization {
        override var initializationState = SoftwareBiometricInitializationState.READY
        var currentLockoutError: Int? = null
        var lockoutReadFailure: RuntimeException? = null
        var snapshotValue: SoftwareBiometricEnrollment = SoftwareBiometricEnrollment.Available(emptyList())
        var enrollmentReadFailure: RuntimeException? = null
        override fun getEnrollmentSnapshot(): SoftwareBiometricEnrollment {
            enrollmentReadFailure?.let { throw it }
            return snapshotValue
        }
        var enrolled = true
        var preparationCalls = 0
        var prepare: (PreparationCallback) -> Unit = { it.onPrepared() }
        override fun prepareForAuthentication(callback: PreparationCallback) {
            preparationCalls++
            prepare(callback)
        }
        override fun getLockoutError(): Int? {
            lockoutReadFailure?.let { throw it }
            return currentLockoutError
        }
        override fun getTimeoutMessage(): CharSequence? = null

        override fun resetLockOut() = Unit

        override fun resetPermanentLockOut() = Unit

        override fun getPermissions(): List<String> = emptyList()

        override fun isHardwareDetected(): Boolean = hardwareDetected

        override fun hasEnrolledBiometric(): Boolean = enrolled

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
