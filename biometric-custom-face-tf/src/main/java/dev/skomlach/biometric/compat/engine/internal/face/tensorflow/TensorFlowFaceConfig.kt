/*
 *  Copyright (c) 2025 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import androidx.annotation.FloatRange
import androidx.annotation.IntRange

/**
 * Public configuration for TensorFlow face recognition / anti-spoofing pipeline.
 *
 * Most callers should only need [performanceProfile] and perhaps [maxDistanceThresholds].
 *
 * Advanced users may override individual thresholds and backend policy.
 */
enum class TensorFlowPerformanceProfile {
    FAST,
    BALANCED,
    STRICT,
    AUTO
}

enum class TensorFlowBackendPreference {
    AUTO,
    CPU_ONLY,
    GPU_PREFERRED,
    GPU_ONLY
}

enum class AntiSpoofingMode {
    OFF,
    AUTO,
    BEFORE_RECOGNITION,
    AFTER_CANDIDATE
}

enum class DevicePerformanceClass {
    LOW_END,
    MID_RANGE,
    HIGH_END
}

data class TensorFlowFaceConfig(
    @FloatRange(from = 0.5, to = 1.0)
    val maxDistanceThresholds: Float = 0.74f,
    @IntRange(from = 1)
    val requiredConsecutiveMatches: Int = 2,
    val performanceProfile: TensorFlowPerformanceProfile = TensorFlowPerformanceProfile.AUTO,
    val backendPreference: TensorFlowBackendPreference = TensorFlowBackendPreference.AUTO,
    val antiSpoofingMode: AntiSpoofingMode = AntiSpoofingMode.AUTO,
    val allowAdaptiveDeviceTuning: Boolean = true,
    val strictPolicyOnModernDevices: Boolean = true,
    val relaxedPolicyOnLowEndDevices: Boolean = true,
    @FloatRange(from = 0.0, to = 45.0)
    val maxHeadAngleX: Float = 20f,
    @FloatRange(from = 0.0, to = 45.0)
    val maxHeadAngleY: Float = 20f,
    @IntRange(from = 64)
    val minFaceSizePx: Int = 160,
    @IntRange(from = 0, to = 255)
    val minBrightnessLuma: Int = 52,
    @IntRange(from = 0)
    val minLaplacianScore: Int = 1100,
    @FloatRange(from = 1.0, to = 2.5)
    val recognitionCropScale: Float = 1.28f,
    @FloatRange(from = 1.0, to = 3.0)
    val livenessCropScale: Float = 1.55f,
    val antiSpoofingEnabled: Boolean = true,
    @FloatRange(from = 0.0, to = 1.0)
    val antiSpoofingScoreThreshold: Float = 0.26f,
    @IntRange(from = 1)
    val antiSpoofingWindowSize: Int = 5,
    @IntRange(from = 1)
    val antiSpoofingMinFramesToDecide: Int = 3,
    @IntRange(from = 1)
    val antiSpoofingFrameStride: Int = 2,
    @IntRange(from = 1)
    val antiSpoofingWarmupMatches: Int = 1,
    val antiSpoofingOnEnrollment: Boolean = true,
    val antiSpoofingOnAuthentication: Boolean = true,
    @IntRange(from = 0)
    val errorCooldownMs: Long = 2_200L,
    @IntRange(from = 1)
    val maxFailedAttemptsBeforeLockout: Int = 5,
    @IntRange(from = 1)
    val maxTemporaryLockoutsBeforePermanent: Int = 5,
    @IntRange(from = 1)
    val lockoutDurationMs: Long = 30_000L,
    val countFailedAttemptsForDistantMismatches: Boolean = false,
    @FloatRange(from = 0.0, to = 0.5)
    val mismatchGraceDistanceDelta: Float = 0.06f,
    val recognitionCpuThreads: Int? = 4,
    val antiSpoofingCpuThreads: Int? = 2,
    val forceDeviceClass: DevicePerformanceClass? = null
) {
    init {
        require(maxDistanceThresholds in 0.5f..1.0f) {
            "maxDistanceThresholds must be between 0.5 and 1.0, but was $maxDistanceThresholds"
        }
        require(requiredConsecutiveMatches > 0) {
            "requiredConsecutiveMatches must be positive, but was $requiredConsecutiveMatches"
        }
        require(maxHeadAngleX in 0f..45f) { "maxHeadAngleX must be in 0..45" }
        require(maxHeadAngleY in 0f..45f) { "maxHeadAngleY must be in 0..45" }
        require(minFaceSizePx >= 64) { "minFaceSizePx must be >= 64" }
        require(minBrightnessLuma in 0..255) { "minBrightnessLuma must be in 0..255" }
        require(minLaplacianScore >= 0) { "minLaplacianScore must be >= 0" }
        require(recognitionCropScale >= 1f) { "recognitionCropScale must be >= 1.0" }
        require(livenessCropScale >= 1f) { "livenessCropScale must be >= 1.0" }
        require(antiSpoofingScoreThreshold in 0f..1f) {
            "antiSpoofingScoreThreshold must be in 0..1"
        }
        require(antiSpoofingWindowSize >= antiSpoofingMinFramesToDecide) {
            "antiSpoofingWindowSize must be >= antiSpoofingMinFramesToDecide"
        }
        require(antiSpoofingFrameStride > 0) { "antiSpoofingFrameStride must be > 0" }
        require(antiSpoofingWarmupMatches > 0) { "antiSpoofingWarmupMatches must be > 0" }

        require(errorCooldownMs >= 0) { "errorCooldownMs must be >= 0" }
        require(maxFailedAttemptsBeforeLockout > 0) { "maxFailedAttemptsBeforeLockout must be > 0" }
        require(maxTemporaryLockoutsBeforePermanent > 0) {
            "maxTemporaryLockoutsBeforePermanent must be > 0"
        }
        require(lockoutDurationMs > 0) { "lockoutDurationMs must be > 0" }
        require(mismatchGraceDistanceDelta in 0f..0.5f) {
            "mismatchGraceDistanceDelta must be in 0..0.5"
        }
        recognitionCpuThreads?.let { require(it > 0) { "recognitionCpuThreads must be > 0" } }
        antiSpoofingCpuThreads?.let { require(it > 0) { "antiSpoofingCpuThreads must be > 0" } }
    }
}
