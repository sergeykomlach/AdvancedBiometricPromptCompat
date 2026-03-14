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

import androidx.collection.LruCache
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.translate.LocalizationHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object DataProviders {

    private fun extractFileNameFromUrl(urlStr: String?): String {
        require(!urlStr.isNullOrBlank()) { "URL is empty" }

        val uri = try {
            URI(urlStr)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL: $urlStr", e)
        }

        var fileName: String? = null

        // ---- 1. Try query parameters ----
        uri.query?.let { query ->
            val params = query.split("&").associate { kv ->
                val parts = kv.split("=", limit = 2)
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = if (parts.size > 1)
                    URLDecoder.decode(parts[1], "UTF-8")
                else ""
                key to value
            }

            fileName = params["filename"]
                ?: params["file"]
                        ?: params["name"]
        }

        // ---- 2. Last segment of path ----
        if (fileName.isNullOrBlank()) {
            var path = uri.path ?: ""

            path = path.replace(Regex("/+$"), "")

            if (path.isEmpty()) {
                throw IllegalArgumentException("Could not extract path from URL: $urlStr")
            }

            fileName = path.substringAfterLast("/")
        }

        // ---- 3. Validate ----
        if (fileName.isBlank()) {
            throw IllegalArgumentException("Could not extract file name from URL: $urlStr")
        }

        return fileName
    }

    private val loadingInProgress = LruCache<String, Boolean>(50)
    fun getOrCacheJSON(url: String): String? {
        var reload = false
        val fileName = extractFileNameFromUrl(url)
        try {
            try {
                val file = File(AndroidContext.appContext.cacheDir, fileName)
                if (file.exists()) {
                    file.also {
                        if (kotlin.math.abs(System.currentTimeMillis() - it.lastModified()) >= TimeUnit.DAYS.toMillis(
                                DeviceInfoManager.OUTDATE_TIME_DAYS_MINUS_ONE
                            )
                        ) {
                            reload = true
                        }
                        FileInputStream(it).use { fis ->
                            val out = ByteArrayOutputStream()
                            NetworkApi.fastCopy(fis, out)
                            return String(out.toByteArray(), StandardCharsets.UTF_8)
                        }
                    }
                } else {
                    reload = true
                }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
            try {
                return AndroidContext.appContext.assets.open("devices/$fileName").use { stream ->
                    val out = ByteArrayOutputStream()
                    NetworkApi.fastCopy(stream, out)
                    String(out.toByteArray(), StandardCharsets.UTF_8)
                }
            } catch (e: Throwable) {
                reload = true
                LogCat.logException(e)
            }
            return null
        } finally {
            if (reload && loadingInProgress[url] != true) {
                loadingInProgress.put(url, true)
                ExecutorHelper.startOnBackground {
                    if (NetworkApi.hasInternet()) {
                        try {
                            val data =
                                LocalizationHelper.fetchFromWeb(url)
                            saveToCache(data ?: return@startOnBackground, fileName)
                        } catch (e: Throwable) {
                            LogCat.logException(e)
                        } finally {
                            loadingInProgress.put(url, false)
                        }
                    } else
                        loadingInProgress.put(url, false)
                }
            }
        }
    }

    private fun saveToCache(data: String, name: String) {
        try {
            val cacheDir = AndroidContext.appContext.cacheDir
            val file = File(cacheDir, name)
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            val tmpFile = File(file.absolutePath + ".tmp")

            FileOutputStream(tmpFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).buffered(512 * 1024).use { writer ->
                    writer.write(data)
                    writer.flush()
                }
                fos.fd.sync()
            }

            if (!tmpFile.renameTo(file)) {
                tmpFile.delete()
                throw IllegalStateException("Failed to rename ${tmpFile.absolutePath} to ${file.absolutePath}")
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

}