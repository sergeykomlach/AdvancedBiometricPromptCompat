package dev.skomlach.biometric.compat.engine.internal.voice

internal enum class VoicePhraseChallengeDecision {
    ACCEPT,
    REJECT_MISSING_PHRASE,
    REJECT_MISMATCH
}

internal fun isVoicePhraseRequired(enrolledPhrases: Collection<String?>): Boolean {
    return enrolledPhrases.any { !it.isNullOrBlank() }
}

internal fun evaluateVoicePhraseChallenge(
    enrolledPhrase: String?,
    presentedPhrase: String?
): VoicePhraseChallengeDecision {
    val expected = enrolledPhrase?.trim()?.takeIf { it.isNotEmpty() }
        ?: return VoicePhraseChallengeDecision.ACCEPT
    val actual = presentedPhrase?.trim()?.takeIf { it.isNotEmpty() }
        ?: return VoicePhraseChallengeDecision.REJECT_MISSING_PHRASE
    return if (expected == actual) {
        VoicePhraseChallengeDecision.ACCEPT
    } else {
        VoicePhraseChallengeDecision.REJECT_MISMATCH
    }
}
