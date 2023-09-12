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

package dev.skomlach.biometric.compat.utils

import android.os.Build
import androidx.biometric.R
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.device.DeviceInfoManager
import dev.skomlach.common.misc.Utils
import java.lang.reflect.Modifier

object DevicesWithKnownBugs {
    private val appContext = AndroidContext.appContext

    //Users reports that on LG devices have a bug with wrong/missing BiometricUI
    //After digging I found that it seems like system BiometricPrompt simply missing on this device
    //https://lg-firmwares.com/models-list/
    private val lgWithMissedBiometricUI = arrayOf(
        //G8 ThinQ
        "G820",
        //G8S ThinQ
        "G810",
        //G8X ThinQ
        "G850",
        //Velvet/Velvet 5G
        "G900",
        //Velvet 4G Dual Sim
        "G910",
    )

    private val isOnePlusWithBiometricBug: Boolean
        get() = (isOnePlus && !Utils.isAtLeastS) && hasUnderDisplayFingerprint

    val isHideDialogInstantly: Boolean
        get() {
            val modelPrefixes =
                appContext.resources.getStringArray(R.array.hide_fingerprint_instantly_prefixes)
            for (modelPrefix in modelPrefixes) {
                if (Build.MODEL.startsWith(modelPrefix)) {
                    return true
                }
            }
            return (systemDealWithBiometricPrompt || (isOnePlus && Utils.isAtLeastS)) && hasUnderDisplayFingerprint
        }

    val systemDealWithBiometricPrompt: Boolean
        get() = (isSamsung || Utils.isAtLeastT)
    private val isSamsung: Boolean
        get() {
            return checkForVendor("Samsung", ignoreCase = true)
        }
    val isOnePlus: Boolean
        get() {
            return checkForVendor("OnePlus", ignoreCase = true)
        }
    val isMissedBiometricUI: Boolean
        get() = (checkForVendor("LG", ignoreCase = false) &&
                listOf(*lgWithMissedBiometricUI).any { knownModel ->
                    Build.MODEL.contains(
                        knownModel,
                        ignoreCase = true
                    )
                }) || isOnePlusWithBiometricBug || !CheckBiometricUI.hasExists(appContext)

    val hasUnderDisplayFingerprint: Boolean
        get() = DeviceInfoManager.hasUnderDisplayFingerprint(BiometricPromptCompat.deviceInfo)

    private fun checkForVendor(vendor: String, ignoreCase: Boolean): Boolean {
        val allFields = Build::class.java.fields
        for (f in allFields) try {
            if (!Modifier.isPrivate(f.modifiers) && f.type == String::class.java) {
                val value = f[null] as String
                if (value.contains(vendor, ignoreCase = ignoreCase)) return true
            }
        } catch (ignore: Throwable) {

        }
        return false
    }

    val isChromeBook: Boolean
        get() {
            //https://developer.chrome.com/apps/getstarted_arc
            //https://github.com/google/talkback/blob/master/src/main/java/com/google/android/marvin/talkback/TalkBackService.java#L1779-L1781
            //https://stackoverflow.com/a/39843396
            return (checkForVendor(
                "Chromium",
                ignoreCase = true
            ) || Build.DEVICE != null && Build.DEVICE.matches(Regex(".+_cheets"))
                    || AndroidContext.appContext.packageManager.hasSystemFeature("org.chromium.arc.device_management"))
        }
}