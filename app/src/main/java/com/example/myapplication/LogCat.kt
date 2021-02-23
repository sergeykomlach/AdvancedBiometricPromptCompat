package com.example.myapplication

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class LogCat private constructor() {

    companion object {
        private var INSTANCE: LogCat? = null
        val instance: LogCat
            get() {
                if (INSTANCE == null) {
                    INSTANCE = LogCat()
                }
                return INSTANCE!!
            }
    }
    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val cache: MutableList<String> = ArrayList()
    private var log2ViewCallback: Log2ViewCallback? = null
    private var FILTER = ""
    fun setFilter(filter: String) {
        FILTER = filter
    }

    fun setLog2ViewCallback(log2ViewCallback: Log2ViewCallback?) {
        this.log2ViewCallback = log2ViewCallback
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
                    if (stream.readLine().also { log = it } != null) {
                        val temp = truncate(log)
                        cache.add(temp)
                        if (log2ViewCallback != null && (TextUtils.isEmpty(FILTER) || temp.contains(
                                FILTER
                            ))
                        ) {
                            handler.post { log2ViewCallback!!.log(temp) }
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
                if (TextUtils.isEmpty(FILTER) || cache[i].contains(FILTER)) {
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
        fun log(string: String?)
    }

}