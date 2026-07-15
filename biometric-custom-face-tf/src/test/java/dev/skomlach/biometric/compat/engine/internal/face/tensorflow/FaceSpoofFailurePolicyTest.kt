package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSpoofFailurePolicyTest {
    @Test
    fun authenticationSpoofCountsAsFailedAttempt() {
        assertTrue(shouldCountFaceSpoofFailure(isEnrolling = false))
    }

    @Test
    fun enrollmentSpoofDoesNotConsumeAuthenticationLockoutBudget() {
        assertFalse(shouldCountFaceSpoofFailure(isEnrolling = true))
    }
}
