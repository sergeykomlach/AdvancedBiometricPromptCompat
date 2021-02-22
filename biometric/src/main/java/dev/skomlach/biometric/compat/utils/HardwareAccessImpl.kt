package dev.skomlach.biometric.compat.utils

import androidx.annotation.RestrictTo
import androidx.core.os.BuildCompat
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.hardware.Android28Hardware
import dev.skomlach.biometric.compat.utils.hardware.Android29Hardware
import dev.skomlach.biometric.compat.utils.hardware.HardwareInfo
import dev.skomlach.biometric.compat.utils.hardware.LegacyHardware

@RestrictTo(RestrictTo.Scope.LIBRARY)
class HardwareAccessImpl private constructor(biometricAuthRequest: BiometricAuthRequest) {
    companion object {
        @JvmStatic
        fun getInstance(api: BiometricAuthRequest): HardwareAccessImpl {
            return HardwareAccessImpl(api)
        }
    }

    private var hardwareInfo: HardwareInfo? = null
    private fun isHardwareReady(info: HardwareInfo?): Boolean {
        return info?.isHardwareAvailable == true && info.isBiometricEnrolled
    }

    val isNewBiometricApi: Boolean
        get() = hardwareInfo !is LegacyHardware
    val isHardwareAvailable: Boolean
        get() = hardwareInfo?.isHardwareAvailable ?: false
    val isBiometricEnrolled: Boolean
        get() = hardwareInfo?.isBiometricEnrolled ?: false
    val isLockedOut: Boolean
        get() = hardwareInfo?.isLockedOut ?: false

    init {
        if (biometricAuthRequest.api === BiometricApi.LEGACY_API) {
            hardwareInfo = LegacyHardware(biometricAuthRequest) //Android 4+
        } else if (biometricAuthRequest.api === BiometricApi.BIOMETRIC_API) {
            if (BuildCompat.isAtLeastQ()) {
                hardwareInfo =
                    Android29Hardware(biometricAuthRequest) //new BiometricPrompt API; Has BiometricManager to deal with hasHardware/isEnrolled/isLockedOut
            } else if (BuildCompat.isAtLeastP()) {
                hardwareInfo =
                    Android28Hardware(biometricAuthRequest) //new BiometricPrompt API; very raw on Android 9, so hacks and workarounds used
            }
        } else { //AUTO
            hardwareInfo = when {
                BuildCompat.isAtLeastQ() -> {
                    Android29Hardware(biometricAuthRequest) //new BiometricPrompt API; Has BiometricManager to deal with hasHardware/isEnrolled/isLockedOut
                }
                BuildCompat.isAtLeastP() -> {
                    Android28Hardware(biometricAuthRequest) //new BiometricPrompt API; very raw on Android 9, so hacks and workarounds used
                }
                else -> {
                    LegacyHardware(biometricAuthRequest) //Android 4+
                }
            }
            if (hardwareInfo !is LegacyHardware) {
                val info = LegacyHardware(biometricAuthRequest)
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY && info.availableBiometricsCount > 1) {
                    hardwareInfo = info
                } else if (!isHardwareReady(hardwareInfo) && isHardwareReady(info)) {
                    hardwareInfo = info
                }
            }
        }
    }

    fun lockout() {
        if (isNewBiometricApi) {
            (hardwareInfo as Android28Hardware).lockout()
        }
    }
}