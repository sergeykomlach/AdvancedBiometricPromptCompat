package dev.skomlach.biometric.compat.engine.internal.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.custom.voice.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.storage.SharedPreferenceProvider
import dev.skomlach.common.translate.LocalizationHelper
import java.util.concurrent.atomic.AtomicBoolean

class VoiceBiometricManager(
    private val context: Context,
    private val store: VoiceTemplateStore = VoiceTemplateStore(),
    private val engine: VoiceEngine = CepstralVoiceEngine()
) : AbstractSoftwareBiometricManager() {

    override val biometricType: BiometricType = BiometricType.BIOMETRIC_VOICE
    override val priority: Int = PRIORITY_BELOW_SYSTEM_HARDWARE

    private val sessionActive = AtomicBoolean(false)
    private var currentHandler: Handler = Handler(Looper.getMainLooper())
    private var resultRunnable: Runnable? = null
    private val prefs by lazy {
        SharedPreferenceProvider.getProtectedPreferences(LOCKOUT_STORAGE_NAME)
    }

    override fun getTimeoutMessage(): CharSequence =
        localized(R.string.biometriccompat_voice_help_timeout)

    override fun resetLockOut() {
        resetTemporaryLockoutState(prefs)
    }

    override fun resetPermanentLockOut() {
        resetPermanentLockoutState(prefs)
    }

    override fun getPermissions(): List<String> = listOf(Manifest.permission.RECORD_AUDIO)

    override fun getLockoutError(): Int? = getStoredLockoutError(prefs, LOCKOUT_POLICY)

    override fun isHardwareDetected(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) &&
            engine.isAvailable()
    }

    override fun hasEnrolledBiometric(): Boolean = store.hasTemplate()

    override fun getManagers(): Set<Any> = setOf(this, engine)

    override fun remove(extra: Bundle?) {
        store.remove(extra?.getString(ENROLLMENT_TAG_KEY))
    }

    override fun getEnrollBundle(name: String?): Bundle {
        return Bundle().apply {
            putBoolean(IS_ENROLLMENT_KEY, true)
            putString(ENROLLMENT_TAG_KEY, store.sanitizeTag(name))
        }
    }

    override fun getEnrolls(): Collection<String> = store.templateNames()

    override fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {
        cancelActiveSession()
        sessionActive.set(true)
        currentHandler = handler ?: Handler(Looper.getMainLooper())
        cancel?.setOnCancelListener {
            cancelActiveSession()
            callback?.onAuthenticationCancelled()
        }

        val lockoutError = getLockoutError()
        if (lockoutError != null) {
            finishWithError(callback, lockoutError, lockoutMessage(lockoutError))
            return
        }

        if (!isHardwareDetected()) {
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                localized(R.string.biometriccompat_voice_help_unavailable)
            )
            return
        }

        if (!hasRecordAudioPermission()) {
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS,
                localized(R.string.biometriccompat_voice_help_permission_required)
            )
            return
        }

        val samples = VoiceSample.fromBundleSamples(extra)
        if (samples.isEmpty()) {
            e("VoiceBiometricManager.authenticate sample=missing_or_incomplete")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_voice_help_sample_required)
            )
            return
        }
        val sample = samples.first()

        val sampleQualityIssue = samples
            .map { it.qualityIssue() }
            .firstOrNull { it != VoiceQualityIssue.NONE }
            ?: VoiceQualityIssue.NONE
        if (sampleQualityIssue != VoiceQualityIssue.NONE) {
            e("VoiceBiometricManager.authenticate quality=$sampleQualityIssue samples=${samples.size}")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                qualityMessage(sampleQualityIssue)
            )
            return
        }

        val embeddingResults = samples.map { engine.extractEmbedding(it) }
        val preprocessMetrics = embeddingResults
            .mapNotNull { it?.preprocessMetrics }
            .joinToString(separator = ";") { it.toLogString() }
        val embeddingQualityIssue = embeddingResults
            .map { it?.qualityIssue ?: VoiceQualityIssue.SAMPLE_MISSING }
            .firstOrNull { it != VoiceQualityIssue.NONE }
            ?: VoiceQualityIssue.NONE
        val embeddings = embeddingResults
            .mapNotNull { it?.embedding?.takeIf { embedding -> embedding.isValidEmbedding() } }
        val featureBatches = embeddingResults
            .map { it?.featureFrames.orEmpty() }
            .filter { it.isNotEmpty() }
        if (embeddings.size != samples.size || embeddingQualityIssue != VoiceQualityIssue.NONE) {
            e("VoiceBiometricManager.authenticate embedding_quality=$embeddingQualityIssue metrics=$preprocessMetrics")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                qualityMessage(embeddingQualityIssue)
            )
            return
        }

        if (extra?.getBoolean(IS_ENROLLMENT_KEY, false) == true) {
            val tag = store.saveAll(
                extra.getString(ENROLLMENT_TAG_KEY),
                sample.phrase,
                embeddings,
                featureBatches
            )
            resetTemporaryLockoutState(prefs)
            e(
                "VoiceBiometricManager.enroll quality=OK samples=${embeddings.size} " +
                    "featureBatches=${featureBatches.size} featureFrames=${featureBatches.sumOf { it.size }} " +
                    "metrics=$preprocessMetrics"
            )
            finishWithSuccess(
                callback,
                crypto,
                localized(R.string.biometriccompat_voice_help_enrolled, tag)
            )
            return
        }

        val embedding = embeddings.first()
        val probeFrames = embeddingResults.firstOrNull()?.featureFrames.orEmpty()

        val templates = store.loadTemplates()
        if (templates.isEmpty()) {
            e("VoiceBiometricManager.authenticate templates=0")
            finishWithError(
                callback,
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                localized(R.string.biometriccompat_voice_help_not_registered)
            )
            return
        }

        val matchingTemplates = templates.filter { template ->
            template.phrase == null || sample.phrase == null || template.phrase == sample.phrase
        }
        val bestMatch = matchingTemplates
            .groupBy { it.tag }
            .values
            .map { enrolledTemplates ->
                matchVoiceTemplatesDetailed(enrolledTemplates, embedding, probeFrames, TOP_K_TEMPLATES)
            }
            .maxByOrNull { it.score }
            ?: VoiceTemplateMatch(0f, VoiceMatchMethod.NONE, 0, 0, probeFrames.size, null)

        e(
            "VoiceBiometricManager.authenticate templates=${templates.size} " +
                "candidates=${matchingTemplates.size} " +
                "method=${bestMatch.method} score=${bestMatch.score} threshold=$MATCH_THRESHOLD " +
                "probeFrames=${bestMatch.probeFrameCount} gmmModels=${bestMatch.gmmModelCount} " +
                "metrics=$preprocessMetrics " +
                "gmm=${bestMatch.gmmDetails?.toLogString().orEmpty()}"
        )
        if (bestMatch.score >= MATCH_THRESHOLD) {
            resetTemporaryLockoutState(prefs)
            finishWithSuccess(
                callback,
                crypto,
                localized(R.string.biometriccompat_voice_help_accepted)
            )
        } else {
            recordFailedAttempt(prefs, LOCKOUT_POLICY)
            val updatedLockout = getLockoutError()
            if (updatedLockout != null) {
                finishWithError(callback, updatedLockout, lockoutMessage(updatedLockout))
            } else {
                finishWithError(
                    callback,
                    CUSTOM_BIOMETRIC_ERROR_VENDOR,
                    localized(R.string.biometriccompat_voice_help_not_matched)
                )
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            context.packageManager.checkPermission(
                Manifest.permission.RECORD_AUDIO,
                context.packageName
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun qualityMessage(issue: VoiceQualityIssue): CharSequence {
        return when (issue) {
            VoiceQualityIssue.NONE -> localized(R.string.biometriccompat_voice_help_sample_valid)
            VoiceQualityIssue.SAMPLE_MISSING ->
                localized(R.string.biometriccompat_voice_help_sample_missing)
            VoiceQualityIssue.SAMPLE_RATE_TOO_LOW ->
                localized(R.string.biometriccompat_voice_help_sample_rate_low)
            VoiceQualityIssue.SAMPLE_TOO_SHORT ->
                localized(R.string.biometriccompat_voice_help_sample_short)
            VoiceQualityIssue.SAMPLE_TOO_LONG ->
                localized(R.string.biometriccompat_voice_help_sample_long)
            VoiceQualityIssue.SAMPLE_TOO_QUIET ->
                localized(R.string.biometriccompat_voice_help_sample_quiet)
            VoiceQualityIssue.SAMPLE_TOO_FLAT ->
                localized(R.string.biometriccompat_voice_help_sample_flat)
            VoiceQualityIssue.SAMPLE_CLIPPED ->
                localized(R.string.biometriccompat_voice_help_sample_clipped)
            VoiceQualityIssue.SAMPLE_REPLAY_RISK ->
                localized(R.string.biometriccompat_voice_help_sample_replay_risk)
            VoiceQualityIssue.EMBEDDING_INVALID ->
                localized(R.string.biometriccompat_voice_help_embedding_invalid)
        }
    }

    private fun finishWithSuccess(
        callback: AuthenticationCallback?,
        crypto: CryptoObject?,
        helpMessage: CharSequence
    ) {
        resultRunnable = Runnable {
            if (sessionActive.compareAndSet(true, false)) {
                callback?.onAuthenticationHelp(CUSTOM_BIOMETRIC_ACQUIRED_GOOD, helpMessage)
                callback?.onAuthenticationSucceeded(AuthenticationResult(crypto))
            }
        }.also {
            currentHandler.postDelayed(it, RESULT_DELAY_MS)
        }
    }

    private fun finishWithError(
        callback: AuthenticationCallback?,
        error: Int,
        message: CharSequence
    ) {
        if (sessionActive.compareAndSet(true, false)) {
            callback?.onAuthenticationError(error, message)
        }
    }

    private fun cancelActiveSession() {
        sessionActive.set(false)
        resultRunnable?.let { currentHandler.removeCallbacks(it) }
        resultRunnable = null
    }

    private fun lockoutMessage(error: Int): CharSequence {
        return when (error) {
            CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT ->
                localized(R.string.biometriccompat_voice_help_lockout_permanent)
            CUSTOM_BIOMETRIC_ERROR_LOCKOUT ->
                localized(R.string.biometriccompat_voice_help_lockout)
            else -> localized(R.string.biometriccompat_voice_help_unavailable)
        }
    }

    private fun localized(id: Int, vararg formatArgs: Any?): String {
        return LocalizationHelper.getLocalizedString(context, id, *formatArgs)
    }

    private companion object {
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"
        const val LOCKOUT_STORAGE_NAME = "voice_lockout"
        const val MATCH_THRESHOLD = 0.78f
        const val TOP_K_TEMPLATES = 3
        const val RESULT_DELAY_MS = 500L

        val LOCKOUT_POLICY = LockoutPolicy(
            maxFailedAttemptsBeforeLockout = 5,
            maxTemporaryLockoutsBeforePermanent = 5,
            lockoutDurationMs = 30_000L
        )
    }
}

private fun GmmConfidenceDetails.toLogString(): String {
    return "avgLL=$averageLogLikelihood enrollLL=$enrollmentLogLikelihood " +
        "drop=$likelihoodDrop allowedDrop=$allowedDrop components=$componentCount"
}
