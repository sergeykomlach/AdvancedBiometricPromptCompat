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


import android.app.Application
import android.os.FileObserver
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.anrwatchdog.ANRWatchDog
import java.io.File
import java.text.DecimalFormat

class AppMonitoringDevTools(val app: Application) {
    private val threadPolicy = StrictMode.getThreadPolicy()
    private val vmPolicy = StrictMode.getVmPolicy()
    private var anrWatchDog: ANRWatchDog? = null
    private var enable: Boolean = false
    private var fileObserver: FileObserver? = null

    private val FILE_SIZE_LIMIT = 524288

    init {
        try {
            val path = try {
                val dir = ContextCompat.getDataDir(app)
                if (dir?.exists() == false)
                    dir.mkdirs()
                dir?.absolutePath ?: app.applicationInfo.dataDir
            } catch (e: Throwable) {
                app.applicationInfo.dataDir
            }
            val allExceptAccessFlags = FileObserver.MODIFY or
                    FileObserver.ATTRIB or
                    FileObserver.CLOSE_WRITE or
                    FileObserver.MOVED_FROM or
                    FileObserver.MOVED_TO or
                    FileObserver.DELETE or
                    FileObserver.CREATE or
                    FileObserver.DELETE_SELF or
                    FileObserver.MOVE_SELF

            fileObserver = object : FileObserver(path, allExceptAccessFlags) {
                override fun onEvent(event: Int, p: String?) {
                    scanRecursivly(path, File(path))
                }
            }
        } catch (e: Throwable) {
            Log.e("AppMonitoringDevTools", e.message, e)
        }
    }


    private fun scanRecursivly(path: String, fileOrDirectory: File?) {
        try {
            if (fileOrDirectory?.isDirectory == true) {
                val files = fileOrDirectory.listFiles()
                if (files != null && files.isNotEmpty()) {
                    for (child in files) {
                        scanRecursivly(path, child)
                    }
                }
            } else {
                if (fileOrDirectory?.length() ?: return >= FILE_SIZE_LIMIT) {//SK: everything that larger than N mb - need to check
                    var file = fileOrDirectory.absolutePath
                    if (file.startsWith(path)) {
                        file = file.substring(path.length, file.length)
                    }
                    Log.e(
                        "AppMonitoringDevTools",
                        "Found large file $file with size ${getReadableFileSize(fileOrDirectory.length() ?: 0)}"
                    )
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun enableMonitoringTools(enable: Boolean) {

        this.enable = enable

        LeakCanaryConfig.setup(enable)

        if (enable) {
            //Log ANR's, always
            if (anrWatchDog == null) {
                anrWatchDog = ANRWatchDog()
                    .setLogThreadsWithoutStackTrace(true)
                    .setIgnoreDebugger(true)
                    .setReportMainThreadOnly()
                    .setInterruptionListener { exception ->
                        Log.e(
                            "ANRWatchDog", "onInterrupted",
                            exception
                        )
                    }
                    .setANRListener { error ->
                        Log.e(
                            "ANRWatchDog", "onAppNotResponding",
                            error
                        )
                    }
            }

            anrWatchDog?.start()
            fileObserver?.startWatching()
        } else {
            try {
                anrWatchDog?.interrupt()
                anrWatchDog = null
            } catch (ignore: InterruptedException) {

            }
            fileObserver?.stopWatching()
        }

        if (enable) {
            //Build-in API
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectAll()
                    .permitDiskWrites()//allow file write operations in main thread
                    .permitDiskReads()//allow file read operations in main thread
                    .penaltyLog()
                    //.penaltyDialog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        } else {
            //suppress all warnings
            StrictMode.setVmPolicy(vmPolicy)
            StrictMode.setThreadPolicy(threadPolicy)
        }


    }

    private fun getReadableFileSize(size: Long): String {
        val BYTES_IN_KILOBYTES = 1024
        val dec = DecimalFormat("###.#")
        val KILOBYTES = " KB"
        val MEGABYTES = " MB"
        val GIGABYTES = " GB"
        var fileSize = 0f
        var suffix = KILOBYTES
        if (size > BYTES_IN_KILOBYTES) {
            fileSize = (size / BYTES_IN_KILOBYTES).toFloat()
            if (fileSize > BYTES_IN_KILOBYTES) {
                fileSize /= BYTES_IN_KILOBYTES
                if (fileSize > BYTES_IN_KILOBYTES) {
                    fileSize /= BYTES_IN_KILOBYTES
                    suffix = GIGABYTES
                } else {
                    suffix = MEGABYTES
                }
            }
        }
        return dec.format(fileSize.toDouble()) + suffix
    }
}