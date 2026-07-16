package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import dev.skomlach.biometric.compat.BehaviorAuthMode
import dev.skomlach.biometric.compat.BundleBuilder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

internal const val EXTRA_BEHAVIOR_SESSION_NONCE = "behavior.session_nonce"

internal object BehaviorCaptureSessionRegistry {
    private val random = SecureRandom()
    private val activeSessions = ConcurrentHashMap<Long, BehaviorCaptureSessionToken>()

    fun start(nowMs: Long): BehaviorCaptureSessionToken {
        while (true) {
            val token = BehaviorCaptureSessionToken(random.nextLong(), nowMs)
            if (activeSessions.putIfAbsent(token.nonce, token) == null) return token
        }
    }

    fun consume(
        nonce: Long,
        nowMs: Long,
        maxDurationMs: Long
    ): BehaviorCaptureSessionDecision {
        val token = activeSessions[nonce]
            ?: return BehaviorCaptureSessionDecision.INVALID_TOKEN
        val decision = evaluateBehaviorCaptureSession(
            nowMs = nowMs,
            submitted = false,
            token = token,
            expectedNonce = nonce,
            maxDurationMs = maxDurationMs
        )
        activeSessions.remove(nonce)
        return decision
    }

    fun startedAt(nonce: Long): Long? = activeSessions[nonce]?.startedAtMs

    fun invalidate(token: BehaviorCaptureSessionToken?) {
        token?.let { activeSessions.remove(it.nonce) }
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
    phrase
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { extras.putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, it) }
    extras.putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS, keyDownTimesMs.copyOf())
    extras.putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_UPS, keyUpTimesMs.copyOf())
    extras.putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS, strokePoints.copyOf())
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
