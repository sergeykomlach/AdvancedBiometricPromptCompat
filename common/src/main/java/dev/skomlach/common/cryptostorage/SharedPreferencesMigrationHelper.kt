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

package dev.skomlach.common.cryptostorage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import dev.skomlach.common.misc.ExecutorHelper
import java.io.File
import java.util.*

object SharedPreferencesMigrationHelper {
    fun migrate(
        context: Context,
        name: String,
        src: SharedPreferences,
        dest: SharedPreferences
    ) {
        try {
            val allSrc = src.all?.filter {
                !it.key.startsWith("__androidx_security_crypto_encrypted_prefs_")
            }
            if (allSrc == null || allSrc.isEmpty()) {
                return
            }
            val e = dest.edit()
            for (k in allSrc.keys) {
                when (val v = allSrc[k]) {
                    is String -> {
                        e.putString(k, v)
                    }
                    is Long -> {
                        e.putLong(k, v)
                    }
                    is Int -> {
                        e.putInt(k, v)
                    }
                    is Boolean -> {
                        e.putBoolean(k, v)
                    }
                    is Float -> {
                        e.putFloat(k, v)
                    }
                    is Set<*> -> {
                        e.putStringSet(k, HashSet(v as Set<String>))
                    }
                }
            }
            e.commit()
            return
        } finally {
            src.edit().clear().commit()
            ExecutorHelper.startOnBackground { deletePreferencesFile(context, "$name.xml") }
        }
    }

    private fun deletePreferencesFile(context: Context, name: String) {
        findAndDelete(ContextCompat.getDataDir(context), name)
    }

    private fun findAndDelete(
        fileOrDirectory: File?,
        fileName: String
    ) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return
        if (fileOrDirectory.isDirectory) {
            val files = fileOrDirectory.listFiles()
            if (files != null && files.isNotEmpty()) {
                for (child in files) {
                    findAndDelete(child, fileName)
                }
            }
        } else {
            if (fileOrDirectory.name == fileName) {
                fileOrDirectory.delete()
            }
        }
    }
}