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
                LocalizationHelper.agents.randomOrNull()?.let { userAgent ->
                    urlConnection.setRequestProperty(
                        "User-Agent", userAgent
                    )
                }
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
                    inputStream = try {
                        urlConnection.inputStream
                    } catch (e: IOException) {
                        urlConnection.errorStream
                    }

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
                LogCat.logException(e)
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
    private fun verifyHTML(originalUrl: String, htmlContent: String): Boolean {
        try {
            val doc = org.jsoup.Jsoup.parse(htmlContent)
            val head = doc.head()

            // Check <link rel="canonical" href="...">
            val links = head.select("link[rel=canonical], link[rel=alternate], link[rel=shortlink]")
            for (link in links) {
                val href = link.attr("href")
                if (checkUrls(originalUrl, href)) {
                    LogCat.log("Ping compare (link): $originalUrl == $href")
                    return true
                }
            }

            // Check <meta property="og:url" content="...">
            val metas = head.select("meta[property=og:url]")
            for (meta in metas) {
                val content = meta.attr("content")
                if (checkUrls(originalUrl, content)) {
                    LogCat.log("Ping compare (meta): $originalUrl == $content")
                    return true
                }
            }

            // Check origin
            val originMeta = head.select("meta[property=origin], meta[name=origin]")
            for (meta in originMeta) {
                val content = meta.attr("content").lowercase(Locale.ROOT)
                if (content == "origin") {
                    LogCat.log("Ping: origin tag matched")
                    return true
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        return false
    }


    private fun checkUrls(original: String, t: String): Boolean {
        var target: String? = t
        if (target != null && !isWebUrl(target)) {
            target = "https://$target"
        }

        //Some providers show "dummy" page, lets compare with target URL
        return matchesUrl(original, target)
    }

    private fun matchesUrl(link1: String?, link2: String?): Boolean {
        val norm1 = normalizeUrl(link1)
        val norm2 = normalizeUrl(link2)
        return norm1 == norm2
    }

    private fun normalizeUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        return try {
            val uri = URL(if (url.contains("://")) url else "https://$url")
            val host = uri.host.removePrefix("www.").removePrefix("m.")
            URL(uri.protocol, host, uri.port, uri.file).toExternalForm().lowercase(Locale.ROOT)
        } catch (e: Exception) {
            LogCat.logException(e)
            ""
        }
    }
}