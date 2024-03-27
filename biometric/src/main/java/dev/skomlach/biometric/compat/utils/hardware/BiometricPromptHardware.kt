/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.utils.hardware

import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import java.lang.reflect.Modifier
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@TargetApi(Build.VERSION_CODES.P)

class BiometricPromptHardware(authRequest: BiometricAuthRequest) :
    AbstractHardware(authRequest) {
    private val appContext = AndroidContext.appContext
    private val biometricFeatures: ArrayList<String>
        get() {
            val list = ArrayList<String>()
            try {
                val fields = PackageManager::class.java.fields
                for (f in fields) {
                    if (Modifier.isStatic(f.modifiers) && f.type == String::class.java) {
                        (f[null] as String?)?.let { name ->

                            val isAOSP = name.contains(".hardware.") && !name.contains(".sensor.")
                            val isOEM = name.startsWith("com.") && !name.contains(".sensor.")
                            if ((isAOSP || isOEM) && (
                                        name.endsWith(".fingerprint")
                                                || name.endsWith(".face")
                                                || name.endsWith(".iris")
                                                || name.endsWith(".biometric")
                                                || name.endsWith(".palm")
                                                || name.endsWith(".voice")
                                                || name.endsWith(".heartrate")
                                                || name.contains(".fingerprint.")
                                                || name.contains(".face.")
                                                || name.contains(".iris.")
                                                || name.contains(".biometric.")
                                                || name.contains(".palm.")
                                                || name.contains(".voice.")
                                                || name.contains(".heartrate.")
                                        )
                            ) {
                                list.add(name)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                e(e)
            }
            list.sort()
            return list
        }
    private val canAuthenticate: Int
        get() {
            var code = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
            try {
                val biometricManager: BiometricManager = BiometricManager.from(appContext)
                val authenticators = arrayOf(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                            or BiometricManager.Authenticators.BIOMETRIC_STRONG,
                    BiometricManager.Authenticators.BIOMETRIC_WEAK,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
                for (authenticator in authenticators) {
                    code = biometricManager.canAuthenticate(authenticator)
                    if (code == BiometricManager.BIOMETRIC_SUCCESS || code == BiometricManager.BIOMETRIC_STATUS_UNKNOWN) {
                        break
                    }
                }

            } catch (e: Throwable) {
                e(e)
            } finally {
                e("Android28Hardware - canAuthenticate=$code")
            }
            return code
        }
    private val isAnyHardwareAvailable: Boolean
        get() {
            return when (canAuthenticate) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false //really no sensors
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> false //security patch required due to vulnerabilities
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false //Some incompatibility issue
                BiometricManager.BIOMETRIC_SUCCESS -> true //all OK
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> true //some biometric exist, but unable to check properly
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true //sensor Ok, just biometric data missing
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> true //sensor temporary unavailable
                else -> false
            }
        }
    private val isAnyBiometricEnrolled: Boolean
        get() {
            return when (canAuthenticate) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false //really no sensors and enrolled data
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> false //security patch required due to vulnerabilities
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false //Some incompatibility issue
                BiometricManager.BIOMETRIC_SUCCESS -> true //all OK
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> true //some biometric exist, but unable to check properly
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false //no biometric
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> true //sensor temporary unavailable; biometric most probably be exists
                else -> false
            }
        }
    override val isHardwareAvailable: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyHardwareAvailable else isHardwareAvailableForType(
            biometricAuthRequest.type
        )
    override val isBiometricEnrolled: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyBiometricEnrolled else isBiometricEnrolledForType(
            biometricAuthRequest.type
        )
    override val isLockedOut: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyLockedOut else isLockedOutForType(
            biometricAuthRequest.type
        )

    override fun lockout() {
        if (!isLockedOut) {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                for (type in BiometricType.entries) {
                    if (type == BiometricType.BIOMETRIC_ANY)
                        continue
                    if (isHardwareAvailableForType(type) && isBiometricEnrolledForType(type)) {
                        BiometricLockoutFix.lockout(type)
                    }
                }
            } else
                BiometricLockoutFix.lockout(biometricAuthRequest.type)
        }
    }

    private val isAnyLockedOut: Boolean
        get() {
            if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) return true
            for (type in BiometricType.entries) {
                if (BiometricLockoutFix.isLockOut(type))
                    return true
            }
            return false
        }//legacy

    //OK to check in this way
    private fun isHardwareAvailableForType(type: BiometricType): Boolean {
        if (isAnyHardwareAvailable) {
            if (type == BiometricType.BIOMETRIC_FINGERPRINT)
                return BiometricAuthentication.getAvailableBiometricModule(type)?.isHardwarePresent
                    ?: false
            //legacy
            val packageManager = appContext.packageManager
            for (f in biometricFeatures) {
                if (packageManager.hasSystemFeature(f)) {
                    if ((f.endsWith(".face") || f.contains(".face.")) &&
                        type == BiometricType.BIOMETRIC_FACE
                    ) return !biometricIsServiceBased("face")
                    if ((f.endsWith(".iris") || f.contains(".iris.")) &&
                        type == BiometricType.BIOMETRIC_IRIS
                    ) return !biometricIsServiceBased("iris")
                    if ((f.endsWith(".palm") || f.contains(".palm.")) &&
                        type == BiometricType.BIOMETRIC_PALMPRINT
                    ) return !biometricIsServiceBased("palm")
                    if ((f.endsWith(".voice") || f.contains(".voice.")) &&
                        type == BiometricType.BIOMETRIC_VOICE
                    ) return !biometricIsServiceBased("voice")
                    if ((f.endsWith(".heartrate") || f.contains(".heartrate.")) &&
                        type == BiometricType.BIOMETRIC_HEARTRATE
                    ) return !biometricIsServiceBased("heartrate")
                }
            }

        }
        return false
    }

    private fun biometricIsServiceBased(string: String): Boolean {
        //SK: should work properly with system apps
        try {
            val packages = appContext.packageManager.getInstalledPackages(0)
            packages.forEach { pi ->
                val s = pi.packageName.lowercase()
                if (s.contains(string) &&
                    (s.contains(string + "id") ||
                            s.contains("scanner") ||
                            s.contains("recognition") ||
                            s.contains("lock") ||
                            s.contains("auth")
                            )
                ) {
                    e("biometricIsServiceBased ${pi.packageName}")
                    return true
                }
            }
        } catch (e: Throwable) {
            e("BiometricPromptHardware", e)
        }
        return false
    }

    //More or less ok this one
    private fun isLockedOutForType(type: BiometricType): Boolean =
        BiometricLockoutFix.isLockOut(type)

    private fun isBiometricEnrolledForType(type: BiometricType): Boolean {
        if (isAnyBiometricEnrolled) {
            if (type == BiometricType.BIOMETRIC_FINGERPRINT)
                return BiometricAuthentication.getAvailableBiometricModule(type)?.hasEnrolled
                    ?: false

            //https://source.android.com/docs/security/features/biometric#device-specific-strings
            val biometricManager = BiometricManager.from(appContext)

//            Class 3 biometric (BIOMETRIC_STRONG)
//            "Use fingerprint"
//            (Only fingerprint satisfies authenticator requirements)
            val probablyFingerprintLabel =
                biometricManager.getStrings(BiometricManager.Authenticators.BIOMETRIC_STRONG)?.buttonLabel

//            Class 2 biometric (BIOMETRIC_WEAK)
//            "Use face"
//            (Face and fingerprint satisfy requirements; only face is enrolled)
            val probablyOtherLabel =
                biometricManager.getStrings(BiometricManager.Authenticators.BIOMETRIC_WEAK)?.buttonLabel

            BiometricLoggerImpl.d("probablyFingerprintLabel=$probablyFingerprintLabel; probablyOtherLabel=$probablyOtherLabel")
            if (BiometricAuthentication.getAvailableBiometricModule(BiometricType.BIOMETRIC_FINGERPRINT)?.hasEnrolled == true) {
                if (!probablyFingerprintLabel.isNullOrEmpty() && !probablyOtherLabel.isNullOrEmpty()) {
                    return probablyFingerprintLabel != probablyOtherLabel
                }
            } else
                return !probablyFingerprintLabel.isNullOrEmpty() || !probablyOtherLabel.isNullOrEmpty()
        }
        return false
    }

    override val isBiometricEnrollChanged: Boolean
        get() {
            return EnrollCheckHelper.isChanged()
        }

    override
    fun updateBiometricEnrollChanged() {
        try {
            if (EnrollCheckHelper.isChanged()) {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias(EnrollCheckHelper.KEY_NAME))
                    keyStore.deleteEntry(EnrollCheckHelper.KEY_NAME)
            }
        } catch (e: Throwable) {
            e(e)
        }

    }

    private object EnrollCheckHelper {
        const val KEY_NAME = "BiometricEnrollChanged.test"

        private fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }

        private fun getSecretKey(): SecretKey? {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            // Before the keystore can be accessed, it must be loaded.
            keyStore.load(null)
            return if (keyStore.containsAlias(KEY_NAME))
                keyStore.getKey(KEY_NAME, null) as SecretKey
            else null
        }

        private fun getCipher(): Cipher {
            return Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/"
                        + KeyProperties.BLOCK_MODE_CBC + "/"
                        + KeyProperties.ENCRYPTION_PADDING_PKCS7
            )
        }

        fun isChanged(): Boolean {
            try {
                val cipher: Cipher = getCipher()
                var secretKey = getSecretKey()
                if (secretKey == null) {
                    secretKey = generateSecretKey(
                        KeyGenParameterSpec.Builder(
                            KEY_NAME,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                            .setRandomizedEncryptionRequired(false)
                            .apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    setUserPresenceRequired(false)
                                    setUserConfirmationRequired(false)
                                    setIsStrongBoxBacked(false)
                                }
                                setInvalidatedByBiometricEnrollment(true)
                                setUserAuthenticationRequired(true)
                            }
                            .build()
                    )
                }
                try {
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                } catch (e: KeyPermanentlyInvalidatedException) {
                    return true
                } catch (e: InvalidKeyException) {
                    e(e)
                }
            } catch (e: Throwable) {
                e(e)
            }
            return false

        }
    }
}