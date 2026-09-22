package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceAntiSpoofingWindowTest {
    @Test fun strictWarmupCannotAuthorizeTheThirdMatchingFrame() {
        val window = FaceAntiSpoofingWindow(6, 4, 0.28f)
        repeat(3) { assertEquals(FaceAntiSpoofingDecision.PENDING, window.add(0.1f)) }
        assertEquals(FaceAntiSpoofingDecision.PASS, window.add(0.1f))
    }

    @Test fun spoofWindowNeverBecomesALiveWindowDuringWarmup() {
        val window = FaceAntiSpoofingWindow(5, 3, 0.28f)
        repeat(2) { assertEquals(FaceAntiSpoofingDecision.PENDING, window.add(0.8f)) }
        assertEquals(FaceAntiSpoofingDecision.SPOOF, window.add(0.8f))
    }

    @Test fun invalidInferenceDiscardsPreviousLiveEvidence() {
        for (invalid in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -0.1f, Float.MAX_VALUE)) {
            val window = FaceAntiSpoofingWindow(3, 2, 0.28f)
            window.add(0.1f)
            assertEquals(FaceAntiSpoofingDecision.PASS, window.add(0.1f))
            assertEquals(FaceAntiSpoofingDecision.UNAVAILABLE, window.add(invalid))
            assertEquals(FaceAntiSpoofingDecision.PENDING, window.add(0.1f))
        }
    }

    @Test fun newSessionOrCandidateCannotReuseLiveEvidence() {
        val window = FaceAntiSpoofingWindow(3, 2, 0.28f)
        window.add(0.1f)
        window.add(0.1f)
        window.reset()
        assertEquals(FaceAntiSpoofingDecision.PENDING, window.add(0.1f))
    }

    @Test fun pendingNeverPassesEvenInCompatibilityMode() {
        for (required in listOf(false, true)) {
            assertEquals(false, isFaceLivenessAccepted(FaceAntiSpoofingDecision.PENDING, required))
            assertEquals(false, isFaceLivenessAccepted(FaceAntiSpoofingDecision.SPOOF, required))
        }
        assertEquals(false, isFaceLivenessAccepted(FaceAntiSpoofingDecision.UNAVAILABLE, true))
        assertEquals(true, isFaceLivenessAccepted(FaceAntiSpoofingDecision.UNAVAILABLE, false))
        assertEquals(true, isFaceLivenessAccepted(FaceAntiSpoofingDecision.PASS, true))
    }
}
