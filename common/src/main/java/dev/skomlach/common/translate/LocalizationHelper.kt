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
import android.os.LocaleList
import androidx.annotation.StringRes
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.storage.SharedPreferenceProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    fun fetchFromWeb(url: String): String? {
        LogCat.logError("translate fetchFromWeb $url")
        if (NetworkApi.hasInternet()&& url.isNotEmpty())
            try {
                val urlConnection =
                    NetworkApi.createConnection(
                        url,
                        TimeUnit.SECONDS.toMillis(30).toInt()
                    )

                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Content-Language", "en-US")
                urlConnection.setRequestProperty("Accept-Language", "en-US")
                urlConnection.setRequestProperty(
                    "User-Agent",
                    agents[SecureRandom().nextInt(agents.size)]
                )
                urlConnection.connect()
                val responseCode = urlConnection.responseCode
                val byteArrayOutputStream = ByteArrayOutputStream()
                val inputStream: InputStream

                //if any 2XX response code
                if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                    inputStream = urlConnection.inputStream
                } else {
                    //Redirect happen
                    if (responseCode >= HttpURLConnection.HTTP_MULT_CHOICE && responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
                        var target = urlConnection.getHeaderField("Location")
                        if (target != null && !NetworkApi.isWebUrl(target)) {
                            target = "https://$target"
                        }
                        return fetchFromWeb(target)
                    }
                    inputStream = urlConnection.inputStream ?: urlConnection.errorStream
                }
                NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
                inputStream.close()
                val data = byteArrayOutputStream.toByteArray()
                byteArrayOutputStream.close()
                urlConnection.disconnect()
                return String(data, Charset.forName("UTF-8"))
            } catch (e: Throwable) {
                LogCat.logException(e, "LocalizationHelper")
            }
        return null
    }

    fun prefetch(context: Context, vararg formatArgs: Any?) {
        try {
            formatArgs.toList().forEach {
                try {
                    if (getTranslatedStringFromResources(context, it as Int).isNullOrEmpty())
                        invoke(
                            getStringForLocale(context, it as Int, Locale.US),
                            Locale.US,
                            AndroidContext.appLocale
                        )
                } catch (e: Throwable) {
                    LogCat.logException(e, "LocalizationHelper")
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
        }
    }

    fun getLocalizedString(context: Context, @StringRes resId: Int): String {
        try {
            getTranslatedStringFromResources(context, resId)?.let {
                return it
            }
            val str = getStringForLocale(context, resId, Locale.US)
            return getLocalizedString(str)
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
            return context.getString(resId)
        }
    }

    fun getLocalizedString(
        context: Context,
        @StringRes resId: Int,
        vararg formatArgs: Any?
    ): String {
        try {
            getTranslatedStringFromResources(context, resId, *formatArgs)?.let {
                return it
            }
            val str = getStringForLocale(context, resId, Locale.US)
            return getLocalizedString(str, *formatArgs)
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
            return context.getString(resId, *formatArgs)
        }
    }

    private fun getLocalizedString(str: String): String {
        return read(Locale.US, AndroidContext.appLocale, str) ?: str
    }

    private fun getLocalizedString(raw: String, vararg formatArgs: Any?): String {
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
            val str = read(fromLang, toLang, text) ?: translate(text, fromLang, toLang).also {
                store(fromLang, toLang, text, it)
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
        if (fromLang.language == toLang.language)
            return text
        val pref = SharedPreferenceProvider.getPreferences("LocalizationHelperV3")
        val key = fromLang.language + ">>" + toLang.language
        val set = HashSet<String>(pref.getStringSet(key, emptySet()) ?: emptySet())
        set.forEach {
            val json = JSONObject(it)
            if (json.has(text))
                return json.getString(text).trim().ifEmpty { return null }
        }

        return null
    }

    private fun store(
        fromLang: Locale,
        toLang: Locale,
        text: String,
        result: String
    ) {
        if (fromLang.language == toLang.language)
            return
        if (text.trim().isEmpty() || result.trim().isEmpty() || text == result)
            return
        val pref = SharedPreferenceProvider.getPreferences("LocalizationHelperV3")
        val key = fromLang.language + ">>" + toLang.language
        val set = HashSet<String>(pref.getStringSet(key, emptySet()) ?: emptySet())
        set.add(JSONObject().apply {
            this.put(text, result)
        }.toString())

        pref.edit()
            .putStringSet(key, set).apply()
    }

    private fun translate(text: String, fromLang: Locale, toLang: Locale): String {
        try {
            val parts = text.split(".")
            //first try
            var sb = StringBuilder()
            parts.forEach {
                translateUseGoogleApi(it, fromLang, toLang)?.let { s ->
                    if (s.isNotEmpty())
                        sb.append(s).append(". ")
                }
            }
            var result = sb.toString().trim()

            if (!text.endsWith(".") && result.endsWith("."))
                result = result.substring(0, result.length - 1)

            if (result.isNotEmpty()) return result

            sb = StringBuilder()
            parts.forEach {
                translateUseFallbackApi(it, fromLang, toLang)?.let { s ->
                    if (s.isNotEmpty())
                        sb.append(s).append(". ")
                }
            }
            result = sb.toString().trim()

            if (!text.endsWith(".") && result.endsWith("."))
                result = result.substring(0, result.length - 1)

            if (result.isNotEmpty()) return result
        } catch (e: Throwable) {
            LogCat.logException(e, "LocalizationHelper")
        }
        return text//return not translated
    }

    //https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=fr&dt=t&q=father&ie=UTF-8&oe=UTF-8
    //https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=fr&dt=t&q=father
    private fun translateUseGoogleApi(t: String, fromLang: Locale, toLang: Locale): String? {
        LogCat.logError("translateUseGoogleApi: from=$fromLang to=$toLang text=$t")
        if (NetworkApi.hasInternet() && t.isNotEmpty())
            try {
                var text = t
                for (i in 1..Int.MAX_VALUE) {
                    if (text.contains("%$i$")) {
                        text = text.replace("%$i$", "%$i%")
                    } else
                        break
                }

                val encode: String = URLEncoder.encode(text.replace("\n", " \\n "), "UTF-8")
                val sb = StringBuilder()

                sb.append("https://translate.googleapis.com/translate_a/single?client=gtx&sl=")
                sb.append(fromLang.language)
                sb.append("&tl=")
                sb.append(toLang.language)
                sb.append("&dt=t&q=")
                sb.append(encode).append("&ie=UTF-8&oe=UTF-8")
                val data = fetchFromWeb(sb.toString())
                //note:
                //[[["père","father",null,null,10]],null,"en",null,null,null,null,[]]

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
                    } else
                        break
                }
                return s
                    .replace(" \\ n ", "\n")
                    .replace("\\ n", "\n")
                    .replace("  ", " ").trim()
                    .ifEmpty { return null }
            } catch (e: Throwable) {
                LogCat.logException(e, "LocalizationHelper")
            }
        return null
    }

    private fun translateUseFallbackApi(t: String, fromLang: Locale, toLang: Locale): String? {
        LogCat.logError("translateUseFallbackApi: from=$fromLang to=$toLang text=$t")
        if (NetworkApi.hasInternet()&& t.isNotEmpty())
            try {
                var text = t
                for (i in 1..Int.MAX_VALUE) {
                    if (text.contains("%$i$")) {
                        text = text.replace("%$i$", "%$i%")
                    } else
                        break
                }

                val encode: String = URLEncoder.encode(text.replace("\n", " \\n "), "UTF-8")
                val sb = StringBuilder()
                sb.append("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=")
                sb.append(fromLang.language)
                sb.append("&tl=")
                sb.append(toLang.language)
                sb.append("&dt=t&q=")
                sb.append(encode).append("&ie=UTF-8&oe=UTF-8")

                val data = fetchFromWeb(sb.toString())

                //["père"]
                val jSONArray =
                    JSONArray(data)
                var s = jSONArray.getString(0)

                for (i in 1..Int.MAX_VALUE) {
                    if (s.contains("%$i%")) {
                        s = s
                            .replace("%$i%", "%$i$")
                            .replace("%$i\$S", "%$i\$s")
                            .replace("%$i\$D", "%$i\$d")
                    } else
                        break
                }
                return s
                    .replace(" \\ n ", "\n")
                    .replace("\\ n", "\n")
                    .replace("  ", " ").trim()
                    .ifEmpty { return null }
            } catch (e: Throwable) {
                LogCat.logException(e, "LocalizationHelper")
            }
        return null
    }


    private fun getTranslatedStringFromResources(currentContext: Context, id: Int): String? {
        val currentLocale = AndroidContext.appLocale
        val authorsLocale = Locale.US
        val stringInCurrentLocale = getStringForLocale(currentContext, id, currentLocale)
        if (authorsLocale.language == currentLocale.language) {
            return stringInCurrentLocale
        } else {
            val authorsOriginalString = getStringForLocale(currentContext, id, authorsLocale)
            if (authorsOriginalString == stringInCurrentLocale) {
                //Check if translations exists for any other locales, but may missed for current -
                //in this case return default translation and give up
                try {
                    val resources = currentContext.resources
                    for (loc in resources.assets.locales) {
                        if (loc.isEmpty()) continue
                        val locale: Locale = Locale.forLanguageTag(loc)
                        val testTranslation = getStringForLocale(currentContext, id, locale)
                        if (testTranslation != authorsOriginalString)
                            return authorsOriginalString
                    }
                } catch (e: Throwable) {
                    LogCat.logException(e, "LocalizationHelper")
                }
                return null
            } else {
                return stringInCurrentLocale
            }
        }
    }

    private fun getTranslatedStringFromResources(
        currentContext: Context,
        id: Int,
        vararg formatArgs: Any?
    ): String? {
        val currentLocale = AndroidContext.appLocale
        val authorsLocale = Locale.US
        val stringInCurrentLocale =
            getStringForLocale(currentContext, id, currentLocale, *formatArgs)
        if (authorsLocale.language == currentLocale.language) {
            return stringInCurrentLocale
        } else {
            val authorsOriginalString =
                getStringForLocale(currentContext, id, authorsLocale, *formatArgs)
            if (authorsOriginalString == stringInCurrentLocale) {
                //Check if translations exists for any other locales, but may missed for current -
                //in this case return default translation and give up
                try {
                    val resources = currentContext.resources
                    for (loc in resources.assets.locales) {
                        if (loc.isEmpty()) continue
                        val locale: Locale = Locale.forLanguageTag(loc)
                        val testTranslation =
                            getStringForLocale(currentContext, id, locale, *formatArgs)
                        if (testTranslation != authorsOriginalString)
                            return authorsOriginalString
                    }
                } catch (e: Throwable) {
                    LogCat.logException(e, "LocalizationHelper")
                }
                return null
            } else {
                return stringInCurrentLocale
            }
        }
    }

    private fun getStringForLocale(
        currentContext: Context,
        id: Int,
        locale: Locale,
        vararg formatArgs: Any?,
    ): String {
        val config = Configuration(currentContext.resources.configuration).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
            setLocale(locale)
        }
        return currentContext.createConfigurationContext(config).getString(id, *formatArgs)
    }

    private fun getStringForLocale(currentContext: Context, id: Int, locale: Locale): String {
        val config = Configuration(currentContext.resources.configuration).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
            setLocale(locale)
        }
        return currentContext.createConfigurationContext(config).getString(id)
    }
}