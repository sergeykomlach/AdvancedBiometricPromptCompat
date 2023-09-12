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

package dev.skomlach.biometric.compat.engine.internal.face.hihonor.impl

import android.os.Build
import com.hihonor.android.facerecognition.FaceRecognizeManager
import com.hihonor.android.facerecognition.FaceRecognizeManager.FaceRecognizeCallback
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.HexUtils
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock


class HihonorFaceRecognizeManager {

    companion object {

        const val DEFAULT_FLAG = 1
        const val CODE_CALLBACK_ACQUIRE = 3
        const val CODE_CALLBACK_BUSY = 4
        const val CODE_CALLBACK_CANCEL = 2
        const val CODE_CALLBACK_OUT_OF_MEM = 5
        const val CODE_CALLBACK_RESULT = 1
        const val HIHONOR_FACE_AUTHENTICATOR_FAIL = 103
        const val HIHONOR_FACE_AUTHENTICATOR_SUCCESS = 100
        const val HIHONOR_FACE_AUTH_ERROR_CANCEL = 102
        const val HIHONOR_FACE_AUTH_ERROR_LOCKED = 129
        const val HIHONOR_FACE_AUTH_ERROR_TIMEOUT = 113

        const val HIHONOR_FACE_AUTH_ERROR_VENDOR = -100
        const val HIHONOR_FACE_AUTH_ERROR_HW_UNAVAILABLE = -101

        const val HIHONOR_FACE_AUTH_STATUS_BRIGHT = 406
        const val HIHONOR_FACE_AUTH_STATUS_DARK = 405
        const val HIHONOR_FACE_AUTH_STATUS_EYE_CLOSED = 403
        const val HIHONOR_FACE_AUTH_STATUS_FACE_OFFET_BOTTOM = 412
        const val HIHONOR_FACE_AUTH_STATUS_FACE_OFFET_LEFT = 409
        const val HIHONOR_FACE_AUTH_STATUS_FACE_OFFET_RIGHT = 410
        const val HIHONOR_FACE_AUTH_STATUS_FACE_OFFET_TOP = 411
        const val HIHONOR_FACE_AUTH_STATUS_FAR_FACE = 404
        const val HIHONOR_FACE_AUTH_STATUS_INSUFFICIENT = 402
        const val HIHONOR_FACE_AUTH_STATUS_MOUTH_OCCLUSION = 408
        const val HIHONOR_FACE_AUTH_STATUS_PARTIAL = 401
        const val HIHONOR_FACE_AUTH_STATUS_QUALITY = 407
        const val TAG = "HihonorFaceRecognize"
        const val TYPE_CALLBACK_AUTH = 2
        var instance: HihonorFaceRecognizeManager? = null
            private set
        var fRManager: FaceRecognizeManager? = null
            private set

        fun converHwAcquireInfoToHihonor(hwAcquireInfo: Int): Int {
            val str = TAG
            val stringBuilder = StringBuilder()
            stringBuilder.append(" converHwhwAcquireInfoToHihonor hwAcquireInfo is ")
            stringBuilder.append(hwAcquireInfo)
            e(str, stringBuilder.toString())
            return when (hwAcquireInfo) {
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_OK -> HIHONOR_FACE_AUTHENTICATOR_SUCCESS
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_EYE_CLOSE -> HIHONOR_FACE_AUTH_STATUS_EYE_CLOSED
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_BAD_QUALITY -> HIHONOR_FACE_AUTH_STATUS_QUALITY
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_NOT_FOUND, FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_SCALE_TOO_SMALL -> HIHONOR_FACE_AUTH_STATUS_INSUFFICIENT
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_SCALE_TOO_LARGE -> HIHONOR_FACE_AUTH_STATUS_FAR_FACE
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_OFFSET_LEFT -> HIHONOR_FACE_AUTH_STATUS_FACE_OFFET_LEFT
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_OFFSET_TOP -> HIHONOR_FACE_AUTH_STATUS_FACE_OFFET_TOP
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_OFFSET_RIGHT -> HIHONOR_FACE_AUTH_STATUS_FACE_OFFET_RIGHT
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_OFFSET_BOTTOM -> HIHONOR_FACE_AUTH_STATUS_FACE_OFFET_BOTTOM
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_NOT_COMPLETE -> HIHONOR_FACE_AUTH_STATUS_PARTIAL
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_DARKLIGHT -> HIHONOR_FACE_AUTH_STATUS_DARK
                FaceRecognizeManager.AcquireInfo.FACE_UNLOCK_FACE_HIGHTLIGHT -> HIHONOR_FACE_AUTH_STATUS_BRIGHT
                else -> HIHONOR_FACE_AUTHENTICATOR_FAIL
            }
        }

        fun converHwErrorCodeToHihonor(hwErrorCode: Int): Int {
            val str = TAG
            val stringBuilder = StringBuilder()
            stringBuilder.append(" converHwErrorCodeToHihonor hwErrorCode is ")
            stringBuilder.append(hwErrorCode)
            e(str, stringBuilder.toString())
            return when (hwErrorCode) {
                FaceRecognizeManager.FaceErrorCode.CAMERA_FAIL -> {
                    SharedPreferenceProvider.getPreferences(TAG).edit().clear().commit()
                    SharedPreferenceProvider.getPreferences(TAG).edit()
                        .putBoolean(md5(Build.FINGERPRINT), false)
                        .putBoolean("broken_camera", true)
                        .apply()
                    return HIHONOR_FACE_AUTH_ERROR_HW_UNAVAILABLE
                }

                FaceRecognizeManager.FaceErrorCode.SUCCESS -> HIHONOR_FACE_AUTHENTICATOR_SUCCESS
                FaceRecognizeManager.FaceErrorCode.CANCELED -> HIHONOR_FACE_AUTH_ERROR_CANCEL
                FaceRecognizeManager.FaceErrorCode.TIMEOUT -> HIHONOR_FACE_AUTH_ERROR_TIMEOUT
                FaceRecognizeManager.FaceErrorCode.IN_LOCKOUT_MODE -> HIHONOR_FACE_AUTH_ERROR_LOCKED

                FaceRecognizeManager.FaceErrorCode.HAL_INVALIDE,
                FaceRecognizeManager.FaceErrorCode.INVALID_PARAMETERS,
                FaceRecognizeManager.FaceErrorCode.ALGORITHM_NOT_INIT -> HIHONOR_FACE_AUTH_ERROR_VENDOR

//                FaceRecognizeManager.FaceErrorCode.COMPARE_FAIL,
//                FaceRecognizeManager.FaceErrorCode.NO_FACE_DATA,
//                FaceRecognizeManager.FaceErrorCode.OVER_MAX_FACES,
//                FaceRecognizeManager.FaceErrorCode.FAILED-> HIHONOR_FACE_AUTHENTICATOR_FAIL
                else -> HIHONOR_FACE_AUTHENTICATOR_FAIL
            }
        }

        fun getTypeString(type: Int): String {
            return when (type) {
                1 -> "ENROLL"
                2 -> "AUTH"
                3 -> "REMOVE"
                else -> "" + type
            }
        }

        fun getCodeString(code: Int): String {
            return when (code) {
                1 -> "result"
                2 -> "cancel"
                3 -> "acquire"
                4 -> "request busy"
                else -> "" + code
            }
        }

        fun getErrorCodeString(code: Int, errorCode: Int): String {
            if (code != 1) {
                if (code == 3) {
                    when (errorCode) {
                        4 -> return "bad quality"
                        5 -> return "no face detected"
                        6 -> return "face too small"
                        7 -> return "face too large"
                        8 -> return "offset left"
                        9 -> return "offset top"
                        10 -> return "offset right"
                        11 -> return "offset bottom"
                        13 -> return "aliveness warning"
                        14 -> return "aliveness failure"
                        15 -> return "rotate left"
                        16 -> return "face rise to high"
                        17 -> return "rotate right"
                        18 -> return "face too low"
                        19 -> return "keep still"
                        21 -> return "eyes occlusion"
                        22 -> return "eyes closed"
                        23 -> return "mouth occlusion"
                        27 -> return "multi faces"
                        28 -> return "face blur"
                        29 -> return "face not complete"
                        30 -> return "too dark"
                        31 -> return "too light"
                        32 -> return "half shadow"
                        else -> {}
                    }
                }
            }
            when (errorCode) {
                0 -> return "success"
                1 -> return "failed"
                2 -> return "cancelled"
                3 -> return "compare fail"
                4 -> return "time out"
                5 -> return "invoke init first"
                6 -> return "hal invalid"
                7 -> return "over max faces"
                8 -> return "in lockout mode"
                9 -> return "invalid parameters"
                10 -> return "no face data"
                11 -> return "low temp & cap"
            }
            return "" + errorCode
        }

        fun isCameraBroken(): Boolean {
            return SharedPreferenceProvider.getPreferences(TAG).getBoolean("broken_camera", false)
        }

        fun resetCheckCamera() {
            val pref = SharedPreferenceProvider.getPreferences(TAG)
            pref.edit().clear().putBoolean(md5(Build.FINGERPRINT), false).apply()
        }

        fun shouldCheckCamera(): Boolean {
            val pref = SharedPreferenceProvider.getPreferences(TAG)
            return pref.getBoolean(md5(Build.FINGERPRINT), true)
        }

        private fun md5(s: String): String? {
            try {
                val digest = MessageDigest.getInstance("MD5")
                digest.reset()
                digest.update(s.toByteArray(Charset.forName("UTF-8")))
                return HexUtils.bytesToHex(digest.digest())
            } catch (e: Exception) {

            }
            return null
        }

        private val lock = ReentrantLock()
        fun createInstance() {
            try {
                lock.runCatching { this.lock() }
                if (instance == null) {
                    instance = HihonorFaceRecognizeManager()
                }
            } finally {
                lock.runCatching {
                    this.unlock()
                }
            }
        }
    }

    private var mAuthenticatorCallback: HihonorFaceManager.AuthenticatorCallback? = null

    //    EMUI 10/0/0
    //    [HihonorFaceRecognize,  onCallbackEvent gotten reqId 14 type 2 code 1 errCode 9]
    //    [HihonorFaceRecognize,  onCallbackEvent gotten reqId 14 type 2 code 2 errCode 0]
    //    EMUI 9/1/0
    //    [HihonorFaceRecognize,  onCallbackEvent gotten reqId 180 type 2 code 1 errCode 9]
    //    [HihonorFaceRecognize,  onCallbackEvent gotten reqId 180 type 2 code 2 errCode 0]
    //    EMUI 11/0/0
    //    [HihonorFaceRecognize,  onCallbackEvent gotten reqId 174 type 2 code 1 errCode 1]
    //    MatePad 8T
    //    [HihonorFaceRecognize, onCallbackEvent gotten reqId 1 type 2 code 1 errCode 1]
    private val mFRCallback: FaceRecognizeCallback = object : FaceRecognizeCallback {
        override fun onCallbackEvent(reqId: Int, type: Int, code: Int, errorCode: Int) {
            var str = TAG
            var stringBuilder = StringBuilder()
            stringBuilder.append(" onCallbackEvent gotten reqId ")
            stringBuilder.append(reqId)
            stringBuilder.append(" type ")
            stringBuilder.append(type).append(" (").append(getTypeString(type)).append(")")
            stringBuilder.append(" code ")
            stringBuilder.append(code).append(" (").append(getCodeString(code)).append(")")
            stringBuilder.append(" errCode ")
            stringBuilder.append(errorCode).append(" (").append(getErrorCodeString(code, errorCode))
                .append(")")
            d(str, stringBuilder.toString())

            ExecutorHelper.post {
                if (mAuthenticatorCallback == null) {
                    e(TAG, "mAuthenticatorCallback empty in onCallbackEvent ")
                    return@post
                }
                if (type != TYPE_CALLBACK_AUTH) {
                    str = TAG
                    stringBuilder = StringBuilder()
                    stringBuilder.append(" gotten not hihonor's auth callback reqid ")
                    e(str, stringBuilder.toString())
                } else
                    if (code == CODE_CALLBACK_ACQUIRE) {
                        val result = converHwAcquireInfoToHihonor(errorCode)
                        val str2 = TAG
                        val stringBuilder2 = StringBuilder()
                        stringBuilder2.append(" result ")
                        stringBuilder2.append(result)
                        d(str2, stringBuilder2.toString())
                        if (result != HIHONOR_FACE_AUTHENTICATOR_FAIL) {
                            mAuthenticatorCallback?.onAuthenticationStatus(result)
                        }
                    } else if (code == CODE_CALLBACK_RESULT) {
                        val result = converHwErrorCodeToHihonor(errorCode)
                        var str2 = TAG
                        var stringBuilder2 = StringBuilder()
                        stringBuilder2.append(" result ")
                        stringBuilder2.append(result)
                        d(str2, stringBuilder2.toString())
                        if (result == HIHONOR_FACE_AUTHENTICATOR_SUCCESS) {
                            d(TAG, "hihonor face auth success")
                            mAuthenticatorCallback?.onAuthenticationSucceeded()
                            mAuthenticatorCallback = null
                        } else if (result != HIHONOR_FACE_AUTHENTICATOR_FAIL) {
                            str2 = TAG
                            stringBuilder2 = StringBuilder()
                            stringBuilder2.append(" error reason ")
                            stringBuilder2.append(result)
                            e(str2, stringBuilder2.toString())

                            mAuthenticatorCallback?.onAuthenticationError(result)
                            mAuthenticatorCallback = null
                        } else {
                            mAuthenticatorCallback?.onAuthenticationFailed()
                            str2 = TAG
                            stringBuilder2 = StringBuilder()
                            stringBuilder2.append(" fail reason ")
                            stringBuilder2.append(result)
                            e(str2, stringBuilder2.toString())
                        }
                    } else {
                        e("bad params, ignore")
                    }
            }
        }
    }
    private val context = AndroidContext.appContext

    init {
        if (fRManager == null) {
            fRManager = FaceRecognizeManager(context, mFRCallback)
        }
    }

    fun init(): Int {
        if (fRManager != null) {
            return fRManager?.init() ?: -1
        }
        return -1
    }

    fun release() {
        if (fRManager != null) {
            fRManager?.release()
        }
    }


    fun setAuthCallback(authCallback: HihonorFaceManager.AuthenticatorCallback?) {
        mAuthenticatorCallback = authCallback
    }
}