package dev.skomlach.biometric.compat.custom

import androidx.fragment.app.Fragment

/** Optional UI capability; existing provider and prompt-host JVM signatures are unchanged. */
interface SoftwareBiometricFragmentHost {
    fun mountContent(ownerTag: String, fragment: Fragment): Boolean
    fun unmountContent(ownerTag: String)
}
