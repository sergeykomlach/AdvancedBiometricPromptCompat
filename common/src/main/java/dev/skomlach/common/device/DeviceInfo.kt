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

package dev.skomlach.common.device

import androidx.annotation.Keep
import java.util.Locale

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This field may contain Unicode characters. If you are using it for User-Agent or HTTP headers, it is better to use 'modelAsAscii' or clean it of non-ASCII characters to avoid IllegalArgumentException/Crash"
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class PotentialNonAsciiContent

@Keep
data class DeviceInfo(
    @PotentialNonAsciiContent
    val model: String,
    val modelAsAscii: String,
    val sensors: Set<String>,
    /**
     * If not null, the app is running inside an emulator / virtualized Android environment.
     * This flag is intentionally separated from [model] to avoid breaking model-based lookups
     * (e.g. sensors DB / device matching).
     */
    val emulatorKind: EmulatorKind? = null
)


fun DeviceInfo.hasBiometricSensors(): Boolean {
    return hasFingerprint() || hasFaceID() || hasIrisScanner() || hasPalmID() || hasVoiceID() || hasHeartrateID()
}

fun DeviceInfo.hasFingerprint(): Boolean {
    val deviceInfo = this
    for (str in deviceInfo.sensors) {
        val s = str.lowercase(Locale.ROOT)
        if (s.contains("fingerprint")) {
            return true
        }
    }
    return false
}

fun DeviceInfo.hasUnderDisplayFingerprint(): Boolean {
    val deviceInfo = this
    for (str in deviceInfo.sensors) {
        val s = str.lowercase(Locale.ROOT)
        if (s.contains("fingerprint") && (s.contains(" display") || s.contains(" screen"))) {
            return true
        }
    }
    return false
}

fun DeviceInfo.hasIrisScanner(): Boolean {
    val deviceInfo = this
    for (str in deviceInfo.sensors) {
        val s = str.lowercase(Locale.ROOT)
        if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                " unlock"
            ) || s.contains(
                " auth"
            )
        ) {
            if (s.contains("iris")) {
                return true
            }
        }
    }
    return false
}

fun DeviceInfo.hasFaceID(): Boolean {
    val deviceInfo = this
    for (str in deviceInfo.sensors) {
        val s = str.lowercase(Locale.ROOT)
        if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                " unlock"
            ) || s.contains(
                " auth"
            )
        ) {
            if (s.contains("face")) {
                return true
            }
        }
    }
    return false
}

fun DeviceInfo.hasVoiceID(): Boolean {
    val deviceInfo = this
    for (str in deviceInfo.sensors) {
        val s = str.lowercase(Locale.ROOT)
        if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                " unlock"
            ) || s.contains(
                " auth"
            )
        ) {
            if (s.contains("voice")) {
                return true
            }
        }
    }
    return false
}

fun DeviceInfo.hasPalmID(): Boolean {
    val deviceInfo = this
    for (str in deviceInfo.sensors) {
        val s = str.lowercase(Locale.ROOT)
        if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                " unlock"
            ) || s.contains(
                " auth"
            )
        ) {
            if (s.contains("palm")) {
                return true
            }
        }
    }
    return false
}

fun DeviceInfo.hasHeartrateID(): Boolean {
    val deviceInfo = this
    for (str in deviceInfo.sensors) {
        val s = str.lowercase(Locale.ROOT)
        if (s.contains(" id") || s.contains(" scanner") || s.contains(" recognition") || s.contains(
                " unlock"
            ) || s.contains(
                " auth"
            )
        ) {
            if (s.contains("heartrate")) {
                return true
            }
        }
    }
    return false
}
