package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Process
import android.os.SystemClock
import kotlin.math.max

internal sealed interface VoiceCaptureOutcome {
    data class Accepted(
        val sample: FloatArray,
        val hadSpeechActivity: Boolean
    ) : VoiceCaptureOutcome

    data class Rejected(
        val decision: VoiceCaptureDecision
    ) : VoiceCaptureOutcome

    data object Timeout : VoiceCaptureOutcome

    data class Fatal(
        val message: CharSequence
    ) : VoiceCaptureOutcome
}

internal class VoiceCaptureOrchestrator(
    private val sampleRateHz: Int,
    private val mainHandler: Handler,
    private val isPromptActive: () -> Boolean,
    private val onOutcome: (VoiceCaptureOutcome) -> Unit,
    private val recorderUnavailableMessage: CharSequence,
    private val streamChunkSize: Int = DEFAULT_STREAM_CHUNK_SIZE,
    private val maxCaptureWindowMs: Long = DEFAULT_MAX_CAPTURE_WINDOW_MS
) {
    private var activeCapture: Any? = null
    private var recordingLifetime: VoiceRecordingLifetime? = null

    @Synchronized
    fun start(step: Int, total: Int) {
        if (activeCapture != null || !isPromptActive()) {
            return
        }
        val capture = Any().also { activeCapture = it }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            dispatch(capture, recorderFailureOutcome())
            return
        }

        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBufferSize, sampleRateHz)
            )
        }.getOrNull()
        if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { audioRecord?.release() }
            dispatch(capture, recorderFailureOutcome())
            return
        }

        val lifetime = VoiceRecordingLifetime(
            startRecording = audioRecord::startRecording,
            stopRecording = { runCatching { audioRecord.stop() } },
            releaseRecorder = { runCatching { audioRecord.release() } }
        )
        recordingLifetime = lifetime
        val worker = Thread {
            val shortBuffer = ShortArray(max(minBufferSize / 2, streamChunkSize))
            val chunks = ArrayList<FloatArray>()
            val detector = VoiceStreamingDetector(sampleRateHz = sampleRateHz)
            var detection = VoiceStreamingDetection(false, false, null, null)
            var captureFailed = false
            val startedAt = SystemClock.elapsedRealtime()

            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                if (!lifetime.start()) return@Thread
                while (lifetime.isActive && isPromptActive()) {
                    val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                    if (read <= 0) {
                        captureFailed = true
                        break
                    }
                    val chunk = FloatArray(read) { index ->
                        (shortBuffer[index] / PCM_SCALE).coerceIn(-1f, 1f)
                    }
                    chunks += chunk

                    detection = detector.detect(chunks, materializeActiveSample = false)
                    if (detection.isComplete) {
                        break
                    }
                    if (SystemClock.elapsedRealtime() - startedAt >= maxCaptureWindowMs) {
                        break
                    }
                }
            } catch (_: Throwable) {
                captureFailed = true
            } finally {
                lifetime.finish()
            }

            val outcome = if (captureFailed) {
                recorderFailureOutcome(hadSpeechActivity = detection.detectedSpeech)
            } else {
                if (detection.detectedSpeech && !detection.isComplete) {
                    detection = detector.detect(chunks, materializeActiveSample = true)
                }
                decideVoiceCaptureSample(detection, sampleRateHz).toOutcome()
            }
            dispatch(capture, outcome)
        }.apply {
            name = "VoiceCaptureOrchestrator-$step-$total"
        }
        try {
            worker.start()
        } catch (_: Throwable) {
            lifetime.finish()
            dispatch(capture, recorderFailureOutcome())
        }
    }

    @Synchronized
    fun cancel() {
        activeCapture = null
        recordingLifetime?.cancel()
        recordingLifetime = null
    }

    private fun dispatch(capture: Any, outcome: VoiceCaptureOutcome) {
        mainHandler.post {
            synchronized(this) {
                if (activeCapture !== capture) return@post
                activeCapture = null
                recordingLifetime = null
                if (!isPromptActive()) return@post
                onOutcome(outcome)
            }
        }
    }

    private fun VoiceCaptureDecision.toOutcome(): VoiceCaptureOutcome {
        acceptedSample?.let {
            return VoiceCaptureOutcome.Accepted(
                sample = it,
                hadSpeechActivity = hadSpeechActivity
            )
        }
        return VoiceCaptureOutcome.Rejected(this)
    }

    private fun recorderFailureOutcome(hadSpeechActivity: Boolean = false): VoiceCaptureOutcome.Rejected {
        return VoiceCaptureOutcome.Rejected(
            VoiceCaptureDecision(
                acceptedSample = null,
                rejectReason = VoiceCaptureRejectReason.RECORDER_FAILURE,
                qualityIssue = VoiceQualityIssue.NONE,
                shouldNotifySpeechDetected = false,
                hadSpeechActivity = hadSpeechActivity
            )
        )
    }

    private companion object {
        const val DEFAULT_STREAM_CHUNK_SIZE = 320
        const val DEFAULT_MAX_CAPTURE_WINDOW_MS = 8_000L
        const val PCM_SCALE = 32768f
    }
}

