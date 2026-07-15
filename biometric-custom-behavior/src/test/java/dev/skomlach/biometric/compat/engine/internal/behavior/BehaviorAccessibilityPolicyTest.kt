package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorAccessibilityPolicyTest {
    @Test
    fun strictModeRejectsUntrustedAccessibilityService() {
        assertEquals(
            BehaviorAccessibilityDecision.REJECT_UNTRUSTED_SERVICE,
            evaluateBehaviorAccessibility(strict = true, hasWhitelistedService = false, hasUntrustedService = true)
        )
    }

    @Test
    fun whitelistedServiceIsAllowedInStrictMode() {
        assertEquals(
            BehaviorAccessibilityDecision.ALLOW,
            evaluateBehaviorAccessibility(strict = true, hasWhitelistedService = true, hasUntrustedService = true)
        )
    }

    @Test
    fun compatibilityModeDoesNotClaimStrictTrust() {
        assertEquals(
            BehaviorAccessibilityDecision.ALLOW_COMPATIBILITY,
            evaluateBehaviorAccessibility(strict = false, hasWhitelistedService = false, hasUntrustedService = true)
        )
    }
}
