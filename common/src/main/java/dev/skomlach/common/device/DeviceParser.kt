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

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

object DeviceParser {

    private val gson = Gson()
    fun parse(json: String): List<Device> {
        val type = object : TypeToken<List<Device>>() {}.type
        return gson.fromJson(json, type)
    }
}

@Keep
data class Device(
    val brand: String? = null,
    val codename: String? = null,
    val name: String? = null,
    val recoveries: List<Recovery>? = null,
    val roms: List<Rom>? = null,
    val specs: Specs? = null
)

data class Recovery(
    val id: String? = null,
    val supported: Boolean? = null,

    @SerializedName("xdathread")
    val xdaThread: String? = null,

    val maintainer: String? = null
)

data class Rom(
    val id: String? = null,

    val cpu: String? = null,
    val ram: String? = null,
    val wifi: String? = null,

    val url: String? = null,
    val download: String? = null,
    val group: String? = null,
    val photo: String? = null,
    val recovery: String? = null,
    val gapps: String? = null,

    @SerializedName("telegram_url")
    val telegramUrl: String? = null,

    @SerializedName("xda_thread")
    val xdaThread: String? = null,

    val maintainer: String? = null,
    val changelog: String? = null,
    val active: Boolean? = null,

    @SerializedName("maintainer_url")
    val maintainerUrl: String? = null,

    @SerializedName("maintainer_name")
    val maintainerName: String? = null,

    @SerializedName("supported_versions")
    val supportedVersions: List<SupportedVersion>? = null,

    // "repostories"
    @SerializedName("repostories")
    val repositories: List<String>? = null,

    val romtype: String? = null,
    val version: String? = null,
    val developer: String? = null
)

data class SupportedVersion(
    @SerializedName("version_code")
    val versionCode: String? = null,

    @SerializedName("xda_thread")
    val xdaThread: String? = null,

    val stable: Boolean? = null,
    val deprecated: Boolean? = null
)

data class Specs(
    val cpu: String? = null,
    val weight: String? = null,
    val year: String? = null,
    val os: String? = null,
    val chipset: String? = null,
    val gpu: String? = null,
    val sensors: String? = null,
    val batlife: String? = null,

    @SerializedName("internalmemory")
    val internalMemory: String? = null
)