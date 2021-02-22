package dev.skomlach.biometric.compat.utils.hardware

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.BiometricAuthentication

@RestrictTo(RestrictTo.Scope.LIBRARY)
class LegacyHardware(authRequest: BiometricAuthRequest) : AbstractHardware(authRequest) {
    val availableBiometricsCount: Int
        get() {
            if (biometricAuthRequest.type === BiometricType.BIOMETRIC_ANY) {
                var count = 0
                for (type in BiometricAuthentication.getAvailableBiometrics()) {
                    val biometricModule = BiometricAuthentication.getAvailableBiometricModule(type)
                    if (biometricModule != null && biometricModule.isHardwarePresent && biometricModule.hasEnrolled()) {
                        count++
                    }
                }
                return count
            }
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return if (biometricModule != null) 1 else 0
        }
    override val isHardwareAvailable: Boolean
        get() {
            if (biometricAuthRequest.type === BiometricType.BIOMETRIC_ANY) return BiometricAuthentication.isHardwareDetected()
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return biometricModule != null && biometricModule.isHardwarePresent
        }
    override val isBiometricEnrolled: Boolean
        get() {
            if (biometricAuthRequest.type === BiometricType.BIOMETRIC_ANY) return BiometricAuthentication.hasEnrolled()
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return biometricModule != null && biometricModule.hasEnrolled()
        }
    override val isLockedOut: Boolean
        get() {
            if (biometricAuthRequest.type === BiometricType.BIOMETRIC_ANY) return BiometricAuthentication.isLockOut()
            val biometricModule = BiometricAuthentication.getAvailableBiometricModule(
                biometricAuthRequest.type
            )
            return biometricModule != null && biometricModule.isLockOut
        }
}