package dev.skomlach.biometric.compat.utils.activityView

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import org.junit.Assert.*
import org.junit.Test

class ForegroundFeedbackStateTest {
    private val voice = BiometricType.BIOMETRIC_VOICE
    private val face = BiometricType.BIOMETRIC_FACE

    @Test fun persistentInstructionSurvivesAnotherSourcesTransientHint() {
        val state = ForegroundFeedbackState()
        state.show(voice, SoftwarePromptStatus("Say", "the phrase", persistent = true))
        val hint = state.show(face, SoftwarePromptStatus("Move closer"))!!
        assertEquals(face, state.current?.source)
        state.expire(hint.id)
        assertEquals("Say\nthe phrase", state.current?.status?.asLegacyHelpMessage())
        assertEquals(voice, state.current?.source)
    }

    @Test fun staleTimeoutCannotHideReplacement() {
        val state = ForegroundFeedbackState()
        val first = state.show(voice, SoftwarePromptStatus("first"))!!
        state.show(voice, SoftwarePromptStatus("second"))
        state.expire(first.id)
        assertEquals("second", state.current?.status?.primaryText)
    }

    @Test fun duplicateDoesNotRestartTimeoutOrAnimation() {
        val state = ForegroundFeedbackState()
        val first = state.show(voice, SoftwarePromptStatus("same"))!!
        assertNull(state.show(voice, SoftwarePromptStatus(String(charArrayOf('s', 'a', 'm', 'e')))))
        assertEquals(first.id, state.current?.id)
    }

    @Test fun terminalErrorIsNotImmediatelyOverwrittenByAnotherSensor() {
        val state = ForegroundFeedbackState()
        val error = state.show(face, SoftwarePromptStatus("unavailable", terminal = true))!!
        state.show(voice, SoftwarePromptStatus("Say phrase", persistent = true))
        assertTrue(state.current!!.status.terminal)
        state.expire(error.id)
        assertEquals(voice, state.current?.source)
    }

    @Test fun sameSourceNextStageReplacesItsPersistentInstruction() {
        val state = ForegroundFeedbackState()
        state.show(voice, SoftwarePromptStatus("Say phrase", persistent = true))
        val matching = state.show(voice, SoftwarePromptStatus("Matching"))!!
        state.expire(matching.id)
        assertNull(state.current)
    }

    @Test fun closeRejectsLateEventsAndDropsSensitiveText() {
        val state = ForegroundFeedbackState()
        val item = state.show(voice, SoftwarePromptStatus("phrase", persistent = true))!!
        state.close()
        assertNull(state.show(face, SoftwarePromptStatus("late")))
        state.expire(item.id)
        assertNull(state.current)
    }

    @Test fun completingSourceCannotResurrectItsOldInstruction() {
        val state = ForegroundFeedbackState()
        state.show(voice, SoftwarePromptStatus("phrase", persistent = true))
        val hint = state.show(face, SoftwarePromptStatus("hint"))!!
        state.clearSource(voice)
        state.expire(hint.id)
        assertNull(state.current)
    }
}
