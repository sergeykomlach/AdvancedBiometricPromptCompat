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
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.biometric.compat.utils.SystemPropertiesProxy
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object DeviceModel {

    private val brand = Build.BRAND?:""
    private val model = (Build.MODEL?:"").replace("  ", " ")

    fun getNames(): Set<String> {
        val strings = HashMap<String, String>()
        var s: String? = getSimpleDeviceName()
        BiometricLoggerImpl.e("AndroidModel - $s")
        s?.let {
            strings.put(it.toLowerCase(Locale.US), fixVendorName(it))
        }
        s = getNameFromAssets()
        s?.let {
            strings.put(it.toLowerCase(Locale.US), fixVendorName(it))
        }
        s = getNameFromDatabase()
        s?.let {
            strings.put(it.toLowerCase(Locale.US), fixVendorName(it))
        }
        BiometricLoggerImpl.e("AndroidModel.names ${strings.values}")
        return HashSet<String>(strings.values)
    }

    private fun fixVendorName(string: String) :String{
        val parts = string.split(" ")

        var vendor = parts[0]
        if(vendor[0].isLowerCase()) {
            vendor = Character.toUpperCase(vendor[0]).toString() + vendor.substring(1)
        }
        return vendor + string.substring(vendor.length, string.length)

    }
    private fun getSimpleDeviceName(): String {
        val s =
            SystemPropertiesProxy.get(AndroidContext.appContext, "ro.config.marketing_name")
        return if (!s.isNullOrEmpty())
            getName(brand, s)
        else
            getName(brand, model)
    }

    @WorkerThread
    private fun getNameFromAssets(): String? {

        BiometricLoggerImpl.e("AndroidModel.getNameFromAssets started")

        try {
            val json = JSONObject(getJSON())
            for (key in json.keys()) {
                if (brand.equals(key, ignoreCase = true)) {
                    val details = json.getJSONArray(key)
                    for (i in 0 until details.length()) {
                        val jsonObject = details.getJSONObject(i)
                        val m = jsonObject.getString("model")
                        val name = jsonObject.getString("name")
                        if (m.isNullOrEmpty() && name.isNullOrEmpty()) {
                            continue
                        } else if (model.equals(m, ignoreCase = true)) {
                            BiometricLoggerImpl.e("AndroidModel - $jsonObject")
                            val modelParts = model.split(" ")
                            val nameParts = name.replace("  ", " ").split(" ")
                            val fullName =
                                if (modelParts[0].length > nameParts[0].length && modelParts[0].startsWith(
                                        nameParts[0],
                                        true
                                    )
                                ) model else name
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
            Network.fastCopy(inputStream, byteArrayOutputStream)
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
        BiometricLoggerImpl.e("AndroidModel - {${info.codename}; ${info.name}; ${info.marketName}; ${info.model}; }")
        return if (info != null) {
            val modelParts = model.split(" ")
            val nameParts = info.name.replace("  ", " ").split(" ")
            val fullName =
                if (modelParts[0].length > nameParts[0].length && modelParts[0].startsWith(
                        nameParts[0],
                        true
                    )
                ) model else info.name

            getName(
                if (!TextUtils.isEmpty(info.manufacturer)) info.manufacturer else brand,
                fullName
            )
        } else {
            null
        }
    }

    private fun getName(vendor: String, model: String): String {
        if (model.startsWith(vendor, true))
            return model
        return "$vendor $model"
    }

}