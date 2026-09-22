package dev.skomlach.biometric.compat.utils.activityView

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import dev.skomlach.biometric.compat.engine.core.ModuleFeedbackListener
import dev.skomlach.biometric.compat.engine.core.interfaces.StatusAuthenticationListener
import org.junit.Assert.*
import org.junit.Test

class ForegroundFeedbackSessionTest {
    @Test fun nativePromptRetainsEveryLegacySourceWithoutProviderOptIn() {
        for (source in BiometricType.values().toList() + null) {
            val session = ForegroundFeedbackSession { it() }
            session.setSystemPromptActive(true)
            val supplied = SoftwarePromptStatus("instruction", "detail")
            session.show(source, supplied)
            val message = session.state.current!!
            assertEquals(source, message.source)
            assertEquals("instruction\ndetail", message.status.asLegacyHelpMessage())
            assertFalse(supplied.persistent)
            assertTrue("Source $source must not require a provider opt-in", message.status.persistent)
            session.state.expire(message.id)
            assertEquals(message, session.state.current)

            session.show(source, SoftwarePromptStatus("next state"))
            assertEquals("next state", session.state.current?.status?.primaryText)
            session.finishSource(source)
            assertNull(session.state.current)
        }
    }

    @Test fun nonNativeStageStillHonorsExplicitPersistence() {
        val session = ForegroundFeedbackSession { it() }
        session.show(BiometricType.BIOMETRIC_IRIS, SoftwarePromptStatus("hint"))
        session.state.expire(session.state.current!!.id)
        assertNull(session.state.current)
        session.show(BiometricType.BIOMETRIC_IRIS, SoftwarePromptStatus("instruction", persistent = true))
        session.state.expire(session.state.current!!.id)
        assertEquals("instruction", session.state.current?.status?.primaryText)
    }

    @Test fun nativeTerminalErrorExpiresAndRestoresAnotherLiveLegacySource() {
        val session = ForegroundFeedbackSession { it() }
        session.setSystemPromptActive(true)
        session.show(BiometricType.BIOMETRIC_IRIS, SoftwarePromptStatus("look here"))
        session.show(BiometricType.BIOMETRIC_FINGERPRINT, SoftwarePromptStatus("touch sensor"))
        session.finishSource(BiometricType.BIOMETRIC_FINGERPRINT, SoftwarePromptStatus("unavailable", terminal = true))
        val error = session.state.current!!
        assertTrue(error.status.terminal)
        session.show(BiometricType.BIOMETRIC_FINGERPRINT, SoftwarePromptStatus("late instruction"))
        assertEquals(error, session.state.current)
        session.state.expire(error.id)
        assertEquals(BiometricType.BIOMETRIC_IRIS, session.state.current?.source)
        session.finishSource(BiometricType.BIOMETRIC_IRIS)
        assertNull(session.state.current)
    }

    @Test fun nativeHelpUsesTheSameSourceBridgeAsAnyLegacyModule() {
        val session = ForegroundFeedbackSession { it() }
        session.setSystemPromptActive(true)
        val listener = object : StatusAuthenticationListener {
            override fun onHelp(msg: CharSequence?) = fail("Source must not be lost")
            override fun onStatus(moduleTag: Int, status: SoftwarePromptStatus) {
                assertEquals(42, moduleTag)
                session.show(BiometricType.BIOMETRIC_PALMPRINT, status)
            }
            override fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?) = Unit
            override fun onFailure(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) = Unit
            override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) = Unit
        }
        ModuleFeedbackListener(listener, 42) { true }.onHelp("place palm")
        val message = session.state.current!!
        session.state.expire(message.id)
        assertEquals(message, session.state.current)
        assertEquals(BiometricType.BIOMETRIC_PALMPRINT, message.source)
    }

    @Test fun stoppingNativeStageResetsRetentionAndRejectsQueuedFinishes() {
        val queue = mutableListOf<() -> Unit>()
        val session = ForegroundFeedbackSession { queue.add(it) }
        session.setSystemPromptActive(true)
        session.finishSource(BiometricType.BIOMETRIC_FACE)
        session.clear()
        session.show(BiometricType.BIOMETRIC_FACE, SoftwarePromptStatus("next compat stage"))
        queue.forEach { it() }
        val next = session.state.current!!
        assertFalse(next.status.persistent)
        session.state.expire(next.id)
        assertNull(session.state.current)
    }

    @Test fun finishedSourceRejectsLateStatusUntilTheNextStage() {
        val session = ForegroundFeedbackSession { it() }
        session.setSystemPromptActive(true)
        session.show(BiometricType.BIOMETRIC_FACE, SoftwarePromptStatus("look here"))
        session.finishSource(BiometricType.BIOMETRIC_FACE)
        session.show(BiometricType.BIOMETRIC_FACE, SoftwarePromptStatus("late"))
        assertNull(session.state.current)
        session.clear()
        session.show(BiometricType.BIOMETRIC_FACE, SoftwarePromptStatus("new stage"))
        assertEquals("new stage", session.state.current?.status?.primaryText)
    }

    @Test fun endingNativePromptDoesNotDowngradeAnAlreadyShownInstruction() {
        val session = ForegroundFeedbackSession { it() }
        session.setSystemPromptActive(true)
        session.show(BiometricType.BIOMETRIC_IRIS, SoftwarePromptStatus("look here"))
        session.setSystemPromptActive(false)
        session.state.expire(session.state.current!!.id)
        assertEquals("look here", session.state.current?.status?.primaryText)
        session.show(BiometricType.BIOMETRIC_IRIS, SoftwarePromptStatus("next compat state"))
        session.state.expire(session.state.current!!.id)
        assertNull(session.state.current)
    }

    @Test fun queuedInstructionKeepsTheNativeModeOfItsEvent() {
        val queue = mutableListOf<() -> Unit>()
        val session = ForegroundFeedbackSession { queue.add(it) }
        session.setSystemPromptActive(true)
        session.show(BiometricType.BIOMETRIC_FACE, SoftwarePromptStatus("look here"))
        session.setSystemPromptActive(false)
        queue.forEach { it() }
        val message = session.state.current!!
        session.state.expire(message.id)
        assertEquals(message, session.state.current)
    }

    @Test fun nativeTerminalStatusDoesNotBecomePersistent() {
        val session = ForegroundFeedbackSession { it() }
        session.setSystemPromptActive(true)
        session.show(BiometricType.BIOMETRIC_FINGERPRINT, SoftwarePromptStatus("touch sensor"))
        session.show(BiometricType.BIOMETRIC_FINGERPRINT, SoftwarePromptStatus("timeout", terminal = true))
        val terminal = session.state.current!!
        assertTrue(terminal.status.terminal)
        assertFalse(terminal.status.persistent)
        session.state.expire(terminal.id)
        assertNull(session.state.current)
    }

    @Test fun completedHiddenSourceCannotReappearAfterAnotherSourceFinishes() {
        val session = ForegroundFeedbackSession { it() }
        session.setSystemPromptActive(true)
        session.show(BiometricType.BIOMETRIC_FACE, SoftwarePromptStatus("look here"))
        session.show(BiometricType.BIOMETRIC_IRIS, SoftwarePromptStatus("move closer"))
        session.finishSource(BiometricType.BIOMETRIC_FACE)
        session.finishSource(BiometricType.BIOMETRIC_IRIS)
        assertNull(session.state.current)
    }

    @Test fun stoppedStageRejectsPreviouslyQueuedStatus() {
        val queue = mutableListOf<() -> Unit>()
        val session = ForegroundFeedbackSession { queue.add(it) }
        session.show(BiometricType.BIOMETRIC_VOICE, SoftwarePromptStatus("old stage"))
        session.clear()
        queue.forEach { it() }
        assertNull(session.state.current)
    }

    @Test fun closedRequestCannotPublishToItsReplacement() {
        val queue = mutableListOf<() -> Unit>()
        val old = ForegroundFeedbackSession { queue.add(it) }
        val next = ForegroundFeedbackSession { queue.add(it) }
        old.show(BiometricType.BIOMETRIC_VOICE, SoftwarePromptStatus("old"))
        old.close()
        next.show(BiometricType.BIOMETRIC_FACE, SoftwarePromptStatus("new"))
        queue.forEach { it() }
        assertNull(old.state.current)
        assertEquals("new", next.state.current?.status?.primaryText)
    }

    @Test fun clearedStageCannotClearNextStagesSameSource() {
        val queue = mutableListOf<() -> Unit>()
        val session = ForegroundFeedbackSession { queue.add(it) }
        session.clearSource(BiometricType.BIOMETRIC_VOICE)
        session.clear()
        session.state.show(BiometricType.BIOMETRIC_VOICE, SoftwarePromptStatus("new stage", persistent = true))
        queue.forEach { it() }
        assertEquals("new stage", session.state.current?.status?.primaryText)
    }
}
