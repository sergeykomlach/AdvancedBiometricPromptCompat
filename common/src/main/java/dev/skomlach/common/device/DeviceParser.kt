/*
 *  Copyright (c) 2026 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dev.skomlach.common.device.DeviceSpecManager.removeBrandPrefixIgnoreCase
import dev.skomlach.common.logging.LogCat
fun extractJsonFragment(text: String, startIndex: Int, openChar: Char, closeChar: Char): String? {
    var balance = 0
    var foundStart = false
    for (i in startIndex until text.length) {
        val char = text[i]
        if (char == openChar) {
            balance++
            foundStart = true
        } else if (char == closeChar) {
            balance--
        }
        if (foundStart && balance == 0) {
            return text.substring(startIndex, i + 1)
        }
    }
    return null
}
object DeviceParser {

    fun findDeviceSpecInJson(
        fullJson: String,
        deviceModel: DeviceModel
    ): DeviceSpec? {
        val brand = deviceModel.brand
        val model = deviceModel.model
        val deviceName = deviceModel.deviceName
        if (fullJson.indexOf(deviceName) == -1 &&
            fullJson.indexOf(model) == -1
        ) return null

        val rawDeviceName = DeviceModelManager.getName(brand, model)
        val marketingModelNoBrand = removeBrandPrefixIgnoreCase(deviceName, brand)

        val searchTerms = mutableMapOf<String, Boolean>().apply {
            put(deviceName, false)
            put(rawDeviceName, false)
            if (marketingModelNoBrand.isNotEmpty()) put(marketingModelNoBrand, true)
        }

        val searchPattern = "\"codename\":\"$model\""
        var startIndex = fullJson.indexOf(searchPattern)

        if (startIndex == -1) {
            for ((term, ignore) in searchTerms) {
                val namePattern = "\"name\":\"$term\""
                startIndex = fullJson.indexOf(namePattern, ignoreCase = ignore)
                if (startIndex != -1) break
            }
        }

        if (startIndex == -1){
            LogCat.logError("findDeviceSpecInJson < startIndex == -1")
            return null
        }

        val objectStart = fullJson.lastIndexOf('{', startIndex)
        if (objectStart == -1) {
            LogCat.logError("findDeviceSpecInJson < objectStart == -1")
            return null
        }

        val jsonObjectString = extractJsonFragment(fullJson, objectStart, '{', '}') ?: run {

                LogCat.logError("findDeviceSpecInJson < jsonObjectString is null")
                return null
        }

        try {

            val rec = Gson().fromJson(jsonObjectString, Device::class.java)
            LogCat.logError("findDeviceSpecInJson $rec")
            val phoneNameNorm = DeviceModelManager.getName(rec.brand ?: "", rec.name ?: "")
            if (phoneNameNorm == deviceName || phoneNameNorm == rawDeviceName || rec.codename == model) {
                return DeviceSpec(
                    phoneName = phoneNameNorm,
                    specs = mutableMapOf<String, String>().apply {
                        rec.specs?.sensors?.let { sensors -> put("Sensors", sensors) }
                    },
                    metadata = emptyMap()
                )
            }
        } catch (e: Exception) {
            LogCat.logException(e)
        }
        LogCat.logError("findDeviceSpecInJson null")
        return null
    }
}

@Keep
data class Device(
    val brand: String? = null,
    val codename: String? = null,
    val name: String? = null,
    val recoveries: List<Recovery>? = null,
    val roms: List<Rom>? = null,
    val specs: Specs? = null
)

data class Recovery(
    val id: String? = null,
    val supported: Boolean? = null,

    @SerializedName("xdathread")
    val xdaThread: String? = null,

    val maintainer: String? = null
)

data class Rom(
    val id: String? = null,

    val cpu: String? = null,
    val ram: String? = null,
    val wifi: String? = null,

    val url: String? = null,
    val download: String? = null,
    val group: String? = null,
    val photo: String? = null,
    val recovery: String? = null,
    val gapps: String? = null,

    @SerializedName("telegram_url")
    val telegramUrl: String? = null,

    @SerializedName("xda_thread")
    val xdaThread: String? = null,

    val maintainer: String? = null,
    val changelog: String? = null,
    val active: Boolean? = null,

    @SerializedName("maintainer_url")
    val maintainerUrl: String? = null,

    @SerializedName("maintainer_name")
    val maintainerName: String? = null,

    @SerializedName("supported_versions")
    val supportedVersions: List<SupportedVersion>? = null,

    // "repostories"
    @SerializedName("repostories")
    val repositories: List<String>? = null,

    val romtype: String? = null,
    val version: String? = null,
    val developer: String? = null
)

data class SupportedVersion(
    @SerializedName("version_code")
    val versionCode: String? = null,

    @SerializedName("xda_thread")
    val xdaThread: String? = null,

    val stable: Boolean? = null,
    val deprecated: Boolean? = null
)

data class Specs(
    val cpu: String? = null,
    val weight: String? = null,
    val year: String? = null,
    val os: String? = null,
    val chipset: String? = null,
    val gpu: String? = null,
    val sensors: String? = null,
    val batlife: String? = null,

    @SerializedName("internalmemory")
    val internalMemory: String? = null
)