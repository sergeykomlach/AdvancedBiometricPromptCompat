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

import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

object DataProviders {

    internal fun extractFileNameFromUrl(urlStr: String?): String {
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

        return sanitizeCacheFileName(fileName, urlStr)
    }

    internal fun sanitizeCacheFileName(rawName: String?, fallbackSeed: String = "cache"): String {
        val lastSegment = rawName
            ?.substringAfterLast("/")
            ?.substringAfterLast("\\")
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.trim('.', '_')
            ?.take(MAX_CACHE_FILE_NAME_LENGTH)
        if (!lastSegment.isNullOrBlank()) return lastSegment
        return "cache_${Integer.toHexString(fallbackSeed.hashCode())}.json"
    }

    private val cacheLoads = HashMap<String, Deferred<Boolean>>()

    fun checkCache(url: String) {
        ExecutorHelper.scope.launch { checkCacheAwaited(url) }
    }

    internal suspend fun checkCacheAwaited(url: String): Boolean {
        val pending = synchronized(cacheLoads) {
            cacheLoads[url] ?: ExecutorHelper.scope.async(start = CoroutineStart.LAZY) {
                refreshCache(url)
            }.also { request ->
                cacheLoads[url] = request
                request.invokeOnCompletion {
                    synchronized(cacheLoads) {
                        if (cacheLoads[url] === request) cacheLoads.remove(url)
                    }
                }
                request.start()
            }
        }
        return pending.await()
    }

    private suspend fun refreshCache(url: String): Boolean {
        val fileName = extractFileNameFromUrl(url)
        try {
            val file = File(AndroidContext.appContext.cacheDir, fileName)
            val fresh = file.exists() && kotlin.math.abs(System.currentTimeMillis() - file.lastModified()) <
                TimeUnit.DAYS.toMillis(DeviceInfoManager.OUTDATE_TIME_DAYS_FILES)
            if (fresh) return true
            // CHECKING is unknown, not a confirmed offline result. Suspend without blocking
            // a worker until the first verdict; no success timestamp is written on a skip.
            if (!DeviceCacheRefresh.awaitInternet(NetworkApi.networkState)) return false
            val data = LocalizationHelper.fetchFromWeb(url) ?: return false
            return saveToCache(data, fileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCat.logException(e)
            return false
        }
    }

    fun getOrCacheJSON(url: String): String? {
        val fileName = extractFileNameFromUrl(url)
        try {
            val file = File(AndroidContext.appContext.cacheDir, fileName)
            if (file.exists()) {
                FileInputStream(file).use { fis ->
                    val out = ByteArrayOutputStream()
                    NetworkApi.fastCopy(fis, out)
                    return String(out.toByteArray(), StandardCharsets.UTF_8)
                }
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
            LogCat.logException(e)
        }
        return null
    }

    private fun saveToCache(data: String, name: String): Boolean {
        try {
            validateJson(data)
            val cacheDir = AndroidContext.appContext.cacheDir
            val file = File(cacheDir, name)
            val canonicalCacheDir = cacheDir.canonicalFile
            val canonicalFile = file.canonicalFile
            if (canonicalFile.parentFile != canonicalCacheDir) {
                throw SecurityException("Unsafe cache file path")
            }
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
                //fos.fd.sync()
            }

            if (!tmpFile.renameTo(file)) {
                tmpFile.delete()
                throw IllegalStateException("Failed to rename ${tmpFile.absolutePath} to ${file.absolutePath}")
            }
            return true
        } catch (e: Throwable) {
            LogCat.logException(e)
            return false
        }
    }

    private fun validateJson(data: String) {
        val trimmed = data.trimStart()
        if (trimmed.startsWith("{")) {
            JSONObject(data)
        } else if (trimmed.startsWith("[")) {
            JSONArray(data)
        } else {
            throw IllegalArgumentException("Unexpected JSON root")
        }
    }

    private const val MAX_CACHE_FILE_NAME_LENGTH = 128

}
