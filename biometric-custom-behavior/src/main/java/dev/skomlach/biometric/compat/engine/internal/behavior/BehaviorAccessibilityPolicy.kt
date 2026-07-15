package dev.skomlach.biometric.compat.engine.internal.behavior

internal enum class BehaviorCaptureTrust {
    STRICT_BUILT_IN,
    COMPATIBILITY_EXTERNAL_VIEW
}

internal enum class BehaviorAccessibilityDecision {
    ALLOW,
    ALLOW_COMPATIBILITY,
    REJECT_UNTRUSTED_SERVICE
}

internal fun evaluateBehaviorAccessibility(
    strict: Boolean,
    hasWhitelistedService: Boolean,
    hasUntrustedService: Boolean
): BehaviorAccessibilityDecision {
    if (!strict) return BehaviorAccessibilityDecision.ALLOW_COMPATIBILITY
    if (hasUntrustedService && !hasWhitelistedService) {
        return BehaviorAccessibilityDecision.REJECT_UNTRUSTED_SERVICE
    }
    return BehaviorAccessibilityDecision.ALLOW
}
