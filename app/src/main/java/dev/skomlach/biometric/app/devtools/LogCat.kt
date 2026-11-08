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

package dev.skomlach.biometric.app.devtools

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

object LogCat {
    internal const val MAX_TEXT_LENGTH = 65_536
    private const val MAX_LINES = 128
    private const val MAX_LINE_LENGTH = MAX_TEXT_LENGTH / MAX_LINES - 1
    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val cache = ArrayDeque<String>()
    private val pending = ArrayDeque<String>()
    private var log2ViewCallback: Log2ViewCallback? = null
    private var filter = ""
    private val dispatchPending = Runnable { deliverPending() }

    @Synchronized
    fun setFilter(filter: String) {
        this.filter = filter
        pending.clear()
    }

    fun setLog2ViewCallback(log2ViewCallback: Log2ViewCallback?) {
        val snapshot = synchronized(this) {
            this.log2ViewCallback = log2ViewCallback
            pending.clear()
            if (log2ViewCallback == null) return
            cache.filter(::matchesFilter).joinToString("\n")
        }
        log2ViewCallback?.log(snapshot)
    }

    @Synchronized
    fun clearLog2ViewCallback(owner: Log2ViewCallback) {
        if (log2ViewCallback === owner) setLog2ViewCallback(null)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                val process = Runtime.getRuntime().exec("logcat -v brief -b main")
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { stream ->
                        while (started.get()) {
                            val line = stream.readLine() ?: break
                            remember(truncate(line).take(MAX_LINE_LENGTH))
                        }
                    }
                } finally {
                    process.destroy()
                }
            } catch (error: Exception) {
                error.printStackTrace()
            } finally {
                started.set(false)
            }
        }
    }

    @Synchronized
    private fun remember(line: String) {
        if (cache.size == MAX_LINES) cache.removeFirst()
        cache.addLast(line)
        if (log2ViewCallback == null || !matchesFilter(line)) return
        if (pending.isEmpty()) {
            handler.removeCallbacks(dispatchPending)
            handler.postDelayed(dispatchPending, 100L)
        }
        if (pending.size == MAX_LINES) pending.removeFirst()
        pending.addLast(line)
    }

    private fun deliverPending() {
        val (callback, batch) = synchronized(this) {
            val callback = log2ViewCallback ?: return
            if (pending.isEmpty()) return
            callback to pending.joinToString("\n").also { pending.clear() }
        }
        callback.log(batch)
    }

    private fun matchesFilter(line: String): Boolean = filter.isEmpty() || line.contains(filter)

    private fun truncate(log: String): String {
        return log.replaceFirst("([\\d-\\s:.]*)".toRegex(), "")
    }

    fun stop() {
        started.set(false)
    }

    interface Log2ViewCallback {
        fun log(log: String)
    }

}