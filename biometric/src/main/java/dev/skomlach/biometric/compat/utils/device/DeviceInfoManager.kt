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
import android.os.Looper
import androidx.annotation.WorkerThread
import dev.skomlach.biometric.compat.utils.device.DeviceModel.getNames
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.io.ByteArrayOutputStream
import java.io.File
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
            deviceInfo = loadDeviceInfo(m.first, m.second)
            if (!deviceInfo?.sensors.isNullOrEmpty()) {
                BiometricLoggerImpl.d("DeviceInfoManager: " + deviceInfo?.model + " -> " + deviceInfo)
                setCachedDeviceInfo(deviceInfo?:continue)
                onDeviceInfoListener.onReady(deviceInfo)
                return
            }
        }
        if (names.isNotEmpty()) {
            setCachedDeviceInfo(DeviceInfo(names.toList()[0].first, HashSet<String>()).also {
                onDeviceInfoListener.onReady(it)
            })
            return
        }
        onDeviceInfoListener.onReady(null)
    }

    private val lastUpdated: Long
        get() {
            val apks: MutableSet<String> = HashSet()
            try {
                val applicationInfo = AndroidContext.appContext.packageManager.getApplicationInfo(
                    AndroidContext.appContext.packageName,
                    0
                )
                applicationInfo.sourceDir?.let {
                    apks.add(it)
                }
                applicationInfo.publicSourceDir?.let {
                    apks.add(it)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    applicationInfo.splitSourceDirs?.let {
                        apks.addAll(listOf(*it))
                    }
                    applicationInfo.splitPublicSourceDirs?.let {
                        apks.addAll(listOf(*it))
                    }
                }

            } catch (e: Throwable) {
            }
            var lst: Long = 0
            for (s in apks) {
                if(File(s).exists()) {
                    val t = File(s).lastModified()
                    if (t > lst) {
                        lst = t
                    }
                }
            }
            return lst
        }
    private var cachedDeviceInfo: DeviceInfo? = null
        get() {
            if (field == null) {
                val sharedPreferences = getPreferences("BiometricCompat_DeviceInfo")
                if (sharedPreferences.getBoolean("checked-$lastUpdated", false)) {
                    val model = sharedPreferences.getString("model-$lastUpdated", null) ?: return null
                    val sensors =
                        sharedPreferences.getStringSet("sensors-$lastUpdated", null) ?: HashSet<String>()
                    field = DeviceInfo(model, sensors)
                } else{
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
                .putStringSet("sensors-$lastUpdated", deviceInfo.sensors)
                .putString("model-$lastUpdated", deviceInfo.model)
                .putBoolean("checked-$lastUpdated", true)
                .apply()
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun loadDeviceInfo(modelReadableName: String, model: String): DeviceInfo? {
        BiometricLoggerImpl.d("DeviceInfoManager: loadDeviceInfo for $modelReadableName/$model")
        return if (model.isEmpty()) null else try {
            val url = "https://m.gsmarena.com/res.php3?sSearch=" + URLEncoder.encode(model)
            var html: String? = getHtml(url) ?: return null
            val detailsLink = getDetailsLink(url, html, model)
                ?: return DeviceInfo(modelReadableName, HashSet<String>())

            //not found
            BiometricLoggerImpl.d("DeviceInfoManager: Link: $detailsLink")
            html = getHtml(detailsLink)
            if (html == null) return DeviceInfo(modelReadableName, HashSet<String>())
            val l = getSensorDetails(html)
            BiometricLoggerImpl.d("DeviceInfoManager: Sensors: $l")
            DeviceInfo(modelReadableName, l)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
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
                        val split = name.split(",").toTypedArray()
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
                } else if (firstFound.isNullOrEmpty() && name.contains(model, ignoreCase = true)) {
                    firstFound = NetworkApi.resolveUrl(url, element.attr("href"))
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
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    var inputStream: InputStream? = null
                    inputStream = urlConnection.inputStream
                    if (inputStream == null) inputStream = urlConnection.errorStream
                    NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
                    inputStream.close()
                    val data = byteArrayOutputStream.toByteArray()
                    byteArrayOutputStream.close()
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
            BiometricLoggerImpl.e(e)
        }
        return null
    }

    interface OnDeviceInfoListener {
        fun onReady(deviceInfo: DeviceInfo?)
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