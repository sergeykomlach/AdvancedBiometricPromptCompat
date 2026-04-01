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
import androidx.annotation.Keep
import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.SystemPropertiesProxy
import org.json.JSONArray

@Keep
data class DeviceModel(
    val deviceName: String,
    val deviceNameAsAnsi: String,
    val brand: String,
    val model: String
)

object DeviceModelManager {

    private val rawBrand: String by lazy {
        if (isAmazonKindleDevice()) "Amazon Kindle" else Build.BRAND ?: Build.MANUFACTURER ?: ""
    }
    private val rawModel: String by lazy {
        if (Build.MODEL.isNullOrEmpty()) {
            SystemPropertiesProxy.get(appContext, "ro.product.model", Build.MODEL)
        } else
            Build.MODEL
    }
    private val dm: DeviceModel by lazy {
        val name = getMarketingName() ?: getNameFromAssets() ?: getNameFromDatabase() ?: getName(
            rawBrand,
            rawModel
        )
        DeviceModel(
            deviceName = name,
            deviceNameAsAnsi = fixModelAsAnsi(name),
            brand = rawBrand,
            model = rawModel
        )
    }

    init {
        LogCat.log("DeviceModel.names brand=$rawBrand; model=$rawModel;")


    }

    fun getDeviceModel() = dm

    private fun isAmazonKindleDevice(): Boolean {
        val manu = (Build.MANUFACTURER ?: "").trim()
        val b = (Build.BRAND ?: Build.MANUFACTURER ?: "").trim()
        val product = (Build.PRODUCT ?: "").trim()
        val dev = (Build.DEVICE ?: "").trim()
        val characteristics = SystemPropertiesProxy.get(appContext, "ro.build.characteristics", "")

        val isAmazon =
            manu.equals("Amazon", true) || b.equals("Amazon", true) || b.contains("amazon", true)
        if (!isAmazon) return false

        val isTablet =
            characteristics.equals("tablet", true) || characteristics.contains("tablet", true)

        val model = rawModel

        val looksLikeKindle = model.startsWith("KF", true) ||
                product.startsWith("KF", true) ||
                dev.startsWith("KF", true) ||
                model.contains("kindle", true) ||
                product.contains("kindle", true) ||
                dev.contains("kindle", true)

        return isTablet || looksLikeKindle
    }

    private fun getMarketingName(): String? {
        if (detectEmulatorKind == EmulatorKind.ANDROID_EMULATOR) {
            buildEmulatorMarketingName()?.let { return it }
        }
        val brand = rawBrand
        SystemPropertiesProxy.get(appContext, "ro.config.marketing_name").let {
            if (it.isNotEmpty())
                return getName(brand, it)
        }
        SystemPropertiesProxy.get(appContext, "ro.product.marketname").let {
            if (it.isNotEmpty())
                return getName(brand, it)
        }

        SystemPropertiesProxy.get(appContext, "ro.product.vendor.marketname").let {
            if (it.isNotEmpty())
                return getName(brand, it)
        }
        SystemPropertiesProxy.get(appContext, "ro.product.system.marketname").let {
            if (it.isNotEmpty())
                return getName(brand, it)
        }
        SystemPropertiesProxy.get(appContext, "ro.product.odm.marketname").let {
            if (it.isNotEmpty())
                return getName(brand, it)
        }
        SystemPropertiesProxy.get(appContext, "ro.camera.model").let {
            if (it.isNotEmpty())
                return getName(brand, it)
        }
        return null
    }

    private fun buildEmulatorMarketingName(): String? {
        val product = (Build.PRODUCT ?: "").trim()
        if (product.isEmpty()) return null
        val model = rawModel.trim()
        if (model.startsWith("Pixel", ignoreCase = true)) {
            return null
        }
        val arch = when {
            product.contains("x86_64", true) -> "x86_64"
            product.contains("x86", true) -> "x86"
            product.contains("arm64", true) -> "arm64"
            product.contains("arm", true) -> "arm"
            else -> null
        }
        val has16k = product.contains("16k", ignoreCase = true)
        val isResizableAvd = product.contains("resizable", true)
        if (product.startsWith("sdk_", ignoreCase = true) || product.contains("emulator", true)) {
            val parts = ArrayList<String>(4)
            if (isResizableAvd) parts += "Resizable"
            if (arch != null) parts += arch
            if (has16k) parts += "16KB pages"

            val suffix = parts.joinToString(", ")
            return if (suffix.isNotEmpty()) {
                "Google Android Emulator ($suffix)"
            } else {
                "Google Android Emulator"
            }
        }

        return null
    }

    @WorkerThread
    private fun getNameFromAssets(): String? {
        LogCat.log("DeviceModel.getNameFromAssets > ")
        try {
            var ts = System.currentTimeMillis()
            val fullJson =
                DataProviders.getOrCacheJSON("https://github.com/androidtrackers/certified-android-devices/blob/master/by_model.json?raw=true")
                    ?: return null

            LogCat.log("DeviceModel.getNameFromAssets fullJson load time ${System.currentTimeMillis() - ts}ms")
            ts = System.currentTimeMillis()
            val list = mutableListOf<String>()

            val searchKey = "$rawModel\":"
            LogCat.log("DeviceModel.getNameFromAssets calculate searchKey time ${System.currentTimeMillis() - ts}ms")
            ts = System.currentTimeMillis()

            val startIndex = fullJson.indexOf(searchKey)
            LogCat.log("DeviceModel.getNameFromAssets find start index time ${System.currentTimeMillis() - ts}ms")
            ts = System.currentTimeMillis()
            if (startIndex == -1) return null

            val arrayStart = fullJson.indexOf("[", startIndex + searchKey.length)
            if (arrayStart == -1) return null
            LogCat.log("DeviceModel.getNameFromAssets find end index time ${System.currentTimeMillis() - ts}ms")
            ts = System.currentTimeMillis()

            val arrayContent = extractJsonFragment(fullJson, arrayStart, '[', ']') ?: return null

            LogCat.log("DeviceModel.getNameFromAssets arrayContent time ${System.currentTimeMillis() - ts}ms")
            ts = System.currentTimeMillis()
            val jsonArray = JSONArray(arrayContent)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val brand = obj.optString("brand")
                val name = getName(brand, obj.optString("name"))

                if (rawBrand.equals(brand, ignoreCase = true)) {
                    return name
                } else {
                    list.add(name)
                }
            }
            LogCat.log("DeviceModel.getNameFromAssets result ready time ${System.currentTimeMillis() - ts}ms")
            ts = System.currentTimeMillis()
            return list.firstOrNull().also {
                LogCat.log("DeviceModel.getNameFromAssets< $it")
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        LogCat.log("DeviceModel.getNameFromAssets < null")
        return null
    }

    @WorkerThread
    private fun getNameFromDatabase(): String? {
        val info = DeviceName
            .getDeviceInfo(appContext)
        try {
            LogCat.log(
                "DeviceModel.getNameFromDatabase { ${
                    Gson().toJson(
                        info,
                        DeviceName.DeviceInfo::class.java
                    )
                }; }"
            )
        } catch (_: Exception) {
        }
        return if (info.manufacturer.isNullOrEmpty()) null else getName(
            info.manufacturer,
            info.marketName
        )
    }

    fun getName(vendor: String, model: String): String {
        return if (model.startsWith(vendor, true))
            model.trim()
        else
            "$vendor $model".trim()
    }

}