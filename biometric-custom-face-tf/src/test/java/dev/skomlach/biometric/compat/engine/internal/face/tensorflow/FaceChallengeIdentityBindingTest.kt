package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.*
import org.junit.Test

class FaceChallengeIdentityBindingTest {
    @Test fun challengeEvidenceCannotTransferToAnotherEnrolledIdentity() {
        val binding = FaceChallengeIdentityBinding()
        assertFalse(binding.update("alice"))
        assertFalse(binding.update("alice"))
        assertTrue(binding.update("bob"))
        assertFalse(binding.update("bob"))
    }

    @Test fun mismatchAndCaptureDiscontinuityDiscardPreviousBinding() {
        val binding = FaceChallengeIdentityBinding()
        binding.update("alice")
        assertTrue(binding.update(null))
        assertFalse(binding.update("alice"))
        binding.reset()
        assertFalse(binding.update("bob"))
    }
}
