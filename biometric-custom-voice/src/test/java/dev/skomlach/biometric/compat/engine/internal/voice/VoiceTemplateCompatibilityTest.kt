package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceTemplateCompatibilityTest {
    @Test fun bundledEnginesHaveDistinctStableIdentities() {
        assertFalse(voiceTemplateIdentityMatches(BasicVoiceEngine().templateIdentity,
            CepstralVoiceEngine().templateIdentity))
        assertEquals(BasicVoiceEngine().templateIdentity, BasicVoiceEngine().templateIdentity)
    }
    @Test fun unversionedAndChangedModelsRequireReenrollment() {
        assertFalse(voiceTemplateIdentityMatches(null, "model:v1"))
        assertFalse(voiceTemplateIdentityMatches("model:v1", "model:v2"))
        assertFalse(voiceTemplateIdentityMatches(null, null))
        assertTrue(voiceTemplateIdentityMatches("model:v1", "model:v1"))
    }

    @Test fun samePrefixWithDifferentDimensionsCannotAuthenticate() {
        assertEquals(0f, VoiceScorer.score(FloatArray(8) { 1f }, FloatArray(16) { 1f }), 0f)
        assertEquals(0f, matchVoiceTemplates(
            listOf(VoiceTemplate("owner", null, FloatArray(8) { 1f })),
            FloatArray(16) { 1f }, emptyList(), 3), 0f)
    }

    @Test fun enrollmentCannotTrainOnDifferentEmbeddingDimensions() {
        assertTrue(trainVoiceTemplates("owner", null,
            listOf(FloatArray(8) { 1f }, FloatArray(16) { 1f })).isEmpty())
    }
}
