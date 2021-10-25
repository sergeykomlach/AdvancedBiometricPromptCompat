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

package dev.skomlach.biometric.compat.utils.device

import android.os.Build
import androidx.annotation.WorkerThread
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.biometric.compat.utils.SystemPropertiesProxy
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.network.NetworkApi
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object DeviceModel {

    private val brand = (Build.BRAND ?: "").replace("  ", " ")
    private val model = (Build.MODEL ?: "").replace("  ", " ")
    private val device = (Build.DEVICE ?: "").replace("  ", " ")

    fun getNames(): Set<String> {
        val strings = HashMap<String, String>()
        var s: String? = getSimpleDeviceName()
        BiometricLoggerImpl.d("AndroidModel - $s")
        s?.let {
            strings.put(it.lowercase(Locale.ROOT), fixVendorName(it))
        }
        s = getNameFromAssets()
        s?.let {
            strings.put(it.lowercase(Locale.ROOT), fixVendorName(it))
        }
        s = getNameFromDatabase()
        s?.let {
            strings.put(it.lowercase(Locale.ROOT), fixVendorName(it))
        }

        val set = HashSet<String>(strings.values)
        val toRemove = HashSet<String>()
        for (name1 in set) {
            for (name2 in set) {
                if (toRemove.contains(name2))
                    continue
                if (name1.length < name2.length && name2.startsWith(name1, ignoreCase = true))
                    toRemove.add(name1)
            }
        }
        set.removeAll(toRemove)
        BiometricLoggerImpl.d("AndroidModel.names $set")
        return set
    }

    private fun fixVendorName(string: String): String {
        val parts = string.split(" ")

        var vendor = parts[0]
        if (vendor[0].isLowerCase()) {
            vendor = Character.toUpperCase(vendor[0]).toString() + vendor.substring(1)
        }
        return (vendor + string.substring(vendor.length, string.length)).trim()
    }

    private fun getSimpleDeviceName(): String? {
        SystemPropertiesProxy.get(AndroidContext.appContext, "ro.config.marketing_name")?.let {
            return getName(brand, it)
        }
        return null
    }

    @WorkerThread
    private fun getNameFromAssets(): String? {

        BiometricLoggerImpl.d("AndroidModel.getNameFromAssets started")

        try {
            val json = JSONObject(getJSON() ?: return null)
            for (key in json.keys()) {
                if (brand.equals(key, ignoreCase = true)) {
                    val details = json.getJSONArray(key)
                    for (i in 0 until details.length()) {
                        val jsonObject = details.getJSONObject(i)
                        val m = jsonObject.getString("model")
                        val name = jsonObject.getString("name")
                        val d = jsonObject.getString("device")
                        if (name.isNullOrEmpty()) {
                            continue
                        } else if (!m.isNullOrEmpty() && model.equals(m, ignoreCase = true)) {
                            BiometricLoggerImpl.d("AndroidModel - $jsonObject")
                            val fullName = getFullName(name)
                            return getName(brand, fullName)
                        } else if (!d.isNullOrEmpty() && device.equals(d, ignoreCase = true)) {
                            BiometricLoggerImpl.d("AndroidModel - $jsonObject")
                            val fullName = getFullName(name)
                            return getName(brand, fullName)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e, "AndroidModel")
        }
        return null
    }

    //tools
    private fun getJSON(): String? {
        try {
            //https://github.com/androidtrackers/certified-android-devices/
            val inputStream =
                AndroidContext.appContext.assets.open("by_brand.json")
            val byteArrayOutputStream = ByteArrayOutputStream()
            NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
            inputStream.close()
            byteArrayOutputStream.close()
            val data = byteArrayOutputStream.toByteArray()
            return String(data)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return null;
    }

    @WorkerThread
    private fun getNameFromDatabase(): String? {
        val info = DeviceName
            .getDeviceInfo(AndroidContext.appContext)
        BiometricLoggerImpl.d("AndroidModel - {${info.codename}; ${info.name}; ${info.marketName}; ${info.model}; }")
        return if (info != null) {
            val fullName = getFullName(info.name)
            getName(
                if (info.manufacturer?.isNotEmpty() == true) info.manufacturer else brand,
                fullName
            )
        } else {
            null
        }
    }

    private fun getFullName(name: String): String {
        val modelParts = model.split(" ")
        val nameParts = name.split(" ")

        return if (modelParts[0].length > nameParts[0].length && modelParts[0].startsWith(
                nameParts[0],
                true
            )
        ) model else name
    }

    private fun getName(vendor: String, model: String): String {
        if (model.startsWith(vendor, true))
            return model
        return "$vendor $model"
    }

}