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
import android.os.Build
import androidx.annotation.StringRes
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.network.Connection
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.storage.SharedPreferenceProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit

object LocalizationHelper {
    val agents = arrayOf(
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/602.2.14 (KHTML, like Gecko) Version/10.0.1 Safari/602.2.14",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:50.0) Gecko/20100101 Firefox/50.0"
    )

    fun prefetch(context: Context, vararg formatArgs: Any?) {
        formatArgs.toList().forEach {
            if (it is String)
                invoke(it, Locale.US, Locale.getDefault())
            else if (it is Int) {
                invoke(context.resources.apply {
                    val config = this.configuration
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        config.setLocale(Locale.US)
                    } else {
                        @Suppress("DEPRECATION")
                        config.locale = Locale.US
                    }
                }.getString(it), Locale.US, Locale.getDefault())
            }
        }
    }

    fun getLocalizedString(context: Context, @StringRes resId: Int): String {
        val str = context.resources.apply {
            val config = this.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(Locale.US)
            } else {
                @Suppress("DEPRECATION")
                config.locale = Locale.US
            }
        }.getString(resId)
        return getLocalizedString(str)
    }

    fun getLocalizedString(
        context: Context,
        @StringRes resId: Int,
        vararg formatArgs: Any?
    ): String {
        val str = context.resources.apply {
            val config = this.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(Locale.US)
            } else {
                @Suppress("DEPRECATION")
                config.locale = Locale.US
            }
        }.getString(resId)
        return getLocalizedString(str, *formatArgs)
    }

    fun getLocalizedString(str: String): String {
        return read(Locale.US, Locale.getDefault(), str) ?: str
    }

    fun getLocalizedString(raw: String, vararg formatArgs: Any?): String {
        return String.format(
            read(Locale.US, Locale.getDefault(), raw) ?: raw,
            *formatArgs
        )
    }

    fun hasTranslation(
        context: Context,
        @StringRes resId: Int
    ): Boolean {
        val str = context.resources.apply {
            val config = this.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(Locale.US)
            } else {
                @Suppress("DEPRECATION")
                config.locale = Locale.US
            }
        }.getString(resId)
        return (Locale.US.language == Locale.getDefault().language) || getLocalizedString(str) != str
    }

    fun hasTranslation(
        context: Context,
        @StringRes resId: Int,
        vararg formatArgs: Any?
    ): Boolean {
        val str = context.resources.apply {
            val config = this.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(Locale.US)
            } else {
                @Suppress("DEPRECATION")
                config.locale = Locale.US
            }
        }.getString(resId)

        return (Locale.US.language == Locale.getDefault().language) || getLocalizedString(
            str,
            *formatArgs
        ) != String.format(str, *formatArgs)
    }

    fun hasTranslation(str: String): Boolean {
        return (Locale.US.language == Locale.getDefault().language) || getLocalizedString(str) != str
    }

    fun hasTranslation(str: String, vararg formatArgs: Any?): Boolean {
        return (Locale.US.language == Locale.getDefault().language) || getLocalizedString(
            str,
            *formatArgs
        ) != String.format(str, *formatArgs)
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
        val pref = SharedPreferenceProvider.getPreferences("LocalizationHelperV2")
        val key = fromLang.language + ">>" + toLang.language
        val set = HashSet<String>(pref.getStringSet(key, emptySet()) ?: emptySet())
        set.forEach {
            val json = JSONObject(it)
            if (json.has(text))
                return json.getString(text)
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
        val pref = SharedPreferenceProvider.getPreferences("LocalizationHelperV2")
        val key = fromLang.language + ">>" + toLang.language
        val set = HashSet<String>(pref.getStringSet(key, emptySet()) ?: emptySet())
        set.add(JSONObject().apply {
            this.put(text, result)
        }.toString())

        pref.edit()
            .putStringSet(key, set).apply()
    }

    private fun translate(text: String, fromLang: Locale, toLang: Locale): String {
        return (translateUseGoogleApi(text, fromLang, toLang) ?://first try
        translateUseFallbackApi(text, fromLang, toLang)) ?://second
        text //return not translated
    }

    //https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=fr&dt=t&q=father&ie=UTF-8&oe=UTF-8
    //https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=fr&dt=t&q=father
    private fun translateUseGoogleApi(text: String, fromLang: Locale, toLang: Locale): String? {
        if (Connection.isConnection)
            try {
                val encode: String = URLEncoder.encode(text.replace("\n", "\\n"), "UTF-8")
                val sb = StringBuilder()

                sb.append("https://translate.googleapis.com/translate_a/single?client=gtx&sl=")
                sb.append(fromLang.language)
                sb.append("&tl=")
                sb.append(toLang.language)
                sb.append("&dt=t&q=")
                sb.append(encode).append("&ie=UTF-8&oe=UTF-8")


                val inputStream: InputStream = getStream(sb.toString(), toLang)
                val byteArrayOutputStream = ByteArrayOutputStream()
                NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
                inputStream.close()
                val data = byteArrayOutputStream.toByteArray()

                //note:
                //[[["père","father",null,null,10]],null,"en",null,null,null,null,[]]

                val s = JSONArray(String(data, Charset.forName("UTF-8")))
                    .getJSONArray(0)
                    .getJSONArray(0)
                    .getString(0).toString()

                return s.replace("\\n", "\n")
            } catch (e: Throwable) {
                LogCat.logException(e, "LocalizationHelper")
            }
        return null
    }

    private fun translateUseFallbackApi(text: String, fromLang: Locale, toLang: Locale): String? {
        if (Connection.isConnection)
            try {
                val encode: String = URLEncoder.encode(text, "UTF-8")
                val sb = StringBuilder()
                sb.append("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=")
                sb.append(fromLang.language)
                sb.append("&tl=")
                sb.append(toLang.language)
                sb.append("&dt=t&q=")
                sb.append(encode).append("&ie=UTF-8&oe=UTF-8")


                val inputStream: InputStream = getStream(sb.toString(), toLang)
                val byteArrayOutputStream = ByteArrayOutputStream()
                NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
                inputStream.close()
                val data = byteArrayOutputStream.toByteArray()

                //["père"]
                val jSONArray =
                    JSONArray(String(data, Charset.forName("UTF-8")))
                var s = jSONArray.get(0).toString()
                for (i in 0..Int.MAX_VALUE) {
                    if (s.contains("%$i $ s")) {
                        s = s.replace("%$i $ s", "%$i\$s")
                    } else
                        break
                }
                return s
            } catch (e: Throwable) {
                LogCat.logException(e, "LocalizationHelper")
            }
        return null
    }

    private fun getStream(url: String, lang: Locale): InputStream {
        val urlConnection = NetworkApi.createConnection(url, TimeUnit.SECONDS.toMillis(30).toInt())
        urlConnection.requestMethod = "GET"
        urlConnection.setRequestProperty(
            "Content-Language",
            "${lang.language.lowercase(Locale.getDefault())}-${lang.country.uppercase(Locale.getDefault())}"
        )
        urlConnection.setRequestProperty(
            "Accept-Language",
            "${lang.language.lowercase(Locale.getDefault())}-${lang.country.uppercase(Locale.getDefault())}"
        )
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
                return getStream(target, lang)
            }
            inputStream = urlConnection.inputStream ?: urlConnection.errorStream
        }
        NetworkApi.fastCopy(inputStream, byteArrayOutputStream)
        inputStream.close()
        val data = byteArrayOutputStream.toByteArray()
        byteArrayOutputStream.close()
        urlConnection.disconnect()
        return ByteArrayInputStream(data)
    }
}