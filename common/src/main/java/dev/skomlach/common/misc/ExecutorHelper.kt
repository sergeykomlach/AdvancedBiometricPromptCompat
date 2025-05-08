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

import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.skomlach.common.logging.LogCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object ExecutorHelper {
    val handler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }
    val executor: Executor by lazy {
        HandlerExecutor()
    }

    //https://proandroiddev.com/what-is-faster-and-in-which-tasks-coroutines-rxjava-executor-952b1ff62506
    val backgroundExecutor: Executor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Executors.newWorkStealingPool(100)
    } else {
        Executors.newFixedThreadPool(100)
    }

    private val dispatcher = backgroundExecutor.asCoroutineDispatcher()
    val scope = CoroutineScope(dispatcher)
    fun startOnBackground(task: Runnable, delay: Long) {
        scope.launch(Dispatchers.IO) {
            delay(delay)
            task.run()
        }
    }

    fun startOnBackground(task: Runnable) {
        scope.launch(Dispatchers.IO) {
            task.run()
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
            handler.post(runnable)
        }
    }
}