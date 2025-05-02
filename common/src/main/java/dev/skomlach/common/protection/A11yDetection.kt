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
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.SettingsHelper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

object A11yDetection {
    private val talkBackPackages =
        listOf("com.google.android.marvin.talkback", "com.android.talkback")

    fun hasWhiteListedService(cnt: Context): Boolean {
        try {
            val accessibilityEnabled =
                SettingsHelper.getInt(cnt, Settings.Secure.ACCESSIBILITY_ENABLED)
            val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')

            if (accessibilityEnabled == 1) {
                val settingValue = SettingsHelper.getString(
                    cnt,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                mStringColonSplitter.setString(settingValue)
                val list = mutableListOf<ComponentName?>()
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    list.add(ComponentName.unflattenFromString(accessibilityService))
                }
                LogCat.log(
                    "A11yDetection",
                    list
                )

                return list.filterNotNull().any {
                    talkBackPackages.contains(it.packageName)
                }.also {
                    LogCat.logError(
                        "A11yDetection",
                        "hasWhiteListedService=$it"
                    )
                }
            }
        } catch (e: Throwable) {
            LogCat.logError(
                "A11yDetection",
                e.message, e
            )
        }
        return false.also {
            LogCat.logError(
                "A11yDetection",
                "hasWhiteListedService=$it"
            )
        }
    }

    //isAccessibilityTool
    fun shouldWeTrustA11y(cnt: Context): Boolean {
        try {
            val accessibilityEnabled =
                SettingsHelper.getInt(cnt, Settings.Secure.ACCESSIBILITY_ENABLED)
            val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')

            if (accessibilityEnabled == 1) {
                val settingValue = SettingsHelper.getString(
                    cnt,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return true
                mStringColonSplitter.setString(settingValue)
                val list = mutableListOf<ComponentName?>()
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    list.add(ComponentName.unflattenFromString(accessibilityService))
                }
                LogCat.log(
                    "A11yDetection",
                    list
                )

                return list.filterNotNull().none {
                    !(InstallerID.verifyInstallerId(cnt, it.packageName) && isAccessibilityTool(
                        cnt,
                        it
                    ))
                }
            }
        } catch (e: Throwable) {
            LogCat.logError(
                "A11yDetection",
                e.message, e
            )
        }
        return true
    }

    private fun isAccessibilityTool(context: Context, componentName: ComponentName): Boolean {
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val list =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

            list.forEach {
                if ("${it.resolveInfo.serviceInfo.packageName}/${it.resolveInfo.serviceInfo.name}" == componentName.flattenToString()) {
                    return AccessibilityServiceInfo::class.java.getDeclaredMethod("isAccessibilityTool")
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
            return true
        } catch (e: Throwable) {
            LogCat.logError(
                "A11yDetection",
                e.message, e
            )
        }
        return false
    }

    private class AssetsChecker(val resources: Resources) {
        fun isAccessibilityTool(res: Int): Boolean {
            try {
                val xml = resources.getXml(res)
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val xpp = factory.newPullParser()
                xpp.setInput(
                    StringReader(
                        getXMLText(xml, resources)
                            .toString()
                    )
                )
                xml.close()
                while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
                    when (xpp.eventType) {
                        XmlPullParser.START_TAG -> {
                            var i = 0
                            while (i < xpp.attributeCount) {
                                LogCat.log(
                                    "A11yDetection",
                                    xpp.getAttributeName(i), xpp.getAttributeValue(i)
                                )
                                if (xpp.name.equals("accessibility-service", ignoreCase = true)
                                    && xpp.getAttributeName(i).equals(
                                        "isAccessibilityTool", ignoreCase = true
                                    )
                                ) {
                                    return xpp.getAttributeValue(i) == "true"
                                }
                                i++
                            }
                        }

                        XmlPullParser.START_DOCUMENT, XmlPullParser.END_TAG, XmlPullParser.TEXT -> {}
                        else -> {}
                    }
                    xpp.next()
                }
            } catch (e: Throwable) {
                LogCat.logError(
                    "A11yDetection",
                    e.message, e
                )
            }
            return false
        }

        private fun insertSpaces(sb: StringBuilder?, num: Int) {
            if (sb == null) {
                return
            }
            for (i in 0 until num) {
                sb.append(" ")
            }
        }

        private fun getXMLText(
            xrp: XmlResourceParser,
            currentResources: Resources
        ): CharSequence {
            val sb = StringBuilder()
            var indent = 0
            try {
                var eventType = xrp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    // for sb
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            indent += 1
                            sb.append("\n")
                            insertSpaces(sb, indent)
                            sb.append("<").append(xrp.name)
                            sb.append(getAttribs(xrp, currentResources))
                            sb.append(">")
                        }

                        XmlPullParser.END_TAG -> {
                            indent -= 1
                            sb.append("\n")
                            insertSpaces(sb, indent)
                            sb.append("</").append(xrp.name).append(">")
                        }

                        XmlPullParser.TEXT -> sb.append("").append(xrp.text)
                        XmlPullParser.CDSECT -> sb.append("<!CDATA[").append(xrp.text).append("]]>")
                        XmlPullParser.PROCESSING_INSTRUCTION -> sb.append("<?").append(xrp.text)
                            .append("?>")

                        XmlPullParser.COMMENT -> sb.append("<!--").append(xrp.text).append("-->")
                    }
                    eventType = xrp.nextToken()
                }
            } catch (e: IOException) {
                LogCat.logError(
                    "A11yDetection",
                    e.message, e
                )
            } catch (e: XmlPullParserException) {
                LogCat.logError(
                    "A11yDetection",
                    e.message, e
                )
            }
            return sb
        }

        /**
         * returns the value, resolving it through the provided resources if it
         * appears to be a resource ID. Otherwise just returns what was provided.
         *
         * @param in String to resolve
         * @param r  Context appropriate resource (system for system, package's for
         * package)
         * @return Resolved value, either the input, or some other string.
         */
        private fun resolveValue(str: String?, r: Resources?): String? {
            return if (str == null || !str.startsWith("@") || r == null) {
                str
            } else try {
                val num = str.substring(1).toInt()
                r.getString(num)
            } catch (e: NumberFormatException) {
                str
            } catch (e: RuntimeException) {
                // formerly noted errors here, but simply not resolving works better
                str
            }
        }

        private fun getAttribs(
            xrp: XmlResourceParser,
            currentResources: Resources
        ): CharSequence {
            val sb = StringBuilder()
            for (i in 0 until xrp.attributeCount) {
                sb.append("\n").append(xrp.getAttributeName(i)).append("=\"")
                    .append(resolveValue(xrp.getAttributeValue(i), currentResources)).append("\"")
            }
            return sb
        }
    }
}