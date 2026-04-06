package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.os.Build
import dev.skomlach.common.logging.LogCat
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.RuntimeFlavor
import org.tensorflow.lite.gpu.GpuDelegateFactory
import kotlin.math.max

enum class TfBackend {
    GPU_SYSTEM,
    GPU_APPLICATION,
    CPU_4,
    CPU_2,
    CPU_1
}

data class TfBackendSelection(
    val backend: TfBackend,
    val benchmarkMs: Long,
    val warmupMs: Long,
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

    fun createOptions(selection: TfBackendSelection): Interpreter.Options {
        return Interpreter.Options().apply {
            when (selection.backend) {
                TfBackend.GPU_SYSTEM -> addDelegate(GpuDelegateFactory().create(RuntimeFlavor.SYSTEM))
                TfBackend.GPU_APPLICATION -> addDelegate(GpuDelegateFactory().create(RuntimeFlavor.APPLICATION))
                TfBackend.CPU_4,
                TfBackend.CPU_2,
                TfBackend.CPU_1 -> setNumThreads(selection.threads)
            }
        }
    }

    fun chooseBestBackend(
        benchmarkName: String,
        benchmark: (TfBackendSelection) -> Long?
    ): TfBackendSelection {
        val candidates = listOf(
            TfBackendSelection(TfBackend.GPU_SYSTEM, Long.MAX_VALUE, -1L, 1, "GPU system runtime"),
            TfBackendSelection(TfBackend.GPU_APPLICATION, Long.MAX_VALUE, -1L, 1, "GPU app runtime"),
            TfBackendSelection(TfBackend.CPU_4, Long.MAX_VALUE, -1L, 4, "CPU 4 threads"),
            TfBackendSelection(TfBackend.CPU_2, Long.MAX_VALUE, -1L, 2, "CPU 2 threads"),
            TfBackendSelection(TfBackend.CPU_1, Long.MAX_VALUE, -1L, 1, "CPU 1 thread")
        )

        var best: TfBackendSelection? = null
        for (candidate in candidates) {
            val time = try {
                benchmark(candidate)
            } catch (t: Throwable) {
                LogCat.logException(t)
                null
            }
            if (time == null) {
                LogCat.log(TAG, "$benchmarkName backend ${candidate.backend} is unavailable")
                continue
            }
            val measured = candidate.copy(benchmarkMs = time)
            LogCat.log(TAG, "$benchmarkName backend ${candidate.backend} -> ${time}ms")
            if (best == null || time < best!!.benchmarkMs) {
                best = measured
            }
        }

        return best ?: TfBackendSelection(
            backend = TfBackend.CPU_2,
            benchmarkMs = Long.MAX_VALUE,
            warmupMs = -1L,
            threads = 2,
            reason = "Fallback CPU because every accelerated backend failed"
        )
    }

    fun evaluateRationality(
        selection: TfBackendSelection,
        target: String,
        strictMode: Boolean = false
    ): TfBackendRationality {
        val maxMs = computeMaxRecommendedAntiSpoofingMs(strictMode)
        val allowed = selection.benchmarkMs in 1..maxMs
        val reason = buildString {
            append(target)
            append(": backend=")
            append(selection.backend)
            append(", inference=")
            append(selection.benchmarkMs)
            append("ms, threshold=")
            append(maxMs)
            append("ms")
            if (!allowed) {
                append(", anti-spoofing should be disabled on this device")
            }
        }
        return TfBackendRationality(selection, allowed, maxMs, reason)
    }

    private fun computeMaxRecommendedAntiSpoofingMs(strictMode: Boolean): Long {
        val cores = max(Runtime.getRuntime().availableProcessors(), 1)
        val lowRamHeuristic = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 || cores <= 4
        return when {
            strictMode -> 18L
            lowRamHeuristic -> 24L
            cores >= 8 -> 36L
            else -> 30L
        }
    }
}
