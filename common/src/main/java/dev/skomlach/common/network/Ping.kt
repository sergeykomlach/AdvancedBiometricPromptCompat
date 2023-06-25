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
package dev.skomlach.common.network

import android.text.TextUtils
import androidx.annotation.WorkerThread
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.contextprovider.AndroidContext.locale
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Deprecated("This functional no longer used in Biometric-Common library and will be removed soon")
internal class Ping(private val connectionStateListener: ConnectionStateListener) {
    private val patternMeta =
        Pattern.compile("<meta(.*?)>") //compile RegEx to extract all <meta/> tags
    private val patternLink =
        Pattern.compile("<link(.*?)>") //compile RegEx to extract all <link/> tags
    private val patternTagAttributes =
        Pattern.compile("(\\w*?)=\"(.*?)\"") //compile RegEx to find all attributes

    private var job: Runnable? = null
    fun cancelConnectionCheckQuery() {
        job?.let {
            ExecutorHelper.removeCallbacks(it)
        }
        job = null
    }

    fun updateConnectionCheckQuery(delaySeconds: Long) {
        cancelConnectionCheckQuery()
        job = Runnable {
            ExecutorHelper.startOnBackground { startPing() }
        }
        job?.let {
            if (delaySeconds > 0)
                ExecutorHelper.postDelayed(
                    it,
                    TimeUnit.SECONDS.toMillis(delaySeconds)
                )
            else
                ExecutorHelper.post(it)
        }
    }

    private fun isWebUrl(u: String): Boolean {
        var url = u
        if (TextUtils.isEmpty(url)) return false
        url = url.lowercase(locale)
        //Fix java.lang.RuntimeException: utext_close failed: U_REGEX_STACK_OVERFLOW
        val slash = url.indexOf("/")
        if (slash > 0 && slash < url.indexOf("?")) {
            url = url.substring(0, url.indexOf("?"))
        }
        return (url.startsWith("http://") || url.startsWith("https://")) && android.util.Patterns.WEB_URL.matcher(
            url
        ).matches()
    }

    @WorkerThread
    private fun startPing() {

        if (!connectionStateListener.isConnectionDetected()) {
            connectionStateListener.setState(false)
            return
        }
        for (host in PingConfig.hostsList) {
            var urlConnection: HttpURLConnection? = null
            try {
                val uri = URI("https://$host")
                urlConnection = NetworkApi.createConnection(
                    uri.toString(),
                    TimeUnit.SECONDS.toMillis(PingConfig.pingTimeoutSec).toInt()
                )
                urlConnection.instanceFollowRedirects = true
                urlConnection.requestMethod = "GET"
                urlConnection.connect()
                val responseCode = urlConnection.responseCode
                val byteArrayOutputStream = ByteArrayOutputStream()
                var inputStream: InputStream
                LogCat.log("ping: $responseCode=${urlConnection.responseMessage}")
                //if any 2XX response code
                if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                    inputStream = urlConnection.inputStream
                } else {
                    //Redirect happen
                    if (responseCode >= HttpURLConnection.HTTP_MULT_CHOICE && responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
                        var target = urlConnection.getHeaderField("Location")
                        if (target != null && !isWebUrl(target)) {
                            target = "https://$target"
                        }
                        //Some providers show "dummy" page, lets compare with target URL
                        if (target != null && !matchesUrl(uri.toString(), target)) {
                            throw IOException("Unable to connect to $host")
                        }
                    }
                    inputStream = urlConnection.inputStream ?: urlConnection.errorStream
                }
                NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
                inputStream.close()
                val data = byteArrayOutputStream.toByteArray()
                byteArrayOutputStream.close()
                urlConnection.disconnect()
                val html = String(data)
                if (!verifyHTML(uri.toString(), html)) {
                    throw IOException("Unable to connect to $host")
                }
                connectionStateListener.setState(true)
                return
            } catch (e: Throwable) {

            } finally {
                if (urlConnection != null) {
                    try {
                        urlConnection.disconnect()
                        urlConnection = null
                    } catch (ignore: Throwable) {
                    }
                }
            }
        }
        connectionStateListener.setState(false)
    }

    @Throws(Exception::class)
    private fun verifyHTML(originalUrl: String, s: String): Boolean {
        var html = s
        val start = html.lowercase(Locale.ROOT).indexOf("<head>")
        val end = html.lowercase(Locale.ROOT).indexOf("</head>")
        if (start != -1 && end != -1) {
            html = html.substring(start + "<head>".length, end)
            //verify URL in HTML body
            var m = patternLink.matcher(html)
            while (m.find()) {
                val rel = m.group(1)
                val url = getUrlFromRel(rel)
                if (url != null) {
                    checkUrls(originalUrl, url)
                    LogCat.log("Ping compare (link):$originalUrl == $url")
                    return true
                }
            }
            m = patternMeta.matcher(html)
            while (m.find()) {
                val meta = m.group(1)
                val url = getUrlFromMeta(meta)
                if (url != null) {
                    checkUrls(originalUrl, url)
                    LogCat.log("Ping compare (meta):$originalUrl == $url")
                    return true
                }
            }
        }
        return false
    }

    private fun getUrlFromMeta(meta: String): String? {
        val attributes = parseHtmlTagAttributes(meta)
        val relValue = attributes["property"]
        return if ("og:url".equals(relValue, ignoreCase = true)) attributes["content"] else null
    }

    private fun getUrlFromRel(rel: String): String? {
        val attributes = parseHtmlTagAttributes(rel)
        val relValue = attributes["rel"]
        return if ("canonical".equals(relValue, ignoreCase = true)
            || "alternate".equals(relValue, ignoreCase = true)
            || "shortlink".equals(relValue, ignoreCase = true)
        ) attributes["href"] else null
    }

    private fun parseHtmlTagAttributes(tag: String): Map<String, String> {
        val matcher = patternTagAttributes.matcher(tag)
        val attributes: MutableMap<String, String> = HashMap()
        while (matcher.find()) {
            val key = matcher.group(1)
            val value = matcher.group(2)
            attributes[key] = value
        }
        return attributes
    }

    @Throws(Exception::class)
    private fun checkUrls(original: String, t: String) {
        var target: String? = t
        if (target != null && !isWebUrl(target)) {
            target = "https://$target"
        }

        //Some providers show "dummy" page, lets compare with target URL
        if (!matchesUrl(original, target)) {
            throw IOException("Unable to connect to $original")
        }
    }

    private fun getScheme(url: String): String {
        try {
            if (TextUtils.isEmpty(url)) return ""
            return if (!isWebUrl(url)) "" else URI(url).scheme
        } catch (e: Exception) {
        }
        return ""
    }

    private fun matchesUrl(link1: String?, link2: String?): Boolean {

        //SpLog.log("matchesUrl: '"+url1 +"' & '"+url2+"'");
        var url1 = link1 ?: ""
        var url2 = link2 ?: ""
        if (TextUtils.isEmpty(url1)) return false
        if (TextUtils.isEmpty(url2)) return false
        try {
            url1 = url1.lowercase(Locale.ROOT)
            url2 = url2.lowercase(Locale.ROOT)
            val schm2 = getScheme(url2)
            if (TextUtils.isEmpty(schm2)) {
                url2 =
                    if (url2.contains("://")) "http" + url2.substring(url2.indexOf("://")) else "http://$url2"
            }
            val schm1 = getScheme(url1)
            if (TextUtils.isEmpty(schm1)) {
                url1 =
                    if (url1.contains("://")) "http" + url1.substring(url1.indexOf("://")) else "http://$url1"
            }
            var u1 = URL(url1)
            var u2 = URL(url2)
            if (u1.protocol != u2.protocol) {
                u1 = URL(u2.protocol, u1.host, u1.port, u1.file)
            }
            val isMobile = u1.host.startsWith("m.") || u2.host.startsWith("m.")
            val isWww = u1.host.startsWith("www.") || u2.host.startsWith("www.")

            //in result, www.facebook.com and m.facebook.com  should be both - facebook.com
            if (isMobile) {
                if (u1.host.startsWith("m.")) {
                    u1 = URL(u1.protocol, u1.host.substring("m.".length), u1.port, u1.file)
                } else if (u2.host.startsWith("m.")) {
                    u2 = URL(u2.protocol, u2.host.substring("m.".length), u2.port, u2.file)
                }
            }
            if (isWww) {
                if (u1.host.startsWith("www.")) {
                    u1 = URL(u1.protocol, u1.host.substring("www.".length), u1.port, u1.file)
                } else if (u2.host.startsWith("www.")) {
                    u2 = URL(u2.protocol, u2.host.substring("www.".length), u2.port, u2.file)
                }
            }
            url1 = u1.toExternalForm()
            url2 = u2.toExternalForm()
        } catch (e: Throwable) {
        }
        return url1 == url2
    }
}