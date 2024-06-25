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

package dev.skomlach.biometric.compat.utils

import android.content.Context
import android.os.Build
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.SystemStringsHelper
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object CheckBiometricUI {
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

    @Throws(Exception::class)
    private fun checkApk(
        fileZip: String
    ): Boolean {

        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(fileZip)
            val entries = zipFile.entries()
            val zipEntries: MutableList<ZipEntry> = ArrayList()

            // iterate through all the entries
            while (entries.hasMoreElements()) {

                // get the zip entry
                zipEntries.add(entries.nextElement())
            }
            zipEntries.sortWith { o1, o2 -> o1.name.compareTo(o2.name) }
            for (zip in zipEntries) {
                if (zip.name.contains("layout", true) &&
                    (zip.name.contains("biometric", true) || zip.name.contains("fingerprint") ||
                            zip.name.contains("face", true) || zip.name.contains("iris"))
                ) {
                    BiometricLoggerImpl.d("Resource in APK ${zip.name}")
                    return true
                }
            }
        } finally {
            try {
                zipFile?.close()
            } catch (ignore: IOException) {
            }
        }
        return false
    }

    @Throws(Exception::class)
    private fun checkForFront(
        fileZip: String
    ): Boolean {

        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(fileZip)
            val entries = zipFile.entries()
            val zipEntries: MutableList<ZipEntry> = ArrayList()

            // iterate through all the entries
            while (entries.hasMoreElements()) {

                // get the zip entry
                zipEntries.add(entries.nextElement())
            }
            zipEntries.sortWith { o1, o2 -> o1.name.compareTo(o2.name) }
            for (zip in zipEntries) {
                if (zip.name.contains("front", true) &&
                    (zip.name.contains("biometric", true) || zip.name.contains("fingerprint"))
                ) {
                    BiometricLoggerImpl.d("Resource in APK ${zip.name}")
                    return true
                }
            }
        } finally {
            try {
                zipFile?.close()
            } catch (ignore: IOException) {
            }
        }
        return false
    }

    fun hasSomethingFrontSensor(context: Context): Boolean {
        try {
            val apks = getAPKs(context, getBiometricUiPackage(context))
            if (apks.isEmpty())
                return true

            for (f in apks) {
                if (checkForFront(f))
                    return true
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return false
    }

    fun hasExists(context: Context): Boolean {
        try {
            val apks = getAPKs(context, getBiometricUiPackage(context))
            if (apks.isEmpty())
                return true

            for (f in apks) {
                if (checkApk(f))
                    return true
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return false
    }

    fun getBiometricUiPackage(context: Context): String {
        return (SystemStringsHelper.getFromSystem(context, "config_biometric_prompt_ui_package")
            ?: "com.android.systemui").also {
            BiometricLoggerImpl.d("CheckBiometricUI", it)
        }
    }
}