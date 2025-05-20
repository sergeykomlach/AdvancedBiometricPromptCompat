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
package dev.skomlach.common.network

import androidx.annotation.WorkerThread
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.translate.LocalizationHelper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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
        if (url.isEmpty()) return false
        url = url.lowercase(Locale.ROOT)
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
                urlConnection.setRequestProperty(
                    "User-Agent", LocalizationHelper.agents[SecureRandom().nextInt(
                        LocalizationHelper.agents.size
                    )]
                )
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
                val html = String(data).ifEmpty {
                    throw IOException("Unable to read data from stream")
                }
                if (!verifyHTML(uri.toString(), html)) {
                    throw IllegalStateException("HTML data do not matched with $host")
                }
                connectionStateListener.setState(true)
                return
            } catch (e: IllegalStateException) {
                LogCat.logException(e)
                connectionStateListener.setState(false)
                return
            } catch (e: Throwable) {
                //UnknownHostException
                //SocketTimeoutException
                if (e.javaClass.name.startsWith("java.net.")) {
                    LogCat.logException(e)
                    connectionStateListener.setState(false)
                    updateConnectionCheckQuery(PingConfig.pingTimeoutSec)
                    return
                }
                LogCat.logException(e)
                //retry
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
        //fallback to the current newtork state; Can be false positive
        connectionStateListener.setState(connectionStateListener.isConnectionDetected())
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
                val rel = m.group(1) ?: continue
                val url = getUrlFromRel(rel)
                if (url != null) {
                    if (checkUrls(originalUrl, url)) {
                        LogCat.log("Ping compare (link):$originalUrl == $url")
                        return true
                    }
                }
            }
            m = patternMeta.matcher(html)
            while (m.find()) {
                val meta = m.group(1) ?: continue
                if (isOriginFromMeta(meta)) return true
                val url = getUrlFromMeta(meta)
                if (url != null) {
                    if (checkUrls(originalUrl, url)) {
                        LogCat.log("Ping compare (meta):$originalUrl == $url")
                        return true
                    }
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

    private fun isOriginFromMeta(meta: String): Boolean {
        val attributes = parseHtmlTagAttributes(meta)
        val relValue = attributes["content"]?.lowercase()
        return relValue == "origin"
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
            val key = matcher.group(1) ?: continue
            val value = matcher.group(2) ?: continue
            attributes[key] = value
        }
        return attributes
    }


    private fun checkUrls(original: String, t: String): Boolean {
        var target: String? = t
        if (target != null && !isWebUrl(target)) {
            target = "https://$target"
        }

        //Some providers show "dummy" page, lets compare with target URL
        return matchesUrl(original, target)
    }

    private fun getScheme(url: String): String {
        try {
            if (url.isEmpty()) return ""
            return if (!isWebUrl(url)) "" else URI(url).scheme
        } catch (e: Exception) {
        }
        return ""
    }

    private fun matchesUrl(link1: String?, link2: String?): Boolean {

        //SpLog.log("matchesUrl: '"+url1 +"' & '"+url2+"'");
        var url1 = link1 ?: ""
        var url2 = link2 ?: ""
        if (url1 == url2) return true
        if (url1.isEmpty()) return false
        if (url2.isEmpty()) return false
        try {
            url1 = url1.lowercase(Locale.ROOT)
            url2 = url2.lowercase(Locale.ROOT)
            val scheme2 = getScheme(url2)
            if (scheme2.isEmpty()) {
                url2 =
                    if (url2.contains("://")) "http" + url2.substring(url2.indexOf("://")) else "http://$url2"
            }
            val scheme1 = getScheme(url1)
            if (scheme1.isEmpty()) {
                url1 =
                    if (url1.contains("://")) "http" + url1.substring(url1.indexOf("://")) else "http://$url1"
            }
            var u1 = URL(url1)
            var u2 = URL(url2)
            if (u1.protocol != u2.protocol) {
                u1 = URL(u2.protocol, u1.host, u1.port, u1.file)
            }

            //in result, www.facebook.com and m.facebook.com  should be both - facebook.com

            if (u1.host.startsWith("m.")) {
                u1 = URL(u1.protocol, u1.host.substring("m.".length), u1.port, u1.file)
            } else if (u1.host.startsWith("www.")) {
                u1 = URL(u1.protocol, u1.host.substring("www.".length), u1.port, u1.file)
            }

            if (u2.host.startsWith("m.")) {
                u2 = URL(u2.protocol, u2.host.substring("m.".length), u2.port, u2.file)
            } else if (u2.host.startsWith("www.")) {
                u2 = URL(u2.protocol, u2.host.substring("www.".length), u2.port, u2.file)
            }

            url1 = u1.toExternalForm()
            url2 = u2.toExternalForm()
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        return url1 == url2
    }
}