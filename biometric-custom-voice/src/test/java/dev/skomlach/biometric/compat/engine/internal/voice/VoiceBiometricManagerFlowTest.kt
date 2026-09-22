package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback
import org.junit.Assert.*
import org.junit.Test

class VoiceBiometricManagerFlowTest {
    @Test fun successIsDeliveredImmediatelyAndOnlyOnce() {
        val active = SoftwareBiometricWorkSession()
        val events = mutableListOf<String>()
        val callback = object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
                events += "help"
            }
            override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) {
                assertFalse(active.isActive)
                events += "success"
            }
        }
        val delivery = SoftwareBiometricWorkerCallback(active, callback) { it() }
        delivery.onAuthenticationHelp(0, "accepted")
        delivery.onAuthenticationSucceeded(null)
        assertEquals(listOf("help", "success"), events)
        delivery.onAuthenticationSucceeded(null)
        assertEquals(2, events.size)
    }

    @Test fun cancelledSessionCannotDeliverSuccess() {
        val active = SoftwareBiometricWorkSession().also { it.cancel() }
        SoftwareBiometricWorkerCallback(active, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) {
                fail("cancelled session succeeded")
            }
        }) { it() }.onAuthenticationSucceeded(null)
    }
}
