package dev.skomlach.biometric.app.signature

import dev.skomlach.biometric.compat.SupplementalBiometricOutcome
import dev.skomlach.biometric.compat.engine.internal.behavior.*
import org.junit.Assert.*
import org.junit.Test

class SignatureSystemFlowTest {
    private val sample = BehaviorSample(BehaviorMode.SIGNATURE, null, emptyList(), emptyList(), emptyList())
    private class Fixture {
        var behaviorCalls = 0
        var systemCalls = 0
        var behaviorClosed = 0
        var systemClosed = 0
        lateinit var behaviorResult: (SupplementalBiometricOutcome) -> Unit
        lateinit var systemResult: (SystemBiometricOutcome) -> Unit
        val flow = SignatureSystemFlow(
            evaluateBehavior = { _, result -> behaviorCalls++; behaviorResult = result; AutoCloseable { behaviorClosed++ } },
            authenticateSystem = { result -> systemCalls++; systemResult = result; AutoCloseable { systemClosed++ } },
            onState = {})
    }

    @Test fun everySupplementalOutcomeStillRequiresSuccessfulSystemBiometrics() {
        SupplementalBiometricOutcome.entries.forEach { outcome ->
            val f = Fixture()
            assertTrue(f.flow.submit(sample))
            assertFalse(f.flow.state.authorized)
            f.behaviorResult(outcome)
            assertEquals(SignatureSystemStage.SYSTEM, f.flow.state.stage)
            assertEquals(outcome, f.flow.state.behavior)
            assertEquals(1, f.systemCalls)
            assertFalse(f.flow.state.authorized)
            f.systemResult(SystemBiometricOutcome.SUCCEEDED)
            assertTrue(f.flow.state.authorized)
            assertEquals(1, f.behaviorClosed)
            assertEquals(1, f.systemClosed)
        }
    }

    @Test fun systemFailureOrCancellationNeverAuthorizesMatchedBehavior() {
        listOf(SystemBiometricOutcome.FAILED, SystemBiometricOutcome.CANCELED).forEach { outcome ->
            val f = Fixture()
            f.flow.submit(sample)
            f.behaviorResult(SupplementalBiometricOutcome.MATCHED)
            f.systemResult(outcome)
            assertFalse(f.flow.state.authorized)
            assertFalse(f.flow.isRunning)
        }
    }

    @Test fun repeatSubmitAndDuplicateBehaviorResultDoNotOpenAnotherPrompt() {
        val f = Fixture()
        f.flow.submit(sample)
        assertFalse(f.flow.submit(sample))
        f.behaviorResult(SupplementalBiometricOutcome.MATCHED)
        f.behaviorResult(SupplementalBiometricOutcome.NOT_MATCHED)
        assertEquals(1, f.behaviorCalls)
        assertEquals(1, f.systemCalls)
        assertEquals(SupplementalBiometricOutcome.MATCHED, f.flow.state.behavior)
    }

    @Test fun canceledAndPreviousAttemptCallbacksCannotAuthorizeNextAttempt() {
        val f = Fixture()
        f.flow.submit(sample)
        val oldBehavior = f.behaviorResult
        f.flow.cancel()
        oldBehavior(SupplementalBiometricOutcome.MATCHED)
        assertEquals(0, f.systemCalls)
        f.flow.submit(sample)
        oldBehavior(SupplementalBiometricOutcome.MATCHED)
        assertEquals(0, f.systemCalls)
        f.behaviorResult(SupplementalBiometricOutcome.UNAVAILABLE)
        val oldSystem = f.systemResult
        f.flow.reset()
        oldSystem(SystemBiometricOutcome.SUCCEEDED)
        assertFalse(f.flow.state.authorized)
        assertEquals(SignatureSystemStage.READY, f.flow.state.stage)
    }

    @Test fun cancelClearsEvenPreviouslyAuthorizedStateAndCloseIsFinal() {
        val f = Fixture()
        f.flow.submit(sample)
        f.behaviorResult(SupplementalBiometricOutcome.MATCHED)
        f.systemResult(SystemBiometricOutcome.SUCCEEDED)
        f.flow.cancel()
        assertFalse(f.flow.state.authorized)
        f.flow.close(); f.flow.reset()
        assertFalse(f.flow.submit(sample))
    }

    @Test fun behaviorExceptionIsAdvisoryButSystemExceptionIsTerminalFailure() {
        var systemCalls = 0
        val flow = SignatureSystemFlow(
            evaluateBehavior = { _, _ -> throw IllegalStateException("provider unavailable") },
            authenticateSystem = { systemCalls++; throw IllegalStateException("system unavailable") },
            onState = {})
        flow.submit(sample)
        assertEquals(1, systemCalls)
        assertEquals(SupplementalBiometricOutcome.UNAVAILABLE, flow.state.behavior)
        assertEquals(SignatureSystemStage.FAILED, flow.state.stage)
        assertFalse(flow.state.authorized)
    }

    @Test fun synchronousProviderCallbackCannotReplaceSystemCancellationHandle() {
        var behaviorClosed = 0
        var systemClosed = 0
        lateinit var systemResult: (SystemBiometricOutcome) -> Unit
        val flow = SignatureSystemFlow(
            evaluateBehavior = { _, callback -> callback(SupplementalBiometricOutcome.MATCHED); AutoCloseable { behaviorClosed++ } },
            authenticateSystem = { callback -> systemResult = callback; AutoCloseable { systemClosed++ } },
            onState = {})
        flow.submit(sample)
        assertEquals(1, behaviorClosed)
        assertEquals(0, systemClosed)
        flow.cancel()
        assertEquals(1, systemClosed)
        systemResult(SystemBiometricOutcome.SUCCEEDED)
        assertFalse(flow.state.authorized)
    }

    @Test fun observerCancellationBeforeTransportStartPreventsAuthentication() {
        var calls = 0
        lateinit var flow: SignatureSystemFlow
        flow = SignatureSystemFlow({ _, _ -> calls++; AutoCloseable {} }, { calls++; AutoCloseable {} },
            onState = { if (it.stage == SignatureSystemStage.BEHAVIOR) flow.cancel() })
        flow.submit(sample)
        assertEquals(0, calls)
        assertFalse(flow.state.authorized)
    }
}
