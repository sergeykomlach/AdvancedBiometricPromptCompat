package dev.skomlach.biometric.compat.engine.core

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.StatusAuthenticationListener
import org.junit.Assert.*
import org.junit.Test

class ModuleFeedbackListenerTest {
    private open class LegacyRecorder : AuthenticationListener {
        var help: CharSequence? = null
        var success: Int? = null
        var failure: AuthenticationFailureReason? = null
        var canceled: AuthenticationFailureReason? = null
        override fun onHelp(msg: CharSequence?) { help = msg }
        override fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?) { success = moduleTag }
        override fun onFailure(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) { failure = reason }
        override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) { canceled = reason }
    }

    @Test fun oldListenersReceiveBothLinesWithoutImplementingNewMethods() {
        val old = LegacyRecorder()
        ModuleFeedbackListener(old, 7) { true }.onStatus(7, SoftwarePromptStatus("one", "two", terminal = true))
        assertEquals("one\ntwo", old.help)
    }

    @Test fun typedListenerKeepsSourceTerminalAndPersistence() {
        val received = mutableListOf<Pair<Int, SoftwarePromptStatus>>()
        val listener = object : LegacyRecorder(), StatusAuthenticationListener {
            override fun onStatus(moduleTag: Int, status: SoftwarePromptStatus) { received.add(moduleTag to status) }
        }
        val bridge = ModuleFeedbackListener(listener, 42) { true }
        bridge.onHelp("hint")
        bridge.onStatus(999, SoftwarePromptStatus("error", "detail", terminal = true, persistent = true))
        assertEquals(listOf(42, 42), received.map { it.first })
        assertTrue(received.last().second.terminal)
        assertTrue(received.last().second.persistent)
        assertEquals("error\ndetail", received.last().second.asLegacyHelpMessage())
    }

    @Test fun canceledModuleCannotSendMoreFeedback() {
        var active = true
        val recorder = LegacyRecorder()
        val bridge = ModuleFeedbackListener(recorder, 7) { active }
        bridge.onHelp("current")
        active = false
        bridge.onHelp("late")
        bridge.onStatus(7, SoftwarePromptStatus("late typed"))
        assertEquals("current", recorder.help)
    }

    @Test fun feedbackDecoratorDoesNotRewriteAuthResultTag() {
        val recorder = LegacyRecorder()
        ModuleFeedbackListener(recorder, 7) { true }.onSuccess(23, null)
        assertEquals(23, recorder.success)
    }

    @Test fun canceledRequestCannotDeliverALateSuccess() {
        var active = true
        val recorder = LegacyRecorder()
        val bridge = ModuleFeedbackListener(recorder, 7) { active }
        active = false
        bridge.onSuccess(23, null)
        assertNull(recorder.success)
    }

    @Test fun replacementRequestDoesNotReactivateOldResultOrRetry() {
        var oldActive = true
        val oldRecorder = LegacyRecorder()
        val oldBridge = ModuleFeedbackListener(oldRecorder, 7) { oldActive }
        oldActive = false
        val currentRecorder = LegacyRecorder()
        val currentBridge = ModuleFeedbackListener(currentRecorder, 7) { true }
        assertFalse(oldBridge.isAuthenticationActive())
        assertTrue(currentBridge.isAuthenticationActive())
        oldBridge.onSuccess(7, null)
        currentBridge.onSuccess(7, null)
        assertNull(oldRecorder.success)
        assertEquals(7, currentRecorder.success)
    }

    @Test fun teardownStillDeliversStorageFailureAndCancellation() {
        val recorder = LegacyRecorder()
        val bridge = ModuleFeedbackListener(recorder, 7) { false }
        bridge.onFailure(7, AuthenticationFailureReason.HARDWARE_UNAVAILABLE, "storage unavailable")
        bridge.onCanceled(7, AuthenticationFailureReason.CANCELED, null)
        assertEquals(AuthenticationFailureReason.HARDWARE_UNAVAILABLE, recorder.failure)
        assertEquals(AuthenticationFailureReason.CANCELED, recorder.canceled)
    }

    @Test fun absentConsumerStillRetainsCancellationForRetries() {
        var active = true
        val bridge = ModuleFeedbackListener(null, 7) { active }
        assertTrue(bridge.isAuthenticationActive())
        active = false
        assertFalse(bridge.isAuthenticationActive())
        bridge.onSuccess(7, null)
        bridge.onCanceled(7, AuthenticationFailureReason.CANCELED, null)
    }

    @Test fun staleQueuedCancellationAndLateSchedulingCannotRemoveCurrentWork() {
        val queue = mutableListOf<Runnable>()
        val oldOwner = ModuleRequestOwner()
        val old = ModuleFeedbackListener(null, 7, oldOwner,
            post = { task, _ -> queue += task }, remove = { queue.remove(it) }) { true }
        var oldRan = false
        old.postCancelTask(Runnable { oldRan = true })
        val alreadyDequeued = queue.removeAt(0)
        oldOwner.supersede()
        val current = ModuleFeedbackListener(null, 7,
            post = { task, _ -> queue += task }, remove = { queue.remove(it) }) { true }
        var currentRan = false
        current.postCancelTask(Runnable { currentRan = true })
        old.postCancelTask(Runnable { fail("Superseded task scheduled") })
        alreadyDequeued.run()
        assertFalse(oldRan)
        assertEquals(1, queue.size)
        queue.removeAt(0).run()
        assertTrue(currentRan)
    }
}
