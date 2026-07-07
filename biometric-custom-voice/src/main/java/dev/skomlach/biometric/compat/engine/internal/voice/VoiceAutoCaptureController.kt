package dev.skomlach.biometric.compat.engine.internal.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.custom.voice.R
import dev.skomlach.common.translate.LocalizationHelper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal class VoiceAutoCaptureController(
    private val context: Context,
    private val builder: BiometricPromptCompat.Builder,
    enroll: Boolean,
    private val callback: VoiceAutoCaptureSession.Callback,
    private val onMaxAttemptsExceeded: () -> VoiceLockoutOutcome
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val disposed = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private val session = VoiceAutoCaptureSession(
        enroll = enroll,
        existingExtras = builder.getExtras(),
        phrase = builder.getVoicePhrase(),
        callback = callback,
        sampleRateHz = SAMPLE_RATE_HZ,
        messages = VoiceAutoCaptureSession.Messages(
            authStart = localized(R.string.biometriccompat_voice_auto_auth_start),
            voiceDetected = localized(R.string.biometriccompat_voice_auto_detected),
            processing = localized(R.string.biometriccompat_voice_status_checking),
            enrollRecordingStarted = { current, total ->
                localized(R.string.biometriccompat_voice_auto_enroll_recording_started, current, total)
            },
            sampleSavedTemplate = { current, total ->
                localized(R.string.biometriccompat_voice_auto_sample_saved, current, total)
            }
        )
    )

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var attemptsForCurrentSample = 0

    fun shouldAutoCapture(): Boolean = session.shouldAutoCapture()

    fun isReadyToStartAuth(): Boolean = session.isReadyToStartAuth()

    fun start() {
        if (!shouldAutoCapture()) {
            return
        }
        session.start()
        startNextAttempt()
    }

    fun dispose() {
        disposed.set(true)
        isRecording.set(false)
        stopRecorder()
        session.dispose()
    }

    private fun startNextAttempt() {
        if (disposed.get() || !callback.isPromptActive() || session.isReadyToStartAuth() || isRecording.get()) {
            return
        }
        Vibro.start()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            session.onFatalError(
                AuthenticationFailureReason.INTERNAL_ERROR,
                localized(R.string.biometriccompat_voice_error_recorder_unavailable)
            )
            return
        }

        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBufferSize, SAMPLE_RATE_HZ)
            )
        }.getOrNull()
        if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            session.onFatalError(
                AuthenticationFailureReason.INTERNAL_ERROR,
                localized(R.string.biometriccompat_voice_error_recorder_unavailable)
            )
            return
        }

        recorder = audioRecord
        isRecording.set(true)
        recordingThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val shortBuffer = ShortArray(max(minBufferSize / 2, STREAM_CHUNK_SIZE))
            val chunks = ArrayList<FloatArray>()
            val detector = VoiceStreamingDetector(sampleRateHz = SAMPLE_RATE_HZ)
            var detection = VoiceStreamingDetection(false, false, null, null)
            var fatalReadError = false
            val startedAt = SystemClock.elapsedRealtime()

            try {
                audioRecord.startRecording()
                while (isRecording.get() && !disposed.get() && callback.isPromptActive()) {
                    val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                    if (read <= 0) {
                        fatalReadError = true
                        break
                    }
                    val chunk = FloatArray(read) { index ->
                        (shortBuffer[index] / PCM_SCALE).coerceIn(-1f, 1f)
                    }
                    chunks += chunk

                    val nextDetection = detector.detect(chunks)
                    detection = nextDetection
                    if (nextDetection.isComplete) {
                        break
                    }
                    if (SystemClock.elapsedRealtime() - startedAt >= MAX_CAPTURE_WINDOW_MS) {
                        break
                    }
                }
            } catch (_: Throwable) {
                fatalReadError = true
            } finally {
                isRecording.set(false)
                stopRecorder()
            }

            mainHandler.post {
                recordingThread = null
                if (disposed.get() || !callback.isPromptActive()) {
                    return@post
                }
                if (fatalReadError) {
                    handleRecoverable(
                        localized(R.string.biometriccompat_voice_error_recording_failed),
                        countTowardsLockout = true
                    )
                    return@post
                }
                val captureDecision = decideVoiceCaptureSample(detection, SAMPLE_RATE_HZ)
                if (captureDecision.acceptedSample == null) {
                    val message = recoverableMessage(captureDecision)
                    val countTowardsLockout = shouldCountTowardsAutoCaptureLockout(captureDecision)
                    d(
                        "VoiceAutoCaptureController.reject reason=${captureDecision.rejectReason} " +
                            "quality=${captureDecision.qualityIssue} speech=${captureDecision.hadSpeechActivity} " +
                            "countTowardsLockout=$countTowardsLockout attempts=$attemptsForCurrentSample"
                    )
                    handleRecoverable(
                        message,
                        countTowardsLockout = countTowardsLockout
                    )
                    return@post
                }

                attemptsForCurrentSample = 0
                if (captureDecision.shouldNotifySpeechDetected) {
                    session.onSpeechDetected()
                }
                session.onSampleCaptured(captureDecision.acceptedSample)
                if (!session.isReadyToStartAuth()) {
                    startNextAttempt()
                }
            }
        }.apply {
            name = "VoiceAutoCaptureController"
            start()
        }
    }

    private fun handleRecoverable(
        message: CharSequence,
        countTowardsLockout: Boolean
    ) {
        if (countTowardsLockout) {
            attemptsForCurrentSample += 1
        }
        if (attemptsForCurrentSample >= MAX_ATTEMPTS_PER_SAMPLE) {
            val outcome = onMaxAttemptsExceeded()
            session.onFatalError(outcome.reason, localized(outcome.messageResId))
            return
        }
        session.onRecoverableError(message)
        startNextAttempt()
    }

    private fun stopRecorder() {
        recorder?.let { audioRecord ->
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }
        recorder = null
    }

    private fun localized(id: Int, vararg formatArgs: Any?): String {
        return LocalizationHelper.getLocalizedString(context, id, *formatArgs)
    }

    private fun recoverableMessage(decision: VoiceCaptureDecision): CharSequence {
        return when (decision.rejectReason) {
            VoiceCaptureRejectReason.NO_SPEECH -> {
                localized(R.string.biometriccompat_voice_error_no_speech)
            }

            VoiceCaptureRejectReason.INCOMPLETE_SAMPLE -> {
                localized(R.string.biometriccompat_voice_error_sample_too_short)
            }

            VoiceCaptureRejectReason.QUALITY_ISSUE -> {
                qualityMessage(decision.qualityIssue)
            }

            VoiceCaptureRejectReason.NONE -> {
                localized(R.string.biometriccompat_voice_error_recording_failed)
            }
        }
    }

    private fun qualityMessage(issue: VoiceQualityIssue): CharSequence {
        return when (issue) {
            VoiceQualityIssue.NONE -> localized(R.string.biometriccompat_voice_help_sample_valid)
            VoiceQualityIssue.SAMPLE_MISSING -> localized(R.string.biometriccompat_voice_help_sample_missing)
            VoiceQualityIssue.SAMPLE_RATE_TOO_LOW -> localized(R.string.biometriccompat_voice_help_sample_rate_low)
            VoiceQualityIssue.SAMPLE_TOO_SHORT -> localized(R.string.biometriccompat_voice_help_sample_short)
            VoiceQualityIssue.SAMPLE_TOO_LONG -> localized(R.string.biometriccompat_voice_help_sample_long)
            VoiceQualityIssue.SAMPLE_TOO_QUIET -> localized(R.string.biometriccompat_voice_help_sample_quiet)
            VoiceQualityIssue.SAMPLE_TOO_FLAT -> localized(R.string.biometriccompat_voice_help_sample_flat)
            VoiceQualityIssue.SAMPLE_CLIPPED -> localized(R.string.biometriccompat_voice_help_sample_clipped)
            VoiceQualityIssue.SAMPLE_REPLAY_RISK -> localized(R.string.biometriccompat_voice_help_sample_replay_risk)
            VoiceQualityIssue.EMBEDDING_INVALID -> localized(R.string.biometriccompat_voice_help_embedding_invalid)
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val STREAM_CHUNK_SIZE = 320
        const val MAX_CAPTURE_WINDOW_MS = 8_000L
        const val MAX_ATTEMPTS_PER_SAMPLE = 3
        const val PCM_SCALE = 32768f
    }
}
