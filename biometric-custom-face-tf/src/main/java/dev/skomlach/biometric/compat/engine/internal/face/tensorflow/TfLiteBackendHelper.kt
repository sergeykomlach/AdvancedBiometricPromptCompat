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

object TfLiteBackendHelper {
    private const val TAG = "TfLiteBackendHelper"
    private const val MAX_DYNAMIC_CPU_THREADS = 6
    private const val HEURISTIC_NO_BENCHMARK_MS = 0L

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

    fun chooseRecognitionBackendHeuristic(cacheKey: String = "Recognition"): TfBackendSelection {
        cachedSelections[cacheKey]?.let { return it }

        val selection = if (CompatibilityList().isDelegateSupportedOnThisDevice) {
            TfBackendSelection(
                backend = TfBackend.GPU,
                threads = 1,
                reason = "Heuristic GPU-first recognition path"
            )
        } else {
            TfBackendSelection(
                backend = TfBackend.CPU,
                threads = fallbackCpuThreads(),
                reason = "Heuristic CPU recognition fallback"
            )
        }

        cachedSelections[cacheKey] = selection
        LogCat.log(TAG, "$cacheKey heuristic selection -> $selection")
        return selection
    }

    fun chooseAntiSpoofingBackendHeuristic(cacheKey: String = "AntiSpoofing"): TfBackendSelection {
        cachedSelections[cacheKey]?.let { return it }

        val cores = availableCpuCores()
        val selection = when {
            CompatibilityList().isDelegateSupportedOnThisDevice && isModernDeviceForGpuWorkloads(cores) -> {
                TfBackendSelection(
                    backend = TfBackend.GPU,
                    threads = 1,
                    reason = "Heuristic GPU-first anti-spoofing path"
                )
            }

            else -> {
                TfBackendSelection(
                    backend = TfBackend.CPU,
                    threads = fallbackCpuThreads(),
                    reason = "Heuristic CPU anti-spoofing fallback"
                )
            }
        }

        cachedSelections[cacheKey] = selection
        LogCat.log(TAG, "$cacheKey heuristic selection -> $selection")
        return selection
    }

    fun evaluateRationality(
        selection: TfBackendSelection,
        target: String,
        strictMode: Boolean = false
    ): TfBackendRationality {
        val maxMs = computeMaxRecommendedAntiSpoofingMs(strictMode)
        val cores = availableCpuCores()
        val lowEndDevice = isLowEndDevice(cores)
        val veryLowEndDevice = isVeryLowEndDevice(cores)

        val allowed = when {
            !target.contains("AntiSpoof", ignoreCase = true) -> true
            veryLowEndDevice -> false
            selection.backend == TfBackend.GPU -> true
            strictMode -> !lowEndDevice && cores >= 6
            else -> cores >= 4
        }

        val reason = buildString {
            append(target)
            append(if (allowed) " is allowed" else " is disabled")
            append(" for ")
            append(selection.backend)
            append("(")
            append(selection.threads)
            append(")")
            append(" by heuristic")
            append("; sdk=")
            append(Build.VERSION.SDK_INT)
            append(", cores=")
            append(cores)
            append(", strictMode=")
            append(strictMode)
            append(", lowEnd=")
            append(lowEndDevice)
            append(", veryLowEnd=")
            append(veryLowEndDevice)
        }

        return TfBackendRationality(
            selection = selection,
            antiSpoofingAllowed = allowed,
            maxRecommendedAntiSpoofingMs = maxMs,
            reason = reason
        )
    }

    private fun availableCpuCores(): Int {
        return max(Runtime.getRuntime().availableProcessors(), 1)
    }

    private fun isModernDeviceForGpuWorkloads(cores: Int): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cores >= 4
    }

    private fun isLowEndDevice(cores: Int): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 || cores <= 4
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

    private fun computeMaxRecommendedAntiSpoofingMs(strictMode: Boolean): Long {
        val cores = availableCpuCores()
        val lowRamHeuristic = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 || cores <= 4
        return when {
            strictMode -> 18L
            lowRamHeuristic -> 24L
            cores >= 8 -> 36L
            else -> 30L
        }
    }
}
