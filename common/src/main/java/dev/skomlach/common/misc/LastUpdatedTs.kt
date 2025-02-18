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

package dev.skomlach.common.misc

import android.os.Build
import androidx.core.content.ContextCompat
import dev.skomlach.common.contextprovider.AndroidContext
import java.io.File
import java.security.SecureRandom

object LastUpdatedTs {
    var timestamp: Long = 0
        get() {
            if (field == 0L)
                try {
                    val apks: MutableSet<String> = HashSet()
                    val applicationInfo =
                        AndroidContext.appContext.packageManager.getApplicationInfo(
                            AndroidContext.appContext.packageName,
                            0
                        )
                    applicationInfo.sourceDir?.let {
                        apks.add(it)
                    }
                    applicationInfo.publicSourceDir?.let {
                        apks.add(it)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        applicationInfo.splitSourceDirs?.let {
                            apks.addAll(listOf(*it))
                        }
                        applicationInfo.splitPublicSourceDirs?.let {
                            apks.addAll(listOf(*it))
                        }
                    }

                    scanRecursivly(ContextCompat.getCodeCacheDir(AndroidContext.appContext), apks)
                    var lst: Long = 0
                    for (s in apks) {
                        if (File(s).exists()) {
                            val t = File(s).lastModified()
                            if (t > lst) {
                                lst = t
                            }
                        }
                    }
                    field = lst
                } catch (e: Throwable) {
                    field = SecureRandom().nextLong()
                }
            return field
        }

    private fun scanRecursivly(fileOrDirectory: File?, apks: MutableSet<String>) {
        try {
            if (fileOrDirectory?.isDirectory == true) {
                val files = fileOrDirectory.listFiles()
                if (files != null && files.isNotEmpty()) {
                    for (child in files) {
                        scanRecursivly(child, apks)
                    }
                }
            } else {
                if (fileOrDirectory?.isFile == true && (fileOrDirectory.name.endsWith("dex"))) {
                    apks.add(fileOrDirectory.absolutePath)
                }
            }
        } catch (e: Throwable) {

        }
    }
}