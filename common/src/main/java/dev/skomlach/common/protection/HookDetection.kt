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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.FileReader


object HookDetection {
    private var job: Job? = null


    fun detect(listener: HookDetectionListener) {
        if (job?.isActive == true) return
        job = GlobalScope.launch(Dispatchers.IO) {
            try {
                if (hooksDetection()) {
                    listener.onDetected(true)
                    return@launch
                }
            } catch (e: Throwable) {
                LogCat.logError(
                    "HookDetection",
                    e.message, e
                )
            }
            listener.onDetected(false)
        }
    }

    private fun hooksDetection(): Boolean {
        return checkMethodsHooking() || checkMapping() || checkSelfMapping()
    }

    private fun checkMapping(): Boolean {
        try {
            val libraries: MutableSet<String> = mutableSetOf()
            val mapsFilename = "/proc/" + android.os.Process.myPid() + "/maps"
            val reader = BufferedReader(FileReader(mapsFilename))
            var line: String = ""
            while (reader.readLine()?.also { line = it } != null) {
                if (line.endsWith(".so") || line.endsWith(".jar")) {
                    val n = line.lastIndexOf(" ")
                    libraries.add(line.substring(n + 1))
                }
            }
            for (library in libraries) {
                if (library.contains("re.frida.server") || library.contains("libfrida-gadget")) {
                    LogCat.logError(
                        "HookDetection",
                        "Frida object found: $library"
                    )
                    return true
                }
                if (library.contains("com.saurik.substrate")) {
                    LogCat.logError(
                        "HookDetection",
                        "Substrate shared object found: $library"
                    )
                    return true
                }
                if (library.contains("XposedBridge.jar")) {
                    LogCat.logError(
                        "HookDetection",
                        "Xposed JAR found: $library"
                    )
                    return true
                }
            }
            reader.close()
        } catch (e: java.lang.Exception) {
            LogCat.logError(
                "HookDetection",
                e
            )
        }

        return false
    }

    private fun checkSelfMapping(): Boolean {
        try {
            val libraries: MutableSet<String> = mutableSetOf()
            val mapsFilename = "/proc/self/maps"
            val reader = BufferedReader(FileReader(mapsFilename))
            var line: String = ""
            while (reader.readLine()?.also { line = it } != null) {
                if (line.endsWith(".so") || line.endsWith(".jar")) {
                    val n = line.lastIndexOf(" ")
                    libraries.add(line.substring(n + 1))
                }
            }
            for (library in libraries) {
                if (library.contains("re.frida.server") || library.contains("libfrida-gadget")) {
                    LogCat.logError(
                        "HookDetection",
                        "Frida object found: $library"
                    )
                    return true
                }
                if (library.contains("com.saurik.substrate")) {
                    LogCat.logError(
                        "HookDetection",
                        "Substrate shared object found: $library"
                    )
                    return true
                }
                if (library.contains("XposedBridge.jar")) {
                    LogCat.logError(
                        "HookDetection",
                        "Xposed JAR found: $library"
                    )
                    return true
                }
            }
            reader.close()
        } catch (e: java.lang.Exception) {
            LogCat.logError(
                "HookDetection",
                e
            )
        }

        return false
    }

    private fun checkMethodsHooking(): Boolean {
        try {
            throw Exception()
        } catch (e: Exception) {
            for (stackTraceElement in e.stackTrace) {
                if (stackTraceElement.className == "com.saurik.substrate.MS$2" && stackTraceElement.methodName == "invoked") {
                    LogCat.logError(
                        "HookDetection",
                        "A method on the stack trace has been hooked using Substrate."
                    )
                    return true
                }
                if (stackTraceElement.className == "de.robv.android.xposed.XposedBridge" && stackTraceElement.methodName == "handleHookedMethod") {
                    LogCat.logError(
                        "HookDetection",
                        "A method on the stack trace has been hooked using Xposed."
                    )
                    return true
                }
            }
        }

        return false
    }

    interface HookDetectionListener {
        fun onDetected(flag: Boolean)
    }
}