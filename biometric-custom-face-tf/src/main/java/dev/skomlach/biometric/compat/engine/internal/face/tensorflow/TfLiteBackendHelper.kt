package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.os.Build
import dev.skomlach.common.logging.LogCat
import org.tensorflow.lite.Delegate
import java.util.concurrent.ConcurrentHashMap
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

    private val cachedSelections = ConcurrentHashMap<String, TfBackendSelection>()
    fun createOptions(selection: TfBackendSelection): Interpreter.Options {
        return Interpreter.Options().apply {
            when (selection.backend) {
                TfBackend.GPU_SYSTEM -> {
                    val delegate = createGpuDelegateOrNull(RuntimeFlavor.SYSTEM)
                    if (delegate != null) {
                        addDelegate(delegate)
                    } else {
                        setNumThreads(2)
                    }
                }

                TfBackend.GPU_APPLICATION -> {
                    val delegate = createGpuDelegateOrNull(RuntimeFlavor.APPLICATION)
                    if (delegate != null) {
                        addDelegate(delegate)
                    } else {
                        setNumThreads(2)
                    }
                }

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
        cachedSelections[benchmarkName]?.let { return it }

        val candidates = buildList {
            if (isGpuRuntimeAvailable(RuntimeFlavor.SYSTEM)) {
                add(
                    TfBackendSelection(
                        TfBackend.GPU_SYSTEM,
                        Long.MAX_VALUE,
                        -1L,
                        1,
                        "GPU system runtime"
                    )
                )
            } else {
                LogCat.log(TAG, "$benchmarkName skip GPU_SYSTEM: runtime unavailable")
            }

            if (isGpuRuntimeAvailable(RuntimeFlavor.APPLICATION)) {
                add(
                    TfBackendSelection(
                        TfBackend.GPU_APPLICATION,
                        Long.MAX_VALUE,
                        -1L,
                        1,
                        "GPU app runtime"
                    )
                )
            } else {
                LogCat.log(TAG, "$benchmarkName skip GPU_APPLICATION: runtime unavailable")
            }

            add(TfBackendSelection(TfBackend.CPU_4, Long.MAX_VALUE, -1L, 4, "CPU 4 threads"))
            add(TfBackendSelection(TfBackend.CPU_2, Long.MAX_VALUE, -1L, 2, "CPU 2 threads"))
            add(TfBackendSelection(TfBackend.CPU_1, Long.MAX_VALUE, -1L, 1, "CPU 1 thread"))
        }

        var best: TfBackendSelection? = null
        for (candidate in candidates) {
            val time = try {
                benchmark(candidate)
            } catch (t: Throwable) {
                LogCat.log(
                    TAG,
                    "$benchmarkName backend ${candidate.backend} failed: ${t.javaClass.simpleName}: ${t.message}"
                )
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

        val resolved = best ?: TfBackendSelection(
            backend = TfBackend.CPU_2,
            benchmarkMs = Long.MAX_VALUE,
            warmupMs = -1L,
            threads = 2,
            reason = "Fallback CPU because every accelerated backend failed"
        )

        cachedSelections[benchmarkName] = resolved
        return resolved
    }

    private fun createGpuDelegateOrNull(flavor: RuntimeFlavor): Delegate? {
        if (!isGpuRuntimeAvailable(flavor)) return null
        return try {
            GpuDelegateFactory().create(flavor)
        } catch (t: Throwable) {
            LogCat.log(
                TAG,
                "GPU delegate create failed for $flavor: ${t.javaClass.simpleName}: ${t.message}"
            )
            null
        }
    }

    private fun isGpuRuntimeAvailable(flavor: RuntimeFlavor): Boolean {
        return try {
            when (flavor) {
                RuntimeFlavor.SYSTEM -> {
                    Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                    true
                }

                RuntimeFlavor.APPLICATION -> {
                    Class.forName("com.google.android.gms.tflite.gpu.GpuDelegate")
                    true
                }

                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun evaluateRationality(
        selection: TfBackendSelection,
        target: String,
        strictMode: Boolean = false
    ): TfBackendRationality {
        val maxMs = computeMaxRecommendedAntiSpoofingMs(strictMode)
        val allowed = selection.benchmarkMs in 1..maxMs
        val reason = if (allowed) {
            "$target is allowed for ${selection.backend} (${selection.benchmarkMs}ms <= $maxMs ms)"
        } else {
            "$target is disabled for ${selection.backend} (${selection.benchmarkMs}ms > $maxMs ms)"
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
