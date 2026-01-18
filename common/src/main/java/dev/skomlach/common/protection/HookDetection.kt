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

import dev.skomlach.common.logging.LogCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetAddress
import java.net.Socket

object HookDetection {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun detect(listener: HookDetectionListener) {
        if (job?.isActive == true) return
        job = scope.launch {
            val result = hooksDetection()
            withContext(Dispatchers.Main) {
                listener.onDetected(result)
            }
        }
    }

    private fun hooksDetection(): Boolean {

        if (checkMemoryMappings()) return true

        if (checkSuspiciousClasses()) return true

        if (checkStackForHooks()) return true

        if (checkFridaPorts()) return true

        return false
    }

    private fun checkMemoryMappings(): Boolean {
        val filesToScan = arrayOf("/proc/self/maps")
        for (filePath in filesToScan) {
            try {
                val file = File(filePath)
                if (!file.exists()) continue

                BufferedReader(FileReader(file)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val mapLine = line?.lowercase() ?: continue

                        if (mapLine.contains("frida") ||
                            mapLine.contains("gum-js-loop") ||
                            mapLine.contains("gmain") ||
                            mapLine.contains("xposed") ||
                            mapLine.contains("substrate") ||
                            mapLine.contains("com.saurik.substrate")
                        ) {
                            LogCat.logError("HookDetection", "Found suspicious mapping: $mapLine")
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                LogCat.logError("HookDetection", "Mapping check error", e)
            }
        }
        return false
    }

    private fun checkSuspiciousClasses(): Boolean {
        val suspiciousClasses = arrayOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XposedHelpers",
            "com.saurik.substrate.MS",
            "org.meowcat.edxposed.manager.EdXposedApp",
            "org.lsposed.lsposed.LSPosed",
            "io.github.libxposed.api.XposedInterface"
        )

        for (className in suspiciousClasses) {
            try {
                Class.forName(className)
                LogCat.logError("HookDetection", "Suspicious class found: $className")
                return true
            } catch (e: ClassNotFoundException) {
                // Class not present, proceed
            }
        }
        return false
    }

    private fun checkStackForHooks(): Boolean {
        try {
            throw Exception("Stack Trace Hook Detection")
        } catch (e: Exception) {
            for (stackTraceElement in e.stackTrace) {
                val className = stackTraceElement.className
                val methodName = stackTraceElement.methodName

                if (className == "com.saurik.substrate.MS$2" && methodName == "invoked") return true
                if (className == "de.robv.android.xposed.XposedBridge" && methodName == "handleHookedMethod") return true

                if (className.contains("xposed.XposedBridge") || className.contains("lsposed.lsposed")) {
                    LogCat.logError(
                        "HookDetection",
                        "Hooking method detected in stack: $className.$methodName"
                    )
                    return true
                }
            }
        }
        return false
    }

    private fun checkFridaPorts(): Boolean {
        val fridaPorts = intArrayOf(27042, 27047)
        for (port in fridaPorts) {
            try {
                Socket(InetAddress.getByName("localhost"), port).use {
                    LogCat.logError("HookDetection", "Frida port $port is open")
                    return true
                }
            } catch (e: Exception) {
            }
        }
        return false
    }

    interface HookDetectionListener {
        fun onDetected(flag: Boolean)
    }
}