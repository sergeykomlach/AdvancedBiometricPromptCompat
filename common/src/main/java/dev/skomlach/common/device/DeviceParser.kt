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
import dev.skomlach.common.device.DeviceSpecManager.removeBrandPrefixIgnoreCase
import dev.skomlach.common.logging.LogCat


//Fix for case when DeviceName contains non-ANSI symbols
//Example: Motorola Edge 軽 7
fun fixModelAsAnsi(ua: String): String {
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

        val rawDeviceName = DeviceModelManager.getName(brand, model)
        val marketingModelNoBrand = removeBrandPrefixIgnoreCase(deviceName, brand)

        val searchTerms = mutableSetOf<String>().apply {
            if (model.isNotEmpty()) add(model.lowercase())
            add(rawDeviceName.lowercase())
            if (marketingModelNoBrand.isNotEmpty()) add(marketingModelNoBrand.lowercase())
        }

        val lowerCasedFullJson = fullJson.lowercase()
        var startIndex = -1
        for (term in searchTerms) {
            startIndex = lowerCasedFullJson.indexOf(term)
            if (startIndex != -1) break
        }

        if (startIndex == -1) {
            LogCat.logError("findDeviceSpecInJson < startIndex == -1")
            return null
        }
        val objectStart = lowerCasedFullJson.lastIndexOf(
            '{',
            lowerCasedFullJson.lastIndexOf("\"brand\"", startIndex)
        )
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
            if (phoneNameNorm == deviceName || phoneNameNorm == rawDeviceName) {
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
    val name: String? = null,
    val specs: Specs? = null
)

@Keep
data class Specs(
    val sensors: String? = null,

    )