package dev.skomlach.biometric.compat

import android.os.Bundle
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSecurityProfile

/** Canonical operation context is written after both caller and prepared-provider extras. */
internal object AuthenticationTrustContext {
    private const val FLOOR = "biometric.internal.trust.minimum"
    private const val BEHAVIOR = "biometric.internal.trust.behavior_authorizer"
    private const val SUPPLEMENTAL = "biometric.internal.trust.supplemental"
    private const val ENROLLMENT = "biometric.internal.trust.enrollment"

    fun snapshot(source: Bundle?): Bundle? = source?.let {
        Bundle(it).apply {
            val keys = setOf(FLOOR, BEHAVIOR, SUPPLEMENTAL, ENROLLMENT, BundleBuilder.ENROLL)
            keySet().toList().filterNot(keys::contains).forEach(::remove)
        }
    }

    fun write(bundle: Bundle, policy: BiometricTrustPolicy, supplemental: Boolean, enrollment: Boolean) {
        bundle.putString(FLOOR, policy.minimumTrust.name)
        bundle.putBoolean(BEHAVIOR, policy.allowBehaviorOnlySuccess)
        bundle.putBoolean(SUPPLEMENTAL, supplemental)
        bundle.putBoolean(ENROLLMENT, enrollment)
        bundle.putBoolean(BundleBuilder.ENROLL, enrollment)
        bundle.putBoolean("is_enrollment", enrollment)
    }

    fun permits(bundle: Bundle?, profile: SoftwareBiometricSecurityProfile, trustedCapture: Boolean): Boolean {
        if (bundle == null || listOf(FLOOR, BEHAVIOR, SUPPLEMENTAL, ENROLLMENT).none(bundle::containsKey)) {
            // Preserve the lower-level LegacyBiometric API's existing contract. Builder flows
            // always carry a canonical context and cannot use this compatibility entry.
            return true
        }
        return try {
            if (listOf(FLOOR, BEHAVIOR, SUPPLEMENTAL, ENROLLMENT).any { !bundle.containsKey(it) }) return false
            val floor = bundle.get(FLOOR) as? String ?: return false
            val behavior = bundle.get(BEHAVIOR) as? Boolean ?: return false
            val supplemental = bundle.get(SUPPLEMENTAL) as? Boolean ?: return false
            val enrollment = bundle.get(ENROLLMENT) as? Boolean ?: return false
            if (bundle.getBoolean(BundleBuilder.ENROLL, false) != enrollment) return false
            if (enrollment) return true
            BiometricTrustPolicy(BiometricTrustLevel.valueOf(floor), behavior).permitsExecution(
                profile.biometricType, false, profile, trustedCapture, supplemental)
        } catch (_: IllegalArgumentException) { false }
    }
}
