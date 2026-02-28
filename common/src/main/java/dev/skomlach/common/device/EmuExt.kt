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

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import dev.skomlach.common.contextprovider.AndroidContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

/**
 * Backwards compatible helper.
 * Prefer [smartCapitalize] for new code.
 */
fun capitalize(text: String?): String = smartCapitalize(text)

/**
 * Title-case helper that keeps short acronyms (e.g. "ROG", "ASUS") intact and normalizes separators.
 */
internal fun smartCapitalize(text: String?): String {
    if (text.isNullOrBlank()) return ""
    return text
        .trim()
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            val t = token.trim()
            val isAcronym = t.length in 2..5 && t.all { it.isLetter() && it.uppercaseChar() == it }
            when {
                isAcronym -> t
                t.equals("asus", true) -> "ASUS"
                t.equals("rog", true) -> "ROG"
                t.equals("cpu", true) -> "CPU"
                t.equals("gpu", true) -> "GPU"
                t.equals("oppo", true) -> "OPPO"
                t.equals("poco", true) -> "POCO"
                t.equals("iqoo", true) -> "iQOO"
                t.equals("tecno", true) -> "TECNO"
                t.equals("cmf", true) -> "CMF"
                t.equals("huawei", true) -> "HUAWEI"
                t.equals("honor", true) -> "HONOR"
                t.equals("zte", true) -> "ZTE"
                t.equals("lg", true) -> "LG"
                t.equals("htc", true) -> "HTC"
                else -> t.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }
}

/**
 * Returns emulator kind if this runtime looks like an emulator/virtualized environment.
 * Covers Android Studio emulator, Genymotion, BlueStacks, Nox, MEmu, LDPlayer, Andy, etc.
 */
fun detectEmulatorKind(): EmulatorKind? = EmulatorDetector.detect()

enum class EmulatorKind {
    ANDROID_EMULATOR, GENYMOTION, BLUESTACKS, NOX, MEMU, LDPLAYER, ANDY, VIRTUAL_MACHINE
}


object EmulatorDetector {

    fun detect(): EmulatorKind? {
        // Most robust (survives spoofed Build.MODEL/BRAND)
        checkKnownPackages(AndroidContext.appContext)?.let { return it }

        // Fast heuristics
        checkBuildProps()?.let { return it }
        checkSpecificFiles()?.let { return it }
        checkSystemProps()?.let { return it }

        // Deeper / noisier heuristics
        if (isGenericVirtualMachine()) return identifySpecificEmulator()
        if (isSensorAnomaly()) return identifySpecificEmulator()
        return null
    }

    private fun checkKnownPackages(context: Context): EmulatorKind? {
        val candidates = listOf(
            // MEmu / Microvirt
            "com.microvirt.tools" to EmulatorKind.MEMU,
            "com.microvirt.download" to EmulatorKind.MEMU,
            "com.memu.launcher" to EmulatorKind.MEMU,

            // BlueStacks
            "com.bluestacks.appmart" to EmulatorKind.BLUESTACKS,
            "com.bluestacks.settings" to EmulatorKind.BLUESTACKS,

            // Nox
            "com.nox.mopen.app" to EmulatorKind.NOX
        )

        for ((pkg, kind) in candidates) {
            if (isPackageInstalled(context, pkg)) return kind
        }
        return null
    }

    private fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun identifySpecificEmulator(): EmulatorKind? {
        // 1) getprop markers (survive most spoofing)
        val propChecks = mapOf(
            // MEmu/Microvirt
            "ro.microvirt.version" to EmulatorKind.MEMU,
            "ro.microvirt.name" to EmulatorKind.MEMU,
            "ro.meizu.rom.config" to EmulatorKind.MEMU, // MEmu sometimez mimic to Meizu

            // Nox
            "ro.nox.virt.prop" to EmulatorKind.NOX,
            "ro.nox.version" to EmulatorKind.NOX,

            // LDPlayer
            "ro.ld.vbox86" to EmulatorKind.LDPLAYER,

            // Genymotion / VBox
            "ro.vbox.rdp.port" to EmulatorKind.GENYMOTION
        )

        for ((prop, kind) in propChecks) {
            if (getProp(prop).isNotEmpty()) return kind
        }

        // 2) Filesystem fingerprints
        val fileChecks = mapOf(
            EmulatorKind.MEMU to arrayOf(
                "/system/bin/microvirt-prop",
                "/system/bin/memu-prop",
                "/dev/memu_temp",
                "/sys/module/memu_gear",
                "/dev/socket/microvirt-port"
            ),
            EmulatorKind.NOX to arrayOf(
                "/system/bin/nox-prop",
                "/system/bin/nox-vbox-guest",
                "/dev/nox_temp"
            ),
            EmulatorKind.LDPLAYER to arrayOf(
                "/system/bin/ld_prop",
                "/system/bin/ldplayer-prop",
                "/dev/ld_temp"
            ),
            EmulatorKind.BLUESTACKS to arrayOf(
                "/system/bin/bst_prop",
                "/system/lib/libhstub.so"
            )
        )

        for ((kind, paths) in fileChecks) {
            if (paths.any { File(it).exists() }) return kind
        }

        // 3) Network heuristics
        return NetworkEmulatorDetector.identifyByNetwork()
    }

    private fun isSensorAnomaly(): Boolean {
        return try {
            val sm = AndroidContext.appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val hasProximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
            val sensorList = sm.getSensorList(Sensor.TYPE_ALL)
            sensorList.size < 5 || (!hasProximity && !Build.MODEL.contains("Tablet", true))
        } catch (_: Throwable) {
            false
        }
    }

    private fun checkSpecificFiles(): EmulatorKind? {
        val fileMap = mapOf(
            // MEmu (Microvirt)
            arrayOf(
                "/system/bin/microvirt-prop",
                "/dev/socket/microvirt-port",
                "/system/bin/memu-prop"
            ) to EmulatorKind.MEMU,

            // Nox
            arrayOf("/system/bin/nox-prop", "/dev/nox_temp", "/system/bin/nox-vbox-guest") to EmulatorKind.NOX,

            // LDPlayer
            arrayOf("/system/bin/ld_prop", "/system/bin/ldplayer-prop") to EmulatorKind.LDPLAYER,

            // BlueStacks
            arrayOf("/system/lib/libhstub.so", "/system/bin/bst_prop") to EmulatorKind.BLUESTACKS,

            // Genymotion
            arrayOf("/dev/vboxguest", "/dev/vboxuser") to EmulatorKind.GENYMOTION,

            // Generic AVD
            arrayOf(
                "/dev/qemu_pipe",
                "/dev/socket/qemud",
                "/system/lib/libc_malloc_debug_qemu.so"
            ) to EmulatorKind.ANDROID_EMULATOR
        )

        for ((paths, kind) in fileMap) {
            if (paths.any { File(it).exists() }) return kind
        }
        return null
    }

    private fun checkSystemProps(): EmulatorKind? {
        // MEmu
        if (getProp("ro.microvirt.version").isNotEmpty()) return EmulatorKind.MEMU
        if (getProp("ro.microvirt.name").isNotEmpty()) return EmulatorKind.MEMU

        // Nox
        if (getProp("ro.nox.virt.prop").isNotEmpty() || getProp("ro.nox.version").isNotEmpty())
            return EmulatorKind.NOX

        // VBox (Genymotion)
        val vboxPort = getProp("ro.vbox.rdp.port")
        if (vboxPort.isNotEmpty() && vboxPort != "0") return EmulatorKind.GENYMOTION

        return null
    }

    private fun isGenericVirtualMachine(): Boolean {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText().lowercase(Locale.ROOT)
            cpuInfo.contains("hypervisor") || cpuInfo.contains("qemu") || cpuInfo.contains("vbox")
        } catch (_: Throwable) {
            false
        }
    }

    private fun getProp(propName: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", propName))
            val out = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            runCatching { process.waitFor() }
            out.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun checkBuildProps(): EmulatorKind? {
        val board = (Build.BOARD ?: "").lowercase(Locale.ROOT)
        val hardware = (Build.HARDWARE ?: "").lowercase(Locale.ROOT)
        val finger = (Build.FINGERPRINT ?: "").lowercase(Locale.ROOT)
        val model = (Build.MODEL ?: "").lowercase(Locale.ROOT)
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val brand = (Build.BRAND ?: "").lowercase(Locale.ROOT)
        val product = (Build.PRODUCT ?: "").lowercase(Locale.ROOT)

        return when {
            finger.contains("vbox") || finger.contains("genymotion") -> EmulatorKind.GENYMOTION

            model.contains("google_sdk") ||
                    model.contains("emulator") ||
                    finger.startsWith("generic") ||
                    hardware.contains("goldfish") ||
                    hardware.contains("ranchu") ||
                    product.contains("sdk_gphone") ||
                    product.contains("sdk_google") ||
                    (brand == "google" && product.contains("sdk")) -> EmulatorKind.ANDROID_EMULATOR

            board.contains("vbox") || finger.contains("vbox") -> EmulatorKind.GENYMOTION

            model.contains("bluestacks") || model.contains("bst") -> EmulatorKind.BLUESTACKS

            hardware.contains("nox") || board.contains("nox") -> EmulatorKind.NOX

            hardware.contains("ttvm") ||
                    hardware.contains("microvirt") ||
                    manufacturer.contains("microvirt") ||
                    brand.contains("microvirt") -> EmulatorKind.MEMU

            else -> null
        }
    }
}

object NetworkEmulatorDetector {

    fun identifyByNetwork(): EmulatorKind? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue

                val name = intf.name.lowercase(Locale.ROOT)

                // eth0 на "телефоні" - дуже сильна ознака емулятора.
                if (name.contains("eth") || name.contains("vboxnet")) {
                    return identifySpecificByMac(intf.hardwareAddress)
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun identifySpecificByMac(mac: ByteArray?): EmulatorKind {
        if (mac == null) return EmulatorKind.ANDROID_EMULATOR

        val macStr = mac.joinToString(":") { "%02x".format(it) }.lowercase(Locale.ROOT)

        return when {
            macStr.startsWith("08:00:27") -> EmulatorKind.GENYMOTION // VirtualBox
            macStr.startsWith("52:54:00") -> EmulatorKind.ANDROID_EMULATOR // QEMU/KVM
            else -> {
                if (checkDefaultGateway("10.0.2.2")) return EmulatorKind.ANDROID_EMULATOR
                EmulatorKind.MEMU
            }
        }
    }

    private fun checkDefaultGateway(ip: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("ip", "route", "show"))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            output.contains(ip)
        } catch (_: Throwable) {
            false
        }
    }
}
