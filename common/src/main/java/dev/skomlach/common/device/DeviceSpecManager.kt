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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

object DeviceSpecManager {

    fun DeviceSpec?.getSensors(): Set<String> {
        if (this == null) return emptySet()
        return stringToArray(specs["Sensors"] ?: "")
    }

    fun DeviceSpec?.getModels(): Set<String> {
        if (this == null) return emptySet()
        return stringToArray(specs["Models"] ?: "")
    }

    //tools
    fun getDeviceSpecCompat(deviceModel: DeviceModel): DeviceSpec? = runBlocking {
        val gsmarenaDeferred = async(Dispatchers.IO) {
            DataProviders.getOrCacheJSON(
                "https://github.com/sergeykomlach/AdvancedBiometricPromptCompat/blob/main/common/src/main/assets/devices/specifications.json?raw=true"
            )?.let { json ->
                findGsmarenaSpec(json, deviceModel)
            }
        }

        val devicesDeferred = async(Dispatchers.IO) {
            DataProviders.getOrCacheJSON(
                "https://github.com/nowrom/devices/blob/main/devices.json?raw=true"
            )?.let { json ->
                DeviceParser.findDeviceSpecInJson(json, deviceModel)
            }
        }

        val first = gsmarenaDeferred.await()
        if (first != null) {
            devicesDeferred.cancel()
            return@runBlocking first
        }

        devicesDeferred.await()
    }
    private fun findGsmarenaSpec(
        json: String,
        deviceModel: DeviceModel
    ): DeviceSpec? {
        val brand = deviceModel.brand
        val model = deviceModel.model
        val deviceName = deviceModel.deviceName
        val rawDeviceName = DeviceModelManager.getName(brand, model)
        val marketingModelNoBrand = removeBrandPrefixIgnoreCase(deviceName, brand)

        val searchTerms = mutableSetOf<String>().apply {
            add("\"phone_name\":\"$deviceName\"")
            add("\"phone_name\":\"$rawDeviceName\"")
            if (model.isNotEmpty()) add(model)
            if (marketingModelNoBrand.isNotEmpty()) add(marketingModelNoBrand)
        }

        val gson = gsonForGsmarena()

        for (term in searchTerms) {
            var lastIndex = 0
            while (true) {
                val index = json.indexOf(term, lastIndex)
                if (index == -1) break
                val objectStart = json.lastIndexOf('{', index)
                if (objectStart != -1) {
                    val fragment = extractJsonFragment(json, objectStart, '{', '}')
                    if (fragment != null) {
                        try {
                            val rec = gson.fromJson(fragment, DeviceSpec::class.java)

                            val phoneNameNorm = rec.phoneName
                            if (phoneNameNorm == deviceName || phoneNameNorm == rawDeviceName) {
                                return rec
                            }

                            val modelsNorm = rec.getModels()
                            if (modelsNorm.any { m ->
                                    m == model || (marketingModelNoBrand.isNotEmpty() && m == marketingModelNoBrand)
                                }
                            ) {
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