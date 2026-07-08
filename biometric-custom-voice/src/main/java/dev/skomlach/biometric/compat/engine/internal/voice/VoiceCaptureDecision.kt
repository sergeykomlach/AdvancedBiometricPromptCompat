package dev.skomlach.biometric.compat.engine.internal.voice

internal enum class VoiceCaptureRejectReason {
    NONE,
    RECORDER_FAILURE,
    NO_SPEECH,
    INCOMPLETE_SAMPLE,
    QUALITY_ISSUE
}

internal data class VoiceCaptureDecision(
    val acceptedSample: FloatArray?,
    val rejectReason: VoiceCaptureRejectReason,
    val qualityIssue: VoiceQualityIssue,
    val shouldNotifySpeechDetected: Boolean,
    val hadSpeechActivity: Boolean
)

internal fun decideVoiceCaptureSample(
    detection: VoiceStreamingDetection,
    sampleRateHz: Int
): VoiceCaptureDecision {
    val candidateSample = detection.completedSample ?: detection.activeSample
        ?: return VoiceCaptureDecision(
            acceptedSample = null,
            rejectReason = VoiceCaptureRejectReason.NO_SPEECH,
            qualityIssue = VoiceQualityIssue.SAMPLE_MISSING,
            shouldNotifySpeechDetected = false,
            hadSpeechActivity = detection.detectedSpeech
        )

    val preprocessResult = VoiceAudioPreprocessor.preprocess(candidateSample, sampleRateHz)
    if (preprocessResult.qualityIssue != VoiceQualityIssue.NONE) {
        return VoiceCaptureDecision(
            acceptedSample = null,
            rejectReason = VoiceCaptureRejectReason.QUALITY_ISSUE,
            qualityIssue = preprocessResult.qualityIssue,
            shouldNotifySpeechDetected = false,
            hadSpeechActivity = detection.detectedSpeech
        )
    }

    if (!detection.isComplete) {
        return VoiceCaptureDecision(
            acceptedSample = null,
            rejectReason = VoiceCaptureRejectReason.INCOMPLETE_SAMPLE,
            qualityIssue = VoiceQualityIssue.NONE,
            shouldNotifySpeechDetected = false,
            hadSpeechActivity = detection.detectedSpeech
        )
    }

    return VoiceCaptureDecision(
        acceptedSample = preprocessResult.pcm,
        rejectReason = VoiceCaptureRejectReason.NONE,
        qualityIssue = VoiceQualityIssue.NONE,
        shouldNotifySpeechDetected = detection.detectedSpeech,
        hadSpeechActivity = detection.detectedSpeech
    )
}
