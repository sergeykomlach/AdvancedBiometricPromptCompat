package com.example.myapplication

import android.widget.Toast
import androidx.fragment.app.Fragment
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

fun Fragment.startBiometric(biometricAuthRequest: BiometricAuthRequest) {
    if (!BiometricManagerCompat.hasEnrolled(biometricAuthRequest)) {
        BiometricManagerCompat.openSettings(requireActivity(), biometricAuthRequest)
        return
    }
    val start = System.currentTimeMillis()
    BiometricLoggerImpl.e("CheckBiometric.start() for $biometricAuthRequest")
    val biometricPromptCompat = BiometricPromptCompat.Builder(
        biometricAuthRequest,
        requireActivity()
    )
        .setTitle("Biometric for Fragment").setNegativeButton("Cancel", null).build()

    biometricPromptCompat.authenticate(object : BiometricPromptCompat.Result {
        override fun onSucceeded() {
            Toast.makeText(activity, "Succeeded", Toast.LENGTH_SHORT).show()
        }

        override fun onCanceled() {
            Toast.makeText(activity, "Canceled", Toast.LENGTH_SHORT).show()
        }

        override fun onFailed(reason: AuthenticationFailureReason?) {
            Toast.makeText(activity, "Error: $reason", Toast.LENGTH_SHORT).show()
        }

        override fun onUIOpened() {
            Toast.makeText(activity, "onUIOpened", Toast.LENGTH_SHORT).show()
        }

        override fun onUIClosed() {
            Toast.makeText(activity, "onUIClosed", Toast.LENGTH_SHORT).show()
        }
    })
}