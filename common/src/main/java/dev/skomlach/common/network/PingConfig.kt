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
package dev.skomlach.common.network

import androidx.core.content.edit
import dev.skomlach.common.storage.SharedPreferenceProvider

object PingConfig {
    //NOTE: in some cases Cloudflare (1.1.1.1) or/and Google (google.com) hosts can be blocked (like in China)
    //So you can try to use "aliexpress.com" or "yandex.ru" or other national domains

    private const val PREF_NAME = "pingConfig_v7"
    private var timeout: Long = 5
    private var hosts: Set<String> = arrayOf(
        "1.1.1.1", //Must be available everywhere
        "google.com", //May be banned in China
        "cdn.jsdelivr.net"//Domain accessible from China; see https://globalping.io/network-tools/dns-from-guangzhou
    ).toSet()
    val hostsList: Set<String>
        get() {
            return hosts
        }
    val pingTimeoutSec: Long
        get() {
            return timeout
        }

    init {
        val pref = SharedPreferenceProvider.getPreferences(PREF_NAME)
        this.timeout = pref.getLong("pingTimeoutSec", 5)
        this.hosts =
            pref.getStringSet(
                "hostsList", arrayOf(
                    "1.1.1.1", //Must be available everywhere
                    "google.com", //May be banned in China
                    "cdn.jsdelivr.net"//Domain accessible from China; see https://globalping.io/network-tools/dns-from-guangzhou
                ).toSet()
            ) ?: emptySet()

    }

    fun setPingTimeoutSec(timeoutSec: Long) {
        SharedPreferenceProvider.getPreferences(PREF_NAME).edit {
            putLong("pingTimeoutSec", timeoutSec)
        }
        this.timeout = timeoutSec
    }

    fun setHostsList(list: Set<String>) {
        SharedPreferenceProvider.getPreferences(PREF_NAME).edit {
            putStringSet("hostsList", list)
        }
        this.hosts = list
    }
}