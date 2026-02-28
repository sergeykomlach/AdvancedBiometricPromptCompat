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

package dev.skomlach.biometric.app.utils

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.device.DeviceInfoManager
import dev.skomlach.common.logging.LogCat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


object MailTo {
    private val BUFFER = 1024 * 1024 * 8

    private fun zip(_files: Array<String>, zipFileName: File) {
        try {
            zipFileName.delete()
            zipFileName.createNewFile()
            val dest = FileOutputStream(zipFileName)
            val out = ZipOutputStream(
                BufferedOutputStream(
                    dest
                )
            )
            for (i in _files.indices) {
                val file = File(_files[i])

                val fi = FileInputStream(file)
                val origin = BufferedInputStream(fi, BUFFER)
                val entry = ZipEntry(file.name)
                out.putNextEntry(entry)
                var count: Int
                val data = ByteArray(BUFFER)
                while (origin.read(data, 0, BUFFER).also { count = it } != -1) {
                    out.write(data, 0, count)
                }
                origin.close()
                fi.close()

            }
            out.flush()
            out.close()
            dest.close()
        } catch (e: Exception) {
            zipFileName.delete()
            e.printStackTrace()
        }
    }

    private fun getAPKs(context: Context, pkg: String): List<String> {
        val apks: MutableSet<String> = HashSet()
        try {
            val applicationInfo = context.packageManager.getApplicationInfo(pkg, 0)
            apks.add(applicationInfo.sourceDir)
            apks.add(applicationInfo.publicSourceDir)
            if (Build.VERSION.SDK_INT >= 21) {
                if (applicationInfo.splitSourceDirs != null) {
                    apks.addAll(listOf(*applicationInfo.splitSourceDirs ?: emptyArray()))
                }
                if (applicationInfo.splitPublicSourceDirs != null) {
                    apks.addAll(listOf(*applicationInfo.splitPublicSourceDirs ?: emptyArray()))
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return ArrayList(apks)
    }

    private fun findServices(context: Context): List<File> {
        val packages = context.packageManager.getInstalledPackages(0)
        val list = mutableListOf<File>()
        packages.forEach { pi ->
            if (pi.packageName.contains("lock", ignoreCase = true)) {

                val s = pi.packageName.lowercase()
                if (s.contains("fingerprint")
                    || s.contains("face")
                    || s.contains("iris")
                    || s.contains("biometric")
                    || s.contains("palm")
                    || s.contains("voice")
                    || s.contains("heartrate")
                    || s.contains("behavior")

                ) {
                    list.addAll(getAPKs(context, pi.packageName).map {
                        File(it)
                    })
                }

            }
        }

        return list
    }

    fun startMailClient(
        ctx: Activity,
        to: String,
        subject: String,
        logData: String
    ) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val list = findServices(ctx).toMutableList()
            list.add(File(ctx.cacheDir, "deviceapi.log"))
            startMailClient(ctx, to, subject, list)
        }

    }

    private fun startMailClient(
        ctx: Activity,
        to: String,
        subject: String,
        list: List<File>
    ) {

        try {
            val dir = File(ctx.filesDir, "zipfiles").also {
                if (!it.exists()) it.mkdirs()
            }
            val file = File(dir, BiometricPromptCompat.deviceInfo?.model + "_biometric_log.zip")
            zip(list.map { it.absolutePath }.toTypedArray(), file)
            val fileUri: Uri? = try {
                FileProvider.getUriForFile(
                    ctx,
                    "dev.skomlach.biometric.fileprovider",
                    file
                )
            } catch (e: IllegalArgumentException) {
                LogCat.logException(
                    e,
                    "File Selector",
                    "The selected file can't be shared: $file"
                )
                null
            }
            // or
            val builder: ShareCompat.IntentBuilder = ShareCompat.IntentBuilder(ctx)
            builder.setType("message/rfc822")
            builder.addEmailTo(to)
            builder.setText("See attachment")
            builder.setStream(fileUri)
            builder.setSubject(subject)

            builder.setChooserTitle("Send e-mail")

            if (ctx.packageManager.queryIntentActivities(builder.intent, 0).isNotEmpty()) {
                builder.startChooser()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

    }

    @Throws(IOException::class)
    private fun fastCopy(src: InputStream?, dest: OutputStream?) {
        val inputChannel = Channels.newChannel(src)
        val outputChannel = Channels.newChannel(dest)
        fastCopy(inputChannel, outputChannel)
        inputChannel.close()
        outputChannel.close()
    }

    @Throws(IOException::class)
    private fun fastCopy(src: ReadableByteChannel, dest: WritableByteChannel) {
        val buffer = ByteBuffer.allocateDirect(16 * 1024)
        while (src.read(buffer) != -1) {
            buffer.flip()
            dest.write(buffer)
            buffer.compact()
        }
        buffer.flip()
        while (buffer.hasRemaining()) {
            dest.write(buffer)
        }
    }
}