/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

object LogCat {
    private val started = AtomicBoolean(false)
    private var handler: Handler = Handler(Looper.getMainLooper())
    private val cache: MutableList<String> = ArrayList()
    private var log2ViewCallback: Log2ViewCallback? = null
    private var FILTER = ""
    fun setFilter(filter: String) {
        FILTER = filter
    }

    fun setLog2ViewCallback(log2ViewCallback: Log2ViewCallback?) {
        LogCat.log2ViewCallback = log2ViewCallback
        log2ViewCallback?.log(log)
    }

    fun start() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            started.set(true)
            try {
                Runtime.getRuntime().exec("logcat -c")
            } catch (ignore: Throwable) {
            }
            try {
                val pq = Runtime.getRuntime().exec("logcat v main")
                val stream = BufferedReader(InputStreamReader(pq.inputStream))
                var log = ""
                while (started.get()) {
                    if (stream.readLine()?.also { log = it } != null) {
                        val temp = truncate(log)
                        cache.add(temp)
                        if (log2ViewCallback != null && (FILTER.isEmpty() || temp.contains(
                                FILTER
                            ))
                        ) {
                            handler.post { log2ViewCallback?.log(temp) }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val log: String
        get() {
            val stringBuilder = StringBuilder()
            for (i in cache.indices) {
                if (FILTER.isNullOrEmpty() || cache[i].contains(FILTER)) {
                    stringBuilder.append(cache[i])
                    if (i < cache.size - 1) {
                        stringBuilder.append("\n")
                    }
                }
            }
            return stringBuilder.toString()
        }

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