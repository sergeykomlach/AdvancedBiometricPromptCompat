/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.utils.LockType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider.getCryptoPreferences
import java.lang.reflect.Modifier
import java.nio.charset.Charset
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator

//Set of tools that tried to behave like BiometricManager API from Android 10
@TargetApi(Build.VERSION_CODES.P)

open class Android28Hardware(authRequest: BiometricAuthRequest) : AbstractHardware(authRequest) {

    companion object {
        private const val TS_PREF = "timestamp_"
        private val timeout = TimeUnit.SECONDS.toMillis(31)
    }

    private val preferences: SharedPreferences = getCryptoPreferences("BiometricModules")
    override val isHardwareAvailable: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyHardwareAvailable else isHardwareAvailableForType
    override val isBiometricEnrolled: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyBiometricEnrolled else isBiometricEnrolledForType
    override val isLockedOut: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyLockedOut else isLockedOutForType
    override val isBiometricEnrollChanged: Boolean
        get() {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY)
                try {
                    val name = "BiometricKey"
                    val keyStore = KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)
                    val key = if (keyStore.containsAlias(name))
                        keyStore.getKey(name, null)
                    else {
                        val keyGenerator =
                            KeyGenerator.getInstance(
                                KeyProperties.KEY_ALGORITHM_AES,
                                keyStore.provider
                            )
                        val builder = KeyGenParameterSpec.Builder(
                            name,
                            KeyProperties.PURPOSE_ENCRYPT or
                                    KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                            .setUserAuthenticationRequired(true)
                            .setInvalidatedByBiometricEnrollment(true)
                        keyGenerator.init(builder.build()) //exception should be thrown here on "normal" devices if no enrolled biometric
                        keyGenerator.generateKey();
                    }
                    //Devices with a bug in Keystore
                    //https://issuetracker.google.com/issues/37127115
                    //https://stackoverflow.com/questions/42359337/android-key-invalidation-when-fingerprints-removed

                    val sym = Cipher.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES + "/"
                                + KeyProperties.BLOCK_MODE_CBC + "/"
                                + KeyProperties.ENCRYPTION_PADDING_PKCS7
                    );
                    sym.init(Cipher.ENCRYPT_MODE, key);
                    sym.doFinal(name.toByteArray(Charset.forName("UTF-8")));
                } catch (throwable: Throwable) {
                    var e = throwable
                    if (e is IllegalBlockSizeException) {
                        return true
                    }
                    var cause: Throwable? = e.cause
                    while (cause != null && cause != e) {
                        if (cause is IllegalStateException || cause.javaClass.name == "android.security.KeyStoreException") {
                            return true
                        }
                        e = cause
                        cause = e.cause
                    }
                }
            return false
        }

    override
    fun updateBiometricEnrollChanged() {
        if (isBiometricEnrollChanged) {
            try {
                val name = "BiometricKey"
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias(name))
                    keyStore.deleteEntry(name)
            } catch (throwable: Throwable) {
            }
        }
    }

    private fun biometricFeatures(): ArrayList<String> {
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

    open val isAnyHardwareAvailable: Boolean
        get() {
            if (BiometricAuthentication.isHardwareDetected) return true
            val list = biometricFeatures()
            val packageManager = appContext.packageManager
            for (f in list) {
                if (packageManager != null && packageManager.hasSystemFeature(f)) {
                    return true
                }
            }
            return false
        }

    open val isAnyBiometricEnrolled: Boolean
        get() {
            val keyguardManager =
                appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
            if (keyguardManager?.isDeviceSecure == true) {
                if (BiometricAuthentication.hasEnrolled()
                    || LockType.isBiometricWeakEnabled(appContext)
                ) {
                    return true
                }

                //Fallback for some devices where previews methods failed

                //https://stackoverflow.com/a/53973970
                var keyStore: KeyStore? = null
                val name = UUID.randomUUID().toString()
                try {
                    keyStore = KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)
                    val keyGenerator =
                        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStore.provider)
                    val builder = KeyGenParameterSpec.Builder(
                        name,
                        KeyProperties.PURPOSE_ENCRYPT or
                                KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true)
                        .setInvalidatedByBiometricEnrollment(true)
                    keyGenerator.init(builder.build()) //exception should be thrown here on "normal" devices if no enrolled biometric

//                keyGenerator.generateKey();
//
//                //Devices with a bug in Keystore
//                //https://issuetracker.google.com/issues/37127115
//                //https://stackoverflow.com/questions/42359337/android-key-invalidation-when-fingerprints-removed
//                try {
//                    SecretKey symKey = (SecretKey) keyStore.getKey(name, null);
//                    Cipher sym = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
//                            + KeyProperties.BLOCK_MODE_CBC + "/"
//                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
//                    sym.init(Cipher.ENCRYPT_MODE, symKey);
//                    sym.doFinal(name.getBytes("UTF-8"));
//                } catch (Throwable e) {
//                    //at least one biometric enrolled
//                    return BiometricAuthentication.hasEnrolled();
//                }
                } catch (throwable: Throwable) {
                    var e = throwable
                    if (e is InvalidAlgorithmParameterException) {
                        return false
                    }
                    var cause: Throwable? = e.cause
                    while (cause != null && cause != e) {
                        if (cause is IllegalStateException) {
                            return false
                        }
                        e = cause
                        cause = e.cause
                    }
                } finally {
                    try {
                        keyStore?.deleteEntry(name)
                    } catch (ignore: Throwable) {
                    }
                }
                return true
            }
            return false
        }

    fun lockout() {
        if (!isLockedOut) {
            preferences.edit()
                .putLong(TS_PREF + "-" + biometricAuthRequest.type.name, System.currentTimeMillis())
                .apply()
        }
    }

    private val isAnyLockedOut: Boolean
        get() {
            try {
                for (key in preferences.all.keys) {
                    val ts = preferences.getLong(key, 0)
                    if (ts > 0) {
                        return if (System.currentTimeMillis() - ts > timeout) {
                            preferences.edit().putLong(key, 0).apply()
                            false
                        } else {
                            true
                        }
                    }
                }
            } catch (ignore: Throwable) {
            }
            return false
        }//legacy

    //OK to check in this way
    private val isHardwareAvailableForType: Boolean
        get() {
            //legacy
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_FINGERPRINT) {
                val biometricModule =
                    BiometricAuthentication.getAvailableBiometricModule(BiometricType.BIOMETRIC_FINGERPRINT)
                if (biometricModule != null && biometricModule.isHardwarePresent) return true
            }
            val list = biometricFeatures()
            val packageManager = appContext.packageManager
            for (f in list) {
                if (packageManager.hasSystemFeature(f)) {
                    if ((f.endsWith(".face") || f.contains(".face.")) &&
                        biometricAuthRequest.type == BiometricType.BIOMETRIC_FACE
                    ) return true
                    if ((f.endsWith(".iris") || f.contains(".iris.")) &&
                        biometricAuthRequest.type == BiometricType.BIOMETRIC_IRIS
                    ) return true
                    if ((f.endsWith(".fingerprint") || f.contains(".fingerprint.")) &&
                        biometricAuthRequest.type == BiometricType.BIOMETRIC_FINGERPRINT
                    ) return true

                    if ((f.endsWith(".palm") || f.contains(".palm.")) &&
                        biometricAuthRequest.type == BiometricType.BIOMETRIC_PALMPRINT
                    ) return true
                    if ((f.endsWith(".voice") || f.contains(".voice.")) &&
                        biometricAuthRequest.type == BiometricType.BIOMETRIC_VOICE
                    ) return true
                    if ((f.endsWith(".heartrate") || f.contains(".heartrate.")) &&
                        biometricAuthRequest.type == BiometricType.BIOMETRIC_HEARTRATE
                    ) return true
                }
            }
            return false
        }

    //More or less ok this one
    private val isLockedOutForType: Boolean
        get() {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_FINGERPRINT) {
                val biometricModule =
                    BiometricAuthentication.getAvailableBiometricModule(BiometricType.BIOMETRIC_FINGERPRINT)
                if (biometricModule != null && biometricModule.isLockOut) return true
            }
            val ts = preferences.getLong(TS_PREF + "-" + biometricAuthRequest.type.name, 0)
            return if (ts > 0) {
                if (System.currentTimeMillis() - ts > timeout) {
                    preferences.edit().putLong(TS_PREF + "-" + biometricAuthRequest.type.name, 0)
                        .apply()
                    false
                } else {
                    true
                }
            } else {
                false
            }
        }

    //This code can produce false-positive results in some conditions
    //https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat/issues/105#issuecomment-834438785
    private val isBiometricEnrolledForType: Boolean
        get() {
            val biometricModule =
                BiometricAuthentication.getAvailableBiometricModule(BiometricType.BIOMETRIC_FINGERPRINT)
            val fingersEnrolled = biometricModule != null && biometricModule.hasEnrolled()
            return if (biometricAuthRequest.type == BiometricType.BIOMETRIC_FINGERPRINT) {
                fingersEnrolled
            } else {
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_FACE &&
                    LockType.isBiometricEnabledInSettings(appContext, "face")
                ) return true
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_IRIS &&
                    LockType.isBiometricEnabledInSettings(appContext, "iris")
                ) return true
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_PALMPRINT &&
                    LockType.isBiometricEnabledInSettings(appContext, "palm")
                ) return true
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_VOICE &&
                    LockType.isBiometricEnabledInSettings(appContext, "voice")
                ) return true
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_HEARTRATE &&
                    LockType.isBiometricEnabledInSettings(appContext, "heartrate")
                ) return true

                return !fingersEnrolled && isHardwareAvailableForType
                        && isAnyBiometricEnrolled
            }
        }
}