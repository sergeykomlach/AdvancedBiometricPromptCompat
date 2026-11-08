package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorAccessibilityPolicyTest {
    @Test
    fun strictModeRejectsUntrustedAccessibilityService() {
        assertEquals(
            BehaviorAccessibilityDecision.REJECT_UNTRUSTED_SERVICE,
            evaluateBehaviorAccessibility(strict = true, hasUntrustedService = true)
        )
    }

    @Test
    fun strictModeAllowsTrustedAccessibilityState() {
        assertEquals(
            BehaviorAccessibilityDecision.ALLOW,
            evaluateBehaviorAccessibility(strict = true, hasUntrustedService = false)
        )
    }

    @Test
    fun compatibilityModeDoesNotClaimStrictTrust() {
        assertEquals(
            BehaviorAccessibilityDecision.ALLOW_COMPATIBILITY,
            evaluateBehaviorAccessibility(strict = false, hasUntrustedService = true)
        )
    }
}
