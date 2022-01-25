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

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

object ExecutorHelper {

    val handler: Handler = Handler(Looper.getMainLooper())
    val executor: Executor = HandlerExecutor()

    fun startOnBackground(task: Runnable) {
        AsyncTask.execute(task)
    }

    fun postDelayed(task: Runnable, delay: Long) {
        handler.postDelayed(task, delay)
    }

    fun post(task: Runnable) {
        handler.post(task)
    }

    fun removeCallbacks(task: Runnable) {
        handler.removeCallbacks(task)
    }

    /**
     * An [Executor] which posts to a [Handler].
     */
    class HandlerExecutor() : Executor {
        override fun execute(runnable: Runnable) {
            handler.post(runnable)
        }
    }
}