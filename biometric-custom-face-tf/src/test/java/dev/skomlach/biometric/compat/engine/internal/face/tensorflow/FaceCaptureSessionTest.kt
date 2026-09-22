package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession
import org.junit.Assert.*
import org.junit.Test

class FaceCaptureSessionTest {
    @Test fun aRunningOldFrameCannotCommitIntoTheReplacementAttempt() {
        val operation = SoftwareBiometricWorkSession()
        val old = FaceCaptureSession(operation)
        val ticket = old.acquireFrame()!!
        operation.cancel()
        val next = FaceCaptureSession(SoftwareBiometricWorkSession())
        var accepted = false
        assertNull(old.commit(ticket) { accepted = true })
        assertFalse(accepted)
        assertNotNull(next.acquireFrame())
        assertNull(old.acquireFrame())
    }

    @Test fun aCaptureGapInvalidatesEvenAnAlreadyRunningInference() {
        val session = FaceCaptureSession(SoftwareBiometricWorkSession())
        val oldTicket = session.acquireFrame()!!
        session.discontinue()
        assertNull(session.commit(oldTicket) { "stale enrollment" })
        session.releaseFrame()
        val newTicket = session.acquireFrame()!!
        assertNotEquals(oldTicket, newTicket)
        assertEquals("fresh frame", session.commit(newTicket) { "fresh frame" })
    }

    @Test fun aBusyWorkerCannotDropTheNoFaceDiscontinuity() {
        val session = FaceCaptureSession(SoftwareBiometricWorkSession())
        val ticket = session.acquireFrame()!!
        assertNull(session.acquireFrame())
        session.discontinue()
        assertFalse(session.owns(ticket))
        session.releaseFrame()
        assertNotNull(session.acquireFrame())
    }
}
