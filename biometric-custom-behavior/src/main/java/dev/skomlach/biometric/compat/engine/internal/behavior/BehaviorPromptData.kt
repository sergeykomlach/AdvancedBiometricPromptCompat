package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import dev.skomlach.biometric.compat.BehaviorAuthMode
import dev.skomlach.biometric.compat.BundleBuilder

internal fun buildBehaviorExtras(
    existing: Bundle?,
    mode: BehaviorMode,
    phrase: CharSequence?,
    keyDownTimesMs: LongArray,
    keyUpTimesMs: LongArray,
    strokePoints: FloatArray,
    enroll: Boolean
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
    return extras
}

internal fun clearBehaviorInput(extras: Bundle) {
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_MODE)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_PHRASE)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_KEY_UPS)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_POINTS)
    extras.remove(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE)
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
