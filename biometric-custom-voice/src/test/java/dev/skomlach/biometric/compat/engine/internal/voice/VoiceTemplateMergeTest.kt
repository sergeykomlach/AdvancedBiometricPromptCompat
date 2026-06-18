package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTemplateMergeTest {
    @Test
    fun mergeVoiceTemplatesKeepsNewestBatchWithinLimit() {
        val existing = (0 until 4).map { index ->
            VoiceTemplate("tag", null, embedding(index))
        }
        val incoming = (4 until 7).map { index ->
            VoiceTemplate("tag", null, embedding(index))
        }

        val merged = mergeVoiceTemplates(existing, incoming, maxTemplates = 5)

        assertEquals(5, merged.size)
        assertEquals(listOf(2f, 3f, 4f, 5f, 6f), merged.map { it.embedding.first() })
    }

    @Test
    fun mergeVoiceTemplatesKeepsOtherPhraseFamilies() {
        val existing = listOf(
            VoiceTemplate("tag", "hello", embedding(1)),
            VoiceTemplate("tag", null, embedding(2))
        )
        val incoming = listOf(VoiceTemplate("tag", null, embedding(3)))

        val merged = mergeVoiceTemplates(existing, incoming, maxTemplates = 5)

        assertEquals(listOf("hello", null, null), merged.map { it.phrase })
    }

    private fun embedding(seed: Int): FloatArray {
        return FloatArray(8) { index -> seed.toFloat() + index / 100f }
    }
}
