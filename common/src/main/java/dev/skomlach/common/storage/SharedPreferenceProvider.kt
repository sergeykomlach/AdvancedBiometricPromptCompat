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

package dev.skomlach.common.storage

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings.Secure
import android.util.Base64
import androidx.core.content.ContextCompat
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import java.io.File
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID
import kotlin.text.Charsets.UTF_8

object SharedPreferenceProvider {

    fun getPreferences(name: String): SharedPreferences {
        return appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun getProtectedPreferences(name: String): SharedPreferences {
        val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && !appContext.isDeviceProtectedStorage
        ) {
            appContext.createDeviceProtectedStorageContext()
        } else {
            appContext
        }
        return EncryptedSharedPreferences(targetContext, name)
    }

    data class EncryptionConfig(val password: ByteArray, val salt: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptionConfig

            if (!password.contentEquals(other.password)) return false
            if (!salt.contentEquals(other.salt)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = password.contentHashCode()
            result = 31 * result + salt.contentHashCode()
            return result
        }

        companion object {
            val instance: EncryptionConfig by lazy {
                getEncryptionConfig()
            }

            val legacyInstance: EncryptionConfig by lazy {
                getLegacyEncryptionConfig()
            }

            private fun getDataDir(): File {
                val context = appContext
                val dataDir = File(
                    ContextCompat.getDataDir(context)?.absolutePath
                        ?: context.applicationInfo.dataDir
                )
                if (!dataDir.exists()) {
                    dataDir.mkdirs()
                }
                return dataDir
            }

            private fun secureRandomBytes(size: Int): ByteArray {
                val bytes = ByteArray(size)
                try {
                    val random: SecureRandom =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            SecureRandom.getInstanceStrong()
                        } else {
                            SecureRandom()
                        }
                    random.nextBytes(bytes)
                } catch (e: Throwable) {
                    SecureRandom().nextBytes(bytes)
                }
                return bytes
            }

            private fun readOrCreateBytes(fileName: String, size: Int): ByteArray {
                val file = File(getDataDir(), fileName)

                if (file.exists()) {
                    try {
                        val bytes = file.readBytes()
                        if (bytes.size == size) {
                            return bytes
                        }
                    } catch (_: Throwable) {

                    }
                    runCatching { file.delete() }
                }

                val replacement = secureRandomBytes(size)
                file.writeBytes(replacement)
                file.setReadable(true, true)
                file.setWritable(true, true)
                file.setExecutable(false, false)
                file.setReadOnly()
                return replacement
            }

            private fun getEncryptionConfig(): EncryptionConfig {
                val password = readOrCreateBytes("bio_key", 32)
                val salt = readOrCreateBytes("bio_hash", 128)
                return EncryptionConfig(password, salt)
            }

            private fun getLegacyEncryptionConfig(): EncryptionConfig {
                val context = appContext

                @SuppressLint("HardwareIds")
                fun deviceID(): String {
                    var deviceID = ""
                    val obsoleteId = Secure.getString(
                        context.contentResolver,
                        Secure.ANDROID_ID
                    )
                    if (obsoleteId.isNullOrEmpty()) {
                        val commonPsshUuid = UUID(0x1077EFECC0B24D02L, -0x531cc3e1ad1d04b5L)
                        val clearkeyUuid = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)
                        val widevineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
                        val playReadyUuid = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)
                        val uuids =
                            listOf(widevineUuid, playReadyUuid, clearkeyUuid, commonPsshUuid)
                        uuids.forEach {
                            if (deviceID.isEmpty()) {
                                try {
                                    val drmIdByteArray = MediaDrm(it)
                                        .getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                                    val drmID = Base64.encodeToString(
                                        drmIdByteArray,
                                        Base64.DEFAULT
                                    )
                                    if (!drmID.isNullOrEmpty()) {
                                        deviceID = drmID
                                    }
                                } catch (ignore: Throwable) {
                                }
                            }
                        }
                    } else {
                        deviceID = obsoleteId
                    }

                    return deviceID
                }

                val password = deviceID().reversed().toByteArray(UTF_8)
                val data = File(getDataDir(), "bio_hash")
                var bytes = ByteArray(128)
                if (data.exists()) {
                    val tmp = data.readBytes()
                    if (tmp.size == bytes.size) {
                        bytes = tmp
                    } else {
                        Arrays.fill(bytes, 0x00.toByte())
                    }
                } else {
                    Arrays.fill(bytes, 0x00.toByte())
                }
                return EncryptionConfig(password, bytes.reversedArray())
            }
        }
    }
}
