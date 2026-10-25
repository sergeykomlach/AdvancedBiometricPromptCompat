package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import dev.skomlach.biometric.compat.BehaviorAuthMode
import dev.skomlach.biometric.compat.BundleBuilder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

internal const val EXTRA_BEHAVIOR_SESSION_NONCE = "behavior.session_nonce"

internal object BehaviorCaptureSessionRegistry {
    private val random = SecureRandom()
    private data class Capture(val token: BehaviorCaptureSessionToken,
        var sample: BehaviorSample? = null, var enrollment: Boolean = false)
    private val activeSessions = ConcurrentHashMap<Long, Capture>()
    private const val MAX_CAPTURE_DURATION_MS = 30_000L

    @Synchronized fun start(nowMs: Long, uptimeMs: Long = nowMs): BehaviorCaptureSessionToken {
        activeSessions.entries.forEach { (nonce, capture) ->
            if (decision(capture.token, nowMs, MAX_CAPTURE_DURATION_MS) != BehaviorCaptureSessionDecision.ACTIVE)
                activeSessions.remove(nonce, capture)
        }
        while (true) {
            val token = BehaviorCaptureSessionToken(random.nextLong(), nowMs, uptimeMs)
            if (activeSessions.putIfAbsent(token.nonce, Capture(token)) == null) return token
        }
    }

    @Synchronized fun seal(token: BehaviorCaptureSessionToken, sample: BehaviorSample,
        enrollment: Boolean, nowMs: Long): Boolean {
        val capture = activeSessions[token.nonce] ?: return false
        if (capture.token != token || capture.sample != null ||
            decision(token, nowMs, MAX_CAPTURE_DURATION_MS) != BehaviorCaptureSessionDecision.ACTIVE) return false
        val normalized = sample.normalizedForMode()
        if (!normalized.hasValidStructure() || !normalized.hasRequiredDataForMode() ||
            normalized.qualityIssue() != BehaviorQualityIssue.NONE) return false
        capture.sample = normalized.copy(
            keyDownTimesMs = java.util.Collections.unmodifiableList(ArrayList(normalized.keyDownTimesMs)),
            keyUpTimesMs = java.util.Collections.unmodifiableList(ArrayList(normalized.keyUpTimesMs)),
            strokePoints = java.util.Collections.unmodifiableList(ArrayList(normalized.strokePoints)))
        capture.enrollment = enrollment
        return true
    }

    @Synchronized fun consumeSample(nonce: Long, nowMs: Long, enrollment: Boolean): BehaviorSample? {
        val capture = activeSessions.remove(nonce) ?: return null
        if (capture.enrollment != enrollment ||
            decision(capture.token, nowMs, MAX_CAPTURE_DURATION_MS) != BehaviorCaptureSessionDecision.ACTIVE) return null
        return capture.sample
    }

    @Synchronized fun consume(
        nonce: Long,
        nowMs: Long,
        maxDurationMs: Long
    ): BehaviorCaptureSessionDecision {
        val token = activeSessions.remove(nonce)?.token
            ?: return BehaviorCaptureSessionDecision.INVALID_TOKEN
        return decision(token, nowMs, maxDurationMs)
    }

    private fun decision(token: BehaviorCaptureSessionToken, nowMs: Long, maxDurationMs: Long) =
        evaluateBehaviorCaptureSession(
            nowMs = nowMs,
            submitted = false,
            token = token,
            expectedNonce = token.nonce,
            maxDurationMs = maxDurationMs
        )

    fun token(nonce: Long): BehaviorCaptureSessionToken? = activeSessions[nonce]?.token

    fun invalidate(token: BehaviorCaptureSessionToken?) {
        token?.let { activeSessions.remove(it.nonce) }
    }
}

/** Only this opaque handle crosses the core prompt/provider extras boundary. */
internal fun sealBehaviorExtras(existing: Bundle?, sample: BehaviorSample, enroll: Boolean,
    token: BehaviorCaptureSessionToken, nowMs: Long): Bundle? {
    if (!BehaviorCaptureSessionRegistry.seal(token, sample, enroll, nowMs)) return null
    return Bundle(existing ?: Bundle()).apply {
        clearBehaviorInput(this)
        putLong(EXTRA_BEHAVIOR_SESSION_NONCE, token.nonce)
        putBoolean(BundleBuilder.ENROLL, enroll)
    }
}

@Suppress("LongParameterList")
internal fun buildBehaviorExtras(
    existing: Bundle?,
    mode: BehaviorMode,
    phrase: CharSequence?,
    keyDownTimesMs: LongArray,
    keyUpTimesMs: LongArray,
    strokePoints: FloatArray,
    enroll: Boolean,
    sessionToken: BehaviorCaptureSessionToken? = null
): Bundle {
    val extras = Bundle(existing ?: Bundle())
    clearBehaviorInput(extras)
    extras.putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, mode.name)
    phrase.takeIf { mode != BehaviorMode.SIGNATURE }
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { extras.putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, it) }
    extras.putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS,
        if (mode == BehaviorMode.SIGNATURE) longArrayOf() else keyDownTimesMs.copyOf())
    extras.putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_UPS,
        if (mode == BehaviorMode.SIGNATURE) longArrayOf() else keyUpTimesMs.copyOf())
    extras.putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS,
        if (mode == BehaviorMode.TYPING) floatArrayOf() else strokePoints.copyOf())
    extras.putInt(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE, BehaviorSample.POINT_STRIDE)
    extras.putBoolean(BundleBuilder.ENROLL, enroll)
    sessionToken?.let { extras.putLong(EXTRA_BEHAVIOR_SESSION_NONCE, it.nonce) }
    return extras
}

internal fun clearBehaviorInput(extras: Bundle) {
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_MODE)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_PHRASE)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_KEY_UPS)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_POINTS)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE)
    extras.remove(EXTRA_BEHAVIOR_SESSION_NONCE)
}

internal fun shouldInstallBehaviorPrompt(
    authMode: BehaviorAuthMode,
    hasController: Boolean
): Boolean = authMode == BehaviorAuthMode.EXPLICIT && hasController

internal fun isBehaviorPromptReady(
    authMode: BehaviorAuthMode,
    hasController: Boolean,
    hasPreparedPayload: Boolean
): Boolean = !shouldInstallBehaviorPrompt(authMode, hasController) || hasPreparedPayload
