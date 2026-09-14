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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.ContextCompat
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.text.Charsets.UTF_8

object SharedPreferenceProvider {
    private val protectedPreferencesCache = ConcurrentHashMap<String, SharedPreferences>()

    fun getPreferences(name: String): SharedPreferences {
        return appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun getProtectedPreferences(name: String): SharedPreferences {
        return protectedPreferencesCache.getOrPut(name) {
            val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && !appContext.isDeviceProtectedStorage
            ) {
                appContext.createDeviceProtectedStorageContext()
            } else {
                appContext
            }
            EncryptedSharedPreferences(targetContext, name)
        }
    }

    fun warmUpProtectedStorage(vararg names: String) {
        val normalizedNames = names
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        ExecutorHelper.startOnBackground {
            runCatching {
                EncryptionConfig.primaryInstance
                EncryptionConfig.legacyDeviceIdInstance
                EncryptionConfig.secondaryInstance
                normalizedNames.forEach { getProtectedPreferences(it) }
            }.onFailure {
                LogCat.logException(it, "SharedPreferenceProvider.warmUpProtectedStorage")
            }
        }
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
            private const val ANDROID_KEYSTORE = "AndroidKeyStore"
            private const val KEYSTORE_ALIAS = "BiometricCompatProtectedPrefs"
            private const val KEYSTORE_WRAPPED_KEY = "bio_key_v2"
            private const val KEYSTORE_SALT = "bio_hash_v2"

            @Volatile
            var failClosedWhenKeyStoreUnavailable: Boolean = true

            val secondaryInstance: EncryptionConfig by lazy {
                getFileBasedEncryptionConfig()
            }

            val legacyDeviceIdInstance: EncryptionConfig? by lazy {
                getDeviceIdEncryptionConfig()
            }

            val primaryInstance: EncryptionConfig? by lazy {
                try {
                    getKeyStoreBackedEncryptionConfig()
                } catch (error: Exception) {
                    if (failClosedWhenKeyStoreUnavailable) {
                        throw ProtectedStorageUnavailableException(
                            "Android Keystore is unavailable; protected preferences fallback is disabled",
                            error
                        )
                    }
                    legacyDeviceIdInstance
                }
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
                return KeyMaterialFile.readOrCreate(
                    File(getDataDir(), fileName), size, { secureRandomBytes(size) }
                )
            }

            private fun getFileBasedEncryptionConfig(): EncryptionConfig {
                val password = readOrCreateBytes("bio_key", 32)
                val salt = readOrCreateBytes("bio_hash", 128)
                return EncryptionConfig(password, salt)
            }

            private fun getKeyStoreBackedEncryptionConfig(): EncryptionConfig {
                check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { "Keystore AES requires API 23" }
                val passwordExists = File(getDataDir(), KEYSTORE_WRAPPED_KEY).exists()
                val saltExists = File(getDataDir(), KEYSTORE_SALT).exists()
                check(passwordExists == saltExists) { "Incomplete protected key material" }
                    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                        check(!passwordExists && !saltExists) { "Keystore key for existing data is missing" }
                        val keyGenerator = KeyGenerator.getInstance(
                            KeyProperties.KEY_ALGORITHM_AES,
                            ANDROID_KEYSTORE
                        )
                        keyGenerator.init(
                            KeyGenParameterSpec.Builder(
                                KEYSTORE_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                            )
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setRandomizedEncryptionRequired(true)
                                .build()
                        )
                        keyGenerator.generateKey()
                    }
                    val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
                        ?: throw ProtectedStorageUnavailableException("Cannot read protected preferences key")
                    val password = readOrCreateWrappedBytes(KEYSTORE_WRAPPED_KEY, 32, secretKey)
                    val salt = readOrCreateWrappedBytes(KEYSTORE_SALT, 128, secretKey)
                    return EncryptionConfig(password, salt)
            }

            private fun readOrCreateWrappedBytes(
                fileName: String,
                size: Int,
                secretKey: SecretKey
            ): ByteArray {
                return KeyMaterialFile.readOrCreate(
                    File(getDataDir(), fileName), size, { secureRandomBytes(size) },
                    decode = { decryptWithKeyStore(secretKey, it) },
                    encode = { encryptWithKeyStore(secretKey, it) }
                )
            }

            private fun encryptWithKeyStore(secretKey: SecretKey, cleartext: ByteArray): ByteArray {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(cleartext)
                return byteArrayOf(iv.size.toByte()) + iv + ciphertext
            }

            private fun decryptWithKeyStore(secretKey: SecretKey, encrypted: ByteArray): ByteArray {
                val ivSize = encrypted.firstOrNull()?.toInt() ?: 0
                require(ivSize > 0 && encrypted.size > ivSize + 1)
                val iv = encrypted.copyOfRange(1, 1 + ivSize)
                val ciphertext = encrypted.copyOfRange(1 + ivSize, encrypted.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                return cipher.doFinal(ciphertext)
            }

            private fun getDeviceIdEncryptionConfig(): EncryptionConfig? {
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

                val deviceID = deviceID().trim()
                if (deviceID.isEmpty()) return null
                val password = deviceID().reversed().toByteArray(UTF_8)
                val bytes = readOrCreateBytes("bio_hash", 128)
                return EncryptionConfig(password, bytes.reversedArray())
            }
        }
    }
}
