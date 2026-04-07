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

package dev.skomlach.biometric.compat.utils.hardware

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.core.content.edit
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.device.hasFaceID
import dev.skomlach.common.device.hasFingerprint
import dev.skomlach.common.device.hasHeartrateID
import dev.skomlach.common.device.hasIrisScanner
import dev.skomlach.common.device.hasPalmID
import dev.skomlach.common.device.hasVoiceID
import dev.skomlach.common.storage.SharedPreferenceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.security.InvalidKeyException
import java.security.KeyStore
import java.text.Normalizer
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@TargetApi(Build.VERSION_CODES.P)

class BiometricPromptHardware(authRequest: BiometricAuthRequest) :
    AbstractHardware(authRequest) {
    companion object {
        private var canAuthenticatePair =
            Pair<Long, Int>(0, BiometricManager.BIOMETRIC_STATUS_UNKNOWN)
    }

    private val modalityCache =
        HashMap<BiometricType, Pair<Long, BiometricModalityDetector.Result>>()

    private fun modalityResult(type: BiometricType): BiometricModalityDetector.Result {
        val now = System.currentTimeMillis()
        val cached = modalityCache[type]
        if (cached != null && now - cached.first < 1500L) {
            return cached.second
        }
        return BiometricModalityDetector.detect(appContext, type).also {
            modalityCache[type] = now to it
            BiometricLoggerImpl.d("modalityResult[$type]=$it")
        }
    }

    private val canAuthenticate: Int
        get() {
            try {
                return if (System.currentTimeMillis() - canAuthenticatePair.first >= 1000L)
                    getStatusCode().also {
                        canAuthenticatePair = Pair(System.currentTimeMillis(), it)
                    }
                else canAuthenticatePair.second
            } catch (e: Throwable) {
                e(e, "BiometricPromptHardware - canAuthenticate")
                return canAuthenticatePair.second
            }
        }

    private fun getStatusCode(): Int {
        var code = BiometricManager.BIOMETRIC_STATUS_UNKNOWN
        try {
            val biometricManager: BiometricManager = BiometricManager.from(appContext)
            val authenticators = arrayOf(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
                        or BiometricManager.Authenticators.BIOMETRIC_STRONG,
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            for (authenticator in authenticators) {
                code = biometricManager.canAuthenticate(authenticator)
                if (code == BiometricManager.BIOMETRIC_SUCCESS) {
                    break
                }
            }

        } catch (e: Throwable) {
            e(e)
        } finally {
            e("BiometricPromptHardware - canAuthenticate=$code")
        }
        return code
    }

    private val isAnyHardwareAvailable: Boolean
        get() {
            return when (canAuthenticate) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false //really no sensors
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> false //security patch required due to vulnerabilities
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false //Some incompatibility issue
                BiometricManager.BIOMETRIC_SUCCESS -> true //all OK
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> true //some biometric exist, but unable to check properly
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true //sensor Ok, just biometric data missing
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> true //sensor temporary unavailable
                else -> false
            }
        }
    private val isAnyBiometricEnrolled: Boolean
        get() {
            return when (canAuthenticate) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false //really no sensors and enrolled data
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> false //security patch required due to vulnerabilities
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false //Some incompatibility issue
                BiometricManager.BIOMETRIC_SUCCESS -> true //all OK
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> true //some biometric exist, but unable to check properly
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false //no biometric
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
                //sensor temporary unavailable; fallback to legacy
                else -> false
            }
        }

    init {
        EnrollCheckHelper.updateState()
    }

    override val isHardwareAvailable: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyHardwareAvailable else isHardwareAvailableForType(
            biometricAuthRequest.type
        )
    override val isBiometricEnrolled: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyBiometricEnrolled else isBiometricEnrolledForType(
            biometricAuthRequest.type
        )
    override val isLockedOut: Boolean
        get() = if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) isAnyLockedOut else isLockedOutForType(
            biometricAuthRequest.type
        )

    override fun lockout() {
        if (!isLockedOut) {
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                for (type in BiometricType.entries) {
                    if (type == BiometricType.BIOMETRIC_ANY)
                        continue
                    if (isHardwareAvailableForType(type) && isBiometricEnrolledForType(type)) {
                        BiometricLockoutFix.lockout(type)
                    }
                }
            } else
                BiometricLockoutFix.lockout(biometricAuthRequest.type)
        }
    }

    private val isAnyLockedOut: Boolean
        get() {
            if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) return true
            for (type in BiometricType.entries) {
                if (BiometricLockoutFix.isLockOut(type))
                    return true
            }
            return false
        }//legacy

    //OK to check in this way
    private fun isHardwareAvailableForType(type: BiometricType): Boolean {
        if (!isAnyHardwareAvailable) return false

        if (type == BiometricType.BIOMETRIC_FINGERPRINT) {
            return LegacyBiometric.getAvailableBiometricModule(type)?.isHardwarePresent == true
        }
        BiometricPromptCompat.deviceInfo?.let {
            if (it.sensors.isNotEmpty()) {
                return when (type) {
                    BiometricType.BIOMETRIC_FACE -> {
                        if (it.model.startsWith("Samsung", ignoreCase = true)) {
                            (it.hasFaceID() && checkDeviceFeature(type)) || SamsungLegacyBiometricDevices.hasSamsungFaceAndIris(
                                it.model
                            )
                        } else if (it.model.startsWith("Google Pixel", ignoreCase = true)) {
                            (it.hasFaceID() && checkDeviceFeature(type)) || PixelModelChecker.isPixel8OrNewer(
                                it.model
                            )
                        } else
                            it.hasFaceID() && checkDeviceFeature(type)
                    }

                    BiometricType.BIOMETRIC_IRIS -> {
                        if (it.model.startsWith("Samsung", ignoreCase = true)) {
                            it.hasIrisScanner() && checkDeviceFeature(type) || SamsungLegacyBiometricDevices.hasSamsungFaceAndIris(
                                it.model
                            )
                        } else
                            it.hasIrisScanner() && checkDeviceFeature(type)
                    }
                    BiometricType.BIOMETRIC_VOICE -> it.hasVoiceID() && checkDeviceFeature(type)
                    BiometricType.BIOMETRIC_HEARTRATE -> it.hasHeartrateID() && checkDeviceFeature(
                        type
                    )

                    BiometricType.BIOMETRIC_PALMPRINT -> it.hasPalmID() && checkDeviceFeature(type)
                    else -> checkDeviceFeature(type)
                }
            }
        }

        //legacy
        return checkDeviceFeature(type)
    }

    private fun checkDeviceFeature(type: BiometricType): Boolean {
        val result = modalityResult(type)

        return when (type) {
            BiometricType.BIOMETRIC_FACE,
            BiometricType.BIOMETRIC_IRIS,
            BiometricType.BIOMETRIC_FINGERPRINT -> {
                result.hardwarePresent &&
                        result.confidence != BiometricModalityDetector.Confidence.NONE
            }

            BiometricType.BIOMETRIC_VOICE,
            BiometricType.BIOMETRIC_HEARTRATE,
            BiometricType.BIOMETRIC_PALMPRINT -> {
                // only heuristic support
                result.hardwarePresent
            }

            else -> false
        }
    }

    //More or less ok this one
    private fun isLockedOutForType(type: BiometricType): Boolean =
        BiometricLockoutFix.isLockOut(type)

    private fun isBiometricEnrolledForType(type: BiometricType): Boolean {
        if (!isAnyHardwareAvailable) return false

        if (type == BiometricType.BIOMETRIC_FINGERPRINT) {
            return LegacyBiometric.getAvailableBiometricModule(type)?.hasEnrolled == true
        }

        val result = modalityResult(type)
        BiometricPromptCompat.deviceInfo?.let {
            if (it.sensors.isNotEmpty()) {
                return when (type) {
                    BiometricType.BIOMETRIC_FACE -> {
                        if (it.model.startsWith("Samsung", ignoreCase = true)) {
                            result.enrolledLikely || isAnyBiometricEnrolled
                        } else if (it.model.startsWith("Google Pixel", ignoreCase = true)) {
                            result.enrolledLikely || isAnyBiometricEnrolled
                        } else
                            result.enrolledLikely
                    }

                    BiometricType.BIOMETRIC_IRIS -> {
                        if (it.model.startsWith("Samsung", ignoreCase = true)) {
                            result.enrolledLikely || isAnyBiometricEnrolled
                        } else
                            result.enrolledLikely
                    }
                    else -> false
                }
            }
        }
        return when (type) {
            BiometricType.BIOMETRIC_FACE,
            BiometricType.BIOMETRIC_IRIS -> result.enrolledLikely

            BiometricType.BIOMETRIC_VOICE,
            BiometricType.BIOMETRIC_HEARTRATE,
            BiometricType.BIOMETRIC_PALMPRINT -> false

            else -> false
        }
    }

    override val isBiometricEnrollChanged: Boolean
        get() {
            EnrollCheckHelper.updateState()
            return SharedPreferenceProvider.getPreferences("BiometricPromptHardware")
                .getBoolean("isBiometricEnrollChanged", false)
        }

    override
    fun updateBiometricEnrollChanged() {
        try {
            if (isBiometricEnrollChanged) {
                try {
                    EnrollCheckHelper.keyStore.load(null)
                    if (EnrollCheckHelper.keyStore.containsAlias(EnrollCheckHelper.KEY_NAME))
                        EnrollCheckHelper.keyStore.deleteEntry(EnrollCheckHelper.KEY_NAME)
                } finally {
                    SharedPreferenceProvider.getPreferences("BiometricPromptHardware").edit()
                        .clear().apply()
                }
            }
        } catch (e: Throwable) {
            e(e)
        }

    }

    private object EnrollCheckHelper {
        const val KEY_NAME = "BiometricEnrollChanged.test"
        private val mutex = Mutex()
        val keyStore: KeyStore by lazy {
            KeyStore.getInstance("AndroidKeyStore")
        }
        private var job: Job? = null

        init {
            GlobalScope.launch(Dispatchers.Main) {
                AndroidContext.resumedActivityLiveData.observeForever {
                    updateState()
                }
            }
        }

        fun updateState() {
            if (job?.isActive == true) return
            job?.cancel()
            job = GlobalScope.launch(Dispatchers.IO) {

                try {
                    val changed = isChanged()
                    SharedPreferenceProvider.getPreferences("BiometricPromptHardware")
                        .edit()
                        .putBoolean("isBiometricEnrollChanged", changed).apply()
                } catch (e: Throwable) {

                }
            }
        }

        private fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }

        private fun getSecretKey(): SecretKey? {
            // Before the keystore can be accessed, it must be loaded.
            keyStore.load(null)
            return if (keyStore.containsAlias(KEY_NAME))
                keyStore.getKey(KEY_NAME, null) as SecretKey
            else null
        }

        private fun getCipher(): Cipher {
            return Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/"
                        + KeyProperties.BLOCK_MODE_CBC + "/"
                        + KeyProperties.ENCRYPTION_PADDING_PKCS7
            )
        }

        fun isChanged(): Boolean {
            if (!mutex.tryLock()) return false
            try {
                val cipher: Cipher = getCipher()
                var secretKey = getSecretKey()
                if (secretKey == null) {
                    secretKey = generateSecretKey(
                        KeyGenParameterSpec.Builder(
                            KEY_NAME,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                            .setRandomizedEncryptionRequired(false)
                            .apply {
                                setInvalidatedByBiometricEnrollment(true)
                                setUserAuthenticationRequired(true)
                            }
                            .build()
                    )
                }
                try {
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    SharedPreferenceProvider.getPreferences("BiometricPromptHardware")
                        .edit {
                            putBoolean("isBiometricConfirmed", true)
                        }
                } catch (e: KeyPermanentlyInvalidatedException) {
                    return true
                } catch (e: InvalidKeyException) {
                    e(e)
                }
            } catch (e: Throwable) {
                if (e.message?.contains("User changed or deleted their auth credentials") == true)
                    return true
                else if (e.message?.contains("At least one") == true && e.message?.contains("must be enrolled to create keys") == true)
                    return SharedPreferenceProvider.getPreferences("BiometricPromptHardware")
                        .getBoolean("isBiometricConfirmed", false)
                else
                    e(e)
            } finally {
                try {
                    if (mutex.isLocked) mutex.unlock()
                } catch (_: Throwable) {
                }
            }
            return false

        }
    }


    internal object BiometricModalityDetector {

        enum class Confidence {
            CONFIRMED,   // official feature + strong supporting signal
            LIKELY,      // official feature or strong OEM hint
            HEURISTIC,   // only weak/vendor signal
            NONE
        }

        data class Result(
            val type: BiometricType,
            val confidence: Confidence,
            val hardwarePresent: Boolean,
            val enrolledLikely: Boolean,
            val reasons: List<String> = emptyList()
        )

        fun detect(context: Context, type: BiometricType): Result {
            return when (type) {
                BiometricType.BIOMETRIC_FACE -> detectFace(context)
                BiometricType.BIOMETRIC_IRIS -> detectIris(context)
                BiometricType.BIOMETRIC_FINGERPRINT -> detectFingerprint(context)
                BiometricType.BIOMETRIC_VOICE -> detectHeuristicOnly(
                    context,
                    type,
                    "voice",
                    emptyList()
                )

                BiometricType.BIOMETRIC_HEARTRATE -> detectHeuristicOnly(
                    context,
                    type,
                    "heartrate",
                    emptyList()
                )

                BiometricType.BIOMETRIC_PALMPRINT -> detectHeuristicOnly(
                    context,
                    type,
                    "palm",
                    emptyList()
                )

                else -> Result(
                    type,
                    Confidence.NONE,
                    hardwarePresent = false,
                    enrolledLikely = false
                )
            }
        }

        private fun detectFingerprint(context: Context): Result {
            val pm = context.packageManager
            val reasons = mutableListOf<String>()

            val officialFeature = pm.safeHasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            if (officialFeature) reasons += "feature:${PackageManager.FEATURE_FINGERPRINT}"

            val auth = authState(context)
            val enrolledLikely = when {
                auth.strongSuccess || auth.weakSuccess -> true
                auth.noneEnrolled -> false
                else -> false
            }

            val confidence = when {
                officialFeature -> Confidence.CONFIRMED
                else -> Confidence.NONE
            }

            return Result(
                type = BiometricType.BIOMETRIC_FINGERPRINT,
                confidence = confidence,
                hardwarePresent = officialFeature,
                enrolledLikely = enrolledLikely,
                reasons = reasons
            )
        }

        private fun scoreLocalizedStrings(
            strings: List<String>,
            type: BiometricType,
            config: BiometricLexicon.Config = BiometricLexicon.Config()
        ): Pair<Int, Set<String>> {
            var totalScore = 0
            val matched = linkedSetOf<String>()

            for (value in strings) {
                val result = BiometricLexicon.match(value, type, config)
                if (result.matched) {
                    totalScore += result.score
                    matched += result.matchedTokens
                }
            }
            return totalScore to matched
        }

        private fun detectFace(context: Context): Result {
            val pm = context.packageManager
            val reasons = mutableListOf<String>()

            val officialFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                pm.safeHasSystemFeature(PackageManager.FEATURE_FACE)
            } else {
                pm.safeHasSystemFeature("android.hardware.biometrics.face")
            }
            if (officialFeature) reasons += "feature:android.hardware.biometrics.face"

            val auth = authState(context)

            val strings = biometricStrings(context)
            val (faceScore, faceTokens) = scoreLocalizedStrings(
                strings = strings,
                type = BiometricType.BIOMETRIC_FACE
            )

            if (faceScore >= 3) {
                reasons += "strings:face(score=$faceScore,tokens=${faceTokens.joinToString()})"
            }
            val vendorFaceFeature = findRelatedSystemFeatures(
                pm, "face", listOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PackageManager.FEATURE_FACE
                    } else {
                        "android.hardware.biometrics.face"
                    }
                )
            )
            if (vendorFaceFeature.isNotEmpty()) {
                reasons += vendorFaceFeature.map { "vendorFeature:$it" }
            }

            val hardwarePresent = officialFeature || vendorFaceFeature.isNotEmpty()

            val enrolledLikely = when {
                faceScore >= 3 && (auth.weakSuccess || auth.strongSuccess) -> true
                auth.noneEnrolled -> false
                else -> false
            }

            val confidence = when {
                officialFeature && faceScore >= 3 -> Confidence.CONFIRMED
                officialFeature -> Confidence.LIKELY
                vendorFaceFeature.isNotEmpty() && faceScore >= 3 -> Confidence.LIKELY
                vendorFaceFeature.isNotEmpty() -> Confidence.HEURISTIC
                else -> Confidence.NONE
            }

            return Result(
                type = BiometricType.BIOMETRIC_FACE,
                confidence = confidence,
                hardwarePresent = hardwarePresent,
                enrolledLikely = enrolledLikely,
                reasons = reasons
            )
        }

        private fun detectIris(context: Context): Result {
            val pm = context.packageManager
            val reasons = mutableListOf<String>()

            val officialFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                pm.safeHasSystemFeature(PackageManager.FEATURE_IRIS)
            } else {
                pm.safeHasSystemFeature("android.hardware.biometrics.iris")
            }
            if (officialFeature) reasons += "feature:android.hardware.biometrics.iris"

            val auth = authState(context)
            val strings = biometricStrings(context)
            val (irisScore, irisTokens) = scoreLocalizedStrings(
                strings = strings,
                type = BiometricType.BIOMETRIC_IRIS
            )

            if (irisScore >= 3) {
                reasons += "strings:iris(score=$irisScore,tokens=${irisTokens.joinToString()})"
            }

            val vendorIrisFeature = findRelatedSystemFeatures(
                pm, "iris", listOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PackageManager.FEATURE_IRIS
                    } else {
                        "android.hardware.biometrics.iris"
                    }
                )
            )
            if (vendorIrisFeature.isNotEmpty()) {
                reasons += vendorIrisFeature.map { "vendorFeature:$it" }
            }

            val hardwarePresent = officialFeature || vendorIrisFeature.isNotEmpty()

            val enrolledLikely = when {
                irisScore >= 3 && (auth.weakSuccess || auth.strongSuccess) -> true
                auth.noneEnrolled -> false
                else -> false
            }

            val confidence = when {
                officialFeature && irisScore >= 3 -> Confidence.CONFIRMED
                officialFeature -> Confidence.LIKELY
                vendorIrisFeature.isNotEmpty() && irisScore >= 3 -> Confidence.LIKELY
                vendorIrisFeature.isNotEmpty() -> Confidence.HEURISTIC
                else -> Confidence.NONE
            }

            return Result(
                type = BiometricType.BIOMETRIC_IRIS,
                confidence = confidence,
                hardwarePresent = hardwarePresent,
                enrolledLikely = enrolledLikely,
                reasons = reasons
            )
        }

        private fun detectHeuristicOnly(
            context: Context,
            type: BiometricType,
            alias: String,
            systemFeatures: List<String>
        ): Result {
            val pm = context.packageManager
            val reasons = mutableListOf<String>()
            val matched = findRelatedSystemFeatures(pm, alias, systemFeatures)
            if (matched.isNotEmpty()) {
                reasons += matched.map { "vendorFeature:$it" }
            }

            return Result(
                type = type,
                confidence = if (matched.isNotEmpty()) Confidence.HEURISTIC else Confidence.NONE,
                hardwarePresent = matched.isNotEmpty(),
                enrolledLikely = false,
                reasons = reasons
            )
        }

        private fun findRelatedSystemFeatures(
            pm: PackageManager,
            alias: String,
            systemFeatures: List<String>
        ): List<String> {
            val ignoredFeatures = systemFeatures.map { it.lowercase() }
            return pm.systemAvailableFeatures
                ?.mapNotNull(FeatureInfo::name)
                ?.filter { name ->
                    val s = name.lowercase(Locale.ROOT)
                    val a = alias.lowercase(Locale.ROOT)
                    !ignoredFeatures.contains(s) && a in s && (
                            ".hardware." in s ||
                                    ".biometric" in s ||
                                    "unlock" in s ||
                                    "recognition" in s ||
                                    "auth" in s
                            )
                }
                ?.distinct()
                ?.sorted()
                .orEmpty()
        }

        private fun biometricStrings(context: Context): List<String> {
            return try {
                val bm = BiometricManager.from(context)
                buildList {
                    bm.getStrings(BiometricManager.Authenticators.BIOMETRIC_WEAK)?.let { strings ->
                        strings.buttonLabel?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
                        strings.promptMessage?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
                        strings.settingName?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                    bm.getStrings(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        ?.let { strings ->
                            strings.buttonLabel?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
                            strings.promptMessage?.toString()?.takeIf { it.isNotBlank() }
                                ?.let(::add)
                            strings.settingName?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
                        }
                }.distinct()
            } catch (t: Throwable) {
                BiometricLoggerImpl.e(t)
                emptyList()
            }
        }

        private data class AuthState(
            val weakSuccess: Boolean,
            val strongSuccess: Boolean,
            val noneEnrolled: Boolean,
            val noHardware: Boolean
        )

        private fun authState(context: Context): AuthState {
            return try {
                val bm = BiometricManager.from(context)
                val weak = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                val strong = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                AuthState(
                    weakSuccess = weak == BiometricManager.BIOMETRIC_SUCCESS,
                    strongSuccess = strong == BiometricManager.BIOMETRIC_SUCCESS,
                    noneEnrolled = weak == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                            strong == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
                    noHardware = weak == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
                            strong == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
                )
            } catch (t: Throwable) {
                BiometricLoggerImpl.e(t)
                AuthState(
                    weakSuccess = false,
                    strongSuccess = false,
                    noneEnrolled = false,
                    noHardware = false
                )
            }
        }

        private fun PackageManager.safeHasSystemFeature(name: String): Boolean {
            return try {
                hasSystemFeature(name)
            } catch (_: Throwable) {
                false
            }
        }

        private fun containsWord(value: String, token: String): Boolean {
            return value.lowercase(Locale.ROOT).contains(token.lowercase(Locale.ROOT))
        }
    }


    internal object BiometricLexicon {

        data class Config(
            val customTokens: Map<BiometricType, Set<String>> = emptyMap(),
            val customAliases: Map<BiometricType, Set<String>> = emptyMap(),
            val enableGenericEnglishAliases: Boolean = true
        )

        data class MatchResult(
            val matched: Boolean,
            val score: Int,
            val matchedTokens: Set<String>,
            val normalizedSource: String
        )

        private val defaultTokens: Map<BiometricType, Set<String>> = mapOf(
            BiometricType.BIOMETRIC_FACE to setOf(
                // English
                "face",
                "facial",
                "face unlock",
                "unlock with face",
                "face recognition",
                "facial recognition",
                "recognize face",
                "scan face",
                "face scan",
                "face authentication",
                "face auth",
                "face id",
                "3d face",
                "face verify",
                "verify face",

                // Ukrainian
                "обличчя",
                "облич",
                "розпізнавання обличчя",
                "сканування обличчя",
                "вхід по обличчю",
                "вхід за обличчям",
                "розблокування обличчям",

                // Russian
                "лицо",
                "лиц",
                "распознавание лица",
                "сканирование лица",
                "вход по лицу",
                "разблокировка лицом",

                // German
                "gesicht",
                "gesichtserkennung",
                "gesichtsentsperrung",

                // French
                "visage",
                "reconnaissance faciale",
                "déverrouillage par visage",

                // Spanish
                "cara",
                "rostro",
                "reconocimiento facial",
                "desbloqueo facial",

                // Italian
                "viso",
                "riconoscimento facciale",
                "sblocco facciale",

                // Portuguese
                "rosto",
                "reconhecimento facial",
                "desbloqueio facial",

                // Polish
                "twarz",
                "rozpoznawanie twarzy",
                "odblokowanie twarza",

                // Turkish
                "yuz",
                "yuz tanima",
                "yuzle kilit acma",

                // Arabic translit fallback
                "wajh",
                "taaruf alwajh",

                // Japanese translit/common kanji token
                "face unlock",
                "kao",
                "顔",
                "顔認証",

                // Korean
                "eolgul",
                "얼굴",
                "얼굴 인식",

                // Chinese
                "人脸",
                "面容",
                "人脸识别",
                "面容识别"
            ),

            BiometricType.BIOMETRIC_IRIS to setOf(
                // English
                "iris",
                "iris unlock",
                "iris scanner",
                "iris recognition",
                "iris authentication",
                "scan iris",
                "eye scan",
                "eye recognition",

                // Ukrainian
                "райдужка",
                "райдуж",
                "сканер райдужки",
                "розпізнавання райдужки",

                // Russian
                "радужка",
                "радуж",
                "сканер радужки",
                "распознавание радужки",

                // German
                "iris",
                "irisscanner",
                "iriserkennung",

                // French
                "iris",
                "reconnaissance de l iris",
                "scanner d iris",

                // Spanish
                "iris",
                "reconocimiento de iris",
                "escaner de iris",

                // Italian
                "iride",
                "riconoscimento dell iride",
                "scanner dell iride",

                // Portuguese
                "iris",
                "reconhecimento da iris",
                "scanner de iris",

                // Polish
                "teczo",
                "teczo\u0301wka",
                "skaner teczowki",
                "rozpoznawanie teczowki",

                // Turkish
                "iris",
                "iris tarama",
                "goz tarama",

                // Japanese
                "虹彩",
                "虹彩認証",

                // Korean
                "홍채",
                "홍채 인식",

                // Chinese
                "虹膜",
                "虹膜识别"
            )
        )

        private val defaultAliases: Map<BiometricType, Set<String>> = mapOf(
            BiometricType.BIOMETRIC_FACE to setOf(
                "faceid",
                "face id",
                "facelock",
                "face lock",
                "face bio",
                "face biometric",
                "facial biometrics"
            ),
            BiometricType.BIOMETRIC_IRIS to setOf(
                "iris bio",
                "iris biometric",
                "eye biometric"
            )
        )

        fun match(
            rawText: String,
            type: BiometricType,
            config: Config = Config()
        ): MatchResult {
            val normalized = normalize(rawText)
            if (normalized.isBlank()) {
                return MatchResult(
                    matched = false,
                    score = 0,
                    matchedTokens = emptySet(),
                    normalizedSource = normalized
                )
            }

            val tokens = buildSet {
                addAll(defaultTokens[type].orEmpty())
                addAll(config.customTokens[type].orEmpty())
                if (config.enableGenericEnglishAliases) {
                    addAll(defaultAliases[type].orEmpty())
                }
                addAll(config.customAliases[type].orEmpty())
            }.map(::normalize).filter { it.isNotBlank() }.toSet()

            val matchedTokens = mutableSetOf<String>()
            var score = 0

            for (token in tokens) {
                if (token in normalized) {
                    matchedTokens += token
                    score += tokenScore(token)
                }
            }

            return MatchResult(
                matched = matchedTokens.isNotEmpty(),
                score = score,
                matchedTokens = matchedTokens,
                normalizedSource = normalized
            )
        }

        fun normalize(raw: String): String {
            val lowered = raw.lowercase(Locale.ROOT)
            val normalized = Normalizer.normalize(lowered, Normalizer.Form.NFKD)
            val withoutDiacritics = buildString(normalized.length) {
                for (ch in normalized) {
                    if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                        append(ch)
                    }
                }
            }

            return withoutDiacritics
                .replace("&", " and ")
                .replace("_", " ")
                .replace("-", " ")
                .replace("/", " ")
                .replace("\\", " ")
                .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun tokenScore(token: String): Int {
            return when {
                token.length >= 18 -> 4
                token.length >= 10 -> 3
                token.length >= 5 -> 2
                else -> 1
            }
        }
    }

    @Deprecated("Legacy Samsung biometric devices")
    private object SamsungLegacyBiometricDevices {
        private val faceAndIrisModels = listOf(
            "galaxy s8",
            "galaxy s8+",
            "galaxy s9",
            "galaxy s9+",
            "galaxy note 8",
            "galaxy note 9",
            "galaxy tab s4"
        )

        fun hasSamsungFaceAndIris(marketingName: String?): Boolean {
            val normalized = normalizeSamsungMarketingName(marketingName) ?: return false
            return matchesAnyModel(normalized, faceAndIrisModels)
        }

        private fun matchesAnyModel(
            normalizedName: String,
            models: List<String>
        ): Boolean {
            return models.any { model -> containsWholeModel(normalizedName, model) }
        }

        private fun containsWholeModel(
            text: String,
            model: String
        ): Boolean {
            val regex = Regex("""(^|[^a-z0-9])${Regex.escape(model)}([^a-z0-9]|$)""")
            return regex.containsMatchIn(text)
        }

        private fun normalizeSamsungMarketingName(marketingName: String?): String? {
            val raw = marketingName
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?: return null

            return raw
                .replace('+', '＋')
                .replace(Regex("""[\(\)\[\],/_-]"""), " ")
                .replace(Regex("""\b5g\b"""), " ")
                .replace(Regex("""\bduos\b"""), " ")
                .replace(Regex("""\bwifi\b"""), " ")
                .replace(Regex("""\blte\b"""), " ")
                .replace(Regex("""\btablet\b"""), " ")
                .replace(Regex("""\bsm-[a-z0-9]+\b"""), " ")
                .replace("samsung galaxy", "galaxy")
                .replace("samsung", " ")
                .replace("note8", "note 8")
                .replace("note9", "note 9")
                .replace("s8plus", "s8+")
                .replace("s9plus", "s9+")
                .replace("s8 plus", "s8+")
                .replace("s9 plus", "s9+")
                .replace("tabs4", "tab s4")
                .replace('＋', '+')
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }

    object PixelModelChecker {

        private val pixelRegex = Regex(
            pattern = """\b(?:google\s+)?pixel\s+(\d+)(?:\s*(a|pro|xl|fold))?(?:\s*(pro|xl|fold))?\b""",
            option = RegexOption.IGNORE_CASE
        )

        data class PixelModelInfo(
            val raw: String,
            val generation: Int,
            val tags: Set<String>
        )

        /**
         * - "Pixel 7a"           -> generation=7, tags=[a]
         * - "Google Pixel 8 Pro" -> generation=8, tags=[pro]
         * - "Pixel 9 Pro XL"     -> generation=9, tags=[pro, xl]
         * - "Pixel 9 Pro Fold"   -> generation=9, tags=[pro, fold]
         * - "Pixel 5"            -> generation=5, tags=[]
         */
        fun parse(input: String): PixelModelInfo? {
            val normalized = input
                .trim()
                .replace(Regex("""\s+"""), " ")

            val match = pixelRegex.find(normalized) ?: return null

            val generation = match.groupValues[1].toIntOrNull() ?: return null

            val tags = buildSet {
                match.groupValues.getOrNull(2)
                    ?.takeIf { it.isNotBlank() }
                    ?.lowercase()
                    ?.let(::add)

                match.groupValues.getOrNull(3)
                    ?.takeIf { it.isNotBlank() }
                    ?.lowercase()
                    ?.let(::add)
            }

            return PixelModelInfo(
                raw = input,
                generation = generation,
                tags = tags
            )
        }

        fun isPixel8OrNewer(input: String): Boolean {
            try {
                val info = parse(input) ?: return false
                return info.generation >= 8
            } catch (_: Exception) {
                return false
            }
        }

    }
}