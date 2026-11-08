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

import android.os.Debug
import android.os.SystemClock
import androidx.annotation.Keep
import dev.skomlach.common.logging.LogCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object HookDetection {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nativeProbeRunning = AtomicBoolean()

    fun detect(listener: HookDetectionListener) {
        val diagnostics = LogCat.DEBUG
        // Capture the invoking stack here. Materialize and inspect its frames on the worker.
        val caller = Throwable()
        scope.launch {
            val mappings = runCatching {
                val deadline = SystemClock.elapsedRealtime() + MAP_SCAN_BUDGET_MS
                File("/proc/self/maps").bufferedReader().use { reader ->
                    scanHookMappings(reader) { SystemClock.elapsedRealtime() < deadline }
                }
            }.getOrDefault(HookMappingScan.INCOMPLETE)
            val debuggerAttached = runCatching { Debug.isDebuggerConnected() }.getOrDefault(false)
            val detected = debuggerAttached || mappings == HookMappingScan.DETECTED ||
                caller.stackTrace.any(::isHookDispatchFrame)
            if (diagnostics && mappings == HookMappingScan.INCOMPLETE) {
                LogCat.log("HookDetection", "Executable mapping scan incomplete")
            }
            withContext(Dispatchers.Main) {
                listener.onDetected(detected)
            }
        }
        if (diagnostics && nativeProbeRunning.compareAndSet(false, true)) {
            scope.launch {
                try {
                    NativeHookProbe.logDiagnostics()
                } finally {
                    nativeProbeRunning.set(false)
                }
            }
        }
    }

    interface HookDetectionListener {
        fun onDetected(flag: Boolean)
    }
}

private const val MAP_SCAN_BUDGET_MS = 150L
private const val MAX_MAP_CHARS = 2 * 1024 * 1024

internal enum class HookMappingScan {
    DETECTED,
    COMPLETE,
    INCOMPLETE
}

private val fridaLibraries = setOf(
    "frida-agent.so", "frida-agent-32.so", "frida-agent-64.so", "frida-gadget.so",
    "frida-agent-arm.so", "frida-agent-arm64.so"
)
private val hookBridges = setOf("libxposed_art.so", "libxposed.so", "libsubstrate.so")

internal fun scanHookMappings(reader: BufferedReader, withinBudget: () -> Boolean): HookMappingScan {
    var remaining = MAX_MAP_CHARS
    while (remaining > 0 && withinBudget()) {
        val line = reader.readLine() ?: return HookMappingScan.COMPLETE
        remaining -= line.length + 1
        if (isExecutableHookMapping(line)) return HookMappingScan.DETECTED
    }
    return HookMappingScan.INCOMPLETE
}

internal fun isExecutableHookMapping(line: String): Boolean {
    // /proc/self/maps fields before the optional pathname are address, permissions, offset,
    // device and inode. Read permissions independently of text found inside a path.
    val permissions = line.indexOf(' ') + 1
    if (permissions == 0 || line.getOrNull(permissions + 2) != 'x') return false
    val path = line.indexOf('/', permissions + 4)
    if (path < 0) return false
    val basename = line.substringAfterLast('/').removeSuffix(" (deleted)").removePrefix("memfd:")
    return basename in hookBridges || basename.removePrefix("lib") in fridaLibraries
}

internal fun isHookDispatchFrame(frame: StackTraceElement): Boolean =
    when (frame.className) {
        "de.robv.android.xposed.XposedBridge" -> frame.methodName == "handleHookedMethod"
        "com.saurik.substrate.MS\$2" -> frame.methodName == "invoked"
        else -> false
    }

@Keep
private object NativeHookProbe {
    private external fun probeNative(): Boolean

    fun logDiagnostics() {
        val found = try {
            System.loadLibrary("abpc_hookprobe")
            probeNative()
        } catch (_: LinkageError) {
            null
        } catch (_: SecurityException) {
            null
        }
        val status = when (found) {
            true -> "known instrumentation library present"
            false -> "no known instrumentation library in loader list"
            null -> "probe unavailable"
        }
        LogCat.log("HookDetection", "JNI diagnostics: $status")
    }
}
