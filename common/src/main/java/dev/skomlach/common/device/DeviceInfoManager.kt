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
import com.google.gson.reflect.TypeToken
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.device.DeviceModel.getNames
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import dev.skomlach.common.translate.LocalizationHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

object DeviceInfoManager {
    val PREF_NAME = "BiometricCompat_DeviceInfo-V7"
    private val pattern = Pattern.compile("\\((.*?)\\)+")
    private val loadingInProgress = AtomicBoolean(false)
    init {
       getPreferences(PREF_NAME).apply {
           edit().clear().commit()
       }
    }
    @WorkerThread
    fun getDeviceInfo(listener: OnDeviceInfoListener) {
        if (Looper.getMainLooper().thread === Thread.currentThread()) throw IllegalThreadStateException(
            "Worker thread required"
        )
        val onDeviceInfoListener = WeakReference(listener)
        var deviceInfo = cachedDeviceInfo
        if (deviceInfo != null) {
            onDeviceInfoListener.get()?.onReady(deviceInfo)
            return
        }

        val names = getNames()
        val devicesList = (try {
            Gson().fromJson(
                getJSON(
                    "devices.json",
                    "https://github.com/nowrom/devices/blob/main/devices.json?raw=true"
                ), Array<DeviceSpec>::class.java
            )
                ?: arrayOf<DeviceSpec>()
        } catch (e: Throwable) {
            arrayOf<DeviceSpec>()
        }).toMutableList().apply {
            addAll(getDeviceSpecCompat())
        }.toTypedArray()

        // Detect emulator/virtualized environment once. This must NOT change the model string,
        // because model-based matching is used to obtain sensors list.
        val emulatorKind: EmulatorKind? = runCatching { detectEmulatorKind() }.getOrNull()
        for (m in names) {
            try {
                val first = m.first
                val secondArray = splitString(m.second, " ")
                val spaceCount = m.second.count { it == ' ' }
                for (limit in secondArray.size downTo 2) {
                    if (limit < (spaceCount - 2).coerceAtLeast(2))//Device should have at least brand + model
                        break
                    val second = join(secondArray, " ", limit)
                    deviceInfo = loadDeviceInfo(
                        devicesList,
                        first,
                        second,
                        DeviceModel.brand,
                        DeviceModel.device,
                        emulatorKind
                    )
                    if (deviceInfo != null) {
                        val hasSensors = deviceInfo.sensors.isNotEmpty()
                        val isMostSpecificAttempt = (limit == secondArray.size)
                        val acceptEvenWithoutSensors = emulatorKind != null || isMostSpecificAttempt
                        if (hasSensors || acceptEvenWithoutSensors) {
                            LogCat.log("DeviceInfoManager: " + deviceInfo.model + " -> " + deviceInfo)
                            setCachedDeviceInfo(deviceInfo, true)
                            onDeviceInfoListener.get()?.onReady(deviceInfo)
                            return
                        } else {
                            LogCat.log("DeviceInfoManager: matched but empty sensors for $first/$second (will try shorter)")
                        }
                    } else {
                        LogCat.log("DeviceInfoManager: no data for $first/$second")
                    }
                }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
        }
        onDeviceInfoListener.get()?.onReady(getAnyDeviceInfo(emulatorKind).also {
            setCachedDeviceInfo(it, false)
        })
    }

    //Fix for case when DeviceName contains non-ASCII symbols
    //Example: Motorola Edge 軽 7
    private fun fixModelAsAscii(ua: String): String {
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
                    result.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return if (result.isEmpty()) "Unknown"
        else
            result.toString().replace("\\s+".toRegex(), " ").trim()
    }

    @Volatile
    private var cachedDeviceInfo: DeviceInfo? = null
        get() {
            if (field == null) {
                val sharedPreferences = getPreferences(PREF_NAME)
                if (sharedPreferences.getBoolean("checked", false)) {
                    val model =
                        sharedPreferences.getString("model", null)
                            ?: return null
                    val sensors =
                        sharedPreferences.getStringSet("sensors", null)
                            ?: HashSet<String>()
                    val emu = sharedPreferences.getString("emulatorKind", null)
                    val emulatorKind =
                        emu?.let { runCatching { EmulatorKind.valueOf(it) }.getOrNull() }
                    field = DeviceInfo(model, fixModelAsAscii(model), sensors, emulatorKind)
                }
            }
            return field
        }

    fun getAnyDeviceInfo(emulatorKind: EmulatorKind? = null): DeviceInfo {
        cachedDeviceInfo?.let {
            return it
        }
        val name: String? = getNames()
            .maxByOrNull { it.first.length }
            ?.first

        return if (!name.isNullOrEmpty())
            DeviceInfo(name, fixModelAsAscii(name), HashSet<String>(), emulatorKind).also {
                LogCat.log("DeviceInfoManager: (fallback 1) " + it.model + " -> " + it)
                cachedDeviceInfo = it
            }
        else
            DeviceInfo(
                DeviceModel.model,
                fixModelAsAscii(DeviceModel.model),
                HashSet<String>(),
                emulatorKind
            ).also {
                LogCat.log("DeviceInfoManager: (fallback 2) " + it.model + " -> " + it)
                cachedDeviceInfo = it
            }
    }

    private fun setCachedDeviceInfo(deviceInfo: DeviceInfo, strictMatch: Boolean) {
        cachedDeviceInfo = deviceInfo
        try {
            val sharedPreferences = getPreferences(PREF_NAME)
                .edit()
            sharedPreferences.clear().commit()
            sharedPreferences
                .putStringSet("sensors", deviceInfo.sensors)
                .putString("model", deviceInfo.model)
                .putString("emulatorKind", deviceInfo.emulatorKind?.name)
                .putBoolean("checked", true)
                .putBoolean("strictMatch", strictMatch)
                .apply()
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    private fun loadDeviceInfo(
        devicesList: Array<DeviceSpec>,
        modelReadableName: String,
        model: String,
        brand: String,
        codeName: String,
        emulatorKind: EmulatorKind?
    ): DeviceInfo? {
        LogCat.log("DeviceInfoManager: loadDeviceInfo(total=${devicesList.size}, modelReadableName=$modelReadableName, brand=$brand, model=$model, codeName=$codeName)")
        try {
            if (model.startsWith(brand, ignoreCase = true)) {
                val info =
                    findDeviceInfo(
                        devicesList,
                        model.substring(brand.length).trim(),
                        brand,
                        codeName,
                        emulatorKind
                    )
                if (info != null)
                    return info
            }
            if (modelReadableName.startsWith(brand, ignoreCase = true)) {
                val info = findDeviceInfo(
                    devicesList,
                    modelReadableName.substring(brand.length).trim(),
                    brand,
                    codeName,
                    emulatorKind
                )
                if (info != null)
                    return info
            }
            val info = findDeviceInfo(devicesList, model, brand, codeName, emulatorKind)
            if (info != null)
                return info

            return findDeviceInfo(devicesList, modelReadableName, brand, codeName, emulatorKind)
        } catch (e: Throwable) {
            LogCat.logException(e, "DeviceInfoManager")
            return null
        }
    }

    private fun findDeviceInfo(
        devicesList: Array<DeviceSpec>,
        model: String,
        brand: String,
        codeName: String,
        emulatorKind: EmulatorKind?
    ): DeviceInfo? {
        LogCat.log("DeviceInfoManager: findDeviceInfo(total=${devicesList.size}, brand=$brand, model=$model, codeName=$codeName)")

        // Normalize once
        val qBrand = brand.trim()
        val qModel = model.trim()
        val qTokens = tokenize(qModel)
        val qDigits = qTokens.filter { it.all(Char::isDigit) }.toSet()

        var bestScore = Int.MIN_VALUE
        var best: DeviceInfo? = null

        for (spec in devicesList) {
            val specBrand = (spec.brand ?: "").trim()
            val specName = (spec.name ?: "").trim()
            if (specName.isEmpty()) continue

            val fullName = buildReadableName(spec, qBrand)

            // Fast-path exact matches
            if (specName.equals(qModel, ignoreCase = true)) {
                val di =
                    DeviceInfo(fullName, fixModelAsAscii(fullName), getSensors(spec), emulatorKind)
                LogCat.log("DeviceInfoManager: (exact) $spec")
                return di
            }
            if (spec.codename == codeName && specBrand.equals(qBrand, ignoreCase = true)) {
                val di =
                    DeviceInfo(fullName, fixModelAsAscii(fullName), getSensors(spec), emulatorKind)
                LogCat.log("DeviceInfoManager: (codename+brand) $spec")
                return di
            }

            // Score candidate
            val score = scoreCandidate(
                qBrand = qBrand,
                qTokens = qTokens,
                qDigits = qDigits,
                specBrand = specBrand,
                specName = specName,
                fullName = fullName,
                codename = spec.codename ?: "",
                qCodename = codeName
            )

            if (score > bestScore) {
                bestScore = score
                best =
                    DeviceInfo(fullName, fixModelAsAscii(fullName), getSensors(spec), emulatorKind)
            }
        }

        // Threshold to avoid terrible "Galaxy" / "9" style false positives.
        // If we have digits in the query, we demand at least one strong digit-aware match.
        val threshold = if (qDigits.isNotEmpty()) 650 else 450
        return if (bestScore >= threshold) best else null
    }

    private fun buildReadableName(spec: DeviceSpec, fallbackBrand: String): String {
        val bRaw = (spec.brand ?: fallbackBrand).trim()
        val nRaw = (spec.name ?: "").trim()

        val b = smartCapitalize(bRaw)
        val n = smartCapitalize(nRaw)

        val combined = when {
            n.isBlank() -> b
            b.isBlank() -> n
            n.startsWith(b, ignoreCase = true) -> n
            else -> "$b $n"
        }
        return combined.trim()
    }

    private fun tokenize(s: String): List<String> {
        // Keep digits as tokens; split on anything else.
        return s.lowercase()
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
    }

    private fun scoreCandidate(
        qBrand: String,
        qTokens: List<String>,
        qDigits: Set<String>,
        specBrand: String,
        specName: String,
        fullName: String,
        codename: String,
        qCodename: String
    ): Int {
        var score = 0

        val nameTokens = tokenize(specName)
        val fullTokens = tokenize(fullName)

        // Brand alignment
        if (specBrand.equals(qBrand, ignoreCase = true)) score += 300
        else if (specBrand.isNotEmpty() && qBrand.isNotEmpty()) score -= 150

        // Codename bonus (but don't allow it to override wrong brand)
        if (qCodename.isNotEmpty() && codename == qCodename) score += 400

        // Digit guard: if query has digits, require candidate to contain them
        if (qDigits.isNotEmpty()) {
            val candidateDigits = (nameTokens + fullTokens).filter { it.all(Char::isDigit) }.toSet()
            val missing = qDigits - candidateDigits
            if (missing.isNotEmpty()) return Int.MIN_VALUE / 4 // hard reject
            score += 250
        }

        // Token overlap
        val tokenSet = (nameTokens + fullTokens).toSet()
        var overlap = 0
        for (t in qTokens) {
            if (t in tokenSet) overlap += if (t.all(Char::isDigit)) 50 else 20
        }
        score += overlap

        // Substring alignment (useful for minor formatting differences)
        if (specName.contains(qBrand, ignoreCase = true)) score += 20
        if (fullName.contains(qBrand, ignoreCase = true)) score += 20

        // Penalize overly short / generic matches
        val qLen = qTokens.sumOf { it.length }.coerceAtLeast(1)
        val nLen = tokenize(specName).sumOf { it.length }.coerceAtLeast(1)
        if (nLen < qLen / 2) score -= 100

        // Prefer longer, more specific names when scores tie
        score += (specName.length.coerceAtMost(60))

        return score
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
    private fun getDeviceSpecCompat(): Set<DeviceSpec> {
        val list = mutableSetOf<DeviceSpec>()
        try {
            val type = object : TypeToken<List<Phone>>() {}.type
            val phones: List<Phone> = Gson().fromJson(
                getJSON(
                    "products_infos.json",
                    "https://github.com/milephm/phonedata_crawler/releases/download/data/products_infos_gsmarena.json"
                ), type
            )
            phones.forEach {
                val device = DeviceSpec(
                    brand = it.brand,
                    name = it.name,
                    specs = Specs(
                        cpu = it.info?.get("CPU"),
                        weight = it.info?.get("Weight"),
                        year = it.info?.get("Announced"),
                        os = it.info?.get("OS"),
                        chipset = it.info?.get("Chipset"),
                        gpu = it.info?.get("GPU"),
                        sensors = it.info?.get("Sensors"),
                        internalmemory = it.info?.get("Internal")
                    )
                )
                list.add(device)
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        return list
    }

    //https://github.com/nowrom/devices/
    private fun getJSON(fileName: String, url: String): String? {
        var reload = false
        try {
            try {
                val file = File(AndroidContext.appContext.cacheDir, fileName)
                if (file.parentFile?.exists() == false) {
                    file.parentFile?.mkdirs()
                }
                file.also {
                    if (it.exists()) {
                        if (kotlin.math.abs(System.currentTimeMillis() - it.lastModified()) >= TimeUnit.DAYS.toMillis(
                                30
                            )
                        ) {
                            reload = true
                        }
                        return it.readText(
                            Charset.forName("UTF-8")
                        )
                    } else {
                        reload = true
                    }
                }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
            try {
                val inputStream =
                    AndroidContext.appContext.assets.open(fileName)
                val byteArrayOutputStream = ByteArrayOutputStream()
                NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
                inputStream.close()
                byteArrayOutputStream.close()
                val data = byteArrayOutputStream.toByteArray()
                return String(data, Charset.forName("UTF-8")).also { data ->
                    saveToCache(data, fileName)
                }
            } catch (e: Throwable) {
                reload = true
                LogCat.logException(e)
            }
            return null
        } finally {
            if (reload && !loadingInProgress.get()) {
                loadingInProgress.set(true)
                ExecutorHelper.startOnBackground {
                    val sharedPreferences =
                        getPreferences(PREF_NAME)
                    if (NetworkApi.hasInternet() && !sharedPreferences.getBoolean(
                            "strictMatch",
                            false
                        )
                    ) {
                        try {
                            val data =
                                LocalizationHelper.fetchFromWeb(url)
                            saveToCache(data ?: return@startOnBackground, fileName)
                        } catch (e: Throwable) {
                            LogCat.logException(e)
                        } finally {
                            loadingInProgress.set(false)
                        }
                    } else
                        loadingInProgress.set(false)
                }
            }
        }
    }

    private fun saveToCache(data: String, name: String) {
        try {
            val file = File(AndroidContext.appContext.cacheDir, name)
            if (file.parentFile?.exists() == false) {
                file.parentFile?.mkdirs()
            }
            file.also {
                it.delete()
                it.writeText(
                    data,
                    Charset.forName("UTF-8")
                )
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
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


}