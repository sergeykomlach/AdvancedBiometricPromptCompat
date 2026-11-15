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

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dev.skomlach.biometric.app.R
import dev.skomlach.biometric.app.devtools.LogCat as CapturedLog
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.logging.LogCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private const val ARCHIVE_DIRECTORY = "zipfiles"
    private const val ARCHIVE_PREFIX = "biometric-"
    private const val ARCHIVE_TTL_MS = 60 * 60 * 1000L
    private val archiveCleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialLog = Regex(
        """(?i)\b(?:authorization|cookie|password|passcode|pin|(?:access|refresh|id)?[_-]?token|api[_-]?key)\b["']?\s*[:=]|\bbearer\s+"""
    )

    private fun zip(
        files: List<File>,
        entryName: String,
        report: String,
        destination: File,
        checkActive: () -> Unit
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val entryNames = hashSetOf(entryName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { output ->
            checkActive()
            output.putNextEntry(ZipEntry(entryName))
            output.write(report.toByteArray(Charsets.UTF_8))
            output.closeEntry()
            for ((index, file) in files.withIndex()) {
                checkActive()
                BufferedInputStream(FileInputStream(file)).use { input ->
                    val name = if (entryNames.add(file.name)) file.name else "$index/${file.name}"
                    output.putNextEntry(ZipEntry(name))
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

    fun startMailClient(
        ctx: FragmentActivity,
        to: String,
        subject: String,
        logData: String
    ) {
        shareArchive(ctx, to, subject, "biometric.log", { logAttachment(logData) }) { emptyList() }
    }

    internal fun logAttachment(logData: String): String {
        val start = (logData.length - CapturedLog.MAX_TEXT_LENGTH).coerceAtLeast(0)
        val tail = logData.substring(start)
        val completeLines = if (start > 0 && logData[start - 1] != '\n') tail.substringAfter('\n', "") else tail
        return completeLines.lineSequence().joinToString("\n") { line ->
            if (credentialLog.containsMatchIn(line)) "[Redacted]" else line
        }
    }

    internal fun startDeviceReport(
        ctx: FragmentActivity,
        to: String,
        subject: String,
        report: String
    ) {
        shareArchive(ctx, to, subject, "deviceapi.log", { report }, ::findServices)
    }

    internal fun resumeArchiveCleanup(context: Context) {
        val appContext = context.applicationContext
        archiveCleanup.launch {
            File(appContext.cacheDir, "deviceapi.log").delete()
            File(appContext.cacheDir, ARCHIVE_DIRECTORY).listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith(ARCHIVE_PREFIX) && file.extension == "zip") {
                    val remaining = (ARCHIVE_TTL_MS - (System.currentTimeMillis() - file.lastModified()))
                        .coerceIn(0L, ARCHIVE_TTL_MS)
                    expireArchive(file, remaining)
                }
            }
        }
    }

    private fun expireArchive(file: File, remaining: Long) {
        archiveCleanup.launch {
            delay(remaining)
            file.delete()
        }
    }

    private fun shareArchive(
        ctx: FragmentActivity,
        to: String,
        subject: String,
        entryName: String,
        report: () -> String,
        attachments: (Context) -> List<File>
    ) {
        val appContext = ctx.applicationContext
        ctx.lifecycleScope.launch {
            var archive: File? = null
            var shared = false
            try {
                val fileUri = withContext(Dispatchers.IO) {
                    val dir = File(appContext.cacheDir, ARCHIVE_DIRECTORY).apply { mkdirs() }
                    val file = File.createTempFile(ARCHIVE_PREFIX, ".zip", dir)
                    archive = file
                    zip(attachments(appContext), entryName, report(), file) { ensureActive() }
                    ensureActive()
                    FileProvider.getUriForFile(appContext, "dev.skomlach.biometric.fileprovider", file)
                }
                if (ctx.isFinishing || !ctx.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                val builder = ShareCompat.IntentBuilder(ctx)
                    .setType("message/rfc822")
                    .addEmailTo(to)
                    .setText(ctx.getString(R.string.see_attachment))
                    .setStream(fileUri)
                    .setSubject(subject)
                    .setChooserTitle(ctx.getString(R.string.send_email))
                builder.startChooser()
                shared = true
            } catch (e: ActivityNotFoundException) {
                LogCat.logException(e, "Opening report chooser")
            } catch (e: IOException) {
                LogCat.logException(e, "Preparing biometric report")
            } catch (e: IllegalArgumentException) {
                LogCat.logException(e, "Preparing biometric report")
            } finally {
                archive?.let { file ->
                    if (shared) expireArchive(file, ARCHIVE_TTL_MS)
                    else withContext(NonCancellable + Dispatchers.IO) { file.delete() }
                }
            }
        }
    }
}
