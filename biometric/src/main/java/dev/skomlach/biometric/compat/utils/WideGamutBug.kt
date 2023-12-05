/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import android.content.pm.ActivityInfo
import android.os.Build
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

object WideGamutBug {
    private val error: String = "WARNING!!!\n" +
            "Do not use android:colorMode=\"wideColorGamut\"  for Activity - it leads to unexpected bugs on OnePlus devices:\n" +
            "https://www.reddit.com/r/redditsync/comments/9ta7df/updated_my_oneplus_6_recently_opening_images/\n" +
            "On OnePlus 6T stop working Fingerprint Sensor O_o"

    fun unsupportedColorMode(activity: FragmentActivity?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (activity?.window?.colorMode == ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && activity?.window?.isWideColorGamut == true)
            ) {
                BiometricLoggerImpl.e(error)
                return true
            }
        }
        return false
    }
}