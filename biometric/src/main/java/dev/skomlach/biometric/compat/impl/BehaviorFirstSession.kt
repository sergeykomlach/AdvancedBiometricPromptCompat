package dev.skomlach.biometric.compat.impl

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.SupplementalBiometricResult
import dev.skomlach.biometric.compat.SupplementalBiometricOutcome

/** One operation, two execution scopes. Availability never substitutes for required results. */
internal class BehaviorFirstSession(
    requiredTypes: Set<BiometricType>,
    private val confirmation: BiometricConfirmation,
    private val startStage: (Set<BiometricType>, Callbacks) -> Unit,
    private val stopStage: (done: () -> Unit) -> Unit,
    private val post: (() -> Unit) -> Unit,
    private val finish: (Outcome, Set<AuthenticationResult>) -> Unit,
    behaviorCanAuthorize: Boolean = false,
    eligiblePrimaryTypes: Set<BiometricType> = requiredTypes.filterTo(LinkedHashSet()) {
        it != BiometricType.BIOMETRIC_BEHAVIOR || behaviorCanAuthorize
    },
    private val onSupplemental: (SupplementalBiometricResult) -> Unit = {}
) {
    enum class Outcome { SUCCEEDED, FAILED, CANCELED }
    interface Callbacks {
        fun onSucceeded(results: Set<AuthenticationResult>)
        fun onFailed(results: Set<AuthenticationResult>)
        fun onCanceled(results: Set<AuthenticationResult>)
    }

    private val behavior = setOf(BiometricType.BIOMETRIC_BEHAVIOR)
    private val requested = requiredTypes.toSet()
    private val eligible = eligiblePrimaryTypes.filterTo(LinkedHashSet()) {
        it in requested && (it != BiometricType.BIOMETRIC_BEHAVIOR || behaviorCanAuthorize)
    }
    private val behaviorIsPrimary = BiometricType.BIOMETRIC_BEHAVIOR in eligible
    private val remaining = eligible - behavior
    private var generation = 0
    private var active = true
    private var started = false
    private var behaviorSuccess = false
    private var behaviorResults = emptySet<AuthenticationResult>()

    fun start() {
        if (started || !active) return
        started = true
        if (eligible.isEmpty()) {
            complete(Outcome.FAILED, requested.ifEmpty { setOf(BiometricType.BIOMETRIC_ANY) }.mapTo(LinkedHashSet()) {
                AuthenticationResult(it, reason = AuthenticationFailureReason.INSUFFICIENT_TRUST)
            })
        } else if (BiometricType.BIOMETRIC_BEHAVIOR in requested) start(behavior, true)
        else start(eligible, false)
    }

    fun cancel() { active = false; generation++ }

    private fun start(scope: Set<BiometricType>, first: Boolean) {
        val owner = ++generation
        fun owns() = active && generation == owner
        startStage(scope, object : Callbacks {
            override fun onSucceeded(results: Set<AuthenticationResult>) = result(true, results)
            override fun onFailed(results: Set<AuthenticationResult>) = result(false, results)
            override fun onCanceled(results: Set<AuthenticationResult>) {
                if (first && !behaviorIsPrimary && results.isNotEmpty() &&
                    results.all { it.reason == AuthenticationFailureReason.TIMEOUT }) {
                    result(false, results)
                    return
                }
                if (owns()) complete(Outcome.CANCELED, results)
            }

            private fun result(success: Boolean, received: Set<AuthenticationResult>) {
                if (!owns()) return
                generation++ // Retire callbacks before cancellation can synchronously call back.
                val accepted = received.filterTo(LinkedHashSet()) {
                    it.type in scope || (!first && it.type == BiometricType.BIOMETRIC_ANY)
                }
                val validSuccess = success && accepted.isNotEmpty() && accepted.all { it.reason == null } &&
                    (confirmation == BiometricConfirmation.ANY ||
                        (!first && accepted.any { it.type == BiometricType.BIOMETRIC_ANY }) ||
                        scope.all { type -> accepted.any { it.type == type } })
                val results = if (validSuccess) accepted else accepted.filterTo(LinkedHashSet()) { it.reason != null }
                    .ifEmpty { scope.mapTo(LinkedHashSet()) { AuthenticationResult(it, reason = AuthenticationFailureReason.AUTHENTICATION_FAILED) } }
                if (first) {
                    behaviorSuccess = validSuccess
                    behaviorResults = results
                    if (!behaviorIsPrimary) {
                        val reasons = results.mapNotNullTo(LinkedHashSet()) { it.reason }
                        onSupplemental(SupplementalBiometricResult(BiometricType.BIOMETRIC_BEHAVIOR,
                            when {
                                validSuccess -> SupplementalBiometricOutcome.MATCHED
                                reasons == setOf(AuthenticationFailureReason.AUTHENTICATION_FAILED) -> SupplementalBiometricOutcome.NOT_MATCHED
                                else -> SupplementalBiometricOutcome.UNAVAILABLE
                            }, reasons.toSet()))
                        if (!active) return
                    }
                    if (behaviorIsPrimary && ((confirmation == BiometricConfirmation.ANY && validSuccess) || remaining.isEmpty())) {
                        complete(if (validSuccess) Outcome.SUCCEEDED else Outcome.FAILED, results)
                    } else {
                        val handoffOwner = generation
                        stopStage {
                            post {
                                if (active && generation == handoffOwner) start(remaining, false)
                            }
                        }
                    }
                } else if (confirmation == BiometricConfirmation.ANY || !behaviorIsPrimary) {
                    complete(if (validSuccess) Outcome.SUCCEEDED else Outcome.FAILED, results)
                } else {
                    val combined = behaviorResults + results
                    val both = behaviorSuccess && validSuccess
                    complete(if (both) Outcome.SUCCEEDED else Outcome.FAILED,
                        if (both) combined else combined.filterTo(LinkedHashSet()) { it.reason != null })
                }
            }
        })
    }

    private fun complete(outcome: Outcome, results: Set<AuthenticationResult>) {
        if (!active) return
        cancel()
        finish(outcome, results)
    }
}
