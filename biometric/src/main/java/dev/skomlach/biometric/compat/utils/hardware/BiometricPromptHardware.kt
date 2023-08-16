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
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import java.lang.reflect.Modifier
import java.util.*

@TargetApi(Build.VERSION_CODES.P)

open class BiometricPromptHardware(authRequest: BiometricAuthRequest) :
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
            val canAuthenticate = canAuthenticate
            return if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS || canAuthenticate == BiometricManager.BIOMETRIC_STATUS_UNKNOWN) {
                true
            } else {
                canAuthenticate != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE && canAuthenticate != BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
            }
        }
    private val isAnyBiometricEnrolled: Boolean
        get() {
            val canAuthenticate = canAuthenticate
            return if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS || canAuthenticate == BiometricManager.BIOMETRIC_STATUS_UNKNOWN) {
                true
            } else {
                canAuthenticate != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED && canAuthenticate != BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
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

    fun lockout() {
        if (!isLockedOut) {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                for (type in BiometricType.values()) {
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
            for (type in BiometricType.values()) {
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
                    ) return true
                    if ((f.endsWith(".iris") || f.contains(".iris.")) &&
                        type == BiometricType.BIOMETRIC_IRIS
                    ) return true
                    if ((f.endsWith(".palm") || f.contains(".palm.")) &&
                        type == BiometricType.BIOMETRIC_PALMPRINT
                    ) return true
                    if ((f.endsWith(".voice") || f.contains(".voice.")) &&
                        type == BiometricType.BIOMETRIC_VOICE
                    ) return true
                    if ((f.endsWith(".heartrate") || f.contains(".heartrate.")) &&
                        type == BiometricType.BIOMETRIC_HEARTRATE
                    ) return true
                }
            }

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

            return probablyFingerprintLabel != probablyOtherLabel
        }
        return false
    }
}