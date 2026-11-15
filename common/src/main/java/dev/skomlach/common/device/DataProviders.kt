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

import android.util.JsonReader
import android.util.JsonToken
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.network.LibraryNetworkPolicy
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.translate.BoundedWebResponse
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.StringReader
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
        if (!LibraryNetworkPolicy.deviceDatasetRefreshEnabled) return false
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
            val file = cacheFile(fileName)
            val fresh = file.isFile && kotlin.math.abs(System.currentTimeMillis() - file.lastModified()) <
                TimeUnit.DAYS.toMillis(DeviceInfoManager.OUTDATE_TIME_DAYS_FILES)
            if (fresh && readCache(fileName) != null) return true
            // CHECKING is unknown, not a confirmed offline result. Suspend without blocking
            // a worker until the first verdict; no success timestamp is written on a skip.
            if (!DeviceCacheRefresh.awaitInternet(NetworkApi.networkState)) return false
            val data = LocalizationHelper.fetchFromWeb(url) ?: return false
            return saveToCache(data, fileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCat.logError("Device dataset refresh failed: ${e.javaClass.simpleName}")
            return false
        }
    }

    fun getOrCacheJSON(url: String): String? = findInCacheOrAsset(url) { it }

    internal fun <T> findInCacheOrAsset(
        url: String,
        acceptCached: (T) -> Boolean = { true },
        find: (String) -> T?
    ): T? {
        val fileName = extractFileNameFromUrl(url)
        readCache(fileName)?.let { cached ->
            try {
                find(cached)?.takeIf(acceptCached)?.let { return it }
            } catch (error: Exception) {
                LogCat.logError("Device dataset cache lookup failed: ${error.javaClass.simpleName}")
            }
        }
        try {
            return AndroidContext.appContext.assets.open("devices/$fileName").use { stream ->
                BoundedWebResponse.readUtf8(stream)?.let(find)
            }
        } catch (error: Exception) {
            LogCat.logError("Bundled device dataset lookup failed: ${error.javaClass.simpleName}")
        }
        return null
    }

    private fun readCache(name: String): String? {
        try {
            val file = cacheFile(name)
            if (!file.isFile) return null
            val data = file.inputStream().use(BoundedWebResponse::readUtf8) ?: return null
            return data.takeIf { isUsableCatalogue(name, it) }
        } catch (error: Exception) {
            LogCat.logError("Device dataset cache read failed: ${error.javaClass.simpleName}")
        }
        return null
    }

    private fun cacheFile(name: String): File {
        val directory = AndroidContext.appContext.cacheDir.canonicalFile
        val file = File(directory, name)
        check(file.canonicalFile == file) { "Unsafe device dataset cache path" }
        return file
    }

    private fun saveToCache(data: String, name: String): Boolean {
        if (!isUsableCatalogue(name, data)) return false
        return try {
            val file = cacheFile(name)
            val temporary = File.createTempFile("device-dataset-", ".tmp", file.parentFile)
            try {
                temporary.bufferedWriter(StandardCharsets.UTF_8).use { it.write(data) }
                temporary.renameTo(file)
            } finally {
                temporary.delete()
            }
        } catch (e: Exception) {
            // JSON exceptions may contain the downloaded response, not just an error code.
            LogCat.logError("Device dataset cache write failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private const val MAX_CACHE_FILE_NAME_LENGTH = 128
}

internal fun isUsableCatalogue(name: String, data: String): Boolean = try {
    JsonReader(StringReader(data)).use { reader ->
        val usable = when (name) {
            "by_model.json" -> {
                var found = false
                reader.beginObject()
                while (reader.hasNext()) {
                    val model = reader.nextName()
                    val hasName = reader.hasNamedDevice("name")
                    found = found || (model.isNotBlank() && hasName)
                }
                reader.endObject()
                found
            }
            "devices.json" -> reader.hasNamedDevice("name", requireBrand = true, sensorSection = { it == "specs" })
            "specifications.json" -> reader.hasNamedDevice("phone_name", sensorSection = { it != "_metadata" })
            else -> {
                // The public cache also accepts other JSON files supplied by consuming apps.
                val root = reader.peek()
                reader.skipValue()
                root == JsonToken.BEGIN_ARRAY || root == JsonToken.BEGIN_OBJECT
            }
        }
        usable && reader.peek() == JsonToken.END_DOCUMENT
    }
} catch (_: Exception) {
    false
}

private fun JsonReader.hasNamedDevice(
    nameField: String,
    requireBrand: Boolean = false,
    sensorSection: ((String) -> Boolean)? = null
): Boolean {
    var found = false
    beginArray()
    while (hasNext()) {
        var hasName = false
        var hasBrand = false
        var hasSensors = sensorSection == null
        beginObject()
        while (hasNext()) {
            val field = nextName()
            when {
                field == nameField && peek() == JsonToken.STRING -> hasName = nextString().isNotBlank()
                requireBrand && field == "brand" && peek() == JsonToken.STRING -> hasBrand = nextString().isNotBlank()
                sensorSection?.invoke(field) == true && peek() == JsonToken.BEGIN_OBJECT -> {
                    beginObject()
                    while (hasNext()) {
                        val key = nextName()
                        if (key.equals("sensors", ignoreCase = true) && peek() == JsonToken.STRING) {
                            val sensors = nextString()
                            hasSensors = hasSensors || (sensors.isNotBlank() && !sensors.equals("Not found", ignoreCase = true))
                        } else {
                            skipValue()
                        }
                    }
                    endObject()
                }
                else -> skipValue()
            }
        }
        endObject()
        found = found || (hasName && hasSensors && (!requireBrand || hasBrand))
    }
    endArray()
    return found
}
