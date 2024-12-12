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
import androidx.annotation.WorkerThread
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.SystemPropertiesProxy
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.storage.SharedPreferenceProvider
import dev.skomlach.common.translate.LocalizationHelper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object DeviceModel {
    private val loadingInProgress = AtomicBoolean(false)
    var brand = (Build.BRAND ?: "").replace("  ", " ")
        private set
    val model = (Build.MODEL ?: "").replace("  ", " ")
    val device = (Build.DEVICE ?: "").replace("  ", " ")
    private val appContext = AndroidContext.appContext
    private val list = ArrayList<Pair<String, String>>()

    init {
        if (brand == "Amazon") {
            SystemPropertiesProxy.get(appContext, "ro.build.characteristics").let {
                if (it == "tablet")
                    brand = "$brand Kindle"
            }
        }

    }

    fun getNames(): List<Pair<String, String>> {
        if (list.isNotEmpty())
            return list

        val strings = HashMap<String, String>()
        getNameFromAssets()?.let {
            for (s in it) {
                val str = fixVendorName(s)
                if (str.trim().isNotEmpty())
                    strings.put(str, str.filter { c ->
                        c.isLetterOrDigit() || c.isWhitespace()
                    })
            }
        }
        if (strings.isEmpty())
            getSimpleDeviceName()?.let {
                val str = fixVendorName(it)
                if (str.trim().isNotEmpty())
                    strings.put(str, str.filter { c ->
                        c.isLetterOrDigit() || c.isWhitespace()
                    })
            }
        //Obsolete DB, use it as last chance
        if (strings.isEmpty())
            getNameFromDatabase()?.let {
                for (s in it) {
                    val str = fixVendorName(s ?: continue)
                    if (str.trim().isNotEmpty())
                        strings.put(str, str.filter { c ->
                            c.isLetterOrDigit() || c.isWhitespace()
                        })
                }
            }


        val set = HashSet<String>(strings.keys)
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
        val l = set.toMutableList().also {
            it.sortWith { p0, p1 -> p0.length.compareTo(p1.length) }
        }
        val list = ArrayList<Pair<String, String>>()
        for (s in l) {
            list.add(Pair(s, strings[s] ?: continue))
        }
        LogCat.log("AndroidModel.names $list")
        return list
    }

    private fun fixVendorName(string: String): String {
        if (string.trim().isEmpty())
            return string
        val parts = string.split(" ")

        var vendor = parts[0]
        if (vendor[0].isLowerCase()) {
            vendor = Character.toUpperCase(vendor[0]).toString() + vendor.substring(1)
        }
        return (vendor + string.substring(vendor.length, string.length)).trim()
    }

    private fun getSimpleDeviceName(): String? {
        SystemPropertiesProxy.get(appContext, "ro.config.marketing_name").let {
            if (it.isNotEmpty())
                return getName(brand, it)
        }
        SystemPropertiesProxy.get(appContext, "ro.camera.model").let {
            if (it.isNotEmpty())
                return getName(brand, it)
        }
        return null
    }

    @WorkerThread
    private fun getNameFromAssets(): Set<String>? {

        LogCat.log("AndroidModel.getNameFromAssets started")

        try {
            val json = JSONObject(getJSON() ?: return null)
            for (key in json.keys()) {

                val details = json.getJSONArray(key)
                for (i in 0 until details.length()) {
                    val jsonObject = details.getJSONObject(i)
                    val m = jsonObject.getString("model")
                    val name = jsonObject.getString("name")
                    val d = jsonObject.getString("device")
                    if (name.isNullOrEmpty()) {
                        continue
                    } else if (!m.isNullOrEmpty() && (model.equals(
                            m,
                            ignoreCase = true
                        ) || model.filter { c ->
                            c.isLetterOrDigit() || c.isWhitespace()
                        }.equals(m.filter { c ->
                            c.isLetterOrDigit() || c.isWhitespace()
                        }, ignoreCase = true))
                    ) {
                        return mutableSetOf<String>().apply {
                            this.add(getName(brand, getFullName(model)))
                            this.add(getName(brand, getFullName(name)))
                        }.also {
                            LogCat.log("AndroidModel.getNameFromAssets1 - $jsonObject -> $it")
                        }
                    }
                }
            }

            for (key in json.keys()) {
                val details = json.getJSONArray(key)
                for (i in 0 until details.length()) {
                    val jsonObject = details.getJSONObject(i)
                    val m = jsonObject.getString("model")
                    val name = jsonObject.getString("name")
                    val d = jsonObject.getString("device")
                    if (name.isNullOrEmpty()) {
                        continue
                    } else if (!d.isNullOrEmpty() && (device.equals(d,
                            ignoreCase = true
                        ) || device.filter { c ->
                            c.isLetterOrDigit() || c.isWhitespace()
                        }.equals(d.filter { c ->
                            c.isLetterOrDigit() || c.isWhitespace()
                        }, ignoreCase = true))
                    ) {
                        return mutableSetOf<String>().apply {
                            this.add(getName(brand, getFullName(model)))
                            this.add(getName(brand, getFullName(name)))
                        }.also {
                            LogCat.log("AndroidModel.getNameFromAssets2 - $jsonObject -> $it")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        } finally {
            System.gc()
        }
        LogCat.log("AndroidModel.getNameFromAssets3 - null")
        return null
    }

    //tools
    //https://github.com/androidtrackers/certified-android-devices/
    private fun getJSON(): String? {
        var reload = false
        try {
            try {
                val file = File(AndroidContext.appContext.cacheDir, "by_brand.json")
                if (file.parentFile?.exists() == false) {
                    file.parentFile?.mkdirs()
                }
                file.also {
                    if (it.exists()) {
                        if (Math.abs(System.currentTimeMillis() - it.lastModified()) >= TimeUnit.DAYS.toMillis(
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
                    AndroidContext.appContext.assets.open("by_brand.json")
                val byteArrayOutputStream = ByteArrayOutputStream()
                NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
                inputStream.close()
                byteArrayOutputStream.close()
                val data = byteArrayOutputStream.toByteArray()
                return String(data, Charset.forName("UTF-8")).also { data ->
                    saveToCache(data)
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
                        SharedPreferenceProvider.getPreferences(DeviceInfoManager.PREF_NAME)
                    if (NetworkApi.hasInternet() && !sharedPreferences.getBoolean(
                            "strictMatch",
                            false
                        )
                    ) {
                        try {
                            val data =
                                LocalizationHelper.fetchFromWeb("https://github.com/androidtrackers/certified-android-devices/blob/master/by_brand.json?raw=true")
                            saveToCache(data ?: return@startOnBackground)
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

    private fun saveToCache(data: String) {
        try {
            val file = File(AndroidContext.appContext.cacheDir, "by_brand.json")
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

    @WorkerThread
    private fun getNameFromDatabase(): List<String?>? {
        val info = DeviceName
            .getDeviceInfo(appContext)
        LogCat.log("AndroidModel.getNameFromDatabase -{ ${info.manufacturer}; ${info.codename}; ${info.name}; ${info.marketName}; ${info.model}; }")
        return if (info != null) {
            val list = mutableListOf<String?>()
            if (info.manufacturer.isNullOrEmpty()) {
                if (info.model != info.codename)
                    list.add(info.model)
                else
                    list.add(info.marketName)
                return list
            } else {
                list.add(
                    getName(
                        if (info.manufacturer?.isNotEmpty() == true) info.manufacturer else brand,
                        getFullName(info.model)
                    )
                )
                list.add(
                    getName(
                        if (info.manufacturer?.isNotEmpty() == true) info.manufacturer else brand,
                        getFullName(info.name)
                    )
                )
            }
            LogCat.log("AndroidModel.getNameFromDatabase2 -{ $list }")
            list
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