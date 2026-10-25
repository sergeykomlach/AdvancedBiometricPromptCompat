package dev.skomlach.biometric.compat.impl

import dev.skomlach.biometric.compat.*
import org.junit.Assert.*
import org.junit.Test

class BehaviorFirstSessionTest {
    private val behavior = BiometricType.BIOMETRIC_BEHAVIOR
    private val finger = BiometricType.BIOMETRIC_FINGERPRINT
    private val success = setOf(AuthenticationResult(behavior))
    private val failure = setOf(AuthenticationResult(behavior, reason = AuthenticationFailureReason.AUTHENTICATION_FAILED))

    private inner class Flow(mode: BiometricConfirmation, types: Set<BiometricType> = setOf(behavior, finger),
                             allowBehavior: Boolean = false, eligible: Set<BiometricType>? = null) {
        val stages = mutableListOf<Pair<Set<BiometricType>, BehaviorFirstSession.Callbacks>>()
        val pending = mutableListOf<() -> Unit>()
        var stopped: (() -> Unit)? = null
        val terminal = mutableListOf<Pair<BehaviorFirstSession.Outcome, Set<AuthenticationResult>>>()
        val signals = mutableListOf<SupplementalBiometricResult>()
        val session = BehaviorFirstSession(types, mode,
            { scope, callback -> stages.add(scope to callback) },
            { done -> stopped = done }, { pending.add(it) },
            { outcome, results -> terminal.add(outcome to results) },
            behaviorCanAuthorize = allowBehavior,
            eligiblePrimaryTypes = eligible ?: types.filterTo(LinkedHashSet()) { it != behavior || allowBehavior },
            onSupplemental = signals::add)
        init { session.start() }
        fun handoff() { stopped!!.invoke(); pending.removeAt(0).invoke() }
    }

    @Test fun defaultAnyBehaviorSuccessMustStillStartBiometrics() {
        val flow = Flow(BiometricConfirmation.ANY)
        assertEquals(setOf(behavior), flow.stages.single().first)
        flow.stages[0].second.onSucceeded(success)
        assertTrue(flow.terminal.isEmpty())
        flow.handoff()
        assertEquals(setOf(finger), flow.stages[1].first)
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        assertEquals(setOf(AuthenticationResult(finger)), flow.terminal.single().second)
    }

    @Test fun anyMismatchWaitsForTeardownThenUsesBiometricResult() {
        val flow = Flow(BiometricConfirmation.ANY)
        flow.stages[0].second.onFailed(failure)
        assertTrue(flow.terminal.isEmpty())
        assertEquals(1, flow.stages.size)
        flow.handoff()
        assertEquals(setOf(finger), flow.stages[1].first)
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        assertEquals(setOf(AuthenticationResult(finger)), flow.terminal.single().second)
    }

    @Test fun nativeAggregateSuccessCompletesTheRemainingStage() {
        val flow = Flow(BiometricConfirmation.ALL)
        flow.stages[0].second.onSucceeded(success)
        flow.handoff()
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(BiometricType.BIOMETRIC_ANY)))
        assertEquals(BehaviorFirstSession.Outcome.SUCCEEDED, flow.terminal.single().first)
    }

    @Test fun defaultAllSupplementalFailureDoesNotVetoSuccessfulPrimary() {
        val flow = Flow(BiometricConfirmation.ALL)
        flow.stages[0].second.onFailed(failure)
        assertTrue(flow.terminal.isEmpty())
        flow.handoff()
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        assertEquals(BehaviorFirstSession.Outcome.SUCCEEDED, flow.terminal.single().first)
        assertEquals(setOf(AuthenticationResult(finger)), flow.terminal.single().second)
    }

    @Test fun defaultAllReturnsOnlyPrimaryAuthenticationResults() {
        val flow = Flow(BiometricConfirmation.ALL)
        flow.stages[0].second.onSucceeded(success)
        flow.handoff()
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        assertEquals(BehaviorFirstSession.Outcome.SUCCEEDED, flow.terminal.single().first)
        assertEquals(setOf(finger), flow.terminal.single().second.map { it.type }.toSet())
    }

    @Test fun allSecondStageFailurePreservesItsReason() {
        val flow = Flow(BiometricConfirmation.ALL)
        flow.stages[0].second.onSucceeded(success)
        flow.handoff()
        val error = setOf(AuthenticationResult(finger, reason = AuthenticationFailureReason.LOCKED_OUT))
        flow.stages[1].second.onFailed(error)
        assertEquals(BehaviorFirstSession.Outcome.FAILED to error, flow.terminal.single())
    }

    @Test fun userCancelNeverStartsFallback() {
        val flow = Flow(BiometricConfirmation.ANY)
        flow.stages[0].second.onCanceled(setOf(AuthenticationResult(behavior, reason = AuthenticationFailureReason.CANCELED)))
        assertEquals(BehaviorFirstSession.Outcome.CANCELED, flow.terminal.single().first)
        assertNull(flow.stopped)
    }

    @Test fun cancelDuringHandoffAndLateCallbacksCannotStartOrFinishAnythingElse() {
        val flow = Flow(BiometricConfirmation.ALL)
        val old = flow.stages[0].second
        old.onFailed(failure)
        flow.session.cancel()
        flow.handoff()
        old.onSucceeded(success)
        assertEquals(1, flow.stages.size)
        assertTrue(flow.terminal.isEmpty())
    }

    @Test fun duplicateFirstStageResultsAndDuplicateTeardownAreIgnored() {
        val flow = Flow(BiometricConfirmation.ALL)
        val old = flow.stages[0].second
        old.onSucceeded(success)
        old.onFailed(failure)
        flow.stopped!!.invoke()
        flow.stopped!!.invoke()
        flow.pending.toList().forEach { it() }
        assertEquals(2, flow.stages.size)
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        flow.stages[1].second.onFailed(emptySet())
        assertEquals(1, flow.terminal.size)
    }

    @Test fun behaviorOnlyFailureDoesNotWaitForNonexistentBiometrics() {
        val flow = Flow(BiometricConfirmation.ALL, setOf(behavior))
        assertTrue(flow.stages.isEmpty())
        assertEquals(BehaviorFirstSession.Outcome.FAILED, flow.terminal.single().first)
        assertEquals(AuthenticationFailureReason.INSUFFICIENT_TRUST, flow.terminal.single().second.single().reason)
    }

    @Test fun explicitBehaviorAuthorizationCanCompleteAnyWithoutOtherProvider() {
        val flow = Flow(BiometricConfirmation.ANY, setOf(behavior), allowBehavior = true)
        flow.stages[0].second.onSucceeded(success)
        assertEquals(BehaviorFirstSession.Outcome.SUCCEEDED to success, flow.terminal.single())
        assertTrue(flow.signals.isEmpty())
    }

    @Test fun explicitBehaviorRoleDoesNotOverrideEmptyPolicyAdmission() {
        val flow = Flow(BiometricConfirmation.ANY, setOf(behavior), allowBehavior = true, eligible = emptySet())
        assertTrue(flow.stages.isEmpty())
        assertEquals(BehaviorFirstSession.Outcome.FAILED, flow.terminal.single().first)
    }

    @Test fun explicitAllKeepsBehaviorAsRequiredFactor() {
        val flow = Flow(BiometricConfirmation.ALL, allowBehavior = true)
        flow.stages[0].second.onFailed(failure)
        flow.handoff()
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        assertEquals(BehaviorFirstSession.Outcome.FAILED to failure, flow.terminal.single())
    }

    @Test fun supplementalTimeoutIsEvidenceAndDoesNotVetoAll() {
        val flow = Flow(BiometricConfirmation.ALL)
        val callback = flow.stages[0].second
        callback.onFailed(setOf(AuthenticationResult(behavior, reason = AuthenticationFailureReason.TIMEOUT)))
        assertEquals(SupplementalBiometricOutcome.UNAVAILABLE, flow.signals.single().outcome)
        callback.onSucceeded(success)
        assertEquals(1, flow.signals.size)
        flow.handoff()
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        assertEquals(BehaviorFirstSession.Outcome.SUCCEEDED, flow.terminal.single().first)
    }

    @Test fun supplementalProviderTimeoutCancellationStillContinuesPrimary() {
        val flow = Flow(BiometricConfirmation.ANY)
        flow.stages[0].second.onCanceled(setOf(AuthenticationResult(behavior, reason = AuthenticationFailureReason.TIMEOUT)))
        assertTrue(flow.terminal.isEmpty())
        assertEquals(SupplementalBiometricOutcome.UNAVAILABLE, flow.signals.single().outcome)
        flow.handoff()
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        assertEquals(BehaviorFirstSession.Outcome.SUCCEEDED, flow.terminal.single().first)
    }

    @Test fun allDoesNotRequireProvidersExcludedBeforeTheOperation() {
        val flow = Flow(BiometricConfirmation.ALL, setOf(behavior, finger, BiometricType.BIOMETRIC_VOICE),
            eligible = setOf(finger))
        flow.stages[0].second.onSucceeded(success)
        flow.handoff()
        assertEquals(setOf(finger), flow.stages[1].first)
        flow.stages[1].second.onSucceeded(setOf(AuthenticationResult(finger)))
        assertEquals(BehaviorFirstSession.Outcome.SUCCEEDED, flow.terminal.single().first)
    }

    @Test fun emptyOrUnexpectedSuccessCannotSatisfyRequiredStage() {
        val flow = Flow(BiometricConfirmation.ALL)
        flow.stages[0].second.onSucceeded(setOf(AuthenticationResult(finger)))
        flow.handoff()
        flow.stages[1].second.onSucceeded(emptySet())
        assertEquals(BehaviorFirstSession.Outcome.FAILED, flow.terminal.single().first)
    }
}
