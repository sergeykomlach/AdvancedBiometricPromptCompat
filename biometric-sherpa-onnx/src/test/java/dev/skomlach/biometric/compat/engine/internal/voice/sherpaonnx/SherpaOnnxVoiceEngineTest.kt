package dev.skomlach.biometric.compat.engine.internal.voice.sherpaonnx

import dev.skomlach.biometric.compat.engine.internal.voice.VoiceQualityIssue
import dev.skomlach.biometric.compat.engine.internal.voice.VoiceSample
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxVoiceEngineTest {
    @Test
    fun returnsEmbeddingProducedByConfiguredRuntime() {
        val expected = floatArrayOf(0.1f, -0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)
        val runtime = FakeRuntime(available = true, embedding = expected)
        val engine = SherpaOnnxVoiceEngine(runtime)
        val result = engine.extractEmbedding(sample())

        assertTrue(engine.isAvailable())
        assertArrayEquals(expected, result?.embedding, 0f)
        assertEquals(VoiceQualityIssue.NONE, result?.qualityIssue)
    }

    @Test
    fun reportsInvalidEmbeddingWhenRuntimeIsUnavailable() {
        val runtime = FakeRuntime(available = false, embedding = null)
        val engine = SherpaOnnxVoiceEngine(runtime)
        val result = engine.extractEmbedding(sample())

        assertFalse(engine.isAvailable())
        assertEquals(VoiceQualityIssue.EMBEDDING_INVALID, result?.qualityIssue)
    }

    @Test
    fun degradesToUnavailableWhenConsumerDidNotPackageSherpaRuntime() {
        val runtime = safelyCreateSherpaRuntime {
            throw NoClassDefFoundError("com/k2fsa/sherpa/onnx/SpeakerEmbeddingExtractor")
        }
        val engine = SherpaOnnxVoiceEngine(runtime)

        assertFalse(engine.isAvailable())
        assertEquals(
            VoiceQualityIssue.EMBEDDING_INVALID,
            engine.extractEmbedding(sample())?.qualityIssue
        )
    }

    private fun sample() = VoiceSample(
        sampleRateHz = 16_000,
        pcmFloat = floatArrayOf(0.1f, 0.2f, 0.3f),
        embedding = null,
        phrase = null
    )

    private class FakeRuntime(
        private val available: Boolean,
        private val embedding: FloatArray?
    ) : SherpaOnnxEmbeddingRuntime {
        override fun isAvailable(): Boolean = available

        override fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray? = embedding
    }
}
