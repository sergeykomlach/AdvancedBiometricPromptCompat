package dev.skomlach.biometric.compat.impl

import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.*
import org.junit.Test

class DeferredSoftwarePreparationTest {
    @Test fun rejectedVoiceCannotRemainACompatDialogCandidateBesideAdmittedBehavior() {
        val finger = BiometricType.BIOMETRIC_FINGERPRINT
        val voice = BiometricType.BIOMETRIC_VOICE
        val behavior = BiometricType.BIOMETRIC_BEHAVIOR
        val dialogCandidates = linkedSetOf(finger, voice, behavior)
        applyDeferredSoftwareAdmission(dialogCandidates.toSet(), setOf(finger), setOf(behavior),
            { it == voice || it == behavior }, { dialogCandidates.remove(it) })
        assertEquals(setOf(finger, behavior), dialogCandidates)
    }

    @Test fun timedOutPreparationRemovesOnlyNonNativeSoftwareFromTheFlow() {
        val finger = BiometricType.BIOMETRIC_FINGERPRINT
        val voice = BiometricType.BIOMETRIC_VOICE
        val face = BiometricType.BIOMETRIC_FACE
        val dialogCandidates = linkedSetOf(finger, voice, face)
        applyDeferredSoftwareAdmission(dialogCandidates.toSet(), setOf(finger), emptySet(),
            { it == finger || it == voice }, { dialogCandidates.remove(it) })
        assertEquals(setOf(finger, face), dialogCandidates)
    }

    @Test fun nativeStartsBeforeSoftwareAndOneReadyBatchJoins() {
        val events = mutableListOf<String>()
        var finish: (() -> Unit)? = null
        val gate = DeferredSoftwarePreparation({ true }, { events += "unschedule" })
        gate.start(
            startNative = { events += "native" },
            scheduleTimeout = {},
            prepare = { done -> events += "prepare"; finish = done },
            onFinished = { ready -> events += "join:$ready" }
        )
        assertEquals(listOf("native", "prepare"), events)
        assertTrue(gate.isPending)
        finish!!()
        finish!!()
        assertEquals(listOf("native", "prepare", "unschedule", "join:true"), events)
        assertFalse(gate.isPending)
    }

    @Test fun timeoutSettlesOnlySoftwareAndLateReadinessCannotJoin() {
        var timeout: (() -> Unit)? = null
        var ready: (() -> Unit)? = null
        val results = mutableListOf<Boolean>()
        val gate = DeferredSoftwarePreparation({ true }, {})
        gate.start({}, { timeout = it }, { ready = it }, results::add)
        timeout!!()
        ready!!()
        assertEquals(listOf(false), results)
        assertFalse(gate.isPending)
    }

    @Test fun nativeSuccessOrCancelPreventsLatePreparationAndCapture() {
        val gate = DeferredSoftwarePreparation({ true }, {})
        gate.start({ gate.cancel() }, {}, { fail("must not prepare after native terminal result") },
            { fail("must not admit retired software") })
        assertFalse(gate.isPending)
    }

    @Test fun retiredFlowCannotJoinEvenIfPreparationCompletes() {
        var active = true
        var ready: (() -> Unit)? = null
        val gate = DeferredSoftwarePreparation({ active }, {})
        gate.start({}, {}, { ready = it }, { fail("old flow callback") })
        active = false
        ready!!()
    }

    @Test fun pendingSoftwareNeverDelaysAnySuccessButDelaysFinalFailure() {
        val fingerprint = BiometricType.BIOMETRIC_FINGERPRINT
        fun outcome(result: AuthResult.AuthResultState) = resolveApi28Completion(
            BiometricConfirmation.ANY, setOf(fingerprint), emptySet(), result,
            mapOf(fingerprint to AuthResult(result, null)), softwarePreparationPending = true
        )
        assertEquals(AuthenticationCompletion.SUCCEEDED, outcome(AuthResult.AuthResultState.SUCCESS))
        assertEquals(AuthenticationCompletion.PENDING, outcome(AuthResult.AuthResultState.FATAL_ERROR))
    }

    @Test fun allEnrollmentSilentAndGenericKeepStrictPreparation() {
        assertTrue(canPrepareSoftwareAlongsideNative(false, false, BiometricConfirmation.ANY, true, true))
        assertFalse(canPrepareSoftwareAlongsideNative(true, false, BiometricConfirmation.ANY, true, true))
        assertFalse(canPrepareSoftwareAlongsideNative(false, true, BiometricConfirmation.ANY, true, true))
        assertFalse(canPrepareSoftwareAlongsideNative(false, false, BiometricConfirmation.ALL, true, true))
        assertFalse(canPrepareSoftwareAlongsideNative(false, false, BiometricConfirmation.ANY, false, true))
        assertFalse(canPrepareSoftwareAlongsideNative(false, false, BiometricConfirmation.ANY, true, false))
    }
}
