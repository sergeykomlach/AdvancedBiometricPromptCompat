package dev.skomlach.biometric.app.signature

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.app.R
import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.engine.internal.behavior.BehaviorBiometricManager
import dev.skomlach.biometric.compat.engine.internal.behavior.BehaviorSample
import java.util.concurrent.atomic.AtomicBoolean

internal object SignatureSystemPolicy {
    fun builder(activity: FragmentActivity): BiometricPromptCompat.Builder = BiometricPromptCompat.Builder(
        BiometricAuthRequest.default().withApi(BiometricApi.BIOMETRIC_API)
            .withProvider(BiometricProviderType.HARDWARE).withType(BiometricType.BIOMETRIC_ANY)
            .withConfirmation(BiometricConfirmation.ANY), activity)
        .setBiometricTrustPolicy(BiometricTrustPolicy(BiometricTrustLevel.SYSTEM_BIOMETRIC, false))
        .setDeviceCredentialFallbackAllowed(false)
        .setCryptoFallbackAllowed(false)
        .setTitle(activity.getString(R.string.signature_system_title))
        .setSubtitle(activity.getString(R.string.signature_system_prompt))
        .setNegativeButtonText(activity.getString(R.string.signature_system_cancel))

    // Provenance comes from the owned BIOMETRIC_API/HARDWARE request, not from type alone.
    fun accepts(results: Set<AuthenticationResult>): Boolean = results.isNotEmpty() && results.all {
        it.reason == null && it.type in setOf(BiometricType.BIOMETRIC_ANY, BiometricType.BIOMETRIC_FINGERPRINT,
            BiometricType.BIOMETRIC_FACE, BiometricType.BIOMETRIC_IRIS)
    }
}

internal class SystemBiometricClient(private val activity: FragmentActivity) {
    fun authenticate(result: (SystemBiometricOutcome) -> Unit): AutoCloseable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            result(SystemBiometricOutcome.FAILED)
            return AutoCloseable {}
        }
        val builder = SignatureSystemPolicy.builder(activity)
        val closed = AtomicBoolean(false)
        val terminal = AtomicBoolean(false)
        var prompt: BiometricPromptCompat? = null
        val handle = AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                prompt?.cancelAuthentication()
                builder.release()
            }
        }
        fun deliver(outcome: SystemBiometricOutcome) {
            if (!closed.get() && terminal.compareAndSet(false, true)) result(outcome)
        }
        try {
            val created = builder.build().also { prompt = it }
            created.authenticate(object : BiometricPromptCompat.AuthenticationCallback() {
                override fun onSucceeded(confirmed: Set<AuthenticationResult>) = deliver(
                    if (SignatureSystemPolicy.accepts(confirmed)) SystemBiometricOutcome.SUCCEEDED else SystemBiometricOutcome.FAILED)
                override fun onFailed(canceled: Set<AuthenticationResult>) = deliver(SystemBiometricOutcome.FAILED)
                override fun onCanceled(canceled: Set<AuthenticationResult>) = deliver(SystemBiometricOutcome.CANCELED)
            })
        } catch (_: Exception) { handle.close(); throw IllegalStateException("System biometric unavailable") }
        return handle
    }
}

/** Compatibility observations are intentionally evaluated outside the system authorization request. */
internal class BehaviorSignalClient(context: Context,
    private val manager: AbstractSoftwareBiometricManager = BehaviorBiometricManager(context.applicationContext)) {
    fun evaluate(sample: BehaviorSample, result: (SupplementalBiometricOutcome) -> Unit): AutoCloseable {
        val signal = CancellationSignal()
        val closed = AtomicBoolean(false)
        val terminal = AtomicBoolean(false)
        fun deliver(outcome: SupplementalBiometricOutcome) {
            if (!closed.get() && terminal.compareAndSet(false, true)) result(outcome)
        }
        val points = sample.strokePoints.flatMap { point -> listOf(point.x, point.y,
            point.timestampMs.toFloat(), point.pressure ?: -1f, point.size ?: -1f, point.strokeId.toFloat()) }.toFloatArray()
        val extras = Bundle().apply {
            putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, "SIGNATURE")
            putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS, points)
            putInt(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE, 6)
        }
        try {
            manager.authenticate(null, 0, signal, object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) =
                    deliver(if (result != null) SupplementalBiometricOutcome.MATCHED else SupplementalBiometricOutcome.UNAVAILABLE)
                override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) = deliver(
                    if (errMsgId == AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_VENDOR)
                        SupplementalBiometricOutcome.NOT_MATCHED else SupplementalBiometricOutcome.UNAVAILABLE)
                override fun onAuthenticationCancelled() = deliver(SupplementalBiometricOutcome.UNAVAILABLE)
            }, Handler(Looper.getMainLooper()), extras)
        } catch (_: Exception) {
            signal.cancel()
            deliver(SupplementalBiometricOutcome.UNAVAILABLE)
        } finally {
            // Manager parses synchronously into its own value objects. Do not retain raw extras in core/Builder.
            extras.clear()
            points.fill(0f)
        }
        return AutoCloseable { if (closed.compareAndSet(false, true)) signal.cancel() }
    }
}
