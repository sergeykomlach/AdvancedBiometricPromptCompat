/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.common.device

import android.os.Looper
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import dev.skomlach.common.device.DeviceSpecManager.getSensors
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import java.lang.ref.WeakReference
import java.util.Date
import java.util.concurrent.TimeUnit

object DeviceInfoManager {
    val PREF_NAME = "BiometricCompat_DeviceInfo-V8"
    const val OUTDATE_TIME_DAYS = 30L
    const val OUTDATE_TIME_DAYS_MINUS_ONE = OUTDATE_TIME_DAYS - 1

    //
//        init {
//        getPreferences(PREF_NAME).apply {
//            edit().clear().commit()
//        }
//    }
    @WorkerThread
    fun getDeviceInfo(listener: OnDeviceInfoListener) {
        if (Looper.getMainLooper().thread === Thread.currentThread()) throw IllegalThreadStateException(
            "Worker thread required"
        )
        val onDeviceInfoListener = WeakReference(listener)
        var deviceInfo = cachedDeviceInfo
        if (deviceInfo != null) {
            onDeviceInfoListener.get()?.onReady(deviceInfo)
            return
        }
        val emulatorKind: EmulatorKind? = runCatching { detectEmulatorKind }.getOrNull()
        deviceInfo = getCurrentDeviceInfo(emulatorKind).also {
            setCachedDeviceInfo(it)
        }
        onDeviceInfoListener.get()?.onReady(deviceInfo)
    }

    //Fix for case when DeviceName contains non-ASCII symbols
    //Example: Motorola Edge 軽 7
    private fun fixModelAsAscii(ua: String): String {
        if (ua.isEmpty()) return ""

        val result = StringBuilder(ua.length)
        var lastWasSpace = false

        for (c in ua) {
            val isValid = c in '\u0020'..'\u007e'

            if (isValid) {
                val isCurrentSpace = (c == ' ')
                if (!(isCurrentSpace && lastWasSpace)) {
                    result.append(c)
                    lastWasSpace = isCurrentSpace
                }
            } else {
                if (!lastWasSpace) {
                    result.append('?')
                    lastWasSpace = true
                }
            }
        }
        return if (result.isEmpty()) "Unknown"
        else
            result.toString().replace("\\s+".toRegex(), " ").trim()
    }

    @Volatile
    private var cachedDeviceInfo: DeviceInfo? = null
        get() {
            if (field == null) {
                val sharedPreferences = getPreferences(PREF_NAME)
                val checked = sharedPreferences.getLong("timestamp", 0)
                if (Date().time - checked <= TimeUnit.DAYS.toMillis(OUTDATE_TIME_DAYS)) {
                    val model =
                        sharedPreferences.getString("model", null)
                            ?: return null
                    val sensors =
                        sharedPreferences.getStringSet("sensors", null)
                            ?: HashSet<String>()
                    val emu = sharedPreferences.getString("emulatorKind", null)
                    val emulatorKind =
                        emu?.let { runCatching { EmulatorKind.valueOf(it) }.getOrNull() }
                    field =
                        DeviceInfo(model, fixModelAsAscii(model), sensors, checked, emulatorKind)
                }
            } else {
                if (Date().time - (field?.timeStamp ?: 0) >= TimeUnit.DAYS.toMillis(
                        OUTDATE_TIME_DAYS
                    )
                ) {
                    field = null
                }
            }
            return field
        }

    private fun getCurrentDeviceInfo(emulatorKind: EmulatorKind?): DeviceInfo {
        cachedDeviceInfo?.let {
            return it
        }
        var ts = System.currentTimeMillis()
        val deviceModel = DeviceModelManager.getDeviceModel()
        LogCat.log("DeviceInfoManager: ts=${System.currentTimeMillis() - ts}; deviceModel=$deviceModel")
        ts = System.currentTimeMillis()
        val deviceSpec = DeviceSpecManager.getDeviceSpecCompat(deviceModel)
        LogCat.log("DeviceInfoManager: ts=${System.currentTimeMillis() - ts}; deviceSpec=$deviceSpec")
        ts = System.currentTimeMillis()
        val deviceName = deviceSpec?.phoneName ?: deviceModel.deviceName

        return DeviceInfo(
            deviceName,
            fixModelAsAscii(deviceName),
            deviceSpec.getSensors(),
            Date().time,
            emulatorKind
        ).also {
            LogCat.log("DeviceInfoManager: ts=${System.currentTimeMillis() - ts}; DeviceInfo=$it")
            cachedDeviceInfo = it
        }
    }

    private fun setCachedDeviceInfo(deviceInfo: DeviceInfo) {
        cachedDeviceInfo = deviceInfo
        try {
            getPreferences(PREF_NAME).edit {
                putStringSet("sensors", deviceInfo.sensors)
                    .putString("model", deviceInfo.model)
                    .putString("emulatorKind", deviceInfo.emulatorKind?.name)
                    .putLong("timestamp", Date().time)
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }


    interface OnDeviceInfoListener {
        fun onReady(deviceInfo: DeviceInfo?)
    }
}