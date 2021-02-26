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
import dev.skomlach.biometric.compat.BiometricPromptCompat.Companion.deviceInfo
import dev.skomlach.biometric.compat.utils.device.DeviceInfoManager
import dev.skomlach.common.contextprovider.AndroidContext.appContext

object DevicesWithKnownBugs {
    //https://forums.oneplus.com/threads/oneplus-7-pro-fingerprint-biometricprompt-does-not-show.1035821/
    private val onePlusModelsWithoutBiometricBug = arrayOf(
        "A0001",  // OnePlus One
        "ONE A2001", "ONE A2003", "ONE A2005",  // OnePlus 2
        "ONE E1001", "ONE E1003", "ONE E1005",  // OnePlus X
        "ONEPLUS A3000", "ONEPLUS SM-A3000", "ONEPLUS A3003",  // OnePlus 3
        "ONEPLUS A3010",  // OnePlus 3T
        "ONEPLUS A5000",  // OnePlus 5
        "ONEPLUS A5010",  // OnePlus 5T
        "ONEPLUS A6000", "ONEPLUS A6003" // OnePlus 6
    )

    //Users reports that on LG G8 displayed "Biometric dialog without fingerprint icon";
    //After digging I found that it seems like BiometricPrompt simply missing on this device,
    //so the fallback screen for In-Screen scanners are displayed, where we expecte that
    //Fingerpint icon will be shown by the System
    //https://lg-firmwares.com/models-list/
    private val lgWithMissedBiometricUI = arrayOf( //G8 ThinQ
        "G820N", "G820QM", "G820QM5", "G820TM", "G820UM",  //G8S ThinQ
        "G810EA", "G810EAW", "G810RA",  //G8X ThinQ
        "G850EM", "G850EMW", "G850QM", "G850UM"
    )
    @JvmStatic
    val isOnePlusWithBiometricBug: Boolean
        get() = Build.BRAND.equals("OnePlus", ignoreCase = true) &&
                !listOf(*onePlusModelsWithoutBiometricBug).contains(Build.MODEL)
    @JvmStatic
    val isHideDialogInstantly: Boolean
        get() {
            val modelPrefixes =
                appContext.resources.getStringArray(R.array.hide_fingerprint_instantly_prefixes)
            for (modelPrefix in modelPrefixes) {
                if (Build.MODEL.startsWith(modelPrefix)) {
                    return true
                }
            }
            return false
        }
    @JvmStatic
    val isLGWithMissedBiometricUI: Boolean
        get() = Build.BRAND.equals("LG", ignoreCase = true) &&
                listOf(*lgWithMissedBiometricUI).contains(Build.MODEL)
    @JvmStatic
    val isShowInScreenDialogInstantly: Boolean
        get() = DeviceInfoManager.INSTANCE.hasUnderDisplayFingerprint(deviceInfo)
}