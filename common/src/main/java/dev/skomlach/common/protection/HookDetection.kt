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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object HookDetection {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nativeProbeSlot = Semaphore(1)
    private val nativeCompromise = AtomicBoolean()

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
            val managedDetected = debuggerAttached || mappings == HookMappingScan.DETECTED ||
                caller.stackTrace.any(::isHookDispatchFrame)
            if (diagnostics && mappings == HookMappingScan.INCOMPLETE) {
                LogCat.log("HookDetection", "Executable mapping scan incomplete")
            }
            val native = if (diagnostics || !managedDetected) scanNative() else null
            if (diagnostics) NativeHookProbe.logDiagnostics(native)
            withContext(Dispatchers.Main) {
                listener.onDetected(managedDetected || native == true || nativeCompromise.get())
            }
        }
    }

    private suspend fun scanNative(): Boolean? {
        if (nativeCompromise.get()) return true
        return withTimeoutOrNull(NATIVE_SCAN_BUDGET_MS) {
            nativeProbeSlot.acquire()
            // The loader lock can block JNI. Keep the worker independent of the caller's
            // deadline, and hold the slot until it returns so probes cannot pile up.
            scope.async {
                try {
                    NativeHookProbe.detect().also { if (it == true) nativeCompromise.set(true) }
                } finally {
                    nativeProbeSlot.release()
                }
            }.await()
        }
    }

    interface HookDetectionListener {
        fun onDetected(flag: Boolean)
    }
}

private const val MAP_SCAN_BUDGET_MS = 150L
private const val NATIVE_SCAN_BUDGET_MS = 150L
private const val MAX_MAP_CHARS = 2 * 1024 * 1024

internal enum class HookMappingScan {
    DETECTED,
    COMPLETE,
    INCOMPLETE
}

// Length plus FNV-1a of the ASCII name keeps marker literals out of the DEX.
// Keep the loader marker set in sync with hook_probe.c.
private val loaderMarkers = mapOf(
    14 to setOf(0xf361f7b83177dcd9uL),
    15 to setOf(0x23ffd26eb03642aeuL),
    17 to setOf(0x274e5fe4c829aff1uL, 0xe517093fa14c877cuL),
    18 to setOf(0xd3fa34aef3623a10uL),
    20 to setOf(0xcc4a252c9ee8d802uL)
)
private val bridgeMarkers = mapOf(
    12 to setOf(0x2bdbf4160c062aa5uL),
    15 to setOf(0x4e98789263e8e191uL),
    16 to setOf(0xd77d225e0da60af3uL)
)

private fun String.markerFingerprint(): ULong? {
    var fingerprint = 14695981039346656037uL
    for (character in this) {
        if (character.code > 0x7f) return null
        fingerprint = (fingerprint xor character.code.toULong()) * 1099511628211uL
    }
    return fingerprint
}

private fun String.matchesMarker(markers: Map<Int, Set<ULong>>): Boolean {
    val expected = markers[length] ?: return false
    val fingerprint = markerFingerprint() ?: return false
    return fingerprint in expected
}

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
    return basename.matchesMarker(bridgeMarkers) || basename.removePrefix("lib").matchesMarker(loaderMarkers)
}

internal fun isHookDispatchFrame(frame: StackTraceElement): Boolean =
    when (frame.className.length) {
        35 -> frame.className.markerFingerprint() == 0x31df45461d5aa03duL &&
            frame.methodName.length == 18 && frame.methodName.markerFingerprint() == 0xe669077dc00c10c8uL
        25 -> frame.className.markerFingerprint() == 0xcd256a038d0313aeuL &&
            frame.methodName.length == 7 && frame.methodName.markerFingerprint() == 0x32258b4a9bfeb38buL
        else -> false
    }

@Keep
private object NativeHookProbe {
    private external fun probeNative(): Boolean

    fun detect(): Boolean? = try {
        System.loadLibrary("abpc_hookprobe")
        probeNative()
    } catch (_: LinkageError) {
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        null
    }

    fun logDiagnostics(found: Boolean?) {
        val status = when (found) {
            true -> "known instrumentation library present"
            false -> "no known instrumentation library in loader list"
            null -> "probe unavailable"
        }
        LogCat.log("HookDetection", "JNI diagnostics: $status")
    }
}
