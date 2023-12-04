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

import android.database.Cursor
import android.net.Uri
import android.util.Base64
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Matcher
import java.util.regex.Pattern

//Dev tool

object DeviceSettings {
    private val pattern = Pattern.compile("\\[(.+)\\]: \\[(.+)\\]")
    fun printAll() {
        printSetting()
        printProperties()
    }

    private fun printSetting() {
        try {
            val subSettings = arrayOf("system", "global", "secure")
            for (sub in subSettings) {
                val u = Uri.parse("content://settings/$sub")
                val mCur = appContext
                    .contentResolver
                    .query(
                        u, null,
                        null,
                        null,
                        null
                    )
                if (mCur != null) {
                    mCur.moveToFirst()
                    while (!mCur.isAfterLast) {
                        try {
                            val nameIndex = mCur
                                .getColumnIndexOrThrow("name")
                            if (!mCur.isNull(nameIndex)) {
                                val valueIndex = try {
                                    mCur
                                        .getColumnIndexOrThrow("values")
                                } catch (ignore: Throwable) {
                                    mCur
                                        .getColumnIndexOrThrow("value")
                                }
                                val type = mCur.getType(valueIndex)
                                val name = mCur.getString(nameIndex)
                                when (type) {
                                    Cursor.FIELD_TYPE_BLOB -> LogCat.log(
                                        "SystemSettings: $sub - $name:" + Base64.encodeToString(
                                            mCur.getBlob(valueIndex),
                                            Base64.DEFAULT
                                        )
                                    )

                                    Cursor.FIELD_TYPE_FLOAT -> LogCat.log(
                                        "SystemSettings: $sub - $name:" + mCur.getFloat(
                                            valueIndex
                                        )
                                    )

                                    Cursor.FIELD_TYPE_INTEGER -> LogCat.log(
                                        "SystemSettings: $sub - $name:" + mCur.getInt(
                                            valueIndex
                                        )
                                    )

                                    Cursor.FIELD_TYPE_NULL -> LogCat.log("SystemSettings: $sub - $name:NULL")
                                    Cursor.FIELD_TYPE_STRING -> LogCat.log(
                                        "SystemSettings: $sub - $name:" + mCur.getString(
                                            valueIndex
                                        )
                                    )

                                    else -> LogCat.log("SystemSettings: $sub - $name: unknown type - $type")
                                }
                            }
                        } catch (e: Throwable) {
                            LogCat.logException(e)
                        }
                        mCur.moveToNext()
                    }

                    mCur.close()
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    private fun printProperties() {
        var line: String? = null
        var m: Matcher
        try {
            val p = Runtime.getRuntime().exec("getprop")
            val input = BufferedReader(InputStreamReader(p.inputStream))
            while (input.readLine()?.also { line = it } != null) {
                m = pattern.matcher(line ?: continue)
                if (m.find()) {
                    val result = m.toMatchResult()
                    val key = result.group(1)
                    val value = result.group(2)
                    LogCat.log("SystemProperties: $line")
                }
            }
            input.close()
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }
}