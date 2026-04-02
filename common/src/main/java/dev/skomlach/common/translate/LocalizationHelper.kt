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

package dev.skomlach.common.translate

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.storage.SharedPreferenceProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

object LocalizationHelper {
    val agents = arrayOf(
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    )

    private const val PREF_NAME = "LocalizationHelperV3"
    private const val AUTHOR_LOCALE_TAG = "en-US"

    @Volatile
    private var lastKnownAppLocaleTag: String = AndroidContext.appLocale.toLanguageTag()

    private val localizedContextCache = ConcurrentHashMap<String, Context>()
    private val resourceStringCache = ConcurrentHashMap<String, String>()
    private val resourceTranslationAvailabilityCache = ConcurrentHashMap<String, Boolean>()

    private val diskTranslationsCache =
        ConcurrentHashMap<String, MutableMap<String, String>>()

    private val translationMemoryCache = ConcurrentHashMap<String, String>()

    private fun ensureLocaleState() {
        val currentTag = AndroidContext.appLocale.toLanguageTag()
        if (currentTag != lastKnownAppLocaleTag) {
            synchronized(this) {
                val latestTag = AndroidContext.appLocale.toLanguageTag()
                if (latestTag != lastKnownAppLocaleTag) {
                    lastKnownAppLocaleTag = latestTag
                    clearCaches()
                }
            }
        }
    }

    fun clearCaches() {
        localizedContextCache.clear()
        resourceStringCache.clear()
        resourceTranslationAvailabilityCache.clear()
        translationMemoryCache.clear()
    }

    private fun translationPairKey(fromLang: Locale, toLang: Locale): String {
        return fromLang.language + ">>" + toLang.language
    }

    private fun translationEntryKey(fromLang: Locale, toLang: Locale, text: String): String {
        return buildString(text.length + 32) {
            append(fromLang.language)
            append(">>")
            append(toLang.language)
            append("::")
            append(text)
        }
    }

    private fun resourceKey(
        resId: Int,
        locale: Locale,
        formatArgs: Array<out Any?> = emptyArray()
    ): String {
        return buildString {
            append(resId)
            append('|')
            append(locale.toLanguageTag())
            if (formatArgs.isNotEmpty()) {
                append('|')
                append(formatArgs.contentDeepHashCode())
            }
        }
    }

    private fun translationAvailabilityKey(
        resId: Int,
        locale: Locale,
        formatArgs: Array<out Any?> = emptyArray()
    ): String {
        return buildString {
            append(resId)
            append('|')
            append(locale.toLanguageTag())
            append('|')
            append(formatArgs.contentDeepHashCode())
        }
    }

    fun fetchFromWeb(url: String): String? {
        return fetchFromWebWithRedirects(url, 0)
    }

    private fun fetchFromWebWithRedirects(
        url: String,
        redirectCount: Int,
        retryCount: Int = 0
    ): String? {
        if (redirectCount > 5) {
            LogCat.logError("Too many redirects for URL: $url")
            return null
        }
        val maxRetries = 3
        var urlConnection: HttpURLConnection? = null
        try {
            urlConnection = NetworkApi.createConnection(url, 5000)
            urlConnection.requestMethod = "GET"
            urlConnection.instanceFollowRedirects = true

            urlConnection.setRequestProperty(
                "User-Agent",
                agents[SecureRandom().nextInt(agents.size)]
            )
            urlConnection.connect()

            val responseCode = urlConnection.responseCode
            if (responseCode == 429 && retryCount < maxRetries) {
                val retryAfter = urlConnection.getHeaderField("Retry-After")?.toLongOrNull() ?: 0L
                val delay = if (retryAfter > 0) {
                    retryAfter * 1000
                } else {
                    2.0.pow(retryCount.toDouble()).toLong() * 1000
                }

                LogCat.log("LocalizationHelper: 429 Too Many Requests. Retrying in $delay ms... (Attempt ${retryCount + 1})")

                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    return null
                }
                return fetchFromWebWithRedirects(url, redirectCount, retryCount + 1)
            } else if (
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 ||
                responseCode == 308
            ) {
                val location = urlConnection.getHeaderField("Location") ?: return null

                val target = when {
                    location.startsWith("//") -> "${urlConnection.url.protocol}:$location"
                    NetworkApi.isWebUrl(location) -> location
                    else -> NetworkApi.resolveUrl(urlConnection.url.toString(), location)
                }

                LogCat.log("Redirecting to: $target")
                urlConnection.disconnect()
                return fetchFromWebWithRedirects(target, redirectCount + 1)
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                urlConnection.inputStream.use { inputStream ->
                    ByteArrayOutputStream().use { result ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (inputStream.read(buffer).also { length = it } != -1) {
                            result.write(buffer, 0, length)
                        }
                        return result.toString("UTF-8")
                    }
                }
            } else {
                val errorMsg = urlConnection.errorStream?.bufferedReader()?.use { it.readText() }
                LogCat.logError("Server returned code: $responseCode for URL: $url. Error: $errorMsg")
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        } finally {
            urlConnection?.disconnect()
        }
        return null
    }

    fun prefetch(context: Context, vararg formatArgs: Any?) {
        ensureLocaleState()
        try {
            formatArgs.toList().forEach { resId ->
                val name = try {
                    context.resources.getResourceEntryName(resId as Int)
                } catch (_: Exception) {
                    resId.toString()
                }

                try {
                    val translated = getTranslatedStringFromResources(context, resId as Int)
                    if (translated.isNullOrEmpty()) {
                        val source = getStringForLocaleCached(context, resId, Locale.US)
                        invoke(source, Locale.US, AndroidContext.appLocale)
                    }
                } catch (e: Throwable) {
                    LogCat.logException(e, "LocalizationHelper $name")
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
        }
    }

    fun getLocalizedString(context: Context, @StringRes resId: Int): String {
        ensureLocaleState()
        return try {
            getTranslatedStringFromResources(context, resId)?.let {
                return it
            }
            val source = getStringForLocaleCached(context, resId, Locale.US)
            getLocalizedString(source)
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
            context.getString(resId)
        }
    }

    fun getLocalizedString(
        context: Context,
        @StringRes resId: Int,
        vararg formatArgs: Any?
    ): String {
        ensureLocaleState()
        return try {
            getTranslatedStringFromResources(context, resId, *formatArgs)?.let {
                return it
            }
            val source = getStringForLocaleCached(context, resId, Locale.US, *formatArgs)
            getLocalizedString(source, *formatArgs)
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
            context.getString(resId, *formatArgs)
        }
    }

    private fun getLocalizedString(str: String): String {
        ensureLocaleState()
        return read(Locale.US, AndroidContext.appLocale, str) ?: str
    }

    private fun getLocalizedString(raw: String, vararg formatArgs: Any?): String {
        ensureLocaleState()
        return try {
            String.format(
                read(Locale.US, AndroidContext.appLocale, raw) ?: raw,
                *formatArgs
            )
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
            raw
        }
    }

    private fun invoke(
        text: String,
        fromLang: Locale,
        toLang: Locale,
        listener: TranslateResult? = null
    ) {
        ExecutorHelper.startOnBackground {
            ensureLocaleState()
            val str = read(fromLang, toLang, text) ?: translate(text, fromLang, toLang).also {
                if (!it.isNullOrEmpty() && it != text) {
                    store(fromLang, toLang, text, it)
                }
            }
            ExecutorHelper.post {
                listener?.onResult(str)
            }
        }
    }

    private fun read(
        fromLang: Locale,
        toLang: Locale,
        text: String
    ): String? {
        if (fromLang.language == toLang.language) return text

        val memKey = translationEntryKey(fromLang, toLang, text)
        if (translationMemoryCache.containsKey(memKey)) {
            return translationMemoryCache[memKey]
        }

        val pairKey = translationPairKey(fromLang, toLang)
        val translations = getOrLoadTranslationsMap(pairKey)

        val value = translations[text]?.trim()?.ifEmpty { null }
        translationMemoryCache[memKey] = value?:return null
        return value
    }

    private fun getOrLoadTranslationsMap(pairKey: String): MutableMap<String, String> {
        return diskTranslationsCache.getOrPut(pairKey) {
            val pref = SharedPreferenceProvider.getPreferences(PREF_NAME)
            val set = pref.getStringSet(pairKey, emptySet()).orEmpty()

            val result = HashMap<String, String>(set.size * 2)
            for (item in set) {
                try {
                    val json = JSONObject(item)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        result[key] = json.optString(key)
                    }
                } catch (e: Throwable) {
                    LogCat.logException(e, "LocalizationHelper")
                }
            }
            result
        }
    }

    private fun store(
        fromLang: Locale,
        toLang: Locale,
        text: String,
        result: String
    ) {
        if (fromLang.language == toLang.language) return
        if (text.trim().isEmpty() || result.trim().isEmpty() || text == result) return

        val pairKey = translationPairKey(fromLang, toLang)

        synchronized(pairKey.intern()) {
            val map = getOrLoadTranslationsMap(pairKey)
            val oldValue = map[text]
            if (oldValue == result) {
                translationMemoryCache[translationEntryKey(fromLang, toLang, text)] = result
                return
            }

            map[text] = result
            translationMemoryCache[translationEntryKey(fromLang, toLang, text)] = result

            try {
                val pref = SharedPreferenceProvider.getPreferences(PREF_NAME)
                val set = HashSet<String>(pref.getStringSet(pairKey, emptySet()) ?: emptySet())

                var existingJsonToRemove: String? = null
                for (item in set) {
                    try {
                        val json = JSONObject(item)
                        if (json.has(text)) {
                            existingJsonToRemove = item
                            break
                        }
                    } catch (_: Throwable) {
                    }
                }
                if (existingJsonToRemove != null) {
                    set.remove(existingJsonToRemove)
                }

                set.add(JSONObject().apply {
                    put(text, result)
                }.toString())

                pref.edit().putStringSet(pairKey, set).apply()
            } catch (e: Throwable) {
                LogCat.logException(e, "LocalizationHelper")
            }
        }
    }

    private fun translate(text: String, fromLang: Locale, toLang: Locale): String {
        try {
            val parts = text.split(".")
            var sb = StringBuilder()

            parts.forEach {
                translateUseGoogleApi(it, fromLang, toLang)?.let { s ->
                    if (s.isNotEmpty()) {
                        sb.append(s).append(". ")
                    }
                }
            }

            var result = sb.toString().trim()
            if (!text.endsWith(".") && result.endsWith(".")) {
                result = result.substring(0, result.length - 1)
            }
            if (result.isNotEmpty()) return result

            sb = StringBuilder()
            parts.forEach {
                translateUseFallbackApi(it, fromLang, toLang)?.let { s ->
                    if (s.isNotEmpty()) {
                        sb.append(s).append(". ")
                    }
                }
            }

            result = sb.toString().trim()
            if (!text.endsWith(".") && result.endsWith(".")) {
                result = result.substring(0, result.length - 1)
            }
            if (result.isNotEmpty()) return result
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
        }
        return text
    }

    //https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=fr&dt=t&q=father&ie=UTF-8&oe=UTF-8
    //https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=fr&dt=t&q=father
    private fun translateUseGoogleApi(t: String, fromLang: Locale, toLang: Locale): String? {
        LogCat.logError("translateUseGoogleApi: from=$fromLang to=$toLang text=$t")
        if (NetworkApi.hasInternet() && t.isNotEmpty()) {
            try {
                var text = t
                for (i in 1..Int.MAX_VALUE) {
                    if (text.contains("%$i$")) {
                        text = text.replace("%$i$", "%$i%")
                    } else {
                        break
                    }
                }

                val encode: String = URLEncoder.encode(text.replace("\n", " \\n "), "UTF-8")
                val sb = StringBuilder()
                sb.append("https://translate.googleapis.com/translate_a/single?client=gtx&sl=")
                sb.append(fromLang.language)
                sb.append("&tl=")
                sb.append(toLang.language)
                sb.append("&dt=t&q=")
                sb.append(encode).append("&ie=UTF-8&oe=UTF-8")

                val data = fetchFromWeb(sb.toString()) ?: return null

                var s = JSONArray(data)
                    .getJSONArray(0)
                    .getJSONArray(0)
                    .getString(0)

                for (i in 1..Int.MAX_VALUE) {
                    if (s.contains("%$i%")) {
                        s = s
                            .replace("%$i%", "%$i$")
                            .replace("%$i\$S", "%$i\$s")
                            .replace("%$i\$D", "%$i\$d")
                    } else {
                        break
                    }
                }

                return s
                    .replace(" \\ n ", "\n")
                    .replace("\\ n", "\n")
                    .replace("  ", " ")
                    .trim()
                    .ifEmpty { null }
            } catch (e: Throwable) {
                LogCat.logException(e, "LocalizationHelper")
            }
        }
        return null
    }

    private fun translateUseFallbackApi(t: String, fromLang: Locale, toLang: Locale): String? {
        LogCat.logError("translateUseFallbackApi: from=$fromLang to=$toLang text=$t")
        if (NetworkApi.hasInternet() && t.isNotEmpty()) {
            try {
                var text = t
                for (i in 1..Int.MAX_VALUE) {
                    if (text.contains("%$i$")) {
                        text = text.replace("%$i$", "%$i%")
                    } else {
                        break
                    }
                }

                val encode: String = URLEncoder.encode(text.replace("\n", " \\n "), "UTF-8")
                val sb = StringBuilder()
                sb.append("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=")
                sb.append(fromLang.language)
                sb.append("&tl=")
                sb.append(toLang.language)
                sb.append("&dt=t&q=")
                sb.append(encode).append("&ie=UTF-8&oe=UTF-8")

                val data = fetchFromWeb(sb.toString()) ?: return null

                var s = JSONArray(data).getString(0)

                for (i in 1..Int.MAX_VALUE) {
                    if (s.contains("%$i%")) {
                        s = s
                            .replace("%$i%", "%$i$")
                            .replace("%$i\$S", "%$i\$s")
                            .replace("%$i\$D", "%$i\$d")
                    } else {
                        break
                    }
                }

                return s
                    .replace(" \\ n ", "\n")
                    .replace("\\ n", "\n")
                    .replace("  ", " ")
                    .trim()
                    .ifEmpty { null }
            } catch (e: Throwable) {
                LogCat.logException(e, "LocalizationHelper")
            }
        }
        return null
    }

    private fun getTranslatedStringFromResources(currentContext: Context, id: Int): String? {
        ensureLocaleState()

        val currentLocale = AndroidContext.appLocale
        val authorsLocale = Locale.forLanguageTag(AUTHOR_LOCALE_TAG)

        val stringInCurrentLocale = getStringForLocaleCached(currentContext, id, currentLocale)

        if (authorsLocale.language == currentLocale.language) {
            return stringInCurrentLocale
        }

        val authorsOriginalString = getStringForLocaleCached(currentContext, id, authorsLocale)
        if (stringInCurrentLocale != authorsOriginalString) {
            return stringInCurrentLocale
        }

        val hasTranslation = resourceTranslationAvailabilityCache.getOrPut(
            translationAvailabilityKey(id, currentLocale)
        ) {
            false
        }

        return if (hasTranslation) stringInCurrentLocale else null
    }

    private fun getTranslatedStringFromResources(
        currentContext: Context,
        id: Int,
        vararg formatArgs: Any?
    ): String? {
        ensureLocaleState()

        val currentLocale = AndroidContext.appLocale
        val authorsLocale = Locale.forLanguageTag(AUTHOR_LOCALE_TAG)

        val stringInCurrentLocale =
            getStringForLocaleCached(currentContext, id, currentLocale, *formatArgs)

        if (authorsLocale.language == currentLocale.language) {
            return stringInCurrentLocale
        }

        val authorsOriginalString =
            getStringForLocaleCached(currentContext, id, authorsLocale, *formatArgs)

        if (stringInCurrentLocale != authorsOriginalString) {
            return stringInCurrentLocale
        }

        val hasTranslation = resourceTranslationAvailabilityCache.getOrPut(
            translationAvailabilityKey(id, currentLocale, formatArgs)
        ) {
            false
        }

        return if (hasTranslation) stringInCurrentLocale else null
    }

    private fun getLocalizedContext(baseContext: Context, locale: Locale): Context {
        val localeTag = locale.toLanguageTag()
        return localizedContextCache.getOrPut(localeTag) {
            val appContext = baseContext.applicationContext ?: baseContext
            val res = appContext.resources
            val config = Configuration(res.configuration)
            ConfigurationCompat.setLocales(config, LocaleListCompat.create(locale))
            if (Build.VERSION.SDK_INT >= 17) {
                appContext.createConfigurationContext(config)
            } else {
                appContext
            }
        }
    }

    private fun getStringForLocaleCached(
        currentContext: Context,
        id: Int,
        locale: Locale,
        vararg formatArgs: Any?
    ): String {
        val key = resourceKey(id, locale, formatArgs)
        return resourceStringCache.getOrPut(key) {
            getStringForLocale(currentContext, id, locale, *formatArgs)
        }
    }

    private fun getStringForLocaleCached(
        currentContext: Context,
        id: Int,
        locale: Locale
    ): String {
        val key = resourceKey(id, locale)
        return resourceStringCache.getOrPut(key) {
            getStringForLocale(currentContext, id, locale)
        }
    }

    private fun getStringForLocale(
        currentContext: Context,
        id: Int,
        locale: Locale,
        vararg formatArgs: Any?
    ): String {
        return if (Build.VERSION.SDK_INT >= 17) {
            getLocalizedContext(currentContext, locale).getString(id, *formatArgs)
        } else {
            val res = currentContext.resources
            val oldConfig = Configuration(res.configuration)
            val config = Configuration(res.configuration)
            config.locale = locale
            res.updateConfiguration(config, res.displayMetrics)
            val s = currentContext.getString(id, *formatArgs)
            res.updateConfiguration(oldConfig, res.displayMetrics)
            s
        }
    }

    private fun getStringForLocale(
        currentContext: Context,
        id: Int,
        locale: Locale
    ): String {
        return if (Build.VERSION.SDK_INT >= 17) {
            getLocalizedContext(currentContext, locale).getString(id)
        } else {
            val res = currentContext.resources
            val oldConfig = Configuration(res.configuration)
            val config = Configuration(res.configuration)
            config.locale = locale
            res.updateConfiguration(config, res.displayMetrics)
            val s = currentContext.getString(id)
            res.updateConfiguration(oldConfig, res.displayMetrics)
            s
        }
    }
}