package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.os.Build
import dev.skomlach.common.logging.LogCat
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

enum class TfBackend {
    GPU,
    CPU
}

data class TfBackendSelection(
    val backend: TfBackend,
    val threads: Int,
    val reason: String
)

data class TfBackendRationality(
    val selection: TfBackendSelection,
    val antiSpoofingAllowed: Boolean,
    val maxRecommendedAntiSpoofingMs: Long,
    val reason: String
)

data class EffectiveTensorFlowFaceConfig(
    val base: TensorFlowFaceConfig,
    val deviceClass: DevicePerformanceClass,
    val profile: TensorFlowPerformanceProfile,
    val recognitionBackendPreference: TensorFlowBackendPreference,
    val antiSpoofingBackendPreference: TensorFlowBackendPreference,
    val antiSpoofingMode: AntiSpoofingMode,
    val antiSpoofingEnabled: Boolean,
    val maxDistanceThreshold: Float,
    val requiredConsecutiveMatches: Int,
    val maxHeadAngleX: Float,
    val maxHeadAngleY: Float,
    val minFaceSizePx: Int,
    val minBrightnessLuma: Int,
    val minLaplacianScore: Int,
    val recognitionCropScale: Float,
    val livenessCropScale: Float,
    val antiSpoofingScoreThreshold: Float,
    val antiSpoofingWindowSize: Int,
    val antiSpoofingMinFramesToDecide: Int,
    val antiSpoofingFrameStride: Int,
    val antiSpoofingWarmupMatches: Int,
    val antiSpoofingOnEnrollment: Boolean,
    val antiSpoofingOnAuthentication: Boolean,
    val errorCooldownMs: Long,
    val maxFailedAttemptsBeforeLockout: Int,
    val maxTemporaryLockoutsBeforePermanent: Int,
    val lockoutDurationMs: Long,
    val countFailedAttemptsForDistantMismatches: Boolean,
    val mismatchGraceDistanceDelta: Float,
    val recognitionCpuThreads: Int?,
    val antiSpoofingCpuThreads: Int?
)

object TfLiteBackendHelper {
    private const val TAG = "TfLiteBackendHelper"
    private const val MAX_DYNAMIC_CPU_THREADS = 6

    private val cachedSelections = ConcurrentHashMap<String, TfBackendSelection>()

    fun createOptions(selection: TfBackendSelection): Interpreter.Options {
        return Interpreter.Options().apply {
            when (selection.backend) {
                TfBackend.GPU -> {
                    val delegateOptions = CompatibilityList().bestOptionsForThisDevice
                    if (delegateOptions != null) {
                        addDelegate(GpuDelegate(delegateOptions))
                    } else {
                        setNumThreads(fallbackCpuThreads())
                    }
                }

                TfBackend.CPU -> {
                    setNumThreads(selection.threads.coerceAtLeast(1))
                }
            }
        }
    }

    fun detectDevicePerformanceClass(config: TensorFlowFaceConfig): DevicePerformanceClass {
        config.forceDeviceClass?.let { return it }
        val cores = availableCpuCores()
        val gpuSupported = CompatibilityList().isDelegateSupportedOnThisDevice
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cores >= 8 && gpuSupported -> DevicePerformanceClass.HIGH_END
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && cores >= 4 -> DevicePerformanceClass.MID_RANGE
            else -> DevicePerformanceClass.LOW_END
        }
    }

    fun resolveEffectiveConfig(base: TensorFlowFaceConfig): EffectiveTensorFlowFaceConfig {
        val deviceClass = detectDevicePerformanceClass(base)
        val profile = if (base.performanceProfile == TensorFlowPerformanceProfile.AUTO) {
            TensorFlowPerformanceProfile.BALANCED
        } else {
            base.performanceProfile
        }

        val relaxed = base.allowAdaptiveDeviceTuning && base.relaxedPolicyOnLowEndDevices &&
                deviceClass == DevicePerformanceClass.LOW_END
        val strict = base.allowAdaptiveDeviceTuning && base.strictPolicyOnModernDevices &&
                deviceClass == DevicePerformanceClass.HIGH_END

        var threshold = base.maxDistanceThresholds
        var consecutive = base.requiredConsecutiveMatches
        var angleX = base.maxHeadAngleX
        var angleY = base.maxHeadAngleY
        var minFace = base.minFaceSizePx
        var brightness = base.minBrightnessLuma
        var laplacian = base.minLaplacianScore
        var recognitionCrop = base.recognitionCropScale
        var livenessCrop = base.livenessCropScale
        var antiThreshold = base.antiSpoofingScoreThreshold
        var antiWindow = base.antiSpoofingWindowSize
        var antiMinFrames = base.antiSpoofingMinFramesToDecide
        var antiStride = base.antiSpoofingFrameStride
        var antiWarmup = base.antiSpoofingWarmupMatches
        var antiMode = base.antiSpoofingMode
        var antiEnabled = base.antiSpoofingEnabled
        var errorCooldownMs = base.errorCooldownMs
        var countDistantMismatch = base.countFailedAttemptsForDistantMismatches

        when (profile) {
            TensorFlowPerformanceProfile.FAST -> {
                threshold += 0.02f
                consecutive = 2
                antiStride = max(antiStride, 3)
                antiMinFrames = antiMinFrames.coerceAtMost(2)
            }

            TensorFlowPerformanceProfile.BALANCED -> Unit
            TensorFlowPerformanceProfile.STRICT -> {
                threshold -= 0.02f
                consecutive = max(consecutive, 3)
                minFace += 10
                brightness += 4
                antiThreshold -= 0.01f
                antiWindow = max(antiWindow, 6)
                antiMinFrames = max(antiMinFrames, 4)
                antiStride = antiStride.coerceAtMost(1)
                antiWarmup = max(antiWarmup, 2)
                errorCooldownMs = errorCooldownMs.coerceAtLeast(2_500L)
            }

            TensorFlowPerformanceProfile.AUTO -> Unit
        }

        if (relaxed) {
            threshold += 0.03f
            consecutive = consecutive.coerceAtMost(2)
            angleX += 4f
            angleY += 4f
            minFace = (minFace - 20).coerceAtLeast(120)
            brightness = (brightness - 6).coerceAtLeast(38)
            laplacian = (laplacian - 200).coerceAtLeast(700)
            antiStride = max(antiStride, 3)
            antiMinFrames = antiMinFrames.coerceAtMost(2)
            antiWarmup = antiWarmup.coerceAtMost(1)
            if (antiMode == AntiSpoofingMode.AUTO) antiMode = AntiSpoofingMode.AFTER_CANDIDATE
        }

        if (strict) {
            threshold -= 0.02f
            consecutive = max(consecutive, 3)
            angleX = angleX.coerceAtMost(20f)
            angleY = angleY.coerceAtMost(20f)
            minFace += 16
            brightness += 4
            laplacian += 100
            antiWindow = max(antiWindow, 6)
            antiMinFrames = max(antiMinFrames, 4)
            antiStride = antiStride.coerceAtMost(1)
            antiWarmup = max(antiWarmup, 2)
            if (antiMode == AntiSpoofingMode.AUTO) antiMode = AntiSpoofingMode.BEFORE_RECOGNITION
            countDistantMismatch = true
        }

        if (!base.allowAdaptiveDeviceTuning && antiMode == AntiSpoofingMode.AUTO) {
            antiMode = AntiSpoofingMode.AFTER_CANDIDATE
        } else if (antiMode == AntiSpoofingMode.AUTO) {
            antiMode = when (deviceClass) {
                DevicePerformanceClass.HIGH_END -> AntiSpoofingMode.BEFORE_RECOGNITION
                DevicePerformanceClass.MID_RANGE -> AntiSpoofingMode.AFTER_CANDIDATE
                DevicePerformanceClass.LOW_END -> AntiSpoofingMode.AFTER_CANDIDATE
            }
        }

        if (deviceClass == DevicePerformanceClass.LOW_END && base.antiSpoofingEnabled) {
            antiEnabled = antiEnabled && base.antiSpoofingOnAuthentication
        }

        threshold = threshold.coerceIn(0.5f, 1.0f)
        angleX = angleX.coerceIn(10f, 35f)
        angleY = angleY.coerceIn(10f, 35f)
        antiThreshold = antiThreshold.coerceIn(0.15f, 0.45f)
        antiWindow = max(antiWindow, antiMinFrames)

        return EffectiveTensorFlowFaceConfig(
            base = base,
            deviceClass = deviceClass,
            profile = profile,
            recognitionBackendPreference = base.backendPreference,
            antiSpoofingBackendPreference = base.backendPreference,
            antiSpoofingMode = antiMode,
            antiSpoofingEnabled = antiEnabled,
            maxDistanceThreshold = threshold,
            requiredConsecutiveMatches = max(consecutive, 1),
            maxHeadAngleX = angleX,
            maxHeadAngleY = angleY,
            minFaceSizePx = minFace,
            minBrightnessLuma = brightness,
            minLaplacianScore = laplacian,
            recognitionCropScale = recognitionCrop,
            livenessCropScale = livenessCrop,
            antiSpoofingScoreThreshold = antiThreshold,
            antiSpoofingWindowSize = antiWindow,
            antiSpoofingMinFramesToDecide = max(1, antiMinFrames),
            antiSpoofingFrameStride = max(1, antiStride),
            antiSpoofingWarmupMatches = max(1, antiWarmup),
            antiSpoofingOnEnrollment = base.antiSpoofingOnEnrollment,
            antiSpoofingOnAuthentication = base.antiSpoofingOnAuthentication,
            errorCooldownMs = errorCooldownMs,
            maxFailedAttemptsBeforeLockout = base.maxFailedAttemptsBeforeLockout,
            maxTemporaryLockoutsBeforePermanent = base.maxTemporaryLockoutsBeforePermanent,
            lockoutDurationMs = base.lockoutDurationMs,
            countFailedAttemptsForDistantMismatches = countDistantMismatch,
            mismatchGraceDistanceDelta = base.mismatchGraceDistanceDelta,
            recognitionCpuThreads = base.recognitionCpuThreads,
            antiSpoofingCpuThreads = base.antiSpoofingCpuThreads
        )
    }

    fun chooseRecognitionBackend(
        config: EffectiveTensorFlowFaceConfig,
        cacheKey: String = "Recognition"
    ): TfBackendSelection {
        val finalCacheKey =
            "$cacheKey-${config.deviceClass}-${config.recognitionBackendPreference}-${config.recognitionCpuThreads}"
        cachedSelections[finalCacheKey]?.let { return it }

        val selection = chooseBackend(
            preference = config.recognitionBackendPreference,
            customCpuThreads = config.recognitionCpuThreads,
            purpose = "recognition",
            allowGpuOnMidRange = true
        )
        cachedSelections[finalCacheKey] = selection
        LogCat.log(TAG, "$finalCacheKey selection -> $selection")
        return selection
    }

    fun chooseAntiSpoofingBackend(
        config: EffectiveTensorFlowFaceConfig,
        cacheKey: String = "AntiSpoofing"
    ): TfBackendSelection {
        val finalCacheKey =
            "$cacheKey-${config.deviceClass}-${config.antiSpoofingBackendPreference}-${config.antiSpoofingCpuThreads}"
        cachedSelections[finalCacheKey]?.let { return it }

        val allowGpu = config.deviceClass != DevicePerformanceClass.LOW_END
        val selection = chooseBackend(
            preference = config.antiSpoofingBackendPreference,
            customCpuThreads = config.antiSpoofingCpuThreads,
            purpose = "antiSpoofing",
            allowGpuOnMidRange = allowGpu
        )
        cachedSelections[finalCacheKey] = selection
        LogCat.log(TAG, "$finalCacheKey selection -> $selection")
        return selection
    }

    fun evaluateRationality(
        selection: TfBackendSelection,
        target: String,
        config: EffectiveTensorFlowFaceConfig
    ): TfBackendRationality {
        val maxMs = computeMaxRecommendedAntiSpoofingMs(config)
        val cores = availableCpuCores()
        val lowEndDevice = config.deviceClass == DevicePerformanceClass.LOW_END
        val veryLowEndDevice = isVeryLowEndDevice(cores)

        val allowed = when {
            !target.contains("AntiSpoof", ignoreCase = true) -> true
            !config.antiSpoofingEnabled -> false
            veryLowEndDevice && selection.backend == TfBackend.CPU -> false
            lowEndDevice && selection.backend == TfBackend.CPU && config.antiSpoofingFrameStride <= 1 -> false
            else -> true
        }

        val reason = buildString {
            append(target)
            append(if (allowed) " is allowed" else " is disabled")
            append(" for ")
            append(selection.backend)
            append("(")
            append(selection.threads)
            append(")")
            append("; sdk=")
            append(Build.VERSION.SDK_INT)
            append(", cores=")
            append(cores)
            append(", deviceClass=")
            append(config.deviceClass)
            append(", antiMode=")
            append(config.antiSpoofingMode)
            append(", stride=")
            append(config.antiSpoofingFrameStride)
        }

        return TfBackendRationality(
            selection = selection,
            antiSpoofingAllowed = allowed,
            maxRecommendedAntiSpoofingMs = maxMs,
            reason = reason
        )
    }

    private fun chooseBackend(
        preference: TensorFlowBackendPreference,
        customCpuThreads: Int?,
        purpose: String,
        allowGpuOnMidRange: Boolean
    ): TfBackendSelection {
        val gpuSupported = CompatibilityList().isDelegateSupportedOnThisDevice
        val cores = availableCpuCores()
        val canUseGpuHeuristically =
            gpuSupported && (allowGpuOnMidRange || isModernDeviceForGpuWorkloads(cores))
        val cpuThreads = customCpuThreads ?: fallbackCpuThreads()

        return when (preference) {
            TensorFlowBackendPreference.CPU_ONLY -> TfBackendSelection(
                backend = TfBackend.CPU,
                threads = cpuThreads,
                reason = "Forced CPU for $purpose"
            )

            TensorFlowBackendPreference.GPU_ONLY -> if (gpuSupported) {
                TfBackendSelection(TfBackend.GPU, 1, "Forced GPU for $purpose")
            } else {
                TfBackendSelection(
                    TfBackend.CPU,
                    cpuThreads,
                    "GPU requested but unavailable for $purpose"
                )
            }

            TensorFlowBackendPreference.GPU_PREFERRED -> if (gpuSupported) {
                TfBackendSelection(TfBackend.GPU, 1, "Preferred GPU for $purpose")
            } else {
                TfBackendSelection(
                    TfBackend.CPU,
                    cpuThreads,
                    "Preferred GPU unavailable for $purpose"
                )
            }

            TensorFlowBackendPreference.AUTO -> if (canUseGpuHeuristically) {
                TfBackendSelection(TfBackend.GPU, 1, "Heuristic GPU-first $purpose path")
            } else {
                TfBackendSelection(TfBackend.CPU, cpuThreads, "Heuristic CPU $purpose path")
            }
        }
    }

    private fun availableCpuCores(): Int = max(Runtime.getRuntime().availableProcessors(), 1)

    private fun isModernDeviceForGpuWorkloads(cores: Int): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cores >= 4
    }

    private fun isVeryLowEndDevice(cores: Int): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || cores <= 2
    }

    private fun recommendedMaxCpuThreads(cores: Int): Int {
        val safeCores = max(cores, 1)
        return when {
            safeCores <= 2 -> 2
            safeCores <= 4 -> safeCores
            safeCores <= 6 -> 4
            else -> minOf(MAX_DYNAMIC_CPU_THREADS, safeCores - 1)
        }.coerceAtLeast(1)
    }

    private fun fallbackCpuThreads(): Int {
        return recommendedMaxCpuThreads(availableCpuCores()).coerceAtMost(4)
    }

    private fun computeMaxRecommendedAntiSpoofingMs(config: EffectiveTensorFlowFaceConfig): Long {
        return when (config.deviceClass) {
            DevicePerformanceClass.HIGH_END -> 36L
            DevicePerformanceClass.MID_RANGE -> 28L
            DevicePerformanceClass.LOW_END -> 20L
        }
    }
}
