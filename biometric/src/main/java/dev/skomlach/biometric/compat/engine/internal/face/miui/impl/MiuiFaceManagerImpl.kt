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

package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import android.content.Context
import android.database.ContentObserver
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.*
import android.os.IBinder.DeathRecipient
import android.provider.Settings
import android.view.Surface
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.*
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.util.*
import kotlin.collections.ArrayList

class MiuiFaceManagerImpl constructor(con: Context) : IMiuiFaceManager {
    companion object {
        const val ERROR_BINDER_CALL = 2100
        const val ERROR_CANCELED = 2000
        const val ERROR_SERVICE_IS_BUSY = 2001
        const val ERROR_SERVICE_IS_IDLE = 2002
        const val ERROR_TIME_OUT = 2003
        const val MG_ATTR_BLUR = 20
        const val MG_ATTR_EYE_CLOSE = 22
        const val MG_ATTR_EYE_OCCLUSION = 21
        const val MG_ATTR_MOUTH_OCCLUSION = 23
        const val MG_OPEN_CAMERA_FAIL = 1000
        const val MG_OPEN_CAMERA_SUCCESS = 1001
        const val MG_UNLOCK_BAD_LIGHT = 26
        const val MG_UNLOCK_COMPARE_FAILURE = 12
        const val MG_UNLOCK_DARKLIGHT = 30
        const val MG_UNLOCK_FACE_BAD_QUALITY = 4
        const val MG_UNLOCK_FACE_BLUR = 28
        const val MG_UNLOCK_FACE_DOWN = 18
        const val MG_UNLOCK_FACE_MULTI = 27
        const val MG_UNLOCK_FACE_NOT_COMPLETE = 29
        const val MG_UNLOCK_FACE_NOT_FOUND = 5
        const val MG_UNLOCK_FACE_NOT_ROI = 33
        const val MG_UNLOCK_FACE_OFFSET_BOTTOM = 11
        const val MG_UNLOCK_FACE_OFFSET_LEFT = 8
        const val MG_UNLOCK_FACE_OFFSET_RIGHT = 10
        const val MG_UNLOCK_FACE_OFFSET_TOP = 9
        const val MG_UNLOCK_FACE_RISE = 16
        const val MG_UNLOCK_FACE_ROTATED_LEFT = 15
        const val MG_UNLOCK_FACE_ROTATED_RIGHT = 17
        const val MG_UNLOCK_FACE_SCALE_TOO_LARGE = 7
        const val MG_UNLOCK_FACE_SCALE_TOO_SMALL = 6
        const val MG_UNLOCK_FAILURE = 3
        const val MG_UNLOCK_FEATURE_MISS = 24
        const val MG_UNLOCK_FEATURE_VERSION_ERROR = 25
        const val MG_UNLOCK_HALF_SHADOW = 32
        const val MG_UNLOCK_HIGHLIGHT = 31
        const val MG_UNLOCK_INVALID_ARGUMENT = 1
        const val MG_UNLOCK_INVALID_HANDLE = 2
        const val MG_UNLOCK_KEEP = 19
        const val MG_UNLOCK_LIVENESS_FAILURE = 14
        const val MG_UNLOCK_LIVENESS_WARNING = 13
        const val MG_UNLOCK_OK = 0
        private const val CODE_ADD_LOCKOUT_RESET_CALLBACK = 16
        private const val CODE_AUTHENTICATE = 3
        private const val CODE_CANCEL_AUTHENTICATE = 4
        private const val CODE_CANCEL_ENROLL = 6
        private const val CODE_ENROLL = 5
        private const val CODE_EXT_CMD = 101
        private const val CODE_GET_AUTHENTICATOR_ID = 14
        private const val CODE_GET_ENROLLED_FACE_LIST = 9
        private const val CODE_GET_VENDOR_INFO = 17
        private const val CODE_HAS_ENROLLED_FACES = 12
        private const val CODE_POST_ENROLL = 11
        private const val CODE_PRE_ENROLL = 10
        private const val CODE_PRE_INIT_AUTHEN = 2
        private const val CODE_REMOVE = 7
        private const val CODE_RENAME = 8
        private const val CODE_RESET_TIMEOUT = 15
        private const val FACEUNLOCK_CURRENT_USE_INVALID_MODEL = 2
        private const val FACEUNLOCK_CURRENT_USE_RGB_MODEL = 1
        private const val FACEUNLOCK_CURRENT_USE_STRUCTURE_MODEL = 0
        private const val FACEUNLOCK_SUPPORT_SUPERPOWER = "faceunlock_support_superpower"
        private const val FACE_UNLOCK_3D_HAS_FEATURE = "face_unlock_has_feature_sl"
        private const val FACE_UNLOCK_HAS_FEATURE = "face_unlock_has_feature"
        private const val FACE_UNLOCK_HAS_FEATURE_URI =
            "content://settings/secure/face_unlock_has_feature"
        private const val FACE_UNLOCK_MODEL = "face_unlock_model"
        private const val FACE_UNLOCK_VALID_FEATURE = "face_unlock_valid_feature"
        private const val FACE_UNLOCK_VALID_FEATURE_URI =
            "content://settings/secure/face_unlock_valid_feature"
        private const val POWERMODE_SUPERSAVE_OPEN = "power_supersave_mode_open"
        private const val POWERMODE_SUPERSAVE_OPEN_URI =
            "content://settings/secure/power_supersave_mode_open"
        private const val RECEIVER_ON_AUTHENTICATION_FAILED = 204
        private const val RECEIVER_ON_AUTHENTICATION_SUCCEEDED = 203
        private const val RECEIVER_ON_ENROLL_RESULT = 201
        private const val RECEIVER_ON_ERROR = 205
        private const val RECEIVER_ON_EXT_CMD = 301
        private const val RECEIVER_ON_LOCKOUT_RESET = 261
        private const val RECEIVER_ON_ON_ACQUIRED = 202
        private const val RECEIVER_ON_PRE_INIT = 207
        private const val RECEIVER_ON_REMOVED = 206
        private const val VERSION_1 = 1
        private const val DEBUG = true
        private const val RECEIVER_DESCRIPTOR = "receiver.FaceService"
        private const val TAG = "FaceManagerImpl"

        @Volatile
        private var INSTANCE: IMiuiFaceManager? = null
        private var SERVICE_DESCRIPTOR: String
        private var SERVICE_NAME: String
        fun getInstance(con: Context): IMiuiFaceManager? {
            if (INSTANCE == null) {
                synchronized(MiuiFaceManagerImpl::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = MiuiFaceManagerImpl(con)
                    }
                }
            }
            return INSTANCE
        }

        init {
            val str = "miui.face.FaceService"
            SERVICE_NAME = str
            SERVICE_DESCRIPTOR = str
        }
    }

    private val mBinderLock = Any()
    private val mContext: Context = con.applicationContext
    private val mToken: IBinder = Binder()
    private var mAuthenticationCallback: IMiuiFaceManager.AuthenticationCallback? = null
    private var mEnrollmentCallback: IMiuiFaceManager.EnrollmentCallback? = null
    private val mFaceUnlockModel = 0
    private lateinit var mHandler: Handler
    private var mHasFaceData = false
    override var isFaceUnlockInited = false
        private set
    private val mServiceReceiver: IBinder = object : Binder() {
        @Throws(RemoteException::class)
        public override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int
        ): Boolean {
            var i = code

            val stringBuilder = StringBuilder()
            stringBuilder.append("mServiceReceiver callback: ")
            stringBuilder.append(i)
            d(TAG, stringBuilder.toString())
            return if (i == 261) {
                data.enforceInterface(RECEIVER_DESCRIPTOR)
                mHandler.obtainMessage(261, data.readInt()).sendToTarget()
                reply?.writeNoException()
                true
            } else if (i != 301) {
                val readLong: Long
                when (i) {
                    201 -> {
                        data.enforceInterface(RECEIVER_DESCRIPTOR)
                        val devId = data.readLong()
                        i = data.readInt()
                        mHandler.obtainMessage(
                            201,
                            data.readInt(),
                            0,
                            Miuiface(null, data.readInt(), i, devId)
                        ).sendToTarget()
                        reply?.writeNoException()
                        true
                    }
                    202 -> {
                        data.enforceInterface(RECEIVER_DESCRIPTOR)
                        mHandler.obtainMessage(
                            202,
                            data.readInt(),
                            data.readInt(),
                            data.readLong()
                        ).sendToTarget()
                        reply?.writeNoException()
                        true
                    }
                    203 -> {
                        data.enforceInterface(RECEIVER_DESCRIPTOR)
                        readLong = data.readLong()
                        var face: Miuiface? = null
                        if (data.readInt() != 0) {
                            face = Miuiface.CREATOR.createFromParcel(data)
                        }
                        mHandler.obtainMessage(203, data.readInt(), 0, face)
                            .sendToTarget()
                        reply?.writeNoException()
                        true
                    }
                    204 -> {
                        data.enforceInterface(RECEIVER_DESCRIPTOR)
                        readLong = data.readLong()
                        mHandler.obtainMessage(204).sendToTarget()
                        reply?.writeNoException()
                        true
                    }
                    205 -> {
                        data.enforceInterface(RECEIVER_DESCRIPTOR)
                        mHandler.obtainMessage(
                            205,
                            data.readInt(),
                            data.readInt(),
                            java.lang.Long.valueOf(data.readLong())
                        ).sendToTarget()
                        reply?.writeNoException()
                        true
                    }
                    206 -> {
                        data.enforceInterface(RECEIVER_DESCRIPTOR)
                        val devId2 = data.readLong()
                        val faceId = data.readInt()
                        val groupId = data.readInt()
                        val remaining = data.readInt()
                        val miuiface = Miuiface(null, groupId, faceId, devId2)
                        i = 206
                        mHandler.obtainMessage(i, remaining, 0, miuiface)
                            .sendToTarget()
                        reply?.writeNoException()
                        true
                    }
                    207 -> {
                        data.enforceInterface(RECEIVER_DESCRIPTOR)
                        isFaceUnlockInited = data.readInt() == 1
                        reply?.writeNoException()
                        true
                    }
                    else -> super.onTransact(code, data, reply, flags)
                }
            } else {
                data.enforceInterface(RECEIVER_DESCRIPTOR)
                mHandler.obtainMessage(301, data.readInt()).sendToTarget()
                reply?.writeNoException()
                true
            }
        }
    }
    private var mIsSuperPower = false
    private var mIsValid = false
    private var mLockoutResetCallback: IMiuiFaceManager.LockoutResetCallback? = null
    private var mMiuiFaceService: IBinder? = null
    private val mBinderDied = DeathRecipient {
        synchronized(mBinderLock) {
            e(TAG, "mMiuiFaceService Service Died.")
            mMiuiFaceService = null
        }
    }
    private var mRemovalCallback: IMiuiFaceManager.RemovalCallback? = null
    private var mRemovalMiuiface: Miuiface? = null

    private inner class FaceObserver(handler: Handler?) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            try {
                if (POWERMODE_SUPERSAVE_OPEN == uri?.lastPathSegment) {
                    mIsSuperPower = SettingsSystem.getIntForUser(
                        mContext.contentResolver,
                        POWERMODE_SUPERSAVE_OPEN,
                        0,
                        0
                    ) != 0
                } else if (FACE_UNLOCK_VALID_FEATURE == uri?.lastPathSegment) {
                    mIsValid = SettingsSecure.getIntForUser(
                        mContext.contentResolver,
                        FACE_UNLOCK_VALID_FEATURE,
                        1,
                        0
                    ) != 0
                } else if (FACE_UNLOCK_HAS_FEATURE == uri?.lastPathSegment) {
                    mHasFaceData = SettingsSecure.getIntForUser(
                        mContext.contentResolver,
                        FACE_UNLOCK_HAS_FEATURE,
                        0,
                        0
                    ) != 0
                }
            } catch (e: Throwable) {
                e(e)
            }
        }
    }

    init {
        mHandler = ClientHandler(
            mContext
        )
        try {
//        if ("ursa".equals(Build.DEVICE)) {
//            ContentResolver contentResolver = this.mContext.getContentResolver();
//            this.mFaceUnlockModel = SettingsSecure.getIntForUser(contentResolver, FACE_UNLOCK_MODEL, 1, -2);
//            if (this.mFaceUnlockModel != 2) {
//                Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_MODEL, 2, -2);
//                if (this.mFaceUnlockModel == 0) {
//                    Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_3D_HAS_FEATURE,
//                            SettingsSecure.getIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_HAS_FEATURE, 0, -2), -2);
//                    Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_HAS_FEATURE, 0, -2);
//                    Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_VALID_FEATURE, 1, -2);
//                }
//            }
//        }
//        Secure.putIntForUser(this.mContext.getContentResolver(), FACEUNLOCK_SUPPORT_SUPERPOWER, 1, -2);
            val faceObserver = FaceObserver(mHandler)
            ContentResolverHelper.registerContentObserver(
                mContext.contentResolver,
                Settings.Secure.getUriFor(FACE_UNLOCK_HAS_FEATURE),
                false,
                faceObserver,
                0
            )
            faceObserver.onChange(false, Uri.parse(FACE_UNLOCK_HAS_FEATURE_URI))
            ContentResolverHelper.registerContentObserver(
                mContext.contentResolver,
                Settings.Secure.getUriFor(FACE_UNLOCK_VALID_FEATURE),
                false,
                faceObserver,
                0
            )
            faceObserver.onChange(false, Uri.parse(FACE_UNLOCK_VALID_FEATURE_URI))
            ContentResolverHelper.registerContentObserver(
                mContext.contentResolver,
                Settings.System.getUriFor(POWERMODE_SUPERSAVE_OPEN),
                false,
                faceObserver,
                0
            )
            faceObserver.onChange(false, Uri.parse(POWERMODE_SUPERSAVE_OPEN_URI))
        } catch (e: Throwable) {
            e(e)
        }
    }

    @Throws(RemoteException::class)
    private fun initService() {
        synchronized(mBinderLock) {
            if (mMiuiFaceService == null) {
                try {
                    mMiuiFaceService = Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String::class.java)
                        .invoke(null, SERVICE_NAME) as IBinder
                } catch (ignore: Exception) {
                }

                mMiuiFaceService?.linkToDeath(mBinderDied, 0)
            }
        }
    }

    override val isFaceFeatureSupport: Boolean
        get() {
            if (mIsSuperPower) {
                d(TAG, "enter super power mode, isFaceFeatureSupport:false")
                return false
            }
            var res = false
            val supportRegion: Array<String>? = if (MiuiBuild.IS_INTERNATIONAL_BUILD) {
                FeatureParser.getStringArray("support_face_unlock_region_global")
            } else {
                FeatureParser.getStringArray("support_face_unlock_region_dom")
            }
            if (supportRegion != null && (listOf(*supportRegion)
                    .contains(MiuiBuild.region) || listOf(*supportRegion)
                    .contains("ALL"))
            ) {
                res = true
            }
            if (DEBUG) {
                val stringBuilder = StringBuilder()
                stringBuilder.append("inernational:")
                stringBuilder.append(MiuiBuild.IS_INTERNATIONAL_BUILD)
                stringBuilder.append(" supportR:")
                stringBuilder.append(Arrays.toString(supportRegion))
                stringBuilder.append(" nowR:")
                stringBuilder.append(MiuiBuild.region)
                d(TAG, stringBuilder.toString())
            }
            return res
        }
    override val isSupportScreenOnDelayed: Boolean
        get() {
            val res = FeatureParser.getBoolean("support_screen_on_delayed", false)
            if (DEBUG) {
                val stringBuilder = StringBuilder()
                stringBuilder.append("isSupportScreenOnDelayed:")
                stringBuilder.append(res)
                d(TAG, stringBuilder.toString())
            }
            return res
        }

    private fun cancelAuthentication() {
        if (DEBUG) {
            d(TAG, "cancelAuthentication ")
        }
        try {
            initService()
            if (mMiuiFaceService != null) {
                binderCallCancelAuthention(mMiuiFaceService, mToken, mContext.packageName)
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun cancelEnrollment() {
        if (DEBUG) {
            d(TAG, "cancelEnrollment ")
        }
        try {
            initService()
            if (mMiuiFaceService != null) {
                binderCallCancelEnrollment(mMiuiFaceService, mToken)
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override val managerVersion: Int
        get() = 1
    override val vendorInfo: String?
        get() {
            var res: String? = ""
            try {
                initService()
                if (mMiuiFaceService != null) {
                    res = binderCallGetVendorInfo(mMiuiFaceService, mContext.packageName)
                }
            } catch (e: RemoteException) {
                val stringBuilder = StringBuilder()
                stringBuilder.append("transact fail. ")
                stringBuilder.append(e)
                e(TAG, stringBuilder.toString())
            }
            if (DEBUG) {
                val stringBuilder2 = StringBuilder()
                stringBuilder2.append("getVendorInfo, res:")
                stringBuilder2.append(res)
                d(TAG, stringBuilder2.toString())
            }
            return res
        }

    override fun authenticate(
        cancel: CancellationSignal?,
        flags: Int,
        callback: IMiuiFaceManager.AuthenticationCallback?,
        handler: Handler?,
        timeout: Int
    ) {
        if (DEBUG) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("authenticate mServiceReceiver:")
            stringBuilder.append(mServiceReceiver)
            d(TAG, stringBuilder.toString())
        }
        if (callback != null) {
            if (cancel != null) {
                if (cancel.isCanceled) {
                    d(
                        TAG,
                        "authentication already canceled"
                    )
                    return
                }
                cancel.setOnCancelListener(OnAuthenticationCancelListener())
            }
            useHandler(handler)
            mAuthenticationCallback = callback
            mEnrollmentCallback = null
            try {
                initService()
                if (mMiuiFaceService != null) {
                    binderCallAuthenticate(
                        mMiuiFaceService,
                        mToken,
                        -1,
                        -1,
                        mServiceReceiver,
                        flags,
                        mContext.packageName,
                        timeout
                    )
                } else {
                    d(
                        TAG,
                        "mMiuiFaceService is null"
                    )
                    callback.onAuthenticationError(2100, getMessageInfo(2100))
                }
            } catch (e: Exception) {
                e(
                    TAG,
                    "Remote exception while authenticating: ",
                    e
                )
                callback.onAuthenticationError(2100, getMessageInfo(2100))
            }
            return
        }
        throw IllegalArgumentException("Must supply an authentication callback")
    }

    override fun enroll(
        cryptoToken: ByteArray?,
        cancel: CancellationSignal,
        flags: Int,
        enrollCallback: IMiuiFaceManager.EnrollmentCallback?,
        surface: Surface?,
        detectArea: Rect?,
        timeout: Int
    ) {
        enroll(
            cryptoToken,
            cancel,
            flags,
            enrollCallback,
            surface,
            null,
            RectF(detectArea),
            timeout
        )
    }

    override fun enroll(
        cryptoToken: ByteArray?,
        cancel: CancellationSignal,
        flags: Int,
        enrollCallback: IMiuiFaceManager.EnrollmentCallback?,
        surface: Surface?,
        detectArea: RectF?,
        enrollArea: RectF,
        timeout: Int
    ) {
        var i: Int
        var e: RemoteException?
        if (enrollCallback != null) {
            if (cancel != null) {
                if (cancel.isCanceled) {
                    d(
                        TAG,
                        "enrollment already canceled"
                    )
                    return
                }
                cancel.setOnCancelListener(OnEnrollCancelListener())
            }
            try {
                initService()
                if (mMiuiFaceService != null) {
                    mEnrollmentCallback = enrollCallback
                    i = 2100
                    try {
                        binderCallEnroll(
                            mMiuiFaceService,
                            mToken,
                            cryptoToken,
                            0,
                            mServiceReceiver,
                            flags,
                            mContext.packageName,
                            surface,
                            enrollArea,
                            timeout
                        )
                    } catch (e2: RemoteException) {
                        e = e2
                        e(
                            TAG,
                            "exception in enroll: ",
                            e
                        )
                        enrollCallback.onEnrollmentError(i, getMessageInfo(i))
                        return
                    }
                }
                i = 2100
                d(TAG, "mMiuiFaceService is null")
                enrollCallback.onEnrollmentError(i, getMessageInfo(i))
            } catch (e3: RemoteException) {
                e = e3
                i = 2100
                e(TAG, "exception in enroll: ", e)
                enrollCallback.onEnrollmentError(i, getMessageInfo(i))
                return
            }
            return
        }
        throw IllegalArgumentException("Must supply an enrollment callback")
    }

    override fun extCmd(cmd: Int, param: Int): Int {
        var res = -1
        try {
            initService()
            if (mMiuiFaceService != null) {
                res = binderCallExtCmd(
                    mMiuiFaceService,
                    mToken,
                    mServiceReceiver,
                    cmd,
                    param,
                    mContext.packageName
                )
            }
        } catch (e: RemoteException) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("transact fail. ")
            stringBuilder.append(e)
            e(TAG, stringBuilder.toString())
        }
        if (DEBUG) {
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append("extCmd  cmd:")
            stringBuilder2.append(cmd)
            stringBuilder2.append(" param:")
            stringBuilder2.append(param)
            stringBuilder2.append(" res:")
            stringBuilder2.append(res)
            d(TAG, stringBuilder2.toString())
        }
        return res
    }

    override fun remove(face: Miuiface, callback: IMiuiFaceManager.RemovalCallback) {
        if (DEBUG) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("remove  faceId:")
            stringBuilder.append(face.miuifaceId)
            stringBuilder.append("  callback:")
            stringBuilder.append(callback)
            d(TAG, stringBuilder.toString())
        }
        try {
            initService()
            if (mMiuiFaceService != null) {
                mRemovalMiuiface = face
                mRemovalCallback = callback
                mEnrollmentCallback = null
                mAuthenticationCallback = null
                binderCallRemove(
                    mMiuiFaceService,
                    mToken,
                    face.miuifaceId,
                    face.groupId,
                    0,
                    mServiceReceiver
                )
                return
            }
            d(TAG, "mMiuiFaceService is null")
            callback.onRemovalError(face, 2100, getMessageInfo(2100))
        } catch (e: RemoteException) {
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append("transact fail. ")
            stringBuilder2.append(e)
            e(TAG, stringBuilder2.toString())
        }
    }

    override fun rename(faceId: Int, name: String) {
        if (DEBUG) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("rename  faceId:")
            stringBuilder.append(faceId)
            stringBuilder.append(" name:")
            stringBuilder.append(name)
            d(TAG, stringBuilder.toString())
        }
        try {
            initService()
            if (mMiuiFaceService != null) {
                binderCallRename(mMiuiFaceService, faceId, 0, name)
            }
        } catch (e: RemoteException) {
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append("transact fail. ")
            stringBuilder2.append(e)
            e(TAG, stringBuilder2.toString())
        }
    }

    override fun addLockoutResetCallback(callback: IMiuiFaceManager.LockoutResetCallback?) {
        if (DEBUG) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("addLockoutResetCallback  callback:")
            stringBuilder.append(callback)
            d(TAG, stringBuilder.toString())
        }
        try {
            initService()
            if (mMiuiFaceService != null) {
                mLockoutResetCallback = callback
                binderCallAddLoackoutResetCallback(mMiuiFaceService, mServiceReceiver)
            }
        } catch (e: RemoteException) {
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append("transact fail. ")
            stringBuilder2.append(e)
            e(TAG, stringBuilder2.toString())
        }
    }

    override fun resetTimeout(token: ByteArray) {
        if (DEBUG) {
            d(TAG, "resetTimeout")
        }
        try {
            initService()
            if (mMiuiFaceService != null) {
                binderCallRestTimeout(mMiuiFaceService, token)
            }
        } catch (e: RemoteException) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("transact fail. ")
            stringBuilder.append(e)
            e(TAG, stringBuilder.toString())
        }
    }

    override val enrolledFaces: List<Miuiface?>?
        get() {
            var stringBuilder: StringBuilder
            var res: List<Miuiface?>? = ArrayList()
            try {
                initService()
                if (mMiuiFaceService != null) {
                    res = binderCallGetEnrolledFaces(mMiuiFaceService, 0, mContext.packageName)
                }
            } catch (e: RemoteException) {
                stringBuilder = StringBuilder()
                stringBuilder.append("transact fail. ")
                stringBuilder.append(e)
                e(TAG, stringBuilder.toString())
            }
            if (DEBUG) {
                val str2: String
                val stringBuilder2 = StringBuilder()
                stringBuilder2.append("getEnrolledFaces   res:")
                if (res == null || res.size == 0) {
                    str2 = " is null"
                } else {
                    stringBuilder = StringBuilder()
                    stringBuilder.append(" ")
                    stringBuilder.append(res.size)
                    str2 = stringBuilder.toString()
                }
                stringBuilder2.append(str2)
                d(TAG, stringBuilder2.toString())
            }
            return res
        }

    override fun hasEnrolledFaces(): Int {
        return try {
            if (mHasFaceData && mIsValid) {
                return 1
            }
            if (mHasFaceData) {
                -1
            } else 0
        } catch (e: Exception) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("transact fail. ")
            stringBuilder.append(e)
            e(TAG, stringBuilder.toString())
            -2
        }
    }

    override fun preInitAuthen() {
        try {
            initService()
            if (mMiuiFaceService != null) {
                isFaceUnlockInited = false
                binderCallPpreInitAuthen(
                    mMiuiFaceService,
                    mToken,
                    mContext.packageName,
                    mServiceReceiver
                )
            }
        } catch (e: RemoteException) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("transact fail. ")
            stringBuilder.append(e)
            e(TAG, stringBuilder.toString())
        }
    }

    override val isReleased: Boolean
        get() = false

    override fun release() {}

    @Throws(RemoteException::class)
    private fun binderCallPpreInitAuthen(
        service: IBinder?,
        token: IBinder,
        packName: String,
        receiver: IBinder
    ) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeStrongBinder(token)
        request.writeString(packName)
        request.writeStrongBinder(receiver)
        service?.transact(2, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallAuthenticate(
        service: IBinder?,
        token: IBinder,
        sessionId: Long,
        userId: Int,
        receiver: IBinder?,
        flags: Int,
        packName: String,
        timeout: Int
    ): Int {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        var iBinder: IBinder? = null
        request.writeStrongBinder(token)
        request.writeLong(sessionId)
        request.writeInt(userId)
        if (receiver != null) {
            iBinder = receiver
        }
        request.writeStrongBinder(iBinder)
        request.writeInt(flags)
        request.writeString(packName)
        request.writeInt(timeout)
        service?.transact(3, request, reply, 0)
        reply.readException()
        val res = reply.readInt()
        request.recycle()
        reply.recycle()
        return res
    }

    @Throws(RemoteException::class)
    private fun binderCallCancelAuthention(service: IBinder?, token: IBinder, packName: String) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeStrongBinder(token)
        request.writeString(packName)
        service?.transact(4, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallEnroll(
        service: IBinder?,
        token: IBinder,
        cryptoToken: ByteArray?,
        groupId: Int,
        receiver: IBinder?,
        flags: Int,
        packName: String,
        surface: Surface?,
        detectArea: RectF?,
        timeout: Int
    ) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        var iBinder: IBinder? = null
        request.writeStrongBinder(token)
        request.writeByteArray(cryptoToken)
        request.writeInt(groupId)
        if (receiver != null) {
            iBinder = receiver
        }
        request.writeStrongBinder(iBinder)
        request.writeInt(flags)
        request.writeString(packName)
        if (surface != null) {
            request.writeInt(1)
            surface.writeToParcel(request, 0)
        } else {
            request.writeInt(0)
        }
        if (detectArea != null) {
            request.writeInt(1)
            detectArea.writeToParcel(request, 0)
        } else {
            request.writeInt(0)
        }
        request.writeInt(timeout)
        service?.transact(5, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallCancelEnrollment(service: IBinder?, token: IBinder) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeStrongBinder(token)
        service?.transact(6, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallRemove(
        service: IBinder?,
        token: IBinder,
        faceId: Int,
        groupId: Int,
        userId: Int,
        receiver: IBinder?
    ) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        var iBinder: IBinder? = null
        request.writeStrongBinder(token)
        request.writeInt(faceId)
        request.writeInt(groupId)
        request.writeInt(userId)
        if (receiver != null) {
            iBinder = receiver
        }
        request.writeStrongBinder(iBinder)
        service?.transact(7, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallRename(service: IBinder?, faceId: Int, groupId: Int, name: String) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeInt(faceId)
        request.writeInt(groupId)
        request.writeString(name)
        service?.transact(8, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallGetEnrolledFaces(
        service: IBinder?,
        groupId: Int,
        packName: String
    ): List<Miuiface?>? {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeInt(groupId)
        request.writeString(packName)
        service?.transact(9, request, reply, 0)
        reply.readException()
        val res: List<Miuiface> = ArrayList(reply.createTypedArrayList(Miuiface.CREATOR))
        request.recycle()
        reply.recycle()
        return res
    }

    @Throws(RemoteException::class)
    private fun binderCallPreEnroll(service: IBinder, token: IBinder) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeStrongBinder(token)
        service.transact(10, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallPostEnroll(service: IBinder, token: IBinder) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeStrongBinder(token)
        service.transact(11, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallHasEnrolledFaces(service: IBinder, groupId: Int, packName: String): Int {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeInt(groupId)
        request.writeString(packName)
        service.transact(12, request, reply, 0)
        reply.readException()
        val res = reply.readInt()
        request.recycle()
        reply.recycle()
        return res
    }

    @Throws(RemoteException::class)
    private fun binderCallAuthenticatorId(service: IBinder, packName: String): Long {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeString(packName)
        service.transact(14, request, reply, 0)
        reply.readException()
        val res = reply.readLong()
        request.recycle()
        reply.recycle()
        return res
    }

    @Throws(RemoteException::class)
    private fun binderCallRestTimeout(service: IBinder?, cryptoToken: ByteArray) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeByteArray(cryptoToken)
        service?.transact(15, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallAddLoackoutResetCallback(service: IBinder?, callback: IBinder) {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeStrongBinder(callback)
        service?.transact(16, request, reply, 0)
        reply.readException()
        request.recycle()
        reply.recycle()
    }

    @Throws(RemoteException::class)
    private fun binderCallGetVendorInfo(service: IBinder?, packName: String): String? {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        request.writeString(packName)
        service?.transact(17, request, reply, 0)
        reply.readException()
        val res = reply.readString()
        request.recycle()
        reply.recycle()
        return res
    }

    @Throws(RemoteException::class)
    private fun binderCallExtCmd(
        service: IBinder?,
        token: IBinder,
        receiver: IBinder?,
        cmd: Int,
        param: Int,
        packName: String
    ): Int {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        request.writeInterfaceToken(SERVICE_DESCRIPTOR)
        var iBinder: IBinder? = null
        request.writeStrongBinder(token)
        if (receiver != null) {
            iBinder = receiver
        }
        request.writeStrongBinder(iBinder)
        request.writeInt(cmd)
        request.writeInt(param)
        request.writeString(packName)
        service?.transact(101, request, reply, 0)
        reply.readException()
        val res = reply.readInt()
        request.recycle()
        reply.recycle()
        return res
    }

    private fun useHandler(handler: Handler?) {
        if (handler != null) {
            mHandler = ClientHandler(handler.looper)
        } else if (mHandler.looper != mContext.mainLooper) {
            mHandler = ClientHandler(
                mContext.mainLooper
            )
        }
    }

    private fun sendRemovedResult(face: Miuiface?, remaining: Int) {
        if (mRemovalCallback != null) {
            if (face == null || mRemovalMiuiface == null) {
                d(TAG, "Received MSG_REMOVED, but face or mRemovalMiuiface is null, ")
                return
            }
            val stringBuilder = StringBuilder()
            stringBuilder.append("sendRemovedResult faceId:")
            stringBuilder.append(face.miuifaceId)
            stringBuilder.append("  remaining:")
            stringBuilder.append(remaining)
            d(TAG, stringBuilder.toString())
            val faceId = face.miuifaceId
            val reqFaceId = mRemovalMiuiface?.miuifaceId
            if (reqFaceId == 0 || faceId == 0 || faceId == reqFaceId) {
                mRemovalCallback?.onRemovalSucceeded(face, remaining)
                return
            }
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append("Face id didn't match: ")
            stringBuilder2.append(faceId)
            stringBuilder2.append(" != ")
            stringBuilder2.append(reqFaceId)
            d(TAG, stringBuilder2.toString())
        }
    }

    private fun sendErrorResult(deviceId: Long, errMsgId: Int, vendorCode: Int) {
        val errorMsg =
            MiuiCodeToString.getErrorString(errMsgId, vendorCode) //getMessageInfo(errMsgId);
        val enrollmentCallback = mEnrollmentCallback
        if (enrollmentCallback != null) {
            enrollmentCallback.onEnrollmentError(errMsgId, errorMsg)
            return
        }
        val authenticationCallback = mAuthenticationCallback
        if (authenticationCallback != null) {
            authenticationCallback.onAuthenticationError(errMsgId, errorMsg)
            return
        }
        val removalCallback = mRemovalCallback
        removalCallback?.onRemovalError(mRemovalMiuiface, errMsgId, errorMsg)
    }

    private fun sendEnrollResult(face: Miuiface, remaining: Int) {
        val enrollmentCallback = mEnrollmentCallback
        enrollmentCallback?.onEnrollmentProgress(remaining, face.miuifaceId)
    }

    private fun sendAuthenticatedSucceeded(face: Miuiface, userId: Int) {
        val authenticationCallback = mAuthenticationCallback
        authenticationCallback?.onAuthenticationSucceeded(face)
    }

    private fun sendAuthenticatedFailed() {
        val authenticationCallback = mAuthenticationCallback
        authenticationCallback?.onAuthenticationFailed()
    }

    private fun sendLockoutReset() {
        val lockoutResetCallback = mLockoutResetCallback
        lockoutResetCallback?.onLockoutReset()
    }

    private fun sendAcquiredResult(deviceId: Long, clientInfo: Int, vendorCode: Int) {
        val msg =
            MiuiCodeToString.getAcquiredString(clientInfo, vendorCode) //getMessageInfo(clientInfo);
        val enrollmentCallback = mEnrollmentCallback
        if (enrollmentCallback != null) {
            enrollmentCallback.onEnrollmentHelp(clientInfo, msg)
            return
        }
        val authenticationCallback = mAuthenticationCallback
        authenticationCallback?.onAuthenticationHelp(clientInfo, msg)
    }

    private fun getMessageInfo(msgId: Int): String {
        val msg = "define by client"
        if (msgId == 1000) {
            return "Failed to open camera"
        }
        if (msgId == 1001) {
            return "Camera opened successfully"
        }
        if (msgId == 2000) {
            return "Cancel success"
        }
        return if (msgId == 2100) {
            "binder Call exception"
        } else when (msgId) {
            1 -> "Invalid parameter"
            2 -> "Handler Incorrect"
            3 -> "Unlock failure (internal error)"
            4 -> "Incoming data quality is not good"
            5 -> "No face detected"
            6 -> "Face is too small"
            7 -> "Face too big"
            8 -> "Face left"
            9 -> "Face up"
            10 -> "Face right"
            11 -> "Face down"
            12 -> "Comparison failed"
            13 -> "Live attack warning"
            14 -> "Vitality test failed"
            15 -> "Turn left"
            16 -> "Look up"
            17 -> "Turn right"
            18 -> "Look down"
            19 -> "Continue to call incoming data"
            20 -> "Picture is blurred"
            21 -> "Eye occlusion"
            22 -> "Eyes closed"
            23 -> "Mouth occlusion"
            24 -> "Handling Feature read exception"
            25 -> "Feature version error"
            26 -> "Bad light"
            27 -> "Multiple faces"
            28 -> "Blurred face"
            29 -> "Incomplete face"
            30 -> "The light is too dark"
            31 -> "The light is too bright"
            32 -> "Yin Yang face"
            else -> {
                if (!DEBUG) {
                    return msg
                }
                val stringBuilder = StringBuilder()
                stringBuilder.append("default msgId: ")
                stringBuilder.append(msgId)
                d(TAG, stringBuilder.toString())
                msg
            }
        }
    }

    private inner class ClientHandler : Handler {
        constructor(context: Context) : super(context.mainLooper)
        constructor(looper: Looper) : super(looper)

        override fun handleMessage(msg: Message) {
            if (DEBUG) {

                val stringBuilder = StringBuilder()
                stringBuilder.append(" handleMessage  callback what:")
                stringBuilder.append(msg.what)
                d(TAG, stringBuilder.toString())
            }
            val i = msg.what
            if (i == 261) {
                sendLockoutReset()
            } else if (i != 301) {
                when (i) {
                    201 -> {
                        sendEnrollResult(msg.obj as Miuiface, msg.arg1)
                        return
                    }
                    202 -> {
                        sendAcquiredResult(msg.obj as Long, msg.arg1, msg.arg2)
                        return
                    }
                    203 -> {
                        sendAuthenticatedSucceeded(msg.obj as Miuiface, msg.arg1)
                        return
                    }
                    204 -> {
                        sendAuthenticatedFailed()
                        return
                    }
                    205 -> {
                        sendErrorResult((msg.obj as Long).toLong(), msg.arg1, msg.arg2)
                        return
                    }
                    206 -> {
                        sendRemovedResult(msg.obj as Miuiface, msg.arg1)
                        return
                    }
                    else -> return
                }
            }
        }
    }

    private inner class OnAuthenticationCancelListener :
        CancellationSignal.OnCancelListener {
        override fun onCancel() {
            cancelAuthentication()
        }
    }

    private inner class OnEnrollCancelListener :
        CancellationSignal.OnCancelListener {
        override fun onCancel() {
            cancelEnrollment()
        }
    }
}