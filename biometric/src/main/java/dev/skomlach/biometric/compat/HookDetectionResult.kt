package dev.skomlach.biometric.compat

/** A known hook is a rejection regardless of how long an injected result waits. */
internal fun hookDetectionFailure(
    results: Set<AuthenticationResult>,
    detected: Boolean
): Set<AuthenticationResult>? {
    if (!detected) return null
    // Never expose a successful crypto object through a rejected authentication result.
    return results.map { it.type }.ifEmpty { listOf(null) }.map { type ->
        AuthenticationResult(
            type = type,
            reason = AuthenticationFailureReason.HOOK_DETECTED,
            description = "Authentication rejected by the debugger/hook detection policy"
        )
    }.toSet()
}
