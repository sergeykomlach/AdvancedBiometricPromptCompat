package dev.skomlach.biometric.compat.engine.internal.behavior

internal data class BehaviorCaptureSessionToken(
    val nonce: Long,
    val startedAtMs: Long
)

internal enum class BehaviorCaptureSessionDecision {
    ACTIVE,
    EXPIRED,
    INVALID_TOKEN,
    ALREADY_SUBMITTED
}

internal fun evaluateBehaviorCaptureSession(
    nowMs: Long,
    submitted: Boolean,
    token: BehaviorCaptureSessionToken,
    expectedNonce: Long,
    maxDurationMs: Long
): BehaviorCaptureSessionDecision {
    if (submitted) return BehaviorCaptureSessionDecision.ALREADY_SUBMITTED
    if (token.nonce != expectedNonce) return BehaviorCaptureSessionDecision.INVALID_TOKEN
    if (nowMs < token.startedAtMs || nowMs - token.startedAtMs >= maxDurationMs) {
        return BehaviorCaptureSessionDecision.EXPIRED
    }
    return BehaviorCaptureSessionDecision.ACTIVE
}
