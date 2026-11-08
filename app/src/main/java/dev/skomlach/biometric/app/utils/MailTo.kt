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

import android.content.Context
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dev.skomlach.biometric.app.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.logging.LogCat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


object MailTo {
    private val BUFFER = 1024 * 1024 * 8

    private fun zip(files: List<File>, destination: File, checkActive: () -> Unit) {
        val buffer = ByteArray(BUFFER)
        val entryNames = HashSet<String>()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { output ->
            for ((index, file) in files.withIndex()) {
                checkActive()
                BufferedInputStream(FileInputStream(file)).use { input ->
                    val entryName = if (entryNames.add(file.name)) file.name else "$index/${file.name}"
                    output.putNextEntry(ZipEntry(entryName))
                    var count = input.read(buffer)
                    while (count != -1) {
                        checkActive()
                        output.write(buffer, 0, count)
                        count = input.read(buffer)
                    }
                    output.closeEntry()
                }
            }
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

    @Suppress("UNUSED_PARAMETER")
    fun startMailClient(
        ctx: FragmentActivity,
        to: String,
        subject: String,
        logData: String
    ) {
        val appContext = ctx.applicationContext
        ctx.lifecycleScope.launch {
            val fileUri = withContext(Dispatchers.IO) {
                val report = File(appContext.cacheDir, "deviceapi.log")
                val list = findServices(appContext).toMutableList()
                if (report.isFile) list.add(report)
                val dir = File(appContext.cacheDir, "zipfiles").apply { mkdirs() }
                var archive: File? = null
                var prepared = false
                try {
                    val file = File.createTempFile("biometric-", ".zip", dir)
                    archive = file
                    zip(list, file) { ensureActive() }
                    ensureActive()
                    FileProvider.getUriForFile(appContext, "dev.skomlach.biometric.fileprovider", file)
                        .also { prepared = true }
                } catch (e: IOException) {
                    LogCat.logException(e, "Preparing biometric report")
                    null
                } catch (e: IllegalArgumentException) {
                    LogCat.logException(e, "Preparing biometric report")
                    null
                } finally {
                    if (!prepared) archive?.delete()
                }
            } ?: return@launch
            if (ctx.isFinishing || !ctx.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            val builder: ShareCompat.IntentBuilder = ShareCompat.IntentBuilder(ctx)
            builder.setType("message/rfc822")
            builder.addEmailTo(to)
            builder.setText(ctx.getString(R.string.see_attachment))
            builder.setStream(fileUri)
            builder.setSubject(subject)

            builder.setChooserTitle(ctx.getString(R.string.send_email))

            try {
                if (ctx.packageManager.queryIntentActivities(builder.intent, 0).isNotEmpty()) {
                    builder.startChooser()
                }
            } catch (e: ActivityNotFoundException) {
                LogCat.logException(e, "Opening report chooser")
            }
        }
    }
}
