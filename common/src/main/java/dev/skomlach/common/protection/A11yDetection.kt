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

package dev.skomlach.common.protection

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

object A11yDetection {
    private val trustedA11yPackages = setOf(
        // Google / Stock Android
        "com.google.android.marvin.talkback",
        "com.android.talkback",
        "com.google.android.apps.accessibility.voiceaccess",
        "com.android.switchaccess",
        "com.google.audio.asl",
        "com.google.android.accessibility.utils",

        // Samsung
        "com.samsung.android.accessibility.talkback",
        "com.samsung.android.app.talkback",

        // Xiaomi
        "com.miui.voiceassist",

        // Huawei / Honor / Jieshuo
        "com.bjbyhd.voiceback",
        "com.huawei.android.accessibility",

        // Oppo / Realme / Vivo
        "com.coloros.accessibility",
        "com.oppo.accessibility",
        "com.bbk.appstore",

        // Third-party trusted
        "com.prudence.screenreader",
        "org.atrc.braille.brltty"
    )

    private var whiteListCache: Pair<Long, Boolean> = Pair(0, false)
    private var trustedListCache: Pair<Long, Boolean> = Pair(0, false)
    private const val CACHE_TTL_MS = 10_000L

    class A11ySettingsObserver(handler: Handler, private val context: Context) :
        ContentObserver(handler) {

        fun register() {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
                false,
                this
            )
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                this
            )
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            clearCache()
            LogCat.logError("A11yDetection", "Accessibility settings changed, cache cleared")
        }

        fun unregister() {
            context.contentResolver.unregisterContentObserver(this)
        }
    }

    init {
        GlobalScope.launch {
            try {
                AndroidContext.appContext.let {
                    A11ySettingsObserver(Handler(Looper.getMainLooper()), it).register()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun clearCache() {
        whiteListCache = Pair(0, false)
        trustedListCache = Pair(0, false)
    }

    fun hasWhiteListedService(cnt: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - whiteListCache.first <= CACHE_TTL_MS) {
            return whiteListCache.second
        }
        try {

            val am = cnt.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
            return enabledServices.any { service ->
                val packageName = service.resolveInfo.serviceInfo.packageName
                (trustedA11yPackages.contains(packageName) || service.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0)
                 && isSystemApp(cnt, packageName)
            }.also {
                whiteListCache = Pair(now, it)
            }
        } catch (e: Throwable) {
            LogCat.logError("A11yDetection", e.message, e)
            whiteListCache = Pair(now, false)
            return false
        }

    }

    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val ai = context.packageManager.getApplicationInfo(packageName, 0)
            (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    //isAccessibilityTool
    fun shouldWeTrustA11y(cnt: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - trustedListCache.first <= CACHE_TTL_MS) {
            return trustedListCache.second
        }
        try {

            val am = cnt.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

            val enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val list = enabledServices.map { service ->
                ComponentName.unflattenFromString("${service.resolveInfo.serviceInfo.packageName}/${service.resolveInfo.serviceInfo.name}")
            }
            LogCat.log(
                "A11yDetection",
                list
            )

            return list.filterNotNull().none {
                val trustedSource =
                    isSystemApp(cnt, it.packageName) || InstallerID.verifyInstallerId(
                        cnt,
                        it.packageName
                    )
                !(trustedSource && isAccessibilityTool(
                    cnt,
                    it
                ))
            }.also {
                trustedListCache = Pair(now, it)
            }

        } catch (e: Throwable) {
            LogCat.logError(
                "A11yDetection",
                e.message, e
            )
            trustedListCache = Pair(now, false)
            return false
        }
    }

    private fun isAccessibilityTool(context: Context, componentName: ComponentName): Boolean {
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val list =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

            list.forEach {
                if ("${it.resolveInfo.serviceInfo.packageName}/${it.resolveInfo.serviceInfo.name}" == componentName.flattenToString()) {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        it.isAccessibilityTool
                    } else AccessibilityServiceInfo::class.java.getDeclaredMethod("isAccessibilityTool")
                        .apply {
                            this.isAccessible = true
                        }.invoke(it) as Boolean
                }
            }
        } catch (e: Throwable) {
            LogCat.logError(
                "A11yDetection",
                e.message, e
            )
        }
        try {
            val ctx = context.createPackageContext(
                componentName.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            val pi = ctx.packageManager.getPackageInfo(
                componentName.packageName,
                PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
            )
            pi.services?.forEach {
                if ("${it.packageName}/${it.name}" == componentName.flattenToString()) {
                    val res = it.metaData.getInt("android.accessibilityservice")
                        .also { i -> if (i == 0) throw IllegalAccessException() }
                    return AssetsChecker(ctx.resources).isAccessibilityTool(res)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            //No permissions
            LogCat.log(
                "A11yDetection",
                e.message
            )
        } catch (e: Throwable) {
            LogCat.logError(
                "A11yDetection",
                e.message, e
            )
        }
        return false
    }

    private class AssetsChecker(val resources: Resources) {
        fun isAccessibilityTool(resId: Int): Boolean {
            var xrp: XmlResourceParser? = null
            try {
                xrp = resources.getXml(resId)
                var eventType = xrp.eventType

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xrp.name == "accessibility-service") {
                        val namespace = "http://schemas.android.com/apk/res/android"
                        val attributeName = "isAccessibilityTool"
                        val value = xrp.getAttributeValue(namespace, attributeName)

                        if (value != null) {
                            return value == "true"
                        }
                        for (i in 0 until xrp.attributeCount) {
                            if (xrp.getAttributeName(i) == attributeName) {
                                return xrp.getAttributeValue(i) == "true"
                            }
                        }
                    }
                    eventType = xrp.next()
                }
            } catch (e: Throwable) {
                LogCat.logError("A11yDetection", "Error parsing A11y XML direct: ${e.message}", e)
            } finally {
                xrp?.close()
            }
            return false
        }
    }
}