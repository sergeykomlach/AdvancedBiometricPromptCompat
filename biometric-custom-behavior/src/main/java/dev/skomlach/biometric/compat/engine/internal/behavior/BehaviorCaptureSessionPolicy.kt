package dev.skomlach.biometric.compat.engine.internal.behavior

internal data class BehaviorCaptureSessionToken(
    val nonce: Long,
    val startedAtMs: Long,
    val startedAtUptimeMs: Long = startedAtMs
) {
    // InputEvent timestamps use uptime; keep payload values small before Float encoding.
    fun relativeUptime(eventUptimeMs: Long): Long = eventUptimeMs - startedAtUptimeMs
}

internal enum class BehaviorCaptureSessionDecision {
    ACTIVE,
    EXPIRED,
    INVALID_TOKEN,
    ALREADY_SUBMITTED
}

@Suppress("ReturnCount")
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
