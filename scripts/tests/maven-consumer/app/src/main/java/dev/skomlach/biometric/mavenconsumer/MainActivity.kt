package dev.skomlach.biometric.mavenconsumer

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricProviderType
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.auth.helpers.BiometricAuthRequestData
import dev.skomlach.biometric.compat.auth.startBiometricAuthentication
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider
import java.util.ServiceLoader

class MainActivity : AppCompatActivity() {
    private var prompt: BiometricPromptCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providers = ServiceLoader.load(SoftwareBiometricProvider::class.java).toList()
        check(providers.size == BuildConfig.EXPECTED_PROVIDERS) {
            "Expected ${BuildConfig.EXPECTED_PROVIDERS} providers, discovered ${providers.size}"
        }
        val request = if (BuildConfig.FLAVOR == "sherpaOnly") {
            BiometricAuthRequest.default()
                .withType(BiometricType.BIOMETRIC_VOICE)
                .withProvider(BiometricProviderType.SOFTWARE)
        } else BiometricAuthRequest.default()
        val status = TextView(this).apply { text = "Discovered providers: ${providers.size}" }
        val button = Button(this).apply {
            text = "Open biometric prompt"
            setOnClickListener {
                prompt?.cancelAuthentication()
                prompt = startBiometricAuthentication(
                    BiometricAuthRequestData(biometricAuthRequest = request),
                    callback = object : BiometricPromptCompat.AuthenticationCallback() {
                        override fun onSucceeded(confirmed: Set<AuthenticationResult>) {
                            super.onSucceeded(confirmed)
                            status.text = "Succeeded"
                        }
                        override fun onCanceled(canceled: Set<AuthenticationResult>) { status.text = "Canceled" }
                        override fun onFailed(failed: Set<AuthenticationResult>) { status.text = "Failed" }
                    }
                )
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(button)
        })
    }

    override fun onDestroy() {
        prompt?.cancelAuthentication()
        super.onDestroy()
    }
}
