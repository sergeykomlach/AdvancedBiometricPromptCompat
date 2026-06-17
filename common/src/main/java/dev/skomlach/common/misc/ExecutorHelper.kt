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

package dev.skomlach.common.misc

import android.os.Handler
import android.os.Looper
import dev.skomlach.common.logging.LogCat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay as coroutineDelay
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object ExecutorHelper {
    private const val MAX_BACKGROUND_THREADS = 8
    private val backgroundThreadCounter = AtomicInteger(0)

    val handler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }
    val executor: Executor by lazy {
        HandlerExecutor()
    }

    val backgroundExecutor: Executor = Executors.newFixedThreadPool(
        MAX_BACKGROUND_THREADS,
        ThreadFactory { runnable ->
            Thread(
                runnable,
                "BiometricCompat-bg-${backgroundThreadCounter.incrementAndGet()}"
            ).apply {
                isDaemon = true
            }
        }
    )

    private val dispatcher = backgroundExecutor.asCoroutineDispatcher()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LogCat.logException(throwable, "ExecutorHelper")
    }
    val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)

    fun startOnBackground(task: Runnable, delay: Long) {
        scope.launch {
            coroutineDelay(delay)
            runCatching {
                task.run()
            }.onFailure {
                LogCat.logException(it, "startOnBackground")
            }
        }
    }

    fun startOnBackground(task: Runnable) {
        scope.launch {
            runCatching {
                task.run()
            }.onFailure {
                LogCat.logException(it, "startOnBackground")
            }
        }
    }

    fun postDelayed(task: Runnable, delay: Long) {
        handler.postDelayed(task, delay)
    }

    fun post(task: Runnable) {
        handler.post(task)
    }

    fun removeCallbacks(task: Runnable) {
        try {
            handler.removeCallbacks(task)
        } catch (e: Throwable) {
            LogCat.logException(e, "removeCallbacks")
        }
    }

    /**
     * An [Executor] which posts to a [Handler].
     */
    class HandlerExecutor : Executor {
        override fun execute(runnable: Runnable) {
            post(runnable)
        }
    }
}
