package dev.skomlach.biometric.compat.engine.internal.voice

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePromptDataTest {

    @Test
    fun buildVoiceExtrasStoresSingleSampleWithoutBatchMetadata() {
        val extras = buildVoiceExtras(
            existing = Bundle().apply {
                putString("other", "value")
                putFloatArray(VOICE_EXTRA_EMBEDDING, floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f))
                putInt(VOICE_EXTRA_SAMPLE_COUNT, 2)
                putFloatArray("$VOICE_EXTRA_PCM_FLOAT.0", floatArrayOf(9f))
            },
            phrase = "  open sesame  ",
            sampleRateHz = 16_000,
            pcmSamples = listOf(floatArrayOf(0.1f, 0.2f, 0.3f))
        )

        assertEquals("value", extras.getString("other"))
        assertEquals("open sesame", extras.getString(VOICE_EXTRA_PHRASE))
        assertEquals(16_000, extras.getInt(VOICE_EXTRA_SAMPLE_RATE))
        assertTrue(extras.getFloatArray(VOICE_EXTRA_PCM_FLOAT)?.contentEquals(floatArrayOf(0.1f, 0.2f, 0.3f)) == true)
        assertEquals(0, extras.getInt(VOICE_EXTRA_SAMPLE_COUNT, 0))
        assertNull(extras.getFloatArray("$VOICE_EXTRA_PCM_FLOAT.0"))
        assertNull(extras.getFloatArray(VOICE_EXTRA_EMBEDDING))
    }

    @Test
    fun buildVoiceExtrasStoresEnrollmentBatchInIndexedSlots() {
        val extras = buildVoiceExtras(
            existing = null,
            phrase = null,
            sampleRateHz = 22_050,
            pcmSamples = listOf(
                floatArrayOf(0.1f, 0.2f),
                floatArrayOf(0.3f, 0.4f),
                floatArrayOf(0.5f, 0.6f)
            )
        )

        assertEquals(22_050, extras.getInt(VOICE_EXTRA_SAMPLE_RATE))
        assertEquals(3, extras.getInt(VOICE_EXTRA_SAMPLE_COUNT))
        assertNull(extras.getFloatArray(VOICE_EXTRA_PCM_FLOAT))
        assertTrue(extras.getFloatArray("$VOICE_EXTRA_PCM_FLOAT.0")?.contentEquals(floatArrayOf(0.1f, 0.2f)) == true)
        assertTrue(extras.getFloatArray("$VOICE_EXTRA_PCM_FLOAT.1")?.contentEquals(floatArrayOf(0.3f, 0.4f)) == true)
        assertTrue(extras.getFloatArray("$VOICE_EXTRA_PCM_FLOAT.2")?.contentEquals(floatArrayOf(0.5f, 0.6f)) == true)
    }

    @Test
    fun hasVoiceInputIgnoresPhraseOnlyExtras() {
        val extras = Bundle().apply {
            putString(VOICE_EXTRA_PHRASE, "phrase only")
        }

        assertFalse(hasVoiceInput(extras))
    }

    @Test
    fun hasVoiceInputReturnsTrueForEmbeddingOrPcmPayload() {
        val pcmExtras = Bundle().apply {
            putInt(VOICE_EXTRA_SAMPLE_RATE, 16_000)
            putFloatArray(VOICE_EXTRA_PCM_FLOAT, floatArrayOf(0.1f))
        }
        val embeddingExtras = Bundle().apply {
            putFloatArray(VOICE_EXTRA_EMBEDDING, floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f))
        }

        assertTrue(hasVoiceInput(pcmExtras))
        assertTrue(hasVoiceInput(embeddingExtras))
    }
}
