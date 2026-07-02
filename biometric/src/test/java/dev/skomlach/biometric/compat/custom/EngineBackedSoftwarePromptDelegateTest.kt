package dev.skomlach.biometric.compat.custom

import android.os.Bundle
import dev.skomlach.biometric.compat.AuthenticationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineBackedSoftwarePromptDelegateTest {

    @Test
    fun startPostsHelpAndSignalsReadyOnce() {
        val callbacks = RecordingCallbacks()
        val delegate = EngineBackedSoftwarePromptDelegate(
            enroll = false,
            callbacks = callbacks
        ) { enroll ->
            if (enroll) "ENROLL_START" else "AUTH_START"
        }

        delegate.start()
        delegate.start()

        assertEquals(listOf("AUTH_START"), callbacks.helpMessages)
        assertEquals(1, callbacks.readyCalls)
        assertTrue(delegate.isReadyToStartAuth())
    }

    @Test
    fun cancelMakesDelegateInert() {
        val callbacks = RecordingCallbacks()
        val delegate = EngineBackedSoftwarePromptDelegate(
            enroll = false,
            callbacks = callbacks
        ) { "AUTH_START" }

        delegate.cancel()
        delegate.start()

        assertTrue(callbacks.helpMessages.isEmpty())
        assertEquals(0, callbacks.readyCalls)
        assertTrue(!delegate.isReadyToStartAuth())
    }

    @Test
    fun disposeMakesDelegateInert() {
        val callbacks = RecordingCallbacks()
        val delegate = EngineBackedSoftwarePromptDelegate(
            enroll = true,
            callbacks = callbacks
        ) { "ENROLL_START" }

        delegate.dispose()
        delegate.start()

        assertTrue(callbacks.helpMessages.isEmpty())
        assertEquals(0, callbacks.readyCalls)
        assertTrue(!delegate.isReadyToStartAuth())
    }

    private class RecordingCallbacks : SoftwareBiometricPromptHost.Callbacks {
        val helpMessages = mutableListOf<String>()
        var readyCalls = 0

        override fun onHelp(message: CharSequence) {
            helpMessages += message.toString()
        }

        override fun onReady(extras: Bundle?) {
            readyCalls += 1
        }

        override fun onFailure(result: AuthenticationResult) = Unit

        override fun isPromptActive(): Boolean = true
    }
}
