package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

data class ZkFingerConfig(
    val vendorId: Int = 0x1b55,
    val productIds: Set<Int> = setOf(0x0120, 0x0124),
    val deviceIndex: Int = 0,
    val enrollmentScanCount: Int = 3,
    val matchThreshold: Int = 70,
    val maxFailedAttemptsBeforeLockout: Int = 5,
    val maxTemporaryLockoutsBeforePermanent: Int = 3,
    val lockoutDurationMs: Long = 30_000L,
    val helpCooldownMs: Long = 1_000L
)
