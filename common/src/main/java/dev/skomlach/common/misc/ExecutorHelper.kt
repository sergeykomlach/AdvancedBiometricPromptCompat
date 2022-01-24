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
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Executor

object ExecutorHelper {

    val handler: Handler = Handler(Looper.getMainLooper())
    val executor: Executor = HandlerExecutor()
    private val tasksInMain = Collections.synchronizedMap(
        mutableMapOf<JobHolder, Job>()
    )

    private fun isMain(): Boolean = Looper.getMainLooper().thread === Thread.currentThread()

    fun startOnBackground(task: Runnable, delay: Long) {
        val job = GlobalScope.launch(Dispatchers.IO) {
            delay(delay)
            task.run()
        }
        tasksInMain[JobHolder(task.hashCode(), WeakReference<Runnable>(task))] = job
    }

    fun startOnBackground(task: Runnable) {
        if (!isMain()) {
            task.run()
        } else {
            val job = GlobalScope.launch(Dispatchers.IO) {
                task.run()
            }
            tasksInMain[JobHolder(task.hashCode(), WeakReference<Runnable>(task))] = job
        }
    }

    fun postDelayed(t: Runnable, delay: Long) {
        val code = t.hashCode()
        val job = GlobalScope.launch(Dispatchers.Main) {
            delay(delay)
            val holder = tasksInMain.keys.firstOrNull {
                it.hasCode == code
            }
            holder?.let {
                it.reference.get()?.run()
                tasksInMain.remove(it)
            }
        }
        tasksInMain[JobHolder(code, WeakReference<Runnable>(t))] = job
    }

    fun post(t: Runnable) {
        if (isMain()) {
            t.run()
        } else {
            val code = t.hashCode()
            val job = GlobalScope.launch(Dispatchers.Main) {
                val holder = tasksInMain.keys.firstOrNull {
                    it.hasCode == code
                }
                holder?.let {
                    it.reference.get()?.run()
                    tasksInMain.remove(it)
                }

            }
            tasksInMain[JobHolder(code, WeakReference<Runnable>(t))] = job
        }
    }

    fun removeCallbacks(task: Runnable) {
        val code = task.hashCode()
        val holder = tasksInMain.keys.firstOrNull {
            it.hasCode == code
        }
        holder?.let {
            tasksInMain[it]?.cancel()
            tasksInMain.remove(it)
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

    data class JobHolder(val hasCode: Int, val reference: WeakReference<Runnable>)
}