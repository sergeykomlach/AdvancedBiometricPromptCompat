/*
 *  Copyright (c) 2025 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.common.misc

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * Returns a language code which is a part of the active [Locale].
 *
 * Deprecated ISO language codes "iw", "ji", and "in" are converted
 * to "he", "yi", and "id", respectively.
 */
fun Locale.languageCompat(): String {
    return verifyLanguage(this.language)
}

val Context.currentLocale: Locale
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val locales = resources.configuration.locales
        (0 until locales.size())
            .map { locales[it] }
            .firstOrNull { it != null && it.language.isNotEmpty() }
            ?: Locale.getDefault()
    } else {
        val locale = resources.configuration.locale
        @Suppress("DEPRECATION")
        if (locale != null && locale.language.isNotEmpty()) locale else Locale.getDefault()
    }
val Configuration.currentLocale: Locale
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        (0 until locales.size())
            .map { locales[it] }
            .firstOrNull { it != null && it.language.isNotEmpty() }
            ?: Locale.getDefault()
    } else {
        @Suppress("DEPRECATION")
        if (locale != null && locale.language.isNotEmpty()) locale else Locale.getDefault()
    }

private fun verifyLanguage(language: String): String {
    // get rid of deprecated language tags
    return when (language) {
        "iw" -> "he"
        "ji" -> "yi"
        "in" -> "id"
        else -> language
    }
}

object LocaleHelper {
    private val TAG = "LocaleHelper"
    private var delegate = UpdateLocaleDelegate()
    fun bcp47(locale: Locale): Locale {
        if (locale == Locale.ROOT) return locale
        return toBCP47LocaleList(locale.toLanguageTag()).get(0) ?: locale
    }

    fun toBCP47LocaleList(vararg locales: String): LocaleListCompat {
        val tags = locales.mapNotNull { raw ->
            val fixed = raw.replace('_', '-').trim()
            if (fixed.isEmpty()) return@mapNotNull null

            val normalized = normalizeBcp47(fixed)

            val langOnly = !normalized.contains('-')
            val locale = if (langOnly) {
                addLikelySubtagsCompatNoReflection(Locale(normalized.lowercase(Locale.ROOT)))
            } else {
                Locale.forLanguageTag(normalized)
            }
            locale.toLanguageTag()
        }.joinToString(",")

        return LocaleListCompat.forLanguageTags(tags)
    }

    private fun normalizeBcp47(tag: String): String {
        val parts = tag.split('-').filter { it.isNotBlank() }
        if (parts.isEmpty()) return tag.lowercase(Locale.ROOT)

        val out = ArrayList<String>(parts.size)
        parts.forEachIndexed { i, p ->
            out += when {
                i == 0 -> p.lowercase(Locale.ROOT) // language
                p.length == 4 -> p.lowercase(Locale.ROOT)
                    .replaceFirstChar { it.titlecase(Locale.ROOT) } // Script
                p.length == 2 || p.length == 3 -> p.uppercase(Locale.ROOT) // Region
                else -> p // variants / extensions
            }
        }
        return out.joinToString("-")
    }

    private fun addLikelySubtagsCompatNoReflection(base: Locale): Locale {
        val lang = base.language.lowercase(Locale.ROOT)
        val country = base.country.uppercase(Locale.ROOT)
        val script = base.script
        if (country.isNotEmpty() || script.isNotEmpty()) {
            return Locale.Builder()
                .setLanguage(lang)
                .apply { if (script.isNotEmpty()) setScript(script) }
                .apply { if (country.isNotEmpty()) setRegion(country) }
                .build()
        }
        if (lang == "zh") {
            return Locale.Builder()
                .setLanguage("zh")
                .setScript("Hans")
                .setRegion("CN")
                .build()
        }
        if (lang in ALT_SCRIPT_LANGS) {
            return Locale.Builder()
                .setLanguage(lang)
                .setScript("Latn")
                .setRegion(guessDefaultRegion(lang))
                .build()
        }

        val region = guessDefaultRegion(lang)
        return if (region.isNotEmpty()) Locale(lang, region) else Locale(lang)
    }

    private fun guessDefaultRegionFallback(lang: String): String = when (lang) {
        "uk" -> "UA"
        "en" -> "US"
        "fr" -> "FR"
        "de" -> "DE"
        "es" -> "ES"
        "pt" -> "PT" // or "BR" LATAM
        "it" -> "IT"
        "pl" -> "PL"
        "tr" -> "TR"
        "cs" -> "CZ"
        "ro" -> "RO"
        "bg" -> "BG"
        "el" -> "GR"
        "ar" -> "EG"
        "fa" -> "IR"
        "hi" -> "IN"
        "bn" -> "BD"
        "ja" -> "JP"
        "ko" -> "KR"
        "nl" -> "NL"
        "sv" -> "SE"
        "no", "nb", "nn" -> "NO"
        "fi" -> "FI"
        else -> ""
    }

    private val ALT_SCRIPT_LANGS = setOf(
        "sr", // Srpski: Latn/Cyrl
        "az", // Azərbaycan: Latn/Cyrl
        "uz"  // Oʻzbek: Latn/Cyrl
    )

    private fun guessDefaultRegion(language: String): String {
        val countries = Locale.getISOCountries()
        return countries.firstOrNull { country ->
            val locale = Locale(language, country)
            locale.displayLanguage.lowercase(Locale.ROOT) == Locale(language).displayLanguage.lowercase(
                Locale.ROOT
            )
        } ?: guessDefaultRegionFallback(language)
    }


    /**
     * Returns the system [Locale]
     */
    fun systemLocale(context: Context): Locale {
        val locales = LocaleManagerCompat.getSystemLocales(context)
        val locale = Resources.getSystem().configuration.currentLocale
        val l = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (0 until locales.size())
                .map { locales[it] }
                .firstOrNull { it != null && it.language.isNotEmpty() }
                ?: if (locale.language.isNotEmpty()) locale else Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            if (locale.language.isNotEmpty()) locale else Locale.getDefault()
        }
        return bcp47(l)
    }

    fun getDefault(context: Context): Locale {
        return load(context)
    }

    fun onAttach(context: Context): Context {
        return delegate.applyLocale(context, getDefault(context))
    }

    private fun load(context: Context): Locale {
        return bcp47(context.getAppLocale())
    }

    private fun Context.getAppLocale(): Locale {
        if (isFollowSystem(this)) return systemLocale(this)
        val localesList = getApplicationLocalesCompat(this)
        if (!localesList.isEmpty) {
            val locales =
                toBCP47LocaleList(localesList.toLanguageTags())
            if (!locales.isEmpty) {
                locales.get(0)?.let {
                    return it
                }
            }
        }
        return systemLocale(this)
    }

    private fun isFollowSystem(context: Context): Boolean {
        val locales = getApplicationLocalesCompat(context)
        return locales.isEmpty || locales.get(0)?.language == Locale.ROOT.language
    }

    private fun getApplicationLocalesCompat(context: Context): LocaleListCompat {
        val localesList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nativeLocaleList = context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales ?: LocaleList.getEmptyLocaleList()
            LocaleListCompat.wrap(nativeLocaleList)
        } else AppCompatDelegate.getApplicationLocales()
        return localesList
    }

    private fun isPerAppLocalesEnabled(context: Context): Boolean {
        context.packageManager
        val assetManager = context.assets
        try {
            val parser = assetManager.openXmlResourceParser("AndroidManifest.xml")
            parser.use {
                val ANDROID_NS = "http://schemas.android.com/apk/res/android"
                while (true) {
                    when (it.next()) {
                        XmlPullParser.END_DOCUMENT -> return false
                        XmlPullParser.START_TAG -> {
                            if (it.name == "application") {
                                for (i in 0 until it.attributeCount) {
                                    val isAndroidNs = it.getAttributeNamespace(i) == ANDROID_NS
                                    val isLocaleCfg = it.getAttributeName(i) == "localeConfig"
                                    if (isAndroidNs && isLocaleCfg) {
                                        return true
                                    }
                                }
                                return false
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            return false
        } finally {
            try {
                assetManager.close()
            } catch (_: Throwable) {
            }
        }
    }

    internal class UpdateLocaleDelegate {

        internal fun applyLocale(context: Context, locale: Locale): Context {
            if (!isPerAppLocalesEnabled(context)) return context
            return if (Build.VERSION.SDK_INT <= 32 && bcp47(context.currentLocale) != bcp47(locale)) {
                updateLocale(context, locale)
            } else context
        }

        @Suppress("DEPRECATION")
        private fun updateLocale(context: Context, locale: Locale): Context {
            val res = context.resources
            val config = Configuration(res.configuration)
            val ctx = if (Build.VERSION.SDK_INT >= 17) {
                ConfigurationCompat.setLocales(config, LocaleListCompat.create(locale))
                context.createConfigurationContext(config)
            } else {
                config.locale = locale
                res.updateConfiguration(config, res.displayMetrics)
                context
            }
            return ctx
        }
    }
}
