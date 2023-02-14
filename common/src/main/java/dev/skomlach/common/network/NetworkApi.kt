/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
import android.text.TextUtils
import dev.skomlach.common.contextprovider.AndroidContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import javax.net.ssl.HttpsURLConnection

object NetworkApi {
    fun isWebUrl(u: String): Boolean {
        var url = u
        if (TextUtils.isEmpty(url)) return false
        url = url.lowercase(AndroidContext.locale)
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
        return Connection.isConnection
    }

    @Throws(Exception::class)
    fun createConnection(link: String?, timeout: Int): HttpURLConnection {
        val url = URL(link).toURI().normalize().toURL()
        val conn = if (url.protocol.equals(
                "https",
                ignoreCase = true
            )
        ) url.openConnection() as HttpsURLConnection else url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        TrafficStats.setThreadStatsTag(link?.hashCode()?:0)
        return conn
    }

    @Throws(IOException::class)
    fun fastCopy(src: InputStream?, dest: OutputStream?) {
        val inputChannel = Channels.newChannel(src)
        val outputChannel = Channels.newChannel(dest)
        fastCopy(inputChannel, outputChannel)
        inputChannel.close()
        outputChannel.close()
    }

    @Throws(IOException::class)
    fun fastCopy(src: ReadableByteChannel, dest: WritableByteChannel) {
        val buffer = ByteBuffer.allocateDirect(16 * 1024)
        while (src.read(buffer) != -1) {
            buffer.flip()
            dest.write(buffer)
            buffer.compact()
        }
        buffer.flip()
        while (buffer.hasRemaining()) {
            dest.write(buffer)
        }
    }

    fun resolveUrl(baseUrl: String?, relativeUrl: String): String {
        try {
            return URI(baseUrl).resolve(relativeUrl).toString()
        } catch (ignore: Throwable) {
        }
        return relativeUrl
    }
}