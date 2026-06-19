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

import android.net.TrafficStats
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

object NetworkApi {
    fun isWebUrl(u: String): Boolean {
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

    fun hasInternet(): Boolean {
        return Connection.refreshAndGetConnection()
    }

    fun refreshConnectionState(): Boolean {
        return Connection.refreshAndGetConnection()
    }

    fun hasNetworkTransport(): Boolean {
        return Connection.hasNetworkTransport()
    }

    @Throws(Exception::class)
    fun createConnection(link: String?, timeout: Int): HttpURLConnection {
        require(!link.isNullOrBlank()) { "URL is empty" }
        val url = URL(link).toURI().normalize().toURL()
        require(url.protocol.equals("http", ignoreCase = true) ||
            url.protocol.equals("https", ignoreCase = true)
        ) {
            "Only HTTP(S) URLs are supported"
        }
        val conn = if (url.protocol.equals(
                "https",
                ignoreCase = true
            )
        ) url.openConnection() as HttpsURLConnection else url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
        return conn
    }

    @Throws(IOException::class)
    fun fastCopy(src: InputStream, dest: OutputStream) {
        if (src is FileInputStream && dest is FileOutputStream) {
            val inChannel = src.channel
            val outChannel = dest.channel
            val size = inChannel.size()
            var position: Long = 0
            while (position < size) {
                position += inChannel.transferTo(position, size - position, outChannel)
            }
            return
        }
        val size = src.available().takeIf { it > 0 } ?: 0
        val buffer = ByteArray(size.coerceIn(MIN_COPY_BUFFER_SIZE, MAX_COPY_BUFFER_SIZE))
        var bytesRead: Int
        while (src.read(buffer).also { bytesRead = it } != -1) {
            dest.write(buffer, 0, bytesRead)
        }
    }

    fun resolveUrl(baseUrl: String?, relativeUrl: String): String {
        try {
            return URI(baseUrl).resolve(relativeUrl).toString()
        } catch (ignore: Throwable) {
        }
        return relativeUrl
    }

    private const val MIN_COPY_BUFFER_SIZE = 8 * 1024
    private const val MAX_COPY_BUFFER_SIZE = 64 * 1024
}
