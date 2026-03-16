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

import androidx.core.content.edit
import dev.skomlach.common.device.DeviceSpecManager.getSensors
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import java.util.Collections
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


object DeviceInfoManager {
    val PREF_NAME = "BiometricCompat_DeviceInfo"
    const val OUTDATE_TIME_DAYS = 30L
    const val OUTDATE_TIME_CACHE_DAYS = 7L
    const val OUTDATE_TIME_DAYS_FILES = OUTDATE_TIME_DAYS - OUTDATE_TIME_CACHE_DAYS

    private var running = AtomicBoolean(false)

    private val listeners =
        Collections.synchronizedList<OnDeviceInfoListener?>(mutableListOf<OnDeviceInfoListener?>())

//    init {
//        getPreferences(PREF_NAME).apply {
//            edit().clear().commit()
//        }
//    }

    fun getDeviceInfo(listener: OnDeviceInfoListener?) {
        synchronized(listeners) {
            listeners.add(listener)
            if (running.getAndSet(true)) {
                return
            }
        }
        running.set(true)
        listeners.add(listener)
        checkCache()
        cachedDeviceInfo?.let { deviceInfo ->
            ExecutorHelper.post {
                synchronized(listeners) {
                    val list = listeners.toMutableList()
                    listeners.clear()
                    list.forEach {
                        it?.onReady(deviceInfo)
                    }
                    running.set(false)
                }

            }
            return
        }
        ExecutorHelper.startOnBackground {
            try {
                val emulatorKind: EmulatorKind? = runCatching { detectEmulatorKind }.getOrNull()
                val deviceInfo = getCurrentDeviceInfo(emulatorKind).also {
                    setCachedDeviceInfo(it)
                }
                notifyListeners(deviceInfo)
            } catch (e: Exception) {

                running.set(false)
            }
        }
    }

    private fun notifyListeners(deviceInfo: DeviceInfo) {
        ExecutorHelper.post {
            val listToNotify = synchronized(listeners) {
                val copy = listeners.toList()
                listeners.clear()
                running.set(false)
                copy
            }

            listToNotify.forEach { l -> l.onReady(deviceInfo) }
        }
    }

    private fun checkCache() {
        val sharedPreferences = getPreferences(PREF_NAME)
        val checked = sharedPreferences.getLong("timestampCache", 0)
        if (Date().time - checked <= TimeUnit.DAYS.toMillis(OUTDATE_TIME_CACHE_DAYS)) {
            return
        }
        ExecutorHelper.startOnBackground {
            DataProviders.checkCache("https://github.com/androidtrackers/certified-android-devices/blob/master/by_model.json?raw=true")
        }
        ExecutorHelper.startOnBackground {
            DataProviders.checkCache("https://github.com/sergeykomlach/AdvancedBiometricPromptCompat/blob/main/common/src/main/assets/devices/specifications.json?raw=true")
        }
        ExecutorHelper.startOnBackground {
            DataProviders.checkCache("https://github.com/nowrom/devices/blob/main/devices.json?raw=true")
        }
        sharedPreferences.edit {
            putLong("timestampCache", Date().time)
        }
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
                        DeviceInfo(model, fixModelAsAnsi(model), sensors, checked, emulatorKind)
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
            fixModelAsAnsi(deviceName),
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