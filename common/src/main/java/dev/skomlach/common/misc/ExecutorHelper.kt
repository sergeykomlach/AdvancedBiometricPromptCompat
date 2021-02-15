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