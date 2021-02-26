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

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import dev.skomlach.common.misc.Utils.isAtLeastR
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class ExecutorHelper private constructor() {
    companion object {
        @JvmField var INSTANCE = ExecutorHelper()
    }

    val handler: Handler = Handler(Looper.getMainLooper())
    val executor: Executor = HandlerExecutor(handler)

    @SuppressLint("StaticFieldLeak")
    fun startOnBackground(task: Runnable) {
        if (isAtLeastR) {
            Executors.newCachedThreadPool().execute(task)
        } else {
            //AsyncTask Deprecated in API 30
            object : AsyncTask<Void?, Void?, Void?>() {
                override fun doInBackground(vararg params: Void?): Void? {
                    task.run()
                    return null
                }
            }.executeOnExecutor(Executors.newCachedThreadPool())
        }
    }

    /**
     * An [Executor] which posts to a [Handler].
     */
    class HandlerExecutor(private val mHandler: Handler) : Executor {
        override fun execute(runnable: Runnable) {
            mHandler.post(runnable)
        }
    }
}