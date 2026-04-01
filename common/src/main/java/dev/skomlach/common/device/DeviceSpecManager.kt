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

import dev.skomlach.common.logging.LogCat

object DeviceSpecManager {
    fun DeviceSpec?.getSensors(): Set<String> {
        if (this == null) return emptySet()
        return stringToArray(specs["Sensors"] ?: specs["sensors"] ?: "")
    }

    fun DeviceSpec?.getModels(): Set<String> {
        if (this == null) return emptySet()
        return stringToArray(specs["Models"] ?: specs["models"] ?: "")
    }

    //tools
    fun getDeviceSpecCompat(deviceModel: DeviceModel): DeviceSpec? {
        var timestamp = System.currentTimeMillis()
        try {
            var ts = System.currentTimeMillis()
            DataProviders.getOrCacheJSON(
                "https://github.com/sergeykomlach/AdvancedBiometricPromptCompat/blob/main/common/src/main/assets/devices/specifications.json?raw=true"
            )?.let { json ->
                LogCat.log("getDeviceSpecCompat findGsmarenaSpec load time ${System.currentTimeMillis() - ts}ms")
                ts = System.currentTimeMillis()
                findGsmarenaSpec(json, deviceModel).also {
                    LogCat.log("getDeviceSpecCompat findGsmarenaSpec lookup time ${System.currentTimeMillis() - ts}ms")
                }?.let {
                    return it
                }
            }
            ts = System.currentTimeMillis()
            DataProviders.getOrCacheJSON(
                "https://github.com/nowrom/devices/blob/main/devices.json?raw=true"
            )?.let { json ->
                LogCat.log("getDeviceSpecCompat findDeviceSpecInJson load time ${System.currentTimeMillis() - ts}ms")
                ts = System.currentTimeMillis()
                DeviceParser.findDeviceSpecInJson(json, deviceModel).also {
                    LogCat.log("getDeviceSpecCompat findDeviceSpecInJson lookup time ${System.currentTimeMillis() - ts}ms")
                }?.let {
                    return it
                }
            }
            return null
        } finally {
            LogCat.log("getDeviceSpecCompat time ${System.currentTimeMillis() - timestamp}ms")
        }
    }

    private fun findGsmarenaSpec(
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


        LogCat.log("getDeviceSpecCompat searchTerms=$searchTerms")
        val lowerCasedFullJson = fullJson.lowercase()
        for (term in searchTerms) {
            var lastIndex = 0
            while (true) {
                val index = lowerCasedFullJson.indexOf(term, lastIndex)
                if (index == -1) break
                val objectStart = lowerCasedFullJson.lastIndexOf('{', lowerCasedFullJson.lastIndexOf("\"phone_name\"", index))
                if (objectStart != -1) {
                    val fragment = extractJsonFragment(fullJson, objectStart, '{', '}')
                    if (fragment != null) {
                        try {
                            val rec = manualParseDeviceSpec(fragment)

                            val phoneNameNorm = rec.phoneName
                            if (phoneNameNorm == deviceName || phoneNameNorm == rawDeviceName) {
                                LogCat.log("getDeviceSpecCompat $term; fired phoneNameNorm=$phoneNameNorm")
                                return rec
                            }

                            val modelsNorm = rec.getModels()
                            if (modelsNorm.any { m ->
                                    m == model || (marketingModelNoBrand.isNotEmpty() && m == marketingModelNoBrand)
                                }
                            ) {
                                LogCat.log("getDeviceSpecCompat $term; fired modelsNorm=$modelsNorm")
                                return rec
                            }
                        } catch (e: Exception) {

                        }
                    }
                }
                lastIndex = index + term.length
            }
        }
        return null
    }


// --- helpers ---

    fun removeBrandPrefixIgnoreCase(name: String, brand: String): String {
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
        if (data.isBlank()) return emptySet()
        return data.split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("Not found", ignoreCase = true) }
            .map { capitalize(it) }
            .toSet()
    }
}