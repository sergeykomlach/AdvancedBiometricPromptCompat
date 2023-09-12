/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ExecutorHelper {


    val handler: Handler = Handler(Looper.getMainLooper())
    val executor: Executor = HandlerExecutor()

    val backgroundExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val tasksInMain = Collections.synchronizedMap(WeakHashMap<Runnable, Job>())

    private fun addTaskSafely(task: Runnable, job: Job) {

        try {
            tasksInMain[task] = job
        } catch (e: Throwable) {
            LogCat.logException(e, "addTaskSafely")
        }

    }

    private fun removeTaskSafely(task: Runnable) {
        try {
            tasksInMain.remove(task)
        } catch (e: Throwable) {
            LogCat.logException(e, "removeTaskSafely")
        }

    }

    fun startOnBackground(task: Runnable, delay: Long) {
        val job = GlobalScope.launch(backgroundExecutor.asCoroutineDispatcher()) {
            delay(delay)
            task.run()
        }
        addTaskSafely(task, job)
    }

    fun startOnBackground(task: Runnable) {
        val job = GlobalScope.launch(backgroundExecutor.asCoroutineDispatcher()) {
            task.run()
        }
        addTaskSafely(task, job)
    }

    fun postDelayed(task: Runnable, delay: Long) {

        val job = GlobalScope.launch(Dispatchers.Main) {
            delay(delay)
            task.run()
            removeTaskSafely(task)
        }
        addTaskSafely(task, job)
    }

    fun post(task: Runnable) {

        val job = GlobalScope.launch(Dispatchers.Main) {
            task.run()
            removeTaskSafely(task)
        }
        addTaskSafely(task, job)


    }

    fun removeCallbacks(task: Runnable) {
        try {
            tasksInMain[task]?.cancel()
            tasksInMain.remove(task)
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