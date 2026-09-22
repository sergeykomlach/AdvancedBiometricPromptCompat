package dev.skomlach.biometric.compat

/** Preserve the existing fast-success heuristic without throwing across an app callback. */
internal fun hookDetectionFailure(
    results: Set<AuthenticationResult>,
    detected: Boolean,
    elapsedMillis: Long,
    fastSuccessWindowMillis: Int
): Set<AuthenticationResult>? {
    if (!detected || elapsedMillis > fastSuccessWindowMillis) return null
    // Never expose a successful crypto object through a rejected authentication result.
    return results.map { it.type }.ifEmpty { listOf(null) }.map { type ->
        AuthenticationResult(
            type = type,
            reason = AuthenticationFailureReason.HOOK_DETECTED,
            description = "Authentication rejected by the debugger/hook detection policy"
        )
    }.toSet()
}
