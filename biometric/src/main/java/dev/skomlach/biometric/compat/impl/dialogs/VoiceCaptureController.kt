package dev.skomlach.biometric.compat.impl.dialogs

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.R
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

internal class VoiceCaptureController(
    private val rootView: View,
    private val builder: BiometricPromptCompat.Builder,
    private val onReady: () -> Unit
) {
    private val recording = AtomicBoolean(false)
    private var prepared = false
    private var overlayView: View? = null
    private var voiceButton: View? = null
    private var audioRecord: AudioRecord? = null
    private val enrollmentRecordings = ArrayList<VoiceRecording>()
    private lateinit var actionButton: Button
    private lateinit var hintText: TextView
    private lateinit var errorText: TextView

    fun install() {
        val container = rootView.findViewById<FrameLayout>(R.id.auth_content_container) ?: return
        voiceButton = createVoiceButton(container).also { container.addView(it) }
    }

    fun consumePrepared(): Boolean = prepared

    fun dispose() {
        stopRecording()
        overlayView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        voiceButton?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlayView = null
        voiceButton = null
    }

    private fun createVoiceButton(container: FrameLayout): View {
        val context = container.context
        val size = (48 * context.resources.displayMetrics.density).toInt()
        return TextView(context).apply {
            text = "V"
            textSize = 18f
            gravity = Gravity.CENTER
            contentDescription = "Voice"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            background = rounded(
                color = ContextCompat.getColor(context, R.color.material_deep_teal_500),
                radius = size / 2f
            )
            elevation = 8f * context.resources.displayMetrics.density
            setOnClickListener {
                showOverlay(container)
            }
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.BOTTOM).apply {
                rightMargin = (12 * context.resources.displayMetrics.density).toInt()
                bottomMargin = (12 * context.resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun showOverlay(container: FrameLayout) {
        if (overlayView != null) return
        overlayView = createOverlay(container).also { container.addView(it) }
    }

    private fun createOverlay(container: FrameLayout): View {
        val context = container.context
        val density = context.resources.displayMetrics.density
        val phrase = voicePhrase()

        actionButton = Button(context).apply {
            text = if (builder.enroll) "Record voice" else "Verify voice"
            contentDescription = if (builder.enroll) "Record voice sample" else "Verify voice sample"
            setOnClickListener {
                if (hasRecordAudioPermission(context)) {
                    startRecording(context)
                } else {
                    showInputError("Microphone permission is required.")
                }
            }
        }
        hintText = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.textColor))
            text = initialHint(phrase)
        }
        errorText = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            visibility = View.GONE
            setTextColor(ContextCompat.getColor(context, R.color.material_red_500))
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )
            background = rounded(
                color = ContextCompat.getColor(context, android.R.color.white),
                radius = 18 * density
            )
            elevation = 10f * density
            addView(TextView(context).apply {
                text = if (builder.enroll) "Create voice sample" else "Confirm voice"
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.textColor))
            })
            addView(hintText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            if (!phrase.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = phrase
                    gravity = Gravity.CENTER
                    textSize = 18f
                    setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                    setTextColor(ContextCompat.getColor(context, R.color.textColor))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            addView(errorText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(actionButton)
        }

        return FrameLayout(context).apply {
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener {
                if (!recording.get()) hideOverlay()
            }
            addView(panel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                leftMargin = (12 * density).toInt()
                rightMargin = (12 * density).toInt()
            })
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            panel.setOnClickListener { }
        }
    }

    private fun startRecording(context: Context) {
        if (!recording.compareAndSet(false, true)) return
        clearInputError()
        actionButton.isEnabled = false
        actionButton.text = "Listening..."
        rootView.findViewById<TextView>(R.id.status)?.text = "Listening for voice"

        ExecutorHelper.startOnBackground {
            val result = recordPcm()
            ExecutorHelper.post {
                recording.set(false)
                audioRecord = null
                if (result == null) {
                    actionButton.isEnabled = true
                    actionButton.text = if (builder.enroll) "Record sample" else "Try again"
                    showInputError("Voice sample could not be captured.")
                } else {
                    onRecordingCaptured(result)
                }
            }
        }
    }

    private fun onRecordingCaptured(result: VoiceRecording) {
        if (!builder.enroll) {
            prepareExtras(listOf(result))
            return
        }
        enrollmentRecordings.add(result)
        if (enrollmentRecordings.size >= ENROLLMENT_RECORDING_COUNT) {
            prepareExtras(enrollmentRecordings.toList())
        } else {
            actionButton.isEnabled = true
            actionButton.text = "Record sample ${enrollmentRecordings.size + 1}"
            hintText.text = enrollmentProgressHint()
            rootView.findViewById<TextView>(R.id.status)?.text = enrollmentProgressHint()
        }
    }

    @Suppress("MissingPermission")
    private fun recordPcm(): VoiceRecording? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE_HZ)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord = recorder
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        return try {
            val targetSamples = SAMPLE_RATE_HZ * RECORD_DURATION_MS / 1000
            val pcm = FloatArray(targetSamples)
            val shortBuffer = ShortArray(minBuffer.coerceAtLeast(1024))
            var written = 0
            recorder.startRecording()
            while (recording.get() && written < targetSamples) {
                val read = recorder.read(
                    shortBuffer,
                    0,
                    minOf(shortBuffer.size, targetSamples - written)
                )
                if (read <= 0) break
                for (index in 0 until read) {
                    pcm[written + index] = shortBuffer[index] / SHORT_PCM_SCALE
                }
                written += read
            }
            val captured = pcm.copyOf(written)
            if (isUsable(captured)) VoiceRecording(SAMPLE_RATE_HZ, captured) else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun prepareExtras(recordings: List<VoiceRecording>) {
        val firstRecording = recordings.first()
        val extras = Bundle(builder.getExtras() ?: Bundle()).apply {
            putInt(EXTRA_VOICE_SAMPLE_RATE, firstRecording.sampleRateHz)
            if (recordings.size == 1) {
                putFloatArray(EXTRA_VOICE_PCM_FLOAT, firstRecording.pcmFloat)
            } else {
                putInt(EXTRA_VOICE_SAMPLE_COUNT, recordings.size)
                recordings.forEachIndexed { index, recording ->
                    putFloatArray("$EXTRA_VOICE_PCM_FLOAT.$index", recording.pcmFloat)
                }
            }
            voicePhrase()?.let {
                putString(EXTRA_VOICE_PHRASE, it)
            }
            putBoolean(BundleBuilder.ENROLL, builder.enroll)
        }
        prepared = true
        builder.setExtras(extras)
        actionButton.isEnabled = false
        rootView.findViewById<TextView>(R.id.status)?.text = "Checking voice"
        hideOverlay()
        onReady()
    }

    private fun initialHint(phrase: String?): String {
        val base = if (phrase.isNullOrBlank()) {
            "Speak naturally for about 2 seconds."
        } else {
            "Say this phrase clearly:"
        }
        return if (builder.enroll) {
            "$base Record $ENROLLMENT_RECORDING_COUNT samples."
        } else {
            base
        }
    }

    private fun enrollmentProgressHint(): String {
        return "Voice sample ${enrollmentRecordings.size} of $ENROLLMENT_RECORDING_COUNT captured."
    }

    private fun isUsable(pcm: FloatArray): Boolean {
        if (pcm.size < SAMPLE_RATE_HZ * MIN_DURATION_MS / 1000) return false
        var sumSquares = 0.0
        for (value in pcm) {
            sumSquares += value * value
        }
        val rms = sqrt(sumSquares / pcm.size).toFloat()
        return rms >= MIN_RMS
    }

    private fun stopRecording() {
        recording.set(false)
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun hideOverlay() {
        overlayView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlayView = null
    }

    private fun showInputError(message: CharSequence) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        rootView.findViewById<TextView>(R.id.status)?.text = message
    }

    private fun clearInputError() {
        if (::errorText.isInitialized) {
            errorText.text = null
            errorText.visibility = View.GONE
        }
    }

    private fun voicePhrase(): String? {
        return resolveVoicePhrase(
            builder.getVoicePhrase(),
            builder.getExtras()?.getString(EXTRA_VOICE_PHRASE)
        )
    }

    private fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class VoiceRecording(
        val sampleRateHz: Int,
        val pcmFloat: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as VoiceRecording
            return sampleRateHz == other.sampleRateHz && pcmFloat.contentEquals(other.pcmFloat)
        }

        override fun hashCode(): Int {
            return 31 * sampleRateHz + pcmFloat.contentHashCode()
        }
    }

    private companion object {
        const val EXTRA_VOICE_SAMPLE_RATE = "voice.sample_rate"
        const val EXTRA_VOICE_PCM_FLOAT = "voice.pcm_float"
        const val EXTRA_VOICE_PHRASE = "voice.phrase"
        const val EXTRA_VOICE_SAMPLE_COUNT = "voice.sample_count"
        const val SAMPLE_RATE_HZ = 16_000
        const val RECORD_DURATION_MS = 2_400
        const val MIN_DURATION_MS = 900
        const val MIN_RMS = 0.012f
        const val SHORT_PCM_SCALE = 32768f
        const val ENROLLMENT_RECORDING_COUNT = 3

        fun rounded(color: Int, radius: Float): GradientDrawable {
            return GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius
            }
        }
    }
}

internal fun resolveVoicePhrase(builderPhrase: CharSequence?, extrasPhrase: String?): String? {
    return builderPhrase?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        ?: extrasPhrase?.trim()?.takeIf { it.isNotEmpty() }
}
