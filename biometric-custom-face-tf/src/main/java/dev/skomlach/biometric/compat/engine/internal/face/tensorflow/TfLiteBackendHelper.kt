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
    CPU
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

    /**
     * Safe upper bound for TFLite CPU threads on mobile devices.
     * In practice, 4 is often optimal, while 6 can help on some big.LITTLE SoCs.
     * I would not go above 6 by default.
     */
    private const val MAX_DYNAMIC_CPU_THREADS = 6

    private val cachedSelections = ConcurrentHashMap<String, TfBackendSelection>()
    fun createOptions(selection: TfBackendSelection): Interpreter.Options {
        return Interpreter.Options().apply {
            when (selection.backend) {
                TfBackend.GPU_SYSTEM -> {
                    val delegate = createGpuDelegateOrNull(RuntimeFlavor.SYSTEM)
                    if (delegate != null) {
                        addDelegate(delegate)
                    } else {
                        setNumThreads(fallbackCpuThreads())
                    }
                }

                TfBackend.GPU_APPLICATION -> {
                    val delegate = createGpuDelegateOrNull(RuntimeFlavor.APPLICATION)
                    if (delegate != null) {
                        addDelegate(delegate)
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

    fun chooseBestBackend(
        benchmarkName: String,
        benchmark: (TfBackendSelection) -> Long?
    ): TfBackendSelection {
        cachedSelections[benchmarkName]?.let { return it }

        val candidates = buildList {
            if (isGpuRuntimeAvailable(RuntimeFlavor.SYSTEM)) {
                add(
                    TfBackendSelection(
                        backend = TfBackend.GPU_SYSTEM,
                        benchmarkMs = Long.MAX_VALUE,
                        warmupMs = -1L,
                        threads = 1,
                        reason = "GPU system runtime"
                    )
                )
            } else {
                LogCat.log(TAG, "$benchmarkName skip GPU_SYSTEM: runtime unavailable")
            }

            if (isGpuRuntimeAvailable(RuntimeFlavor.APPLICATION)) {
                add(
                    TfBackendSelection(
                        backend = TfBackend.GPU_APPLICATION,
                        benchmarkMs = Long.MAX_VALUE,
                        warmupMs = -1L,
                        threads = 1,
                        reason = "GPU app runtime"
                    )
                )
            } else {
                LogCat.log(TAG, "$benchmarkName skip GPU_APPLICATION: runtime unavailable")
            }

            addAll(buildCpuCandidates())
        }

        var best: TfBackendSelection? = null
        for (candidate in candidates) {
            val time = try {
                benchmark(candidate)
            } catch (t: Throwable) {
                LogCat.log(
                    TAG,
                    "$benchmarkName backend ${candidate.backend}(${candidate.threads}) failed: " +
                            "${t.javaClass.simpleName}: ${t.message}"
                )
                null
            }

            if (time == null) {
                LogCat.log(
                    TAG,
                    "$benchmarkName backend ${candidate.backend}(${candidate.threads}) is unavailable"
                )
                continue
            }

            val measured = candidate.copy(benchmarkMs = time)
            LogCat.log(
                TAG,
                "$benchmarkName backend ${candidate.backend}(${candidate.threads}) -> ${time}ms"
            )

            if (best == null || time < best!!.benchmarkMs) {
                best = measured
            }
        }

        val resolved = best ?: TfBackendSelection(
            backend = TfBackend.CPU,
            benchmarkMs = Long.MAX_VALUE,
            warmupMs = -1L,
            threads = fallbackCpuThreads(),
            reason = "Fallback CPU because every accelerated backend failed"
        )

        cachedSelections[benchmarkName] = resolved
        return resolved
    }

    private fun buildCpuCandidates(): List<TfBackendSelection> {
        val cores = max(Runtime.getRuntime().availableProcessors(), 1)
        val maxThreads = recommendedMaxCpuThreads(cores)

        val candidates = linkedSetOf<Int>()

        when {
            maxThreads <= 2 -> {
                candidates += 2
                candidates += 1
            }

            maxThreads == 3 -> {
                candidates += 3
                candidates += 2
                candidates += 1
            }

            maxThreads == 4 -> {
                candidates += 4
                candidates += 3
                candidates += 2
                candidates += 1
            }

            else -> {
                candidates += maxThreads
                candidates += 4
                candidates += 3
                candidates += 2
                candidates += 1
            }
        }

        return candidates
            .filter { it in 1..maxThreads }
            .distinct()
            .map { threads ->
                TfBackendSelection(
                    backend = TfBackend.CPU,
                    benchmarkMs = Long.MAX_VALUE,
                    warmupMs = -1L,
                    threads = threads,
                    reason = "CPU $threads threads (dynamic from $cores cores, cap=$maxThreads)"
                )
            }
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
        return recommendedMaxCpuThreads(max(Runtime.getRuntime().availableProcessors(), 1))
            .coerceAtMost(4)
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
            "$target is allowed for ${selection.backend}(${selection.threads}) " +
                    "(${selection.benchmarkMs}ms <= $maxMs ms)"
        } else {
            "$target is disabled for ${selection.backend}(${selection.threads}) " +
                    "(${selection.benchmarkMs}ms > $maxMs ms)"
        }

        return TfBackendRationality(
            selection = selection,
            antiSpoofingAllowed = allowed,
            maxRecommendedAntiSpoofingMs = maxMs,
            reason = reason
        )
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
