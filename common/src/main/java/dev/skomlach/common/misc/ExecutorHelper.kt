/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

object ExecutorHelper {

    val lock = ReentrantLock()
    val handler: Handler = Handler(Looper.getMainLooper())
    val executor: Executor = HandlerExecutor()

    val backgroundExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val tasksInMain = WeakHashMap<Runnable, Job>()

    private fun isMain(): Boolean = Looper.getMainLooper().thread === Thread.currentThread()

    private fun addTaskSafely(task: Runnable, job: Job) {
        if (lock.tryLock(1, TimeUnit.SECONDS)) {
            try {
                tasksInMain[task] = job
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                lock.runCatching {
                    this.unlock()
                }
            }
        }
    }

    private fun removeTaskSafely(task: Runnable) {
        if (lock.tryLock(1, TimeUnit.SECONDS)) {
            try {
                tasksInMain.remove(task)
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                lock.runCatching {
                    this.unlock()
                }
            }
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
        if (!isMain()) {
            task.run()
        } else {
            val job = GlobalScope.launch(backgroundExecutor.asCoroutineDispatcher()) {
                task.run()
            }
            addTaskSafely(task, job)
        }

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
        if (isMain()) {
            task.run()
        } else {
            val job = GlobalScope.launch(Dispatchers.Main) {
                task.run()
                removeTaskSafely(task)
            }
            addTaskSafely(task, job)
        }

    }

    fun removeCallbacks(task: Runnable) {
        if (lock.tryLock(1, TimeUnit.SECONDS)) {
            try {
                tasksInMain[task]?.cancel()
                tasksInMain.remove(task)
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                lock.runCatching {
                    this.unlock()
                }
            }
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