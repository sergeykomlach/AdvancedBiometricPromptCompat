package dev.skomlach.biometric.compat.engine.internal.voice

/** Stored membership is independent of model readiness; READY means at least one compatible tag. */
enum class VoiceEnrollmentStatus {
    NOT_ENROLLED,
    ENGINE_NOT_READY,
    READY,
    REENROLLMENT_REQUIRED
}

internal fun voiceEnrollmentStatus(
    storedIdentities: Collection<String?>,
    expectedIdentity: String?
): VoiceEnrollmentStatus = when {
    storedIdentities.isEmpty() -> VoiceEnrollmentStatus.NOT_ENROLLED
    expectedIdentity.isNullOrBlank() -> VoiceEnrollmentStatus.ENGINE_NOT_READY
    storedIdentities.any { voiceTemplateIdentityMatches(it, expectedIdentity) } -> VoiceEnrollmentStatus.READY
    else -> VoiceEnrollmentStatus.REENROLLMENT_REQUIRED
}
