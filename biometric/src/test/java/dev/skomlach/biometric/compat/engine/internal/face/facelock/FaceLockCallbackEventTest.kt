package dev.skomlach.biometric.compat.engine.internal.face.facelock

import org.junit.Assert.*
import org.junit.Test

class FaceLockCallbackEventTest {
    @Test fun allNoArgumentCallbacksDispatchWithoutReadingPayload() {
        val target = Callback()
        for (event in FaceLockCallbackEvent.entries.filter { it != FaceLockCallbackEvent.TIMED_WAKE }) {
            event.capture(target) { error("No duration in this contract") }.invoke()
        }
        assertEquals(listOf("unlock", "cancel", "failed", "fallback", "wake"), target.calls)
    }

    @Test fun timedWakeCapturesDurationBeforeTheBinderParcelIsRecycled() {
        val target = Callback()
        var parcelReadable = true
        val pending = FaceLockCallbackEvent.TIMED_WAKE.capture(target) {
            check(parcelReadable)
            1234
        }
        parcelReadable = false
        assertTrue(target.calls.isEmpty())
        pending()
        assertEquals(listOf("wake:1234"), target.calls)
    }

    @Test fun unreadableDurationCannotDispatchAnyCallback() {
        val target = Callback()
        assertThrows(IllegalStateException::class.java) {
            FaceLockCallbackEvent.TIMED_WAKE.capture(target) { error("missing payload") }
        }
        assertTrue(target.calls.isEmpty())
    }

    @Test fun callbackFailuresAreNotConvertedIntoAnotherAction() {
        val target = object : Callback() { override fun unlock() { throw IllegalStateException("callback failed") } }
        assertThrows(IllegalStateException::class.java) {
            FaceLockCallbackEvent.UNLOCK.capture(target) { 0 }.invoke()
        }
        assertTrue(target.calls.isEmpty())
    }

    private open class Callback : IFaceLockCallback {
        val calls = mutableListOf<String>()
        override fun unlock() { calls += "unlock" }
        override fun cancel() { calls += "cancel" }
        override fun reportFailedAttempt() { calls += "failed" }
        override fun exposeFallback() { calls += "fallback" }
        override fun pokeWakelock(millis: Int) { calls += "wake:$millis" }
        override fun pokeWakelock() { calls += "wake" }
    }
}
