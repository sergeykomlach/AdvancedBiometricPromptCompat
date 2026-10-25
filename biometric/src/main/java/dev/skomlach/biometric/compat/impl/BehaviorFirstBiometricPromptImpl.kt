package dev.skomlach.biometric.compat.impl

import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.common.misc.ExecutorHelper

/** Keeps the public auth flow and its completion owner alive across two backend lifetimes. */
internal class BehaviorFirstBiometricPromptImpl(
    override val builder: BiometricPromptCompat.Builder,
    private val createBackend: () -> IBiometricPromptImpl
) : IBiometricPromptImpl {
    private var current: IBiometricPromptImpl? = null
    private var session: BehaviorFirstSession? = null
    private var generation = 0L
    private var stageGeneration = 0L

    override fun authenticate(callback: BiometricPromptCompat.AuthenticationCallback?) {
        cancelAuthentication()
        val flow = ++generation
        val eligible = builder.getRequiredAuthenticationTypes().toSet()
        val required = builder.getAuthenticationExecutionTypes() + eligible
        session = BehaviorFirstSession(required, builder.getBiometricAuthRequest().confirmation,
            startStage = { types, listener ->
                if (generation == flow) {
                    builder.authenticationStageTypes = types
                    val stage = ++stageGeneration
                    val executable = builder.getAuthenticationExecutionTypes()
                    val missing = types - executable
                    if (executable.isEmpty()) {
                        listener.onFailed(types.mapTo(LinkedHashSet()) {
                            AuthenticationResult(it, reason = AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED)
                        })
                        return@BehaviorFirstSession
                    }
                    val backend = (if (builder.isBehaviorFirstStage()) {
                        if (builder.isSilentAuthEnabled() || builder.getBehaviorAuthMode() == BehaviorAuthMode.PASSIVE)
                            BiometricPromptSilentImpl(builder) else BiometricPromptGenericImpl(builder)
                    } else createBackend()).also { current = it }
                    builder.systemPromptOwnsUi = when (backend) {
                        is BiometricPromptApi28Impl -> !DevicesWithKnownBugs.isMissedBiometricUI
                        is BiometricPromptGenericImpl -> backend.systemPromptOwnsUi
                        else -> false
                    }
                    fun active() = generation == flow && stageGeneration == stage
                    backend.authenticate(object : BiometricPromptCompat.AuthenticationCallback() {
                        override fun onSucceeded(confirmed: Set<AuthenticationResult>) {
                            if (!active()) return
                            if (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && missing.isNotEmpty()) {
                                listener.onFailed(missing.mapTo(LinkedHashSet()) {
                                    AuthenticationResult(it, reason = AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED)
                                })
                            } else listener.onSucceeded(confirmed)
                        }
                        override fun onFailed(canceled: Set<AuthenticationResult>) { if (active()) listener.onFailed(canceled) }
                        override fun onCanceled(canceled: Set<AuthenticationResult>) { if (active()) listener.onCanceled(canceled) }
                        override fun onUIOpened() { if (active()) callback?.onUIOpened() }
                        // Closing a child stage is not completion of the outer authentication.
                        override fun onUIClosed() {}
                    })
                }
            },
            stopStage = { done ->
                stageGeneration++
                val closing = current
                current = null
                if (closing is BiometricPromptGenericImpl) {
                    closing.afterAuthenticationStopped = done
                    closing.cancelAuthentication()
                } else {
                    closing?.cancelAuthentication()
                    done()
                }
            },
            post = { task -> ExecutorHelper.post { if (generation == flow) task() } },
            finish = { outcome, results ->
                if (generation == flow) when (outcome) {
                    BehaviorFirstSession.Outcome.SUCCEEDED -> callback?.onSucceeded(results)
                    BehaviorFirstSession.Outcome.FAILED -> callback?.onFailed(results)
                    BehaviorFirstSession.Outcome.CANCELED -> callback?.onCanceled(results)
                }
            },
            behaviorCanAuthorize = BiometricType.BIOMETRIC_BEHAVIOR in eligible,
            eligiblePrimaryTypes = eligible,
            onSupplemental = { signal -> if (generation == flow) callback?.onSupplementalResult(signal) })
        session?.start()
    }

    override fun cancelAuthentication() {
        generation++
        stageGeneration++
        session?.cancel()
        session = null
        val closing = current
        current = null
        closing?.cancelAuthentication()
        builder.authenticationStageTypes = null
    }
}
