/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.app.utils

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.crypto.CryptographyManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import java.nio.charset.Charset

private val testString = "Test data"
private var cryptoTests = HashMap<BiometricAuthRequest, CryptoTest>().apply {
    for (r in BiometricPromptCompat.getAvailableAuthRequests()) {
        this[r] = CryptoTest(testString.toByteArray(Charset.forName("UTF-8")))
    }
}

fun Fragment.startBiometric(
    biometricAuthRequest: BiometricAuthRequest,
    silentAuth: Boolean,
    crypto: Boolean,
    allowCredentials: Boolean
) {

    val credentialsAllowed = allowCredentials && BiometricManagerCompat.isDeviceSecureAvailable(requireContext())

    if (!BiometricManagerCompat.isBiometricReadyForUsage(biometricAuthRequest) && !credentialsAllowed) {
        if (!BiometricManagerCompat.hasPermissionsGranted(biometricAuthRequest))
            showAlertDialog(
                requireActivity(),
                "No permissions for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",

                )
        else
        if (!BiometricManagerCompat.isHardwareDetected(biometricAuthRequest))
            showAlertDialog(
                requireActivity(),
                "No hardware for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",

                )
        else if (!BiometricManagerCompat.hasEnrolled(biometricAuthRequest)) {
            showAlertDialog(
                requireActivity(),
                "No enrolled biometric for - ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
            )
        } else if (BiometricManagerCompat.isLockOut(biometricAuthRequest))
            showAlertDialog(
                requireActivity(),
                "Biometric sensor temporary locked for ${biometricAuthRequest.api}/${biometricAuthRequest.type}\nTry again later",
            )
        else if (BiometricManagerCompat.isBiometricSensorPermanentlyLocked(biometricAuthRequest))
            showAlertDialog(
                requireActivity(),
                "Biometric sensor permanently locked for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
            )
        else{
            showAlertDialog(
                requireActivity(),
                "Unexpected error state for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
            )
        }


        return
    }

    val start = System.currentTimeMillis()
    BiometricLoggerImpl.e("CheckBiometric.start() for $biometricAuthRequest")
    val biometricPromptCompat = BiometricPromptCompat.Builder(
        biometricAuthRequest,
        requireActivity()
    )
        .setTitle("Biometric for Fragment: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .setSubtitle("Biometric Subtitle: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .setDescription("Biometric Description: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more").apply {
            setNegativeButtonText("Cancel: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
            setDeviceCredentialFallbackAllowed(credentialsAllowed)
        }
        .also {
            if (crypto) {
                it.setCryptographyPurpose(
                    BiometricCryptographyPurpose(
                        cryptoTests[biometricAuthRequest]?.type?:BiometricCryptographyPurpose.ENCRYPT,
                        cryptoTests[biometricAuthRequest]?.vector
                    )
                )
            }
            if (silentAuth) {
                it.enableSilentAuth()
            }
        }
        .build()


    biometricPromptCompat.authenticate(object : BiometricPromptCompat.AuthenticationCallback() {
        override fun onSucceeded(confirmed: Set<AuthenticationResult>) {
            super.onSucceeded(confirmed)
            var cryptoText = "Crypto doesn't work or disabled"
            if (cryptoTests[biometricAuthRequest]?.type == BiometricCryptographyPurpose.ENCRYPT) {
                CryptographyManager.encryptData(
                    cryptoTests[biometricAuthRequest]?.byteArray,
                    confirmed
                )?.let {
                    cryptoText = "Crypto encryption result=${
                        String(
                            it.data,
                            Charset.forName("UTF-8")
                        )
                    }"
                    cryptoTests[biometricAuthRequest] = CryptoTest(it.data, it.initializationVector, BiometricCryptographyPurpose.DECRYPT)
                }

            } else {
                CryptographyManager.decryptData(
                    cryptoTests[biometricAuthRequest]?.byteArray,
                    confirmed
                )?.let {
                    cryptoText = "Crypto decryption result=${
                        String(
                            it.data,
                            Charset.forName("UTF-8")
                        )
                    }"
                    cryptoTests[biometricAuthRequest] =
                        CryptoTest(testString.toByteArray(Charset.forName("UTF-8")))
                }

            }

            BiometricLoggerImpl.e("CheckBiometric.onSucceeded() for $confirmed; $cryptoText")
            Toast.makeText(
                AndroidContext.appContext,
                "Succeeded - $confirmed; $cryptoText",
                Toast.LENGTH_SHORT
            )
                .show()
        }

        override fun onCanceled() {
            BiometricLoggerImpl.e("CheckBiometric.onCanceled()")
            Toast.makeText(AndroidContext.appContext, "Canceled", Toast.LENGTH_SHORT).show()
        }

        override fun onFailed(reason: AuthenticationFailureReason?, msg: CharSequence?) {
            BiometricLoggerImpl.e("CheckBiometric.onFailed() - $reason")
            try {
                when (reason) {
                    AuthenticationFailureReason.NO_HARDWARE -> showAlertDialog(
                        requireActivity(),
                        "No hardware for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
                    )

                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED -> showAlertDialog(
                        requireActivity(),
                        "No enrolled biometric for - ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
                    )

                    AuthenticationFailureReason.LOCKED_OUT -> showAlertDialog(
                        requireActivity(),
                        "Biometric sensor temporary locked for ${biometricAuthRequest.api}/${biometricAuthRequest.type}\nTry again later",
                    )

                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE -> showAlertDialog(
                        requireActivity(),
                        "Biometric sensor permanently locked for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
                    )

                    else -> showAlertDialog(requireActivity(), "Failure: $reason")
                }
            } catch (ignore: Throwable) {
                Toast.makeText(AndroidContext.appContext, "Failure: $reason", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        override fun onUIOpened() {
            BiometricLoggerImpl.e("CheckBiometric.onUIOpened()")
            Toast.makeText(AndroidContext.appContext, "onUIOpened", Toast.LENGTH_SHORT).show()
        }

        override fun onUIClosed() {
            BiometricLoggerImpl.e("CheckBiometric.onUIClosed()")
            Toast.makeText(AndroidContext.appContext, "onUIClosed", Toast.LENGTH_SHORT).show()
        }
    })
    Toast.makeText(
        AndroidContext.appContext,
        "Start biometric ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
        Toast.LENGTH_SHORT
    ).show()
}

private fun showAlertDialog(context: Context, msg: String) {
    AlertDialog.Builder(context).setTitle("Biometric Error").setMessage(msg)
        .setNegativeButton(android.R.string.cancel, null).show()
}

data class CryptoTest(
    val byteArray: ByteArray,
    val vector: ByteArray? = null,
    val type : Int = BiometricCryptographyPurpose.ENCRYPT,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CryptoTest

        if (!byteArray.contentEquals(other.byteArray)) return false
        if (vector != null) {
            if (other.vector == null) return false
            if (!vector.contentEquals(other.vector)) return false
        } else if (other.vector != null) return false
        return type == other.type
    }

    override fun hashCode(): Int {
        var result = byteArray.contentHashCode()
        result = 31 * result + (vector?.contentHashCode() ?: 0)
        result = 31 * result + type
        return result
    }
}