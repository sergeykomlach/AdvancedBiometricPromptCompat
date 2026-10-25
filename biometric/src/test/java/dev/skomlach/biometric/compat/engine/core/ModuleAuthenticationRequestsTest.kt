package dev.skomlach.biometric.compat.engine.core

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import org.junit.Assert.*
import org.junit.Test

class ModuleAuthenticationRequestsTest {
    @Test fun directReplacementRetiresTheOldListenerAndKeepsCurrentTeardownErrors() {
        val requests = ModuleAuthenticationRequests(7)
        val events = mutableListOf<String>()
        val firstSignal = CancellationSignal()
        val first = requests.bind(recorder(events), firstSignal)
        val secondSignal = CancellationSignal()
        val second = requests.bind(recorder(events), secondSignal)
        assertTrue(firstSignal.isCanceled)
        first.onFailure(7, AuthenticationFailureReason.LOCKED_OUT, null)
        first.onCanceled(7, AuthenticationFailureReason.CANCELED, null)
        assertTrue(events.isEmpty())
        assertSame(second, requests.bind(second, secondSignal))
        assertFalse(secondSignal.isCanceled)
        secondSignal.cancel()
        second.onFailure(7, AuthenticationFailureReason.HARDWARE_UNAVAILABLE, null)
        assertEquals(listOf("failure:HARDWARE_UNAVAILABLE"), events)
    }

    @Test fun directStartAfterCoreOwnedStartCannotLeaveCoreCleanupTargetingTheNewCapture() {
        val requests = ModuleAuthenticationRequests(7)
        val events = mutableListOf<String>()
        val oldSignal = CancellationSignal()
        val coreOwned = ModuleFeedbackListener(recorder(events), 7,
            cancelRequest = { oldSignal.cancel() }) { !oldSignal.isCanceled }
        requests.bind(coreOwned, oldSignal)
        val directSignal = CancellationSignal()
        val direct = requests.bind(recorder(events), directSignal)
        assertTrue(oldSignal.isCanceled)
        coreOwned.onCanceled(7, AuthenticationFailureReason.CANCELED, null)
        coreOwned.cancelAuthentication()
        assertTrue(events.isEmpty())
        assertFalse(directSignal.isCanceled)
        direct.onFailure(7, AuthenticationFailureReason.TIMEOUT, null)
        assertEquals(listOf("failure:TIMEOUT"), events)
    }

    @Test fun reentrantNewerDirectStartSupersedesTheReservedOlderStart() {
        val requests = ModuleAuthenticationRequests(7)
        val initialSignal = CancellationSignal()
        requests.bind(null, initialSignal)
        val latestSignal = CancellationSignal()
        var latest: ModuleFeedbackListener? = null
        initialSignal.setOnCancelListener { latest = requests.bind(null, latestSignal) }
        val supersededSignal = CancellationSignal()
        val superseded = requests.bind(null, supersededSignal)
        assertFalse(superseded.isAuthenticationActive())
        assertTrue(supersededSignal.isCanceled)
        assertTrue(latest!!.isAuthenticationActive())
        assertFalse(latestSignal.isCanceled)
    }

    private fun recorder(events: MutableList<String>) = object : AuthenticationListener {
        override fun onHelp(msg: CharSequence?) = Unit
        override fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?) = Unit
        override fun onFailure(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) { events += "failure:$reason" }
        override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) { events += "canceled" }
    }
}
