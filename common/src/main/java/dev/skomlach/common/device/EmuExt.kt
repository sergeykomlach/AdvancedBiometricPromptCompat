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

import android.os.Build

fun capitalize(text: String?): String {
    if (text.isNullOrEmpty()) return ""
    return text.split(" ").filter { it.isNotEmpty() }
        .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
}

enum class EmulatorKind {
    ANDROID_EMULATOR,
    GENYMOTION,
    BLUESTACKS,
    NOX,
    MEMU,
    LDPLAYER,
    ANDY,
    UNKNOWN
}

/**
 * Returns emulator kind if this runtime looks like an emulator/virtualized environment.
 * Covers Android Studio emulator, Genymotion, BlueStacks, Nox, MEmu, LDPlayer, Andy, etc.
 */
fun detectEmulatorKind(): EmulatorKind? {
    val fingerprint = Build.FINGERPRINT ?: ""
    val model = Build.MODEL ?: ""
    val product = Build.PRODUCT ?: ""
    val device = Build.DEVICE ?: ""
    val hardware = Build.HARDWARE ?: ""
    val manufacturer = Build.MANUFACTURER ?: ""
    val brand = Build.BRAND ?: ""

    fun c(hay: String, needle: String) = hay.contains(needle, ignoreCase = true)

    // Genymotion (VirtualBox)
    val isGenymotion =
        c(manufacturer, "genymotion") || c(brand, "genymotion") ||
                c(product, "vbox") || c(device, "vbox") || c(hardware, "vbox86")

    // BlueStacks
    val isBlueStacks =
        c(manufacturer, "bluestacks") || c(brand, "bluestacks") ||
                c(product, "bst") || c(device, "bst") || c(fingerprint, "bluestacks")

    // Nox
    val isNox =
        c(manufacturer, "nox") || c(product, "nox") || c(device, "nox") || c(fingerprint, "nox")

    // MEmu / Microvirt
    val isMemu =
        c(manufacturer, "microvirt") || c(manufacturer, "memu") ||
                c(product, "memu") || c(device, "memu") || c(fingerprint, "memu")

    // LDPlayer
    val isLd =
        c(manufacturer, "ldplayer") || c(product, "ld") && c(product, "sdk") || c(
            fingerprint,
            "ldplayer"
        )

    // Andy
    val isAndy =
        c(manufacturer, "andy") || c(product, "andy") || c(fingerprint, "andy")

    // Android Emulator / AOSP generic
    val isGeneric =
        c(fingerprint, "generic") || c(fingerprint, "unknown") || c(fingerprint, "sdk") ||
                c(model, "emulator") || c(model, "android sdk built for") ||
                c(product, "sdk") || c(product, "emulator") ||
                c(device, "generic") ||
                c(hardware, "goldfish") || c(hardware, "ranchu")

    val kind = when {
        isGenymotion -> EmulatorKind.GENYMOTION
        isBlueStacks -> EmulatorKind.BLUESTACKS
        isNox -> EmulatorKind.NOX
        isMemu -> EmulatorKind.MEMU
        isLd -> EmulatorKind.LDPLAYER
        isAndy -> EmulatorKind.ANDY
        isGeneric -> EmulatorKind.ANDROID_EMULATOR
        else -> null
    }

    return kind
}