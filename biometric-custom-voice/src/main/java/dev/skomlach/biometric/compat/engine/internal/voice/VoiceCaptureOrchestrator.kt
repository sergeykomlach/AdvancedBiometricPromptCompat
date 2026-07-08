package dev.skomlach.biometric.compat.engine.internal.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
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
    private val isRecording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null

    fun start(step: Int, total: Int) {
        if (isRecording.get() || !isPromptActive()) {
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            dispatch(recorderFailureOutcome())
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
            audioRecord?.release()
            dispatch(recorderFailureOutcome())
            return
        }

        recorder = audioRecord
        isRecording.set(true)
        recordingThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val shortBuffer = ShortArray(max(minBufferSize / 2, streamChunkSize))
            val chunks = ArrayList<FloatArray>()
            val detector = VoiceStreamingDetector(sampleRateHz = sampleRateHz)
            var detection = VoiceStreamingDetection(false, false, null, null)
            var captureFailed = false
            val startedAt = SystemClock.elapsedRealtime()

            try {
                audioRecord.startRecording()
                while (isRecording.get() && isPromptActive()) {
                    val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                    if (read <= 0) {
                        captureFailed = true
                        break
                    }
                    val chunk = FloatArray(read) { index ->
                        (shortBuffer[index] / PCM_SCALE).coerceIn(-1f, 1f)
                    }
                    chunks += chunk

                    detection = detector.detect(chunks)
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
                isRecording.set(false)
                stopRecorder()
            }

            val outcome = if (captureFailed) {
                recorderFailureOutcome(hadSpeechActivity = detection.detectedSpeech)
            } else {
                decideVoiceCaptureSample(detection, sampleRateHz).toOutcome()
            }
            dispatch(outcome)
        }.apply {
            name = "VoiceCaptureOrchestrator-$step-$total"
            start()
        }
    }

    fun cancel() {
        isRecording.set(false)
        stopRecorder()
        recordingThread = null
    }

    private fun dispatch(outcome: VoiceCaptureOutcome) {
        mainHandler.post {
            recordingThread = null
            if (!isPromptActive()) {
                return@post
            }
            onOutcome(outcome)
        }
    }

    private fun stopRecorder() {
        recorder?.let { audioRecord ->
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }
        recorder = null
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
