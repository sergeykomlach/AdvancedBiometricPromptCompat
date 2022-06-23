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

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import dev.skomlach.common.contextprovider.AndroidContext

object Vibro {
    private val appContext = AndroidContext.appContext
    private var v: Vibrator? = null
    private var audioManager: AudioManager? = null

    init {
        v = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        audioManager =
            appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
    }

    private fun canVibrate(): Boolean {
        var canVibrate = false
        if (v?.hasVibrator() == true) canVibrate =
            audioManager?.ringerMode != AudioManager.RINGER_MODE_SILENT
        return canVibrate
    }


    fun start() {
        if (canVibrate()) {
            if (Build.VERSION.SDK_INT < 26)
                v?.vibrate(75)
            else try {
                v?.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (ignore: Throwable) {
                v?.vibrate(75)
            }
        }
    }
}