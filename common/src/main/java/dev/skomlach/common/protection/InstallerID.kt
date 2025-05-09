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

import android.content.Context
import dev.skomlach.common.misc.Utils

enum class InstallerID(private val text: String) {

    //https://madappgang.com/blog/the-size-of-the-mobile-application-development-market-in-australia-and-beyond
    GOOGLE_PLAY("com.android.vending|com.google.android.feedback"),

    //vendors
    MEIZU_APPS("com.xrom.intl.appcenter"),
    VIVO_APPS("com.bbk.appstore"),
    OPPO_APPS("com.oppo.market"),
    MIUI_APPS("com.xiaomi.market"),
    AMAZON_APPS("com.amazon.venezia"),
    LGWORLD("com.lge.lgworld"),
    HUAWEI_APPS("com.huawei.appmarket"),
    GALAXY_APPS("com.sec.android.app.samsungapps"),

    //3rd-party (open-source, China etc.)
    FDROID("org.fdroid.fdroid"),
    SLIDEME("com.slideme.sam.manager"),
    APTOIDE("cm.aptoide.pt"),
    APPBRAIN("com.appspot.swisscodemonkeys.apps"),
    MOBILE9("com.mobile9.market.ggs"),
    MOBANGO("com.appswiz.mobangoappstorehgaje"),
    ONEMOBILE("me.onemobile.android"),
    BAIDU("com.baidu.appsearch"),
    SOGOU_MOBILE_ASSISTANT("com.sogou.androidtool"),
    PP_ASSISTANT("com.pp.assistant"),
    AURORA("com.aurora.store"),
    WANDOUJIA("com.wandoujia.phoenix2");

    /* (non-Javadoc)
         * @see java.lang.Enum#toString()
         */
    override fun toString(): String {
        return text
    }

    fun toIDs(): List<String> = if (text.contains("|")) {
        val split = text.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        ArrayList(listOf(*split))
    } else {
        ArrayList(listOf(text))
    }

    companion object {
        fun getInstallerId(context: Context, packageName: String): String? {

            val validInstallers = ArrayList<String>()
            val installer = try {
                if (Utils.isAtLeastR)
                    context.packageManager.getInstallSourceInfo(packageName).installingPackageName.toString()
                        .ifEmpty { context.packageManager.getInstallerPackageName(packageName) }
                else
                    context.packageManager.getInstallerPackageName(packageName)
            } catch (e: Throwable) {
                return "com.android.vending" //unable to get InstallerPackageName
            }
            for (id in entries) {
                validInstallers.addAll(id.toIDs())
            }
            return if (installer != null && validInstallers.contains(installer)) {
                validInstallers[validInstallers.indexOf(installer)]
            } else
                null
        }

        fun verifyInstallerId(context: Context, packageName: String): Boolean {
            val validInstallers = ArrayList<String>()
            val installer = try {
                if (Utils.isAtLeastR)
                    context.packageManager.getInstallSourceInfo(packageName).installingPackageName.toString()
                        .ifEmpty { context.packageManager.getInstallerPackageName(packageName) }
                else
                    context.packageManager.getInstallerPackageName(packageName)
            } catch (e: Throwable) {
                return true //unable to get InstallerPackageName
            }
            for (id in InstallerID.entries) {
                validInstallers.addAll(id.toIDs())
            }
            return installer != null && validInstallers.contains(installer)
        }

    }
}