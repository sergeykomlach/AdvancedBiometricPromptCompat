package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceEnrollmentStatusTest {
    @Test fun emptyStorageIsNotEnrolled() {
        assertEquals(VoiceEnrollmentStatus.NOT_ENROLLED, voiceEnrollmentStatus(emptyList(), null))
    }

    @Test fun modelPreparationDoesNotMislabelStoredProfilesAsMissing() {
        assertEquals(VoiceEnrollmentStatus.ENGINE_NOT_READY, voiceEnrollmentStatus(listOf("model-a"), null))
    }

    @Test fun legacyProfileRequiresReenrollment() {
        assertEquals(VoiceEnrollmentStatus.REENROLLMENT_REQUIRED, voiceEnrollmentStatus(listOf(null), "model-a"))
    }

    @Test fun changedModelRequiresReenrollmentEvenWithSameEmbeddingShape() {
        assertEquals(VoiceEnrollmentStatus.REENROLLMENT_REQUIRED, voiceEnrollmentStatus(listOf("model-a"), "model-b"))
    }

    @Test fun compatibleProfileRemainsUsableBesideOlderProfiles() {
        assertEquals(VoiceEnrollmentStatus.READY, voiceEnrollmentStatus(listOf(null, "model-a"), "model-a"))
    }

    @Test fun preparationMustProduceAnIdentity() {
        assertFalse(prepareVoiceEngine(TestEngine(identity = null)))
        assertFalse(prepareVoiceEngine(TestEngine(identity = " ")))
        assertTrue(prepareVoiceEngine(TestEngine(identity = "model-a")))
    }

    @Test fun missingNativeLibraryFailsPreparationClosed() {
        assertFalse(prepareVoiceEngine(object : VoiceEngine, PreparingVoiceEngine, VoiceTemplateIdentityProvider {
            override val templateIdentity = "model-a"
            override fun prepare(): Boolean = throw UnsatisfiedLinkError("missing runtime")
            override fun isAvailable() = true
            override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? = null
        }))
    }

    @Test fun preparationRunsBeforeIdentityIsRead() {
        val engine = object : VoiceEngine, PreparingVoiceEngine, VoiceTemplateIdentityProvider {
            private var ready = false
            override val templateIdentity: String? get() = if (ready) "model-a" else null
            override fun prepare(): Boolean { ready = true; return true }
            override fun isAvailable() = true
            override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? = null
        }
        assertTrue(prepareVoiceEngine(engine))
    }

    @Test fun previousTrainingPolicyCannotAuthenticateOrMergeIntoANewEnrollment() {
        val expected = voiceEnrollmentIdentity(TestEngine("model-a"))
        assertEquals("voice-consensus-v2:model-a", expected)
        assertEquals(VoiceEnrollmentStatus.REENROLLMENT_REQUIRED,
            voiceEnrollmentStatus(listOf("model-a"), expected))
        assertFalse(voiceTemplateIdentityMatches("model-a", expected))
        assertTrue(voiceTemplateIdentityMatches(expected, expected))
        assertNull(voiceEnrollmentIdentity(TestEngine(null)))
    }

    private class TestEngine(private val identity: String?) : VoiceEngine, VoiceTemplateIdentityProvider {
        override val templateIdentity get() = identity
        override fun isAvailable() = true
        override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? = null
    }
}
