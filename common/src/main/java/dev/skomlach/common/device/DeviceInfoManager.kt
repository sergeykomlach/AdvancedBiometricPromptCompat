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

import android.os.Build
import android.os.Looper
import androidx.annotation.WorkerThread
import com.google.gson.Gson
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.device.DeviceModel.getNames
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.LastUpdatedTs
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import java.util.regex.Pattern

object DeviceInfoManager {
    private val pattern = Pattern.compile("\\((.*?)\\)+")

    fun hasBiometricSensors(deviceInfo: DeviceInfo?): Boolean {
        return hasFingerprint(deviceInfo) || hasFaceID(deviceInfo) || hasIrisScanner(deviceInfo) || hasPalmID(
            deviceInfo
        ) || hasVoiceID(deviceInfo) || hasHeartrateID(deviceInfo)
    }

    fun hasFingerprint(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains("fingerprint")) {
                return true
            }
        }
        return false
    }

    fun hasUnderDisplayFingerprint(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains("fingerprint") && s.contains("under display")) {
                return true
            }
        }
        return false
    }

    fun hasIrisScanner(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                    " unlock"
                ) || s.contains(
                    " auth"
                )
            ) {
                if (s.contains("iris")) {
                    return true
                }
            }
        }
        return false
    }

    fun hasFaceID(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                    " unlock"
                ) || s.contains(
                    " auth"
                )
            ) {
                if (s.contains("face")) {
                    return true
                }
            }
        }
        return false
    }

    fun hasVoiceID(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                    " unlock"
                ) || s.contains(
                    " auth"
                )
            ) {
                if (s.contains("voice")) {
                    return true
                }
            }
        }
        return false
    }

    fun hasPalmID(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                    " unlock"
                ) || s.contains(
                    " auth"
                )
            ) {
                if (s.contains("palm")) {
                    return true
                }
            }
        }
        return false
    }

    fun hasHeartrateID(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                    " unlock"
                ) || s.contains(
                    " auth"
                )
            ) {
                if (s.contains("heartrate")) {
                    return true
                }
            }
        }
        return false
    }

    @WorkerThread
    fun getDeviceInfo(onDeviceInfoListener: OnDeviceInfoListener) {
        if (Looper.getMainLooper().thread === Thread.currentThread()) throw IllegalThreadStateException(
            "Worker thread required"
        )
        var deviceInfo = cachedDeviceInfo
        if (deviceInfo != null) {
            onDeviceInfoListener.onReady(deviceInfo)
            return
        }
        val names = getNames()
        for (m in names) {
            val first = m.first
            val secondArray = splitString(m.second, " ")
            for (i in secondArray.indices - 1) {
                val limit = secondArray.size - i
                if (limit < 2)//Device should have at least brand + model
                    break
                val second = join(secondArray, " ", limit)
                deviceInfo = loadDeviceInfo(first, second, DeviceModel.brand, DeviceModel.device)
                if (!deviceInfo?.sensors.isNullOrEmpty()) {
                    LogCat.log("DeviceInfoManager: " + deviceInfo?.model + " -> " + deviceInfo)
                    setCachedDeviceInfo(deviceInfo ?: continue)
                    onDeviceInfoListener.onReady(deviceInfo)
                    return
                } else {
                    LogCat.log("DeviceInfoManager: no data for $first/$second")
                }
            }
        }
        onDeviceInfoListener.onReady(getAnyDeviceInfo().also {
            setCachedDeviceInfo(it)
        })
    }


    private var cachedDeviceInfo: DeviceInfo? = null
        get() {
            if (field == null) {
                val sharedPreferences = getPreferences("BiometricCompat_DeviceInfo")
                if (sharedPreferences.getBoolean("checked-${LastUpdatedTs.timestamp}", false)) {
                    val model =
                        sharedPreferences.getString("model-${LastUpdatedTs.timestamp}", null)
                            ?: return null
                    val sensors =
                        sharedPreferences.getStringSet("sensors-${LastUpdatedTs.timestamp}", null)
                            ?: HashSet<String>()
                    field = DeviceInfo(model, sensors)
                }
            }
            return field
        }

    fun getAnyDeviceInfo(): DeviceInfo {
        cachedDeviceInfo?.let {
            return it
        }
        try {
            val sharedPreferences = getPreferences("BiometricCompat_DeviceInfo")
            if (sharedPreferences.getBoolean(sharedPreferences.all.keys.firstOrNull {
                    it.startsWith(
                        "checked-"
                    )
                } ?: "", false)) {
                val model =
                    sharedPreferences.getString(sharedPreferences.all.keys.firstOrNull {
                        it.startsWith(
                            "model-"
                        )
                    }
                        ?: Build.MODEL, null) ?: ""
                val sensors =
                    sharedPreferences.getStringSet(sharedPreferences.all.keys.firstOrNull {
                        it.startsWith(
                            "sensors-"
                        )
                    } ?: "", null)
                        ?: HashSet<String>()
                DeviceInfo(model, sensors).also {
                    LogCat.log("DeviceInfoManager: (fallback) " + it.model + " -> " + it)
                    cachedDeviceInfo = it
                }
            }
        } catch (e :Throwable){
            LogCat.logException(e, "DeviceInfoManager")
        }
        val names = getNames()
        return if (names.isNotEmpty())
            DeviceInfo(names.toList()[0].first, HashSet<String>()).also {
                LogCat.log("DeviceInfoManager: (fallback) " + it.model + " -> " + it)
                cachedDeviceInfo = it
            }
        else
            DeviceInfo(Build.MODEL, HashSet<String>()).also {
                LogCat.log("DeviceInfoManager: (fallback) " + it.model + " -> " + it)
                cachedDeviceInfo = it
            }
    }

    private fun setCachedDeviceInfo(deviceInfo: DeviceInfo) {
        cachedDeviceInfo = deviceInfo
        try {
            val sharedPreferences = getPreferences("BiometricCompat_DeviceInfo")
                .edit()
            sharedPreferences.clear().commit()
            sharedPreferences
                .putStringSet("sensors-${LastUpdatedTs.timestamp}", deviceInfo.sensors)
                .putString("model-${LastUpdatedTs.timestamp}", deviceInfo.model)
                .putBoolean("checked-${LastUpdatedTs.timestamp}", true)
                .apply()
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    private fun loadDeviceInfo(
        modelReadableName: String,
        model: String,
        brand : String,
        codeName: String
    ): DeviceInfo? {
        try {
            val devicesList = Gson().fromJson(getJSON(), Array<DeviceSpec>::class.java)

            val info = findDeviceInfo(devicesList, model, brand, codeName)
            if (info != null)
                return info

            return findDeviceInfo(devicesList, modelReadableName, brand, codeName)
        } catch (e: Throwable) {
            LogCat.logException(e, "DeviceInfoManager")
            return null
        } finally {
            System.gc()
        }
    }

    private fun findDeviceInfo(
        devicesList: Array<DeviceSpec>,
        model: String,
        brand : String,
        codeName: String
    ): DeviceInfo? {
        LogCat.log("DeviceInfoManager: findDeviceInfo(${devicesList.size}, $model, $brand, $codeName)")
        var firstFound: DeviceInfo? = null
        devicesList.forEach {
            val m = if (it.name?.startsWith(
                    it.brand ?: "",
                    ignoreCase = true
                ) == true
            ) capitalize(it.name) else capitalize(it.brand) + " " + capitalize(it.name)

            if (it.name.equals(model, ignoreCase = true) || (brand.contains(it.brand.toString(), ignoreCase = true) && it.codename == codeName)) {
                LogCat.log("DeviceInfoManager: $it")
                return DeviceInfo(m, getSensors(it))
            } else if (firstFound == null) {
                if (it.name?.contains(model, ignoreCase = true) == true) {
                    LogCat.log("DeviceInfoManager: $it")
                    firstFound = DeviceInfo(m, getSensors(it))
                } else {
                    val arr = splitString(model, " ")
                    var i = arr.size
                    for (s in arr) {
                        if (i < 2) //Device should have at least brand + model
                            break
                        val shortName = join(arr, " ", i)
                        if (it.name?.contains(shortName, ignoreCase = true) == true) {
                            LogCat.log("DeviceInfoManager: $it")
                            firstFound = DeviceInfo(m, getSensors(it))
                        }
                        i--
                    }

                }
            }
        }

        return firstFound
    }

    private fun getSensors(spec: DeviceSpec): Set<String> {
        val list = mutableSetOf<String>()
        var name: String = spec.specs?.sensors ?: ""
        if (name.isNotEmpty()) {
            val matcher = pattern.matcher(name)
            while (matcher.find()) {
                val s = matcher.group()
                name = name.replace(s, s.replace(",", ";"))
            }
            val split = splitString(name, ",")
            for (s in split) {
                list.add(capitalize(s.trim { it <= ' ' }))
            }
        }
        return list
    }

    //tools
    //https://github.com/nowrom/devices/
    private fun getJSON(): String? {
        try {
            val file = File(AndroidContext.appContext.cacheDir, "devices.json")
            if (file.parentFile?.exists() == false) {
                file.parentFile?.mkdirs()
            }
            file.also {
                if (it.exists()) {
                    return it.readText(
                        Charset.forName("UTF-8")
                    )
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        try {
            val inputStream =
                AndroidContext.appContext.assets.open("devices.json")
            val byteArrayOutputStream = ByteArrayOutputStream()
            NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
            inputStream.close()
            byteArrayOutputStream.close()
            val data = byteArrayOutputStream.toByteArray()
            return String(data, Charset.forName("UTF-8"))
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        return null
    }
    interface OnDeviceInfoListener {
        fun onReady(deviceInfo: DeviceInfo?)
    }

    private fun join(array: Array<String>, delim: String, limit: Int): String {
        val stringBuilder = StringBuilder()
        for (i in 0 until (array.size).coerceAtMost(limit)) {
            stringBuilder.append(array[i]).append(delim)
        }
        return stringBuilder.toString().trim()

    }

    private fun splitString(str: String, delimiter: String): Array<String> {
        if (str.isEmpty() || delimiter.isEmpty()) {
            return arrayOf(str)
        }
        val list = ArrayList<String>()
        var start = 0
        var end = str.indexOf(delimiter, start)
        while (end != -1) {
            list.add(str.substring(start, end))
            start = end + delimiter.length
            end = str.indexOf(delimiter, start)
        }
        list.add(str.substring(start))
        return list.toTypedArray()
    }

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            Character.toUpperCase(first).toString() + s.substring(1)
        }
    }
}