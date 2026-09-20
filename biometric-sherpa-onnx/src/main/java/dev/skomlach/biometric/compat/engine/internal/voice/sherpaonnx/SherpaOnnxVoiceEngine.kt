package dev.skomlach.biometric.compat.engine.internal.voice.sherpaonnx

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import dev.skomlach.biometric.compat.engine.internal.voice.VoiceEmbeddingResult
import dev.skomlach.biometric.compat.engine.internal.voice.VoiceEngine
import dev.skomlach.biometric.compat.engine.internal.voice.VoiceQualityIssue
import dev.skomlach.biometric.compat.engine.internal.voice.VoiceSample
import java.io.IOException

/**
 * Uses sherpa-onnx's speaker-embedding extractor through its typed public API. The published
 * wrapper has a compile-only dependency: the application supplies the runtime and an asset at
 * [DEFAULT_MODEL_ASSET_PATH].
 */
class SherpaOnnxVoiceEngine internal constructor(
    private val runtime: SherpaOnnxEmbeddingRuntime
) : VoiceEngine {
    constructor(context: Context) : this(
        SherpaOnnxEmbeddingRuntimeFactory.create(
            context = context.applicationContext,
            modelAssetPath = DEFAULT_MODEL_ASSET_PATH
        )
    )

    override fun isAvailable(): Boolean = runtime.isAvailable()

    override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? {
        val pcm = sample.pcmFloat ?: return invalidEmbedding(VoiceQualityIssue.SAMPLE_MISSING)
        if (sample.sampleRateHz <= 0 || pcm.isEmpty()) {
            return invalidEmbedding(VoiceQualityIssue.SAMPLE_MISSING)
        }
        val embedding = runtime.extractEmbedding(pcm, sample.sampleRateHz)
            ?: return invalidEmbedding(VoiceQualityIssue.EMBEDDING_INVALID)
        return VoiceEmbeddingResult(embedding = embedding)
    }

    private fun invalidEmbedding(issue: VoiceQualityIssue) = VoiceEmbeddingResult(
        embedding = FloatArray(0),
        qualityIssue = issue
    )

    internal companion object {
        const val DEFAULT_MODEL_ASSET_PATH = "sherpa-onnx/speaker-embedding.onnx"
    }
}

internal interface SherpaOnnxEmbeddingRuntime {
    fun isAvailable(): Boolean

    fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray?
}

/**
 * This class deliberately contains the only direct references to sherpa-onnx. The factory keeps
 * it behind a linkage boundary so an application that omits the optional AAR receives an
 * unavailable voice engine rather than a process crash while providers are loaded.
 */
private class DirectSherpaOnnxEmbeddingRuntime(
    private val context: Context,
    private val modelAssetPath: String
) : SherpaOnnxEmbeddingRuntime {
    private var permanentlyUnavailable = false
    private var extractor: SpeakerEmbeddingExtractor? = null

    override fun isAvailable(): Boolean = synchronized(this) {
        ensureExtractor() != null
    }

    override fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray? = synchronized(this) {
        val currentExtractor = ensureExtractor() ?: return@synchronized null
        var stream: OnlineStream? = null
        try {
            stream = currentExtractor.createStream()
            stream.acceptWaveform(pcm, sampleRateHz)
            stream.inputFinished()
            if (currentExtractor.isReady(stream)) currentExtractor.compute(stream) else null
        } catch (error: LinkageError) {
            permanentlyUnavailable = true
            null
        } catch (error: RuntimeException) {
            permanentlyUnavailable = true
            null
        } finally {
            releaseStream(stream)
        }
    }

    private fun ensureExtractor(): SpeakerEmbeddingExtractor? {
        if (permanentlyUnavailable) return null
        extractor?.let { return it }
        return try {
            context.assets.open(modelAssetPath).use { }
            val config = SpeakerEmbeddingExtractorConfig(
                modelAssetPath,
                DEFAULT_THREAD_COUNT,
                false,
                "cpu"
            )
            SpeakerEmbeddingExtractor(context.assets, config).also { extractor = it }
        } catch (error: IOException) {
            permanentlyUnavailable = true
            null
        } catch (error: LinkageError) {
            permanentlyUnavailable = true
            null
        } catch (error: RuntimeException) {
            permanentlyUnavailable = true
            null
        }
    }

    private fun releaseStream(stream: OnlineStream?) {
        try {
            stream?.release()
        } catch (error: LinkageError) {
            permanentlyUnavailable = true
        } catch (error: RuntimeException) {
            permanentlyUnavailable = true
        }
    }

    private companion object {
        const val DEFAULT_THREAD_COUNT = 1
    }
}

/** Safe boundary for a consumer APK that did not package the compile-only sherpa runtime. */
internal object SherpaOnnxEmbeddingRuntimeFactory {
    fun create(context: Context, modelAssetPath: String): SherpaOnnxEmbeddingRuntime =
        safelyCreateSherpaRuntime {
            DirectSherpaOnnxEmbeddingRuntime(context, modelAssetPath)
        }
}

internal fun safelyCreateSherpaRuntime(
    create: () -> SherpaOnnxEmbeddingRuntime
): SherpaOnnxEmbeddingRuntime = try {
    create()
} catch (_: LinkageError) {
    UnavailableSherpaOnnxEmbeddingRuntime
}

private object UnavailableSherpaOnnxEmbeddingRuntime : SherpaOnnxEmbeddingRuntime {
    override fun isAvailable(): Boolean = false

    override fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray? = null
}
