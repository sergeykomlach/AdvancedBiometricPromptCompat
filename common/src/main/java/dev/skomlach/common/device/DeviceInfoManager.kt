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

import android.os.Looper
import androidx.annotation.WorkerThread
import dev.skomlach.common.device.DeviceModel.getNames
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.LastUpdatedTs

import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLHandshakeException

object DeviceInfoManager {
    val agents = arrayOf(
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/602.2.14 (KHTML, like Gecko) Version/10.0.1 Safari/602.2.14",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:50.0) Gecko/20100101 Firefox/50.0"
    )

    private val pattern = Pattern.compile("\\((.*?)\\)+")
    fun hasFingerprint(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains("fingerprint")) {
                return true
            }
        }
        return false
    }

    fun hasUnderDisplayFingerprint(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains("fingerprint") && s.contains("under display")) {
                return true
            }
        }
        return false
    }

    fun hasIrisScanner(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                    " unlock"
                ) || s.contains(
                    " auth"
                )
            ) {
                if (s.contains("iris")) {
                    return true
                }
            }
        }
        return false
    }

    fun hasFaceID(deviceInfo: DeviceInfo?): Boolean {
        if (deviceInfo?.sensors == null) return false
        for (str in deviceInfo.sensors) {
            val s = str.lowercase(Locale.ROOT)
            if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                    " unlock"
                ) || s.contains(
                    " auth"
                )
            ) {
                if (s.contains("face")) {
                    return true
                }
            }
        }
        return false
    }

    @WorkerThread
    fun getDeviceInfo(onDeviceInfoListener: OnDeviceInfoListener) {
        if (Looper.getMainLooper().thread === Thread.currentThread()) throw IllegalThreadStateException(
            "Worker thread required"
        )
        var deviceInfo = cachedDeviceInfo
        if (deviceInfo != null) {
            onDeviceInfoListener.onReady(deviceInfo)
            return
        }
        val names = getNames()
        for (m in names) {
            val first = m.first
            val secondArray = splitString(m.second, " ")
            for (i in secondArray.indices - 1) {
                val limit = secondArray.size - i
                if (limit < 2)//Device should have at least brand + model
                    break
                val second = join(secondArray, " ", limit)
                deviceInfo = loadDeviceInfo(first, second)
                if (!deviceInfo?.sensors.isNullOrEmpty()) {
                    LogCat.log("DeviceInfoManager: " + deviceInfo?.model + " -> " + deviceInfo)
                    setCachedDeviceInfo(deviceInfo ?: continue)
                    onDeviceInfoListener.onReady(deviceInfo)
                    return
                } else {
                    LogCat.log("DeviceInfoManager: no data for $first/$second")
                }
            }
        }
        if (names.isNotEmpty()) {
            onDeviceInfoListener.onReady(
                DeviceInfo(
                    names.toList()[0].first,
                    HashSet<String>()
                ).also {
                    LogCat.log("DeviceInfoManager: (fallback) " + it.model + " -> " + it)
                    cachedDeviceInfo = it
                })
            return
        }
        LogCat.log("DeviceInfoManager: (null) null -> null")
        cachedDeviceInfo = null
        onDeviceInfoListener.onReady(null)
    }


    private var cachedDeviceInfo: DeviceInfo? = null
        get() {
            if (field == null) {
                val sharedPreferences = getPreferences("BiometricCompat_DeviceInfo")
                if (sharedPreferences.getBoolean("checked-${LastUpdatedTs.timestamp}", false)) {
                    val model =
                        sharedPreferences.getString("model-${LastUpdatedTs.timestamp}", null)
                            ?: return null
                    val sensors =
                        sharedPreferences.getStringSet("sensors-${LastUpdatedTs.timestamp}", null)
                            ?: HashSet<String>()
                    field = DeviceInfo(model, sensors)
                } else {
                    sharedPreferences.edit().clear().commit()
                }
            }
            return field
        }


    private fun setCachedDeviceInfo(deviceInfo: DeviceInfo) {
        cachedDeviceInfo = deviceInfo
        try {
            val sharedPreferences = getPreferences("BiometricCompat_DeviceInfo")
                .edit()
            sharedPreferences.clear().commit()
            sharedPreferences
                .putStringSet("sensors-${LastUpdatedTs.timestamp}", deviceInfo.sensors)
                .putString("model-${LastUpdatedTs.timestamp}", deviceInfo.model)
                .putBoolean("checked-${LastUpdatedTs.timestamp}", true)
                .apply()
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    private fun loadDeviceInfo(modelReadableName: String, model: String): DeviceInfo? {
        LogCat.log("DeviceInfoManager: loadDeviceInfo for $modelReadableName/$model")
        return if (model.isEmpty()) null else try {
            val url = "https://m.gsmarena.com/res.php3?sSearch=" + URLEncoder.encode(model) +"&tn="+getTn()
            LogCat.log("DeviceInfoManager: SearchUrl: $url")
            var html: String? = getHtml(url) ?: return null
            LogCat.log("DeviceInfoManager: html loaded, start parsing")
            val detailsLink = getDetailsLink(url, html, model)
                ?: return DeviceInfo(modelReadableName, HashSet<String>())

            //not found
            LogCat.log("DeviceInfoManager: Link: $detailsLink")
            html = getHtml(detailsLink) ?: return DeviceInfo(modelReadableName, HashSet<String>())
            LogCat.log("DeviceInfoManager: html loaded, start parsing")
            val l = getSensorDetails(html)
            LogCat.log("DeviceInfoManager: Sensors: $l")
            val m = try {
                Jsoup.parse(html).body().getElementById("content")
                    ?.getElementsByClass("section nobor")?.text() ?: modelReadableName
            } catch (ignore: Throwable) {
                modelReadableName
            }
            LogCat.log("DeviceInfoManager: Model: $m Sensors: $l")
            DeviceInfo(m, l)
        } catch (e: Throwable) {
            LogCat.logException(e)
            null
        }
    }

    //parser
    private fun getSensorDetails(html: String?): Set<String> {
        val list: MutableSet<String> = HashSet()
        html?.let {
            val doc = Jsoup.parse(html)
            val body = doc.body().getElementById("content")
            val rElements = body?.getElementsByAttribute("data-spec") ?: Elements()
            for (i in rElements.indices) {
                val element = rElements[i]
                if (element.attr("data-spec") == "sensors") {
                    var name = element.text()
                    if (!name.isNullOrEmpty()) {
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
                }
            }
        }
        return list
    }

    private fun getDetailsLink(url: String, html: String?, model: String): String? {
        html?.let {
            var firstFound: String? = null
            val doc = Jsoup.parse(html)
            val body = doc.body().getElementById("content")
            val rElements = body?.getElementsByTag("a") ?: Elements()
            for (i in rElements.indices) {
                val element = rElements[i]
                val name = element.text()
                if (name.isNullOrEmpty()) {
                    continue
                }
                if (name.equals(model, ignoreCase = true)) {
                    return NetworkApi.resolveUrl(url, element.attr("href"))
                } else if (firstFound.isNullOrEmpty()) {
                    if (name.contains(model, ignoreCase = true))
                        firstFound = NetworkApi.resolveUrl(url, element.attr("href"))
                    else {
                        val arr = splitString(model, " ")
                        var i = arr.size
                        for (s in arr) {
                            if (i < 2) //Device should have at least brand + model
                                break
                            val shortName = join(arr, " ", i)
                            if (name.contains(shortName, ignoreCase = true)) {
                                firstFound = NetworkApi.resolveUrl(url, element.attr("href"))
                            }
                            i--
                        }

                    }
                }
            }
            return firstFound
        }
        return null
    }

    //tools
    private fun getHtml(url: String): String? {
        try {
            var urlConnection: HttpURLConnection? = null
            if (NetworkApi.hasInternet()) {
                return try {
                    urlConnection = NetworkApi.createConnection(
                        url, TimeUnit.SECONDS.toMillis(30)
                            .toInt()
                    )
                    urlConnection.requestMethod = "GET"
                    urlConnection.setRequestProperty("Content-Language", "en-US")
                    urlConnection.setRequestProperty("Accept-Language", "en-US")
                    urlConnection.setRequestProperty(
                        "User-Agent",
                        agents[SecureRandom().nextInt(agents.size)]
                    )
                    urlConnection.connect()
                    val responseCode = urlConnection.responseCode
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val inputStream: InputStream
                    LogCat.log("getHtml: $responseCode=${urlConnection.responseMessage}")
                    //if any 2XX response code
                    if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                        inputStream = urlConnection.inputStream
                    } else {
                        //Redirect happen
                        if (responseCode >= HttpURLConnection.HTTP_MULT_CHOICE && responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
                            var target = urlConnection.getHeaderField("Location")
                            if (target != null && !NetworkApi.isWebUrl(target)) {
                                target = "https://$target"
                            }
                            return getHtml(target)
                        }
                        inputStream = urlConnection.inputStream ?: urlConnection.errorStream
                    }
                    NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
                    inputStream.close()
                    val data = byteArrayOutputStream.toByteArray()
                    byteArrayOutputStream.close()
                    urlConnection.disconnect()
                    String(data, Charset.forName("UTF-8"))
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect()
                        urlConnection = null
                    }
                }
            }
        } catch (e: Throwable) {
            //ignore - old device cannt resolve SSL connection
            if (e is SSLHandshakeException) {
                return "<html></html>"
            }
            LogCat.logException(e)
        }
        return null
    }

    private fun getTn():String?{
        val url = "https://m.gsmarena.com"
        LogCat.log("DeviceInfoManager: getTnValue: $url")
        val html =  getHtml(url) ?: return null

        return getTnValue(html)
    }
    private fun getTnValue(html: String?): String? {
        html?.let {
            val doc = Jsoup.parse(html)
            val body = doc.body()
            val rElements = body.getElementsByTag("input") ?: Elements()
            for (i in rElements.indices) {
                val element = rElements[i]
                val name = element.attr("name")
                if (name.isNullOrEmpty()) {
                    continue
                }
                if (name.equals("tn", ignoreCase = true)) {
                    return element.`val`()
                }
            }

        }
        return null
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

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            Character.toUpperCase(first).toString() + s.substring(1)
        }
    }
}