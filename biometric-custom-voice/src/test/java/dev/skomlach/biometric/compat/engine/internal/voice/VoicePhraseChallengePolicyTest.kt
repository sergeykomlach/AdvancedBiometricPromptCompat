package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoicePhraseChallengePolicyTest {
    @Test
    fun `phrase is required for a mixed template set`() {
        assertEquals(true, isVoicePhraseRequired(listOf(null, "blue river")))
    }

    @Test
    fun `phrase is optional for an all legacy template set`() {
        assertEquals(false, isVoicePhraseRequired(listOf(null, " ", null)))
    }

    @Test
    fun `enrolled phrase requires a presented phrase`() {
        assertEquals(
            VoicePhraseChallengeDecision.REJECT_MISSING_PHRASE,
            evaluateVoicePhraseChallenge("blue river", null)
        )
    }

    @Test
    fun `matching phrase is accepted`() {
        assertEquals(
            VoicePhraseChallengeDecision.ACCEPT,
            evaluateVoicePhraseChallenge("blue river", " blue river ")
        )
    }

    @Test
    fun `different phrase is rejected`() {
        assertEquals(
            VoicePhraseChallengeDecision.REJECT_MISMATCH,
            evaluateVoicePhraseChallenge("blue river", "red river")
        )
    }

    @Test
    fun `legacy template without phrase remains compatible`() {
        assertEquals(
            VoicePhraseChallengeDecision.ACCEPT,
            evaluateVoicePhraseChallenge(null, null)
        )
    }
}
