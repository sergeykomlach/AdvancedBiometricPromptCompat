package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.SoftwareBiometricSessionGuard
import dev.skomlach.biometric.compat.custom.SoftwareBiometricTerminalState
import org.junit.Assert.*
import org.junit.Test

class SoftwareBiometricCallbackGateTest {
    private val sessions = SoftwareBiometricSessionGuard()
    private val token = sessions.start()
    private var now = 0L
    private var canceled = false
    private val gate = SoftwareBiometricCallbackGate(sessions, token, { canceled }, { now })

    @Test fun repeatedCameraFramesDoNotRepeatTheSameInstruction() {
        val shown = (0L..6_000L step 100L).filter {
            now = it
            gate.tryHelp("Look at the camera")
        }
        assertEquals(listOf(0L, 2_000L, 4_000L, 6_000L), shown)
    }

    @Test fun changedInstructionIsPromptButAlternatingFramesAreBounded() {
        assertTrue(gate.tryHelp("Move closer"))
        now = 100
        assertFalse(gate.tryHelp("Look at the camera"))
        now = 250
        assertTrue(gate.tryHelp("Look at the camera"))
    }

    @Test fun blankHintsDoNotConsumeTheInterval() {
        assertFalse(gate.tryHelp(null))
        assertFalse(gate.tryHelp("  "))
        assertTrue(gate.tryHelp("Look at the camera"))
    }

    @Test fun suppressionOfHelpNeverSuppressesSuccessOrError() {
        assertTrue(gate.tryHelp("Look at the camera"))
        assertFalse(gate.tryHelp("Look at the camera"))
        assertTrue(gate.canDispatch())
    }

    @Test fun canceledAttemptRejectsEveryCallback() {
        canceled = true
        assertFalse(gate.canDispatch())
        assertFalse(gate.tryHelp("Look at the camera"))
    }

    @Test fun oldProviderCannotReportIntoAReplacementSession() {
        sessions.start()
        assertFalse(gate.canDispatch())
        assertFalse(gate.tryHelp("Look at the camera"))
    }

    @Test fun completedSessionRejectsLateHelpErrorsAndSuccess() {
        sessions.tryTerminate(token, SoftwareBiometricTerminalState.SUCCEEDED)
        assertFalse(gate.canDispatch())
        assertFalse(gate.tryHelp("Look at the camera"))
    }

    @Test fun lateErrorCannotMutateLockoutAfterReplacement() {
        var lockoutWrites = 0
        val lateError = { gate.dispatch { lockoutWrites++ } }
        sessions.start()
        assertFalse(lateError())
        assertEquals(0, lockoutWrites)
    }

    @Test fun canceledAttemptCannotRestartCapture() {
        var starts = 0
        val retry = { gate.dispatch { starts++ } }
        canceled = true
        assertFalse(retry())
        assertEquals(0, starts)
    }

    @Test fun acceptedCallbackIsNotThrottledByHelp() {
        var failures = 0
        gate.tryHelp("Try again")
        assertTrue(gate.dispatch { failures++ })
        assertEquals(1, failures)
    }
}
