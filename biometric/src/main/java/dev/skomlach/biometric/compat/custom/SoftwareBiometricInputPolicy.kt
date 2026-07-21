package dev.skomlach.biometric.compat.custom

enum class SoftwareBiometricInputDecision {
    ACCEPT,
    REJECT_INVALID_VALUE,
    REJECT_TOO_LARGE,
    REJECT_INVALID_TIME,
    REJECT_EXPIRED
}

object SoftwareBiometricInputPolicy {
    fun validateFinite(value: Double, min: Double, max: Double): SoftwareBiometricInputDecision {
        return if (value.isFinite() && value in min..max) {
            SoftwareBiometricInputDecision.ACCEPT
        } else {
            SoftwareBiometricInputDecision.REJECT_INVALID_VALUE
        }
    }

    fun validateSize(size: Int, maxSize: Int): SoftwareBiometricInputDecision {
        return if (size in 0..maxSize) {
            SoftwareBiometricInputDecision.ACCEPT
        } else {
            SoftwareBiometricInputDecision.REJECT_TOO_LARGE
        }
    }

    fun validateDuration(
        startedAtMs: Long,
        nowMs: Long,
        maxDurationMs: Long
    ): SoftwareBiometricInputDecision {
        if (startedAtMs < 0L || nowMs < 0L || maxDurationMs <= 0L || nowMs < startedAtMs) {
            return SoftwareBiometricInputDecision.REJECT_INVALID_TIME
        }
        return if (nowMs - startedAtMs <= maxDurationMs) {
            SoftwareBiometricInputDecision.ACCEPT
        } else {
            SoftwareBiometricInputDecision.REJECT_EXPIRED
        }
    }
}
