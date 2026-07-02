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
import dev.skomlach.biometric.app.R
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricAuthException
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricAuthSnapshot
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.crypto.CryptographyManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.contextprovider.getFixedContext
import java.nio.charset.Charset

private val testString = "Test data"
private val cryptoTests = HashMap<BiometricAuthRequest, CryptoTest>()

fun Fragment.startBiometric(
    biometricAuthRequest: BiometricAuthRequest,
    silentAuth: Boolean,
    crypto: Boolean,
    allowCredentials: Boolean,
    isRegister: Boolean
) {

    BiometricLoggerImpl.e("CheckBiometric.start() for $biometricAuthRequest isRegister=$isRegister")
    val authSnapshot = BiometricManagerCompat.getAuthSnapshot(biometricAuthRequest)
    if (isRegister) {
        val readyForEnroll = authSnapshot.readyForEnroll
        if (!readyForEnroll && !allowCredentials) {
            showAlertDialog(
                requireActivity(),
                biometricUnavailableMessage(
                    authSnapshot,
                    forEnroll = true,
                    context = requireActivity()
                )
            )
            return
        }
    } else {
        val readyForUsage = authSnapshot.readyForUsage
        if (!readyForUsage && !allowCredentials) {
//        if (!BiometricManagerCompat.hasPermissionsGranted(biometricAuthRequest))
//            showAlertDialog(
//                requireActivity(),
//                "No permissions for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
//
//                )
//        else
            showAlertDialog(
                requireActivity(),
                biometricUnavailableMessage(
                    authSnapshot,
                    forEnroll = false,
                    context = requireActivity()
                )
            )
            return
        }
    }
    val start = System.currentTimeMillis()

    val biometricPromptCompat = BiometricPromptCompat.Builder(
        biometricAuthRequest,
        requireActivity()
    )
        .setTitle(getString(R.string.biometric_demo_title))
        .setSubtitle(getString(R.string.biometric_demo_subtitle))
        .setDescription(getString(R.string.biometric_demo_description))
        .apply {
            setNegativeButtonText(getString(R.string.biometric_demo_cancel))
            setDeviceCredentialFallbackAllowed(allowCredentials)
        }
        .also {
            if (crypto) {
                val cryptoTest = cryptoTests.getOrPut(biometricAuthRequest) {
                    CryptoTest(testString.toByteArray(Charset.forName("UTF-8")))
                }
                it.setCryptographyPurpose(
                    BiometricCryptographyPurpose(
                        cryptoTest.type,
                        cryptoTest.vector
                    )
                )
            }
            if (silentAuth) {
                it.enableSilentAuth()
            }
        }
        .build()


    val callback = object : BiometricPromptCompat.AuthenticationCallback() {
        override fun onSucceeded(confirmed: Set<AuthenticationResult>) {
            super.onSucceeded(confirmed)
            var cryptoText = this@startBiometric.getString(R.string.crypto_disabled)
            if (cryptoTests[biometricAuthRequest]?.type == BiometricCryptographyPurpose.ENCRYPT) {
                CryptographyManager.encryptData(
                    cryptoTests[biometricAuthRequest]?.byteArray,
                    confirmed
                )?.let {
                    cryptoText = this@startBiometric.getString(
                        R.string.crypto_encryption_result,
                        String(
                            it.data,
                            Charset.forName("UTF-8")
                        )
                    )
                    cryptoTests[biometricAuthRequest] = CryptoTest(
                        it.data,
                        it.initializationVector,
                        BiometricCryptographyPurpose.DECRYPT
                    )
                }

            } else {
                CryptographyManager.decryptData(
                    cryptoTests[biometricAuthRequest]?.byteArray,
                    confirmed
                )?.let {
                    cryptoText = this@startBiometric.getString(
                        R.string.crypto_decryption_result,
                        String(
                            it.data,
                            Charset.forName("UTF-8")
                        )
                    )
                    cryptoTests[biometricAuthRequest] =
                        CryptoTest(testString.toByteArray(Charset.forName("UTF-8")))
                }
            }

            BiometricLoggerImpl.e("CheckBiometric.onSucceeded() for $confirmed; $cryptoText")
            Toast.makeText(
                AndroidContext.appContext.getFixedContext(),
                this@startBiometric.getString(
                    R.string.biometric_demo_success,
                    confirmed.toString(),
                    cryptoText
                ),
                Toast.LENGTH_LONG
            )
                .show()
        }

        override fun onCanceled(canceled: Set<AuthenticationResult>) {
            BiometricLoggerImpl.e("CheckBiometric.onCanceled() $canceled")
            Toast.makeText(
                AndroidContext.appContext.getFixedContext(),
                this@startBiometric.getString(
                    R.string.biometric_demo_canceled,
                    canceled.toString()
                ),
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onFailed(canceled: Set<AuthenticationResult>) {
            BiometricLoggerImpl.e(
                BiometricAuthException(canceled.toString()),
                "CheckBiometric.onFailed()"
            )
            val reason = canceled.firstOrNull()?.reason
            try {
                when (reason) {
                    AuthenticationFailureReason.NO_HARDWARE -> showAlertDialog(
                        requireActivity(),
                        this@startBiometric.getString(
                            R.string.biometric_demo_no_hardware,
                            "${biometricAuthRequest.api}/${biometricAuthRequest.type}"
                        ),
                    )

                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED -> showAlertDialog(
                        requireActivity(),
                        this@startBiometric.getString(
                            R.string.biometric_demo_not_enrolled,
                            "${biometricAuthRequest.api}/${biometricAuthRequest.type}"
                        ),
                    )

                    AuthenticationFailureReason.LOCKED_OUT -> showAlertDialog(
                        requireActivity(),
                        this@startBiometric.getString(
                            R.string.biometric_demo_temp_lockout,
                            "${biometricAuthRequest.api}/${biometricAuthRequest.type}"
                        ),
                    )

                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE -> showAlertDialog(
                        requireActivity(),
                        this@startBiometric.getString(
                            R.string.biometric_demo_perm_lockout,
                            "${biometricAuthRequest.api}/${biometricAuthRequest.type}"
                        ),
                    )

                    else -> showAlertDialog(
                        requireActivity(),
                        this@startBiometric.getString(
                            R.string.biometric_demo_failure,
                            canceled.toString()
                        )
                    )
                }
            } catch (ignore: Throwable) {
                Toast.makeText(
                    AndroidContext.appContext.getFixedContext(),
                    this@startBiometric.getString(
                        R.string.biometric_demo_failure,
                        canceled.toString()
                    ),
                    Toast.LENGTH_LONG
                )
                    .show()
            }
        }

        override fun onUIOpened() {
            BiometricLoggerImpl.e("CheckBiometric.onUIOpened()")
            Toast.makeText(
                AndroidContext.appContext.getFixedContext(),
                this@startBiometric.getString(R.string.biometric_demo_ui_opened),
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onUIClosed() {
            BiometricLoggerImpl.e("CheckBiometric.onUIClosed()")
            Toast.makeText(
                AndroidContext.appContext.getFixedContext(),
                this@startBiometric.getString(R.string.biometric_demo_ui_closed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    if (isRegister) {
        biometricPromptCompat.setupBiometric(callback)


        Toast.makeText(
            AndroidContext.appContext.getFixedContext(),
            this@startBiometric.getString(
                R.string.biometric_demo_start_setup,
                biometricAuthRequest.api.toString(),
                biometricAuthRequest.type.toString()
            ),
            Toast.LENGTH_SHORT
        ).show()
    } else {
        biometricPromptCompat.authenticate(callback)


        Toast.makeText(
            AndroidContext.appContext.getFixedContext(),
            this@startBiometric.getString(
                R.string.biometric_demo_start_authenticate,
                biometricAuthRequest.api.toString(),
                biometricAuthRequest.type.toString()
            ),
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun biometricUnavailableMessage(
    authSnapshot: BiometricAuthSnapshot,
    forEnroll: Boolean,
    context: Context
): String {
    val biometricAuthRequest = authSnapshot.request
    val state = authSnapshot.state
    val route = "${biometricAuthRequest.api}/${biometricAuthRequest.type}"
    return when {
        !state.hardwareDetected ->
            context.getString(R.string.biometric_demo_no_hardware, route)

        !forEnroll && !state.enrolled ->
            context.getString(R.string.biometric_demo_not_enrolled, route)

        state.lockedOut ->
            context.getString(R.string.biometric_demo_temp_lockout, route)

        state.permanentlyLocked ->
            context.getString(R.string.biometric_demo_perm_lockout, route)

        else -> context.getString(R.string.biometric_demo_unexpected_error_state, route)
    }
}

private fun showAlertDialog(context: Context, msg: String) {
    AlertDialog.Builder(context, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog_Alert)
        .setTitle(context.getString(R.string.biometric_demo_error_title))
        .setMessage(msg)
        .setNegativeButton(android.R.string.cancel, null).show()
}

data class CryptoTest(
    val byteArray: ByteArray,
    val vector: ByteArray? = null,
    val type: Int = BiometricCryptographyPurpose.ENCRYPT,
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
