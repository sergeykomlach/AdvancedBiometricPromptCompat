package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import dev.skomlach.biometric.compat.custom.SoftwareBiometricDeferredInitialization
import dev.skomlach.biometric.compat.custom.SoftwareBiometricInitializationState
import java.io.IOException

/**
 * Uses sherpa-onnx's speaker-embedding extractor through its typed public API. The published
 * wrapper has a compile-only dependency: the application supplies the runtime and an asset at
 * [DEFAULT_MODEL_ASSET_PATH].
 */
class SherpaOnnxVoiceEngine internal constructor(
    private val createRuntime: () -> SherpaOnnxEmbeddingRuntime
) : VoiceEngine, VoiceTemplateIdentityProvider, PreparingVoiceEngine,
    SoftwareBiometricDeferredInitialization {
    private val preparationLock = Any()
    private var runtime: SherpaOnnxEmbeddingRuntime? = null
    @Volatile private var runtimeHealthy = false
    @Volatile override var templateIdentity: String? = null
        private set
    @Volatile override var initializationState = SoftwareBiometricInitializationState.NEW
        private set

    internal constructor(runtime: SherpaOnnxEmbeddingRuntime) : this({ runtime })

    constructor(context: Context) : this(defaultRuntimeFactory(context.applicationContext))

    /** Worker-only. Concurrent calls join one preparation; UI queries never acquire this lock. */
    override fun prepare(): Boolean = synchronized(preparationLock) {
        when (initializationState) {
            SoftwareBiometricInitializationState.READY -> return@synchronized runtimeHealthy
            SoftwareBiometricInitializationState.FAILED,
            SoftwareBiometricInitializationState.PREPARING -> return@synchronized false
            SoftwareBiometricInitializationState.NEW -> Unit
        }
        initializationState = SoftwareBiometricInitializationState.PREPARING
        var ready = false
        try {
            val prepared = createRuntime()
            if (prepared.prepare() && prepared.isAvailable()) {
                val identity = prepared.templateIdentity
                if (!identity.isNullOrBlank()) {
                    runtime = prepared
                    templateIdentity = identity
                    runtimeHealthy = true
                    ready = true
                }
            }
        } catch (_: Exception) {
            // Optional model/runtime cannot make provider discovery or authentication crash.
        } catch (_: LinkageError) {
            // Includes absent AAR/SO and incompatible native ABI.
        } finally {
            initializationState = if (ready) SoftwareBiometricInitializationState.READY
                else SoftwareBiometricInitializationState.FAILED
        }
        ready
    }

    override fun isAvailable(): Boolean =
        initializationState == SoftwareBiometricInitializationState.READY && runtimeHealthy

    override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? {
        val pcm = sample.pcmFloat ?: return invalidEmbedding(VoiceQualityIssue.SAMPLE_MISSING)
        if (sample.sampleRateHz <= 0 || pcm.isEmpty()) {
            return invalidEmbedding(VoiceQualityIssue.SAMPLE_MISSING)
        }
        if (!isAvailable()) return invalidEmbedding(VoiceQualityIssue.EMBEDDING_INVALID)
        val prepared = runtime ?: return invalidEmbedding(VoiceQualityIssue.EMBEDDING_INVALID)
        val embedding = try {
            val result = prepared.extractEmbedding(pcm, sample.sampleRateHz)
            runtimeHealthy = prepared.isAvailable()
            result.takeIf { runtimeHealthy }
        } catch (_: Exception) {
            runtimeHealthy = false
            null
        } catch (_: LinkageError) {
            runtimeHealthy = false
            null
        }
            ?: return invalidEmbedding(VoiceQualityIssue.EMBEDDING_INVALID)
        return VoiceEmbeddingResult(embedding = embedding)
    }

    private fun invalidEmbedding(issue: VoiceQualityIssue) = VoiceEmbeddingResult(
        embedding = FloatArray(0),
        qualityIssue = issue
    )

    internal companion object {
        const val DEFAULT_MODEL_ASSET_PATH = "sherpa-onnx/speaker-embedding.onnx"

        private fun defaultRuntimeFactory(appContext: Context): () -> SherpaOnnxEmbeddingRuntime = {
            SherpaOnnxEmbeddingRuntimeFactory.create(appContext, DEFAULT_MODEL_ASSET_PATH)
        }
    }
}

internal interface SherpaOnnxEmbeddingRuntime {
    val templateIdentity: String? get() = null
    fun prepare(): Boolean = isAvailable() && !templateIdentity.isNullOrBlank()
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
    private val modelIdentity = PreparedModelIdentity { context.assets.open(modelAssetPath) }
    override val templateIdentity: String? get() = modelIdentity.value

    // Only the explicit worker preparation may open the model or construct a native extractor.
    override fun prepare(): Boolean =
        modelIdentity.prepare() != null && synchronized(this) { ensureExtractor() != null }
    @Volatile private var permanentlyUnavailable = false
    @Volatile private var extractor: SpeakerEmbeddingExtractor? = null

    override fun isAvailable(): Boolean = !permanentlyUnavailable && extractor != null

    override fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray? = synchronized(this) {
        if (permanentlyUnavailable) return@synchronized null
        val currentExtractor = extractor ?: return@synchronized null
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

