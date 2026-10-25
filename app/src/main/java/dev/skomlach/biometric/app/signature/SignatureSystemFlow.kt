package dev.skomlach.biometric.app.signature

import dev.skomlach.biometric.compat.SupplementalBiometricOutcome
import dev.skomlach.biometric.compat.engine.internal.behavior.BehaviorSample

internal enum class SignatureSystemStage { READY, BEHAVIOR, SYSTEM, AUTHORIZED, FAILED, CANCELED }
internal enum class SystemBiometricOutcome { SUCCEEDED, FAILED, CANCELED }
internal data class SignatureSystemState(
    val stage: SignatureSystemStage = SignatureSystemStage.READY,
    val behavior: SupplementalBiometricOutcome? = null
) {
    val authorized: Boolean get() = stage == SignatureSystemStage.AUTHORIZED
}

/** Sample-local orchestration: a software match is evidence, never authorization. Main thread only. */
internal class SignatureSystemFlow(
    private val evaluateBehavior: (BehaviorSample, (SupplementalBiometricOutcome) -> Unit) -> AutoCloseable,
    private val authenticateSystem: ((SystemBiometricOutcome) -> Unit) -> AutoCloseable,
    private val onState: (SignatureSystemState) -> Unit
) : AutoCloseable {
    var state = SignatureSystemState()
        private set
    val isRunning: Boolean get() = state.stage == SignatureSystemStage.BEHAVIOR || state.stage == SignatureSystemStage.SYSTEM
    private var generation = 0L
    private var pending: AutoCloseable? = null
    private var closed = false

    fun submit(sample: BehaviorSample): Boolean {
        if (closed || isRunning) return false
        val owner = ++generation
        publish(SignatureSystemState(SignatureSystemStage.BEHAVIOR))
        if (active(owner, SignatureSystemStage.BEHAVIOR)) try {
            adopt(evaluateBehavior(sample) { behaviorFinished(owner, it) }, owner, SignatureSystemStage.BEHAVIOR)
        } catch (_: Exception) {
            behaviorFinished(owner, SupplementalBiometricOutcome.UNAVAILABLE)
        }
        return true
    }

    private fun behaviorFinished(owner: Long, signal: SupplementalBiometricOutcome) {
        if (!active(owner, SignatureSystemStage.BEHAVIOR)) return
        val previous = pending.also { pending = null }
        // Change phase before cancellation: a synchronous cancellation callback must be stale.
        state = SignatureSystemState(SignatureSystemStage.SYSTEM, signal)
        safelyClose(previous)
        onState(state)
        if (active(owner, SignatureSystemStage.SYSTEM)) try {
            adopt(authenticateSystem { systemFinished(owner, it) }, owner, SignatureSystemStage.SYSTEM)
        } catch (_: Exception) {
            systemFinished(owner, SystemBiometricOutcome.FAILED)
        }
    }

    private fun systemFinished(owner: Long, result: SystemBiometricOutcome) {
        if (!active(owner, SignatureSystemStage.SYSTEM)) return
        val previous = pending.also { pending = null }
        state = state.copy(stage = when (result) {
            SystemBiometricOutcome.SUCCEEDED -> SignatureSystemStage.AUTHORIZED
            SystemBiometricOutcome.FAILED -> SignatureSystemStage.FAILED
            SystemBiometricOutcome.CANCELED -> SignatureSystemStage.CANCELED
        })
        safelyClose(previous)
        onState(state)
    }

    private fun active(owner: Long, stage: SignatureSystemStage) = !closed && generation == owner && state.stage == stage
    private fun adopt(handle: AutoCloseable, owner: Long, stage: SignatureSystemStage) {
        if (active(owner, stage)) pending = handle else safelyClose(handle)
    }
    private fun publish(next: SignatureSystemState) { state = next; onState(next) }
    private fun stop(stage: SignatureSystemStage) {
        generation++
        val previous = pending.also { pending = null }
        state = SignatureSystemState(stage)
        safelyClose(previous)
        onState(state)
    }
    fun cancel() { if (!closed) stop(SignatureSystemStage.CANCELED) }
    fun reset() { if (!closed) stop(SignatureSystemStage.READY) }
    override fun close() {
        if (closed) return
        closed = true
        stop(SignatureSystemStage.CANCELED)
    }
    private fun safelyClose(handle: AutoCloseable?) { runCatching { handle?.close() } }
}
