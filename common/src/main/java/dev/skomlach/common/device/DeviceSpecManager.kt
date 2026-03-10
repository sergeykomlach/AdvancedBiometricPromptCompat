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

import java.util.regex.Pattern

object DeviceSpecManager {

    private val pattern = Pattern.compile("\\((.*?)\\)+")

    fun DeviceSpec?.getSensors(): Set<String> {
        if (this == null) return emptySet()
        return stringToArray(specs["Sensors"] ?: "")
    }

    fun DeviceSpec?.getModels(): Set<String> {
        if (this == null) return emptySet()
        return stringToArray(specs["Models"] ?: "")
    }

    //tools
    fun getDeviceSpecCompat(deviceModel: DeviceModel): DeviceSpec? {
        val json = DataProviders.getOrCacheJSON(
            "https://github.com/sergeykomlach/AdvancedBiometricPromptCompat/blob/main/common/src/main/assets/devices/specifications.json?raw=true"
        ) ?: return null

        val list = parseGsmarenaSpecsJson(json).toMutableList()

        if (list.isEmpty()) return null

        val brand = deviceModel.brand
        val model = deviceModel.model
        val deviceName = deviceModel.deviceName

        val rawDeviceName = DeviceModelManager.getName(brand, model)

        // marketing model without vendor (case-insensitive remove)
        val marketingModelNoBrand = removeBrandPrefixIgnoreCase(deviceName, brand)

        for (rec in list) {
            val phoneNameNorm = rec.phoneName

            if (phoneNameNorm == deviceName || phoneNameNorm == rawDeviceName) return rec

            val modelsNorm = rec.getModels()

            if (modelsNorm.any { m ->
                    m == model ||
                            (marketingModelNoBrand.isNotEmpty() && m == marketingModelNoBrand)
                }
            ) return rec
        }
        DataProviders.getOrCacheJSON(
            "https://github.com/nowrom/devices/blob/main/devices.json?raw=true"
        )?.let {
            val devices = DeviceParser.parse(it)
            devices.forEach { rec ->
                val phoneNameNorm = DeviceModelManager.getName(rec.brand ?: "", rec.name ?: "")
                if (phoneNameNorm == deviceName || phoneNameNorm == rawDeviceName)
                    return DeviceSpec(phoneNameNorm, mutableMapOf<String, String>().apply {
                        rec.specs?.sensors?.let { sensors ->
                            put("Sensors", sensors)
                        }
                    }, emptyMap())
            }
        }
        return null
    }

// --- helpers ---

    private fun removeBrandPrefixIgnoreCase(name: String, brand: String): String {
        if (brand.isBlank()) return name.trim()
        val n = name.trim()
        val b = brand.trim()
        return if (n.regionMatches(0, b, 0, b.length, ignoreCase = true)) {
            n.substring(b.length).trim()
        } else {
            n
        }
    }


    private fun stringToArray(data: String): Set<String> {
        var name = data
        val list = mutableSetOf<String>()

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
        return list.filter { !it.equals("Not found", ignoreCase = true) }.toSet()
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

}