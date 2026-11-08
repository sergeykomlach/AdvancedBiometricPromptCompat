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
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RestrictTo
import dev.skomlach.common.logging.LogCat
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.atomic.AtomicLong

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

    private val generation = AtomicLong()
    private val observerLock = Any()
    @Volatile private var observing = false
    private val processObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) = clearCache()
    }

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @get:JvmSynthetic
    val settingsGeneration: Long
        get() = generation.get()
    private val isAccessibilityToolMethod by lazy {
        runCatching { AccessibilityServiceInfo::class.java.getMethod("isAccessibilityTool") }
            .getOrNull()
    }

    private data class ServiceTrust(val allTrusted: Boolean, val hasApprovedReader: Boolean)
    private val unknown = ServiceTrust(false, false)

    class A11ySettingsObserver(handler: Handler, private val context: Context) :
        ContentObserver(handler) {

        fun register() {
            registerObserver(context, this)
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            clearCache()
        }

        fun unregister() {
            context.contentResolver.unregisterContentObserver(this)
        }
    }

    private fun registerObserver(context: Context, observer: ContentObserver) {
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer
        )
        try {
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer
            )
        } catch (error: Exception) {
            resolver.unregisterContentObserver(observer)
            throw error
        }
    }

    private fun observeSettings(context: Context) {
        if (observing) return
        synchronized(observerLock) {
            if (!observing) {
                registerObserver(context.applicationContext, processObserver)
                observing = true
            }
        }
    }

    fun clearCache() {
        generation.incrementAndGet()
    }

    /** A screen may relax its restrictions only when the entire enabled set is trusted. */
    fun hasWhiteListedService(cnt: Context): Boolean =
        readTrust(cnt).let { it.allTrusted && it.hasApprovedReader }

    fun shouldWeTrustA11y(cnt: Context): Boolean = readTrust(cnt).allTrusted

    private fun readTrust(context: Context): ServiceTrust {
        val version = generation.get()
        val trust = try {
            observeSettings(context)
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return unknown
            val configured = configuredServices(context)
            val verified = HashSet<AccessibilityServiceIdentity>()
            var hasApprovedReader = false

            fun verify(service: AccessibilityServiceInfo): Boolean {
                val identity = serviceIdentity(service) ?: return false
                val flags = service.resolveInfo?.serviceInfo?.applicationInfo?.flags ?: return false
                if (!isSystemAccessibilitySource(flags) || !isAccessibilityTool(context, service, identity)) {
                    return false
                }
                verified.add(identity)
                if (service.feedbackType and AccessibilityServiceInfo.FEEDBACK_SPOKEN != 0 &&
                    identity.packageName in trustedA11yPackages
                ) {
                    hasApprovedReader = true
                }
                return true
            }

            if (!manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).all(::verify)) {
                return unknown
            }

            // The enabled list contains bound services. Verify pending components using installed
            // metadata so an approved reader does not depend on when its bind completes.
            val pending = configured - verified
            if (pending.isNotEmpty()) {
                val installed = manager.installedAccessibilityServiceList.associateBy(::serviceIdentity)
                if (!areAllAccessibilityServicesTrusted(pending) { identity ->
                    installed[identity]?.let(::verify) == true
                }) return unknown
            }
            ServiceTrust(configured == configuredServices(context), hasApprovedReader)
        } catch (_: Exception) {
            LogCat.logError("A11yDetection", "Could not verify enabled accessibility services")
            unknown
        }
        return if (version == generation.get()) trust else unknown
    }

    private fun serviceIdentity(service: AccessibilityServiceInfo): AccessibilityServiceIdentity? =
        service.resolveInfo?.serviceInfo?.let { AccessibilityServiceIdentity.from(it.packageName, it.name) }

    private fun configuredServices(context: Context): Set<AccessibilityServiceIdentity?> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().takeIf { it.isNotEmpty() }?.split(':')?.mapTo(HashSet()) { value ->
                ComponentName.unflattenFromString(value)?.let {
                    AccessibilityServiceIdentity.from(it.packageName, it.className)
                }
            }.orEmpty()

    private fun isAccessibilityTool(
        context: Context,
        service: AccessibilityServiceInfo,
        identity: AccessibilityServiceIdentity
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return service.isAccessibilityTool

        val reflected = runCatching { isAccessibilityToolMethod?.invoke(service) as? Boolean }.getOrNull()
        if (reflected != null) return reflected

        val packages = context.packageManager
        val serviceInfo = packages.getPackageInfo(
            identity.packageName,
            PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
        ).services?.firstOrNull { AccessibilityServiceIdentity.from(it.packageName, it.name) == identity }
            ?: return false
        val parser = serviceInfo.loadXmlMetaData(packages, "android.accessibilityservice") ?: return false
        parser.use {
            var event = it.eventType
            while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
                event = it.next()
            }
            return event == XmlPullParser.START_TAG && it.name == "accessibility-service" &&
                it.getAttributeBooleanValue(
                    "http://schemas.android.com/apk/res/android", "isAccessibilityTool", false
                )
        }
    }
}
