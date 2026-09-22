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
        override fun onHelp(msg: CharSequence?) { help = msg }
        override fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?) { success = moduleTag }
        override fun onFailure(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) = Unit
        override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) = Unit
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
}
