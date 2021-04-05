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
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.*
import android.provider.Settings
import android.view.Surface
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.BiometricClient.ServiceCallback
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.*
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*

class Miui3DFaceManagerImpl constructor(private val mContext: Context) : IMiuiFaceManager,
    ServiceCallback {

    companion object {
        const val COMMAND_ENROLL_RESUME_ENROLL_LOGIC = 0
        const val MSG_AUTHENTICATION_HELP_ALL_BLOCKED = 28
        const val MSG_AUTHENTICATION_HELP_BAD_AMBIENT_LIGHT = 32
        const val MSG_AUTHENTICATION_HELP_BOTH_EYE_BLOCKED = 25
        const val MSG_AUTHENTICATION_HELP_BOTH_EYE_CLOSE = 31
        const val MSG_AUTHENTICATION_HELP_FACE_AUTH_FAILD = 70
        const val MSG_AUTHENTICATION_HELP_FACE_DETECT_FAIL = 20
        const val MSG_AUTHENTICATION_HELP_FACE_DETECT_OK = 10
        const val MSG_AUTHENTICATION_HELP_FACE_TOO_NEER = 33
        const val MSG_AUTHENTICATION_HELP_LEFTEYE_MOUSE_BLOCKED = 26
        const val MSG_AUTHENTICATION_HELP_LEFT_EYE_BLOCKED = 22
        const val MSG_AUTHENTICATION_HELP_LEFT_EYE_CLOSE = 29
        const val MSG_AUTHENTICATION_HELP_LIVING_BODY_DETECTION_FAILED = 63
        const val MSG_AUTHENTICATION_HELP_MOUSE_BLOCKED = 24
        const val MSG_AUTHENTICATION_HELP_RIGHTEYE_MOUSE_BLOCKED = 27
        const val MSG_AUTHENTICATION_HELP_RIGHT_EYE_BLOCKED = 23
        const val MSG_AUTHENTICATION_HELP_RIGHT_EYE_CLOSE = 30
        const val MSG_AUTHENTICATION_STOP = 34
        const val MSG_ENROLL_ENROLL_TIMEOUT = 66
        const val MSG_ENROLL_ERROR_CREATE_FOLDER_FAILED = 52
        const val MSG_ENROLL_ERROR_DISABLE_FAIL = 57
        const val MSG_ENROLL_ERROR_ENABLE_FAIL = 50
        const val MSG_ENROLL_ERROR_FACE_LOST = 62
        const val MSG_ENROLL_ERROR_FLOOD_ITO_ERR = 41
        const val MSG_ENROLL_ERROR_IR_CAM_CLOSED = 6
        const val MSG_ENROLL_ERROR_LASER_ITO_ERR = 40
        const val MSG_ENROLL_ERROR_LIVING_BODY_DETECTION_FAILED = 63
        const val MSG_ENROLL_ERROR_NOT_SAME_PERSON = 58
        const val MSG_ENROLL_ERROR_PREVIEW_CAM_ERROR = 5
        const val MSG_ENROLL_ERROR_RTMV_IC_ERR = 53
        const val MSG_ENROLL_ERROR_SAVE_TEMPLATE_FAILED = 51
        const val MSG_ENROLL_ERROR_SDK_ERROR = 59
        const val MSG_ENROLL_ERROR_SYSTEM_EXCEPTION = 54
        const val MSG_ENROLL_ERROR_TEMLATE_FILE_NOT_EXIST = 56
        const val MSG_ENROLL_ERROR_TOF_BE_COVERED = 64
        const val MSG_ENROLL_ERROR_TOF_NOT_MOUNT = 65
        const val MSG_ENROLL_ERROR_UNLOCK_FAIL = 55
        const val MSG_ENROLL_FACE_IR_FOUND = 2
        const val MSG_ENROLL_FACE_IR_NOT_FOUND = 4
        const val MSG_ENROLL_FACE_RGB_FOUND = 1
        const val MSG_ENROLL_FACE_RGB_NOT_FOUND = 3
        const val MSG_ENROLL_HELP_ALL_BLOCKED = 28
        const val MSG_ENROLL_HELP_BAD_AMBIENT_LIGHT = 32
        const val MSG_ENROLL_HELP_BOTH_EYE_BLOCKED = 25
        const val MSG_ENROLL_HELP_BOTH_EYE_CLOSE = 31
        const val MSG_ENROLL_HELP_DIRECTION_DOWN = 13
        const val MSG_ENROLL_HELP_DIRECTION_LEFT = 14
        const val MSG_ENROLL_HELP_DIRECTION_RIGHT = 15
        const val MSG_ENROLL_HELP_DIRECTION_UP = 12
        const val MSG_ENROLL_HELP_FACE_DETECT_FAIL_NOT_IN_ROI = 21
        const val MSG_ENROLL_HELP_FACE_DETECT_OK = 10
        const val MSG_ENROLL_HELP_FACE_TOO_NEER = 33
        const val MSG_ENROLL_HELP_IR_CAM_OPEND = 2
        const val MSG_ENROLL_HELP_LEFTEYE_MOUSE_BLOCKED = 26
        const val MSG_ENROLL_HELP_LEFT_EYE_BLOCKED = 22
        const val MSG_ENROLL_HELP_LEFT_EYE_CLOSE = 29
        const val MSG_ENROLL_HELP_MOUSE_BLOCKED = 24
        const val MSG_ENROLL_HELP_PREVIEW_CAM_OPEND = 1
        const val MSG_ENROLL_HELP_RIGHTEYE_MOUSE_BLOCKED = 27
        const val MSG_ENROLL_HELP_RIGHT_EYE_BLOCKED = 23
        const val MSG_ENROLL_HELP_RIGHT_EYE_CLOSE = 30
        const val MSG_ENROLL_PROGRESS_SUCCESS = 0
        const val TABLE_TEMPLATE_COLUMN_DATA = "data"
        const val TABLE_TEMPLATE_COLUMN_GROUP_ID = "group_id"
        const val TABLE_TEMPLATE_COLUMN_ID = "_id"
        const val TABLE_TEMPLATE_COLUMN_NAME = "template_name"
        const val TABLE_TEMPLATE_COLUMN_VALID = "valid"
        const val TABLE_TEMPLATE_NAME = "_template"
        private const val CANCEL_STATUS_DONE = 1
        private const val CANCEL_STATUS_NONE = 0
        private const val DB_STATUS_NONE = 0
        private const val DB_STATUS_PREPARED = 2
        private const val DB_STATUS_PREPARING = 1
        private const val FACEUNLOCK_SUPPORT_SUPERPOWER = "faceunlock_support_superpower"
        private const val FACE_UNLOCK_HAS_FEATURE = "face_unlock_has_feature_sl"
        private const val FACE_UNLOCK_HAS_FEATURE_URI =
            "content://settings/secure/face_unlock_has_feature_sl"
        private const val POWERMODE_SUPERSAVE_OPEN = "power_supersave_mode_open"
        private const val POWERMODE_SUPERSAVE_OPEN_URI =
            "content://settings/secure/power_supersave_mode_open"
        private const val RECEIVER_ON_AUTHENTICATION_TIMEOUT = 1
        private const val RECEIVER_ON_ENROLL_TIMEOUT = 0
        private const val TEMPLATE_PATH = "/data/user/0/com.xiaomi.biometric/files/"
        private const val VERSION_1 = 1
        private const val height = 4
        private const val width = 3
        private const val DEBUG = true
        private const val LOG_TAG = "3DFaceManagerImpl"

        @Volatile
        private var INSTANCE: IMiuiFaceManager? = null
        fun getInstance(con: Context): IMiuiFaceManager? {
            if (INSTANCE != null && INSTANCE?.isReleased == true) {
                INSTANCE = null
            }
            if (INSTANCE == null) {
                synchronized(MiuiFaceManagerImpl::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = Miui3DFaceManagerImpl(con)
                    }
                }
            }
            return INSTANCE
        }
    }

    private val hasEnrollFace = 0
    private val mBinderLock = Any()
    private val mEnrollParam = EnrollParam()
    private lateinit var mSuperPowerOpenObserver: ContentObserver
    private lateinit var mHasFaceDataObserver: ContentObserver
    private val mMiuifaceList: List<Miuiface>? = null
    private var boostFramework: Any? = null
    private var mAuthenticationCallback: IMiuiFaceManager.AuthenticationCallback?
    private var mBiometricClient: BiometricClient? = null
    private var mDatabaseChanged = false
    private var mDatabaseStatus = 0
    private var mDisonnected = false
    private var mEnrollmentCallback: IMiuiFaceManager.EnrollmentCallback?
    private var mFaceInfo: FaceInfo? = null
    private var mGroupIdMax = 0
    private var mGroupItemList: MutableList<GroupItem?>? = null
    private var mHandler: Handler
    private var mHasFaceData = false
    private var mIsSuperPower = false
    override var isReleased = false
        private set
    private var mRemovalCallback: IMiuiFaceManager.RemovalCallback?
    private var mRemovalMiuiface: Miuiface? = null
    private var mTemplateIdMax = 0
    private var mTemplateItemList: MutableList<TemplateItem?>? = null
    private var mcancelStatus = 0
    private var myDB: SQLiteDatabase? = null
    private var myTemplateItemList: MutableList<TemplateItem?>? = null
    private var sAcquireFunc: Method? = null
    private var sPerfClass: Class<*>? = null
    private var sReleaseFunc: Method? = null

    init {
        mDisonnected = true
        isReleased = false
        mRemovalCallback = null
        mAuthenticationCallback = null
        mEnrollmentCallback = null

        mHandler = ClientHandler(
            mContext
        )
        mSuperPowerOpenObserver = object : ContentObserver(mHandler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                mIsSuperPower = SettingsSystem.getIntForUser(
                    mContext.contentResolver,
                    POWERMODE_SUPERSAVE_OPEN,
                    0,
                    0
                ) != 0
            }
        }
        mHasFaceDataObserver = object : ContentObserver(mHandler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                mHasFaceData = SettingsSecure.getIntForUser(
                    mContext.contentResolver,
                    FACE_UNLOCK_HAS_FEATURE, 0, 0
                ) != 0
            }
        }
        try {

//        Secure.putIntForUser(this.mContext.getContentResolver(), FACEUNLOCK_SUPPORT_SUPERPOWER, 1, -2);
            ContentResolverHelper.registerContentObserver(
                mContext.contentResolver, Settings.Secure.getUriFor(FACE_UNLOCK_HAS_FEATURE),
                false, mHasFaceDataObserver, 0
            )
            mHasFaceDataObserver.onChange(false)
            ContentResolverHelper.registerContentObserver(
                mContext.contentResolver, Settings.System.getUriFor(POWERMODE_SUPERSAVE_OPEN),
                false, mSuperPowerOpenObserver, 0
            )
            mSuperPowerOpenObserver.onChange(false)
        } catch (e: Throwable) {
            e(e)
        }
        mBiometricClient = BiometricClient(mContext)
        mBiometricClient?.let {
            it.startService(this)
        }
        preloadBoostFramework()
    }

    private fun preloadBoostFramework() {
        try {
            sPerfClass = Class.forName("android.util.BoostFramework")
            val constuctor = sPerfClass?.getConstructor()
            boostFramework = constuctor?.newInstance()
            sAcquireFunc =
                sPerfClass?.getMethod("perfLockAcquire", Integer.TYPE, IntArray::class.java)
            sReleaseFunc = sPerfClass?.getMethod("perfLockRelease")
            d(LOG_TAG, "preload BoostFramework succeed.")
        } catch (e: Exception) {
            e(LOG_TAG, "preload class android.util.BoostFramework failed")
        }
    }

    override fun onBiometricServiceConnected() {
        val str = LOG_TAG
        val stringBuilder = StringBuilder()
        stringBuilder.append("onBiometricServiceConnected ")
        stringBuilder.append(this)
        d(str, stringBuilder.toString())
        mDisonnected = false
        prepareDatabase()
    }

    override fun onBiometricServiceDisconnected() {
        var str = LOG_TAG
        var stringBuilder = StringBuilder()
        stringBuilder.append("onBiometricServiceDisconnected ")
        stringBuilder.append(this)
        d(str, stringBuilder.toString())
        if (!mDisonnected) {
            str = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append("xiaomi--> set mDisonnected true ")
            stringBuilder.append(this)
            d(str, stringBuilder.toString())
            mDisonnected = true
            release()
        }
    }

    override fun onBiometricEventClassLoader(bundle: Bundle) {
        if (BiometricConnect.DEBUG_LOG) {
            d(LOG_TAG, "onBiometricEventClassLoader")
        }
        bundle.classLoader = BiometricConnect::class.java.classLoader
    }

    override fun release() {
        val str: String
        val stringBuilder: StringBuilder
        if (isReleased) {
            str = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append("release ignore ")
            stringBuilder.append(this)
            e(str, stringBuilder.toString())
            return
        }
        str = LOG_TAG
        stringBuilder = StringBuilder()
        stringBuilder.append("release ctx:")
        stringBuilder.append(mContext)
        stringBuilder.append(", this:")
        stringBuilder.append(this)
        d(str, stringBuilder.toString())
        isReleased = true
    }

    override fun onBiometricEventCallback(module_id: Int, event: Int, arg1: Int, arg2: Int) {
        val str: String
        val stringBuilder: StringBuilder
        if (module_id != 1) {
            str = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append("onBiometricEventCallback ignore - module_id:+")
            stringBuilder.append(module_id)
            stringBuilder.append(" event: ")
            stringBuilder.append(event)
            stringBuilder.append(", arg1:")
            stringBuilder.append(arg1)
            stringBuilder.append(", arg2:")
            stringBuilder.append(arg2)
            e(str, stringBuilder.toString())
        } else if (mDisonnected) {
            str = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append("onBiometricEventCallback mDisonnected:")
            stringBuilder.append(mDisonnected)
            stringBuilder.append(" ignore event: ")
            stringBuilder.append(event)
            stringBuilder.append(", arg1:")
            stringBuilder.append(arg1)
            stringBuilder.append(", arg2:")
            stringBuilder.append(arg2)
            e(str, stringBuilder.toString())
        } else {
            var str2: String
            var stringBuilder2: StringBuilder
            if (BiometricConnect.DEBUG_LOG) {
                str2 = LOG_TAG
                stringBuilder2 = StringBuilder()
                stringBuilder2.append("onBiometricEventCallback - event: ")
                stringBuilder2.append(event)
                stringBuilder2.append(", arg1:")
                stringBuilder2.append(arg1)
                stringBuilder2.append(", arg2:")
                stringBuilder2.append(arg2)
                d(str2, stringBuilder2.toString())
            }
            if (event == 0) {
                val enrollmentCallback = mEnrollmentCallback
                enrollmentCallback?.onEnrollmentHelp(1, null)
            } else if (event != 1) {
                if (!(event == 2 || event == 3)) {
                    var enrollmentCallback2: IMiuiFaceManager.EnrollmentCallback?
                    if (event != 4) {
                        when (event) {
                            20 -> {
                                enrollmentCallback2 = mEnrollmentCallback
                                if (enrollmentCallback2 != null) {
                                    enrollmentCallback2.onEnrollmentHelp(2, null)
                                }
                            }
                            21 -> if (BiometricConnect.DEBUG_LOG) {
                                str = LOG_TAG
                                stringBuilder = StringBuilder()
                                stringBuilder.append("onBiometricEventCallback  MSG_CB_EVENT_IR_CAM_PREVIEW_SIZE arg1:")
                                stringBuilder.append(arg1)
                                stringBuilder.append(",arg2:")
                                stringBuilder.append(arg2)
                                d(str, stringBuilder.toString())
                            }
                            22 -> {
                            }
                            23 -> d(LOG_TAG, "MSG_CB_EVENT_IR_CAM_CLOSED")
                            24 -> {
                                enrollmentCallback2 = mEnrollmentCallback
                                if (enrollmentCallback2 != null) {
                                    enrollmentCallback2.onEnrollmentError(6, null)
                                    cancelEnrollment()
                                }
                            }
                            else -> when (event) {
                                30 -> {
                                    str2 = LOG_TAG
                                    stringBuilder2 = StringBuilder()
                                    stringBuilder2.append("onBiometricEventCallback  MSG_CB_EVENT_ENROLL_SUCCESS mEnrollmentCallback:")
                                    stringBuilder2.append(mEnrollmentCallback)
                                    d(str2, stringBuilder2.toString())
                                    val enrollmentCallback3 = mEnrollmentCallback
                                    if (enrollmentCallback3 != null) {
                                        enrollmentCallback3.onEnrollmentProgress(0, arg1)
                                        //                                            Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_HAS_FEATURE, 1, -2);
                                        synchronized(mBinderLock) {
                                            mDatabaseStatus = 0
                                            prepareDatabase()
                                        }
                                    }
                                }
                                31 -> {
                                    enrollmentCallback2 = mEnrollmentCallback
                                    if (enrollmentCallback2 != null) {
                                        enrollmentCallback2.onEnrollmentHelp(arg1, null)
                                        str = LOG_TAG
                                        stringBuilder = StringBuilder()
                                        stringBuilder.append("MSG_CB_EVENT_ENROLL_ERROR arg1 = ")
                                        stringBuilder.append(arg1)
                                        d(str, stringBuilder.toString())
                                    }
                                }
                                32 -> {
                                    str = LOG_TAG
                                    stringBuilder2 = StringBuilder()
                                    stringBuilder2.append("MSG_CB_EVENT_ENROLL_INFO arg1 = ")
                                    stringBuilder2.append(arg1)
                                    d(str, stringBuilder2.toString())
                                    enrollmentCallback2 = mEnrollmentCallback
                                    if (enrollmentCallback2 != null) {
                                        enrollmentCallback2.onEnrollmentHelp(arg1, null)
                                    }
                                }
                                else -> {
                                    val authenticationCallback: IMiuiFaceManager.AuthenticationCallback?
                                    when (event) {
                                        40 -> {
                                            str = LOG_TAG
                                            stringBuilder2 = StringBuilder()
                                            stringBuilder2.append("onBiometricEventCallback  MSG_CB_EVENT_VERIFY_SUCCESS mAuthenticationCallback:")
                                            stringBuilder2.append(mAuthenticationCallback)
                                            d(str, stringBuilder2.toString())
                                            authenticationCallback = mAuthenticationCallback
                                            if (authenticationCallback != null) {
                                                authenticationCallback.onAuthenticationSucceeded(
                                                    null
                                                )
                                                cancelAuthentication()
                                            }
                                        }
                                        41 -> {
                                            authenticationCallback = mAuthenticationCallback
                                            if (authenticationCallback != null) {
                                                authenticationCallback.onAuthenticationHelp(
                                                    70,
                                                    null
                                                )
                                            }
                                        }
                                        42 -> {
                                            authenticationCallback = mAuthenticationCallback
                                            if (authenticationCallback != null) {
                                                authenticationCallback.onAuthenticationHelp(
                                                    arg1,
                                                    null
                                                )
                                            }
                                        }
                                        43 -> {
                                            val removalCallback = mRemovalCallback
                                            if (removalCallback != null) {
                                                removalCallback.onRemovalSucceeded(
                                                    mRemovalMiuiface,
                                                    mMiuifaceList?.size ?: 0
                                                )
                                                mRemovalCallback = null
                                                mRemovalMiuiface = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    enrollmentCallback2 = mEnrollmentCallback
                    if (enrollmentCallback2 != null) {
                        enrollmentCallback2.onEnrollmentError(5, null)
                        cancelEnrollment()
                    }
                }
            } else if (BiometricConnect.DEBUG_LOG) {
                str = LOG_TAG
                stringBuilder = StringBuilder()
                stringBuilder.append("onBiometricEventCallback  MSG_CB_EVENT_RGB_CAM_PREVIEW_SIZE mEnrollmentCallback:")
                stringBuilder.append(mEnrollmentCallback)
                stringBuilder.append(" arg1:")
                stringBuilder.append(arg1)
                stringBuilder.append(",arg2:")
                stringBuilder.append(arg2)
                d(str, stringBuilder.toString())
            }
        }
    }

    override fun onBiometricBundleCallback(module_id: Int, key: Int, bundle: Bundle) {
        val str: String
        val stringBuilder: StringBuilder
        if (module_id != 1) {
            str = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append("onBiometricBundleCallback ignore module_id:")
            stringBuilder.append(module_id)
            stringBuilder.append(", key:")
            stringBuilder.append(key)
            e(str, stringBuilder.toString())
        } else if (mDisonnected) {
            str = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append("onBiometricBundleCallback mDisonnected:")
            stringBuilder.append(mDisonnected)
            stringBuilder.append(" ignore key:")
            stringBuilder.append(key)
            e(str, stringBuilder.toString())
        } else {
            if (BiometricConnect.DEBUG_LOG) {
                str = LOG_TAG
                stringBuilder = StringBuilder()
                stringBuilder.append("onBiometricBundleCallback key:")
                stringBuilder.append(key)
                d(str, stringBuilder.toString())
            }
            if (key == 2) {
                handlerFace(bundle)
            } else if (key == 3) {
                handlerDatabase(bundle)
            } else if (key != 5) {
            }
        }
    }

    override val isFaceFeatureSupport: Boolean
        get() = if (mIsSuperPower) {
            d(
                LOG_TAG,
                "enter super power mode, isFaceFeatureSupport:false"
            )
            false
        } else if (MiuiBuild.IS_INTERNATIONAL_BUILD) {
            false
        } else {
            "ursa" == MiuiBuild.DEVICE
        }
    override val isSupportScreenOnDelayed: Boolean
        get() = false
    override val isFaceUnlockInited: Boolean
        get() = false
    override val managerVersion: Int
        get() = 1
    override val vendorInfo: String?
        get() = "3D Structure Light"

    private fun handlerDatabase(bundle: Bundle) {
        if (mDatabaseStatus != 1) {
            e(LOG_TAG, "handlerDatabase mDatabaseStatus ignore")
            return
        }
        if (BiometricConnect.DEBUG_LOG) {
            d(LOG_TAG, "handlerDatabase ")
        }
        mTemplateIdMax = bundle.getInt(BiometricConnect.MSG_CB_BUNDLE_DB_TEMPLATE_ID_MAX)
        mGroupIdMax = bundle.getInt(BiometricConnect.MSG_CB_BUNDLE_DB_GROUP_ID_MAX)
        val listGroup = bundle.getParcelableArrayList<Parcelable>("group")
        if (BiometricConnect.DEBUG_LOG) {
            val str = LOG_TAG
            val stringBuilder = StringBuilder()
            stringBuilder.append("handlerDatabase listGroup:")
            stringBuilder.append(listGroup?.size)
            d(str, stringBuilder.toString())
        }
        val listTemplate =
            bundle.getParcelableArrayList<Parcelable>(BiometricConnect.MSG_CB_BUNDLE_DB_TEMPLATE)
        if (BiometricConnect.DEBUG_LOG) {
            val str2 = LOG_TAG
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append("handlerDatabase list:")
            stringBuilder2.append(listTemplate?.size)
            d(str2, stringBuilder2.toString())
        }
        initClientDB(listGroup, listTemplate)
    }

    private fun initClientDB(listGroup: ArrayList<Parcelable>?, list: ArrayList<Parcelable>?) {
        if (mDatabaseStatus != 2 || mDatabaseChanged) {
            d(LOG_TAG, "initClientDB begin")
            mTemplateItemList = ArrayList<TemplateItem?>()
            var it: Iterator<*> = list?.iterator() ?: listOf<TemplateItem?>().iterator()
            var clazz: Class<*>? = null
            while (it.hasNext()) {
                val i = it.next()
                try {
                    if (clazz == null) {
                        clazz = i?.javaClass
                    }
                    clazz?.let {
                        val item = TemplateItem()
                        item.id = it.getField("mId").getInt(i)
                        item.name = it.getField("mName").get(i) as String
                        item.group_id = it.getField("mGroupId").getInt(i)
                        item.data = it.getField("mData").get(i) as String
                        mTemplateItemList?.add(item)
                    }
                } catch (e: Throwable) {
                    e(e)
                }
            }
            mGroupItemList = ArrayList<GroupItem?>()
            it = listGroup?.iterator() ?: listOf<TemplateItem?>().iterator()
            clazz = null
            while (it.hasNext()) {
                val group: Any? = it.next()
                try {
                    if (clazz == null) {
                        clazz = group?.javaClass
                    }
                    clazz?.let {
                        val groupItem = GroupItem()
                        groupItem.id = it.getField("mId").getInt(group)
                        groupItem.name = it.getField("mName").get(group) as String
                        mGroupItemList?.add(groupItem)
                    }
                } catch (e: Throwable) {
                    e(e)
                }
            }
            clazz = null
            mDatabaseStatus = 2
            mDatabaseChanged = false
            d(LOG_TAG, "initClientDB ok")
        }
    }

    private fun prepareDatabase() {
        if (mDatabaseStatus != 0) {
            e(LOG_TAG, "prepareDatabase ignore!")
            return
        }
        d(LOG_TAG, "prepareDatabase")
        mDatabaseStatus = 1
        mBiometricClient?.sendCommand(9)
    }

    private fun resetDatabase() {
        if (mDatabaseStatus != 2) {
            e(LOG_TAG, "resetDatabase ignore!")
            return
        }
        d(LOG_TAG, "resetDatabase")
        mDatabaseStatus = 1
        mBiometricClient?.sendCommand(10)
    }

    private fun commitDatabase() {
        if (isReleased) {
            e(LOG_TAG, "commitDatabase ignore!")
        } else if (mDatabaseChanged) {
            d(LOG_TAG, "commitDatabase")
            val out_bundle = Bundle()
            out_bundle.classLoader = BiometricConnect::class.java.classLoader
            val listGroup: ArrayList<Parcelable?> = ArrayList<Parcelable?>()
            mGroupItemList?.let {
                for (g in it) {
                    if (g != null)
                        listGroup.add(BiometricConnect.getDBGroup(g.id, g.name))
                }
            }

            out_bundle.putParcelableArrayList("group", listGroup)
            val listTemplate: ArrayList<Parcelable?> = ArrayList<Parcelable?>()
            mTemplateItemList?.let {
                for (i in it) {
                    if (i != null)
                        listTemplate.add(
                            BiometricConnect.getDBTemplate(
                                i.id,
                                i.name,
                                i.data,
                                i.group_id
                            )
                        )
                }
            }

            out_bundle.putParcelableArrayList(
                BiometricConnect.MSG_CB_BUNDLE_DB_TEMPLATE,
                listTemplate
            )
            mBiometricClient?.sendBundle(4, out_bundle)
        }
    }

    private fun handlerFace(bundle: Bundle) {
        if (mEnrollmentCallback != null) {
            if (BiometricConnect.DEBUG_LOG) {
                d(LOG_TAG, "handlerFace ")
            }
            val is_ir_detect = bundle.getBoolean(BiometricConnect.MSG_CB_BUNDLE_FACE_IS_IR)
            if (bundle.getBoolean(BiometricConnect.MSG_CB_BUNDLE_FACE_HAS_FACE)) {
                if (mFaceInfo == null) {
                    mFaceInfo = FaceInfo()
                }
                val parcelable =
                    bundle.getParcelable<Parcelable>(BiometricConnect.MSG_CB_BUNDLE_FACE_RECT_BOUND)
                if (parcelable is Rect) {
                    mFaceInfo?.bounds = parcelable
                    if (BiometricConnect.DEBUG_LOG) {
                        val str = LOG_TAG
                        val stringBuilder = StringBuilder()
                        stringBuilder.append("handlerFace detect face:")
                        stringBuilder.append(mFaceInfo?.bounds.toString())
                        d(str, stringBuilder.toString())
                    }
                }
                mFaceInfo?.yaw = bundle.getFloat(BiometricConnect.MSG_CB_BUNDLE_FACE_FLOAT_YAW)
                mFaceInfo?.pitch = bundle.getFloat("pitch")
                mFaceInfo?.roll = bundle.getFloat(BiometricConnect.MSG_CB_BUNDLE_FACE_FLOAT_ROLL)
                mFaceInfo?.eye_dist =
                    bundle.getFloat(BiometricConnect.MSG_CB_BUNDLE_FACE_FLOAT_EYE_DIST)
                try {
                    val parcelables =
                        bundle.getParcelableArray(BiometricConnect.MSG_CB_BUNDLE_FACE_POINTS_ARRAY)
                    mFaceInfo?.points_array = parcelables as Array<Point?>
                } catch (cc: Exception) {
                }
                if (!is_ir_detect) {
                    val enrollmentCallback = mEnrollmentCallback
                    if (enrollmentCallback != null) {
                        enrollmentCallback.onEnrollmentHelp(1, null)
                        return
                    }
                }
                if (is_ir_detect && mEnrollmentCallback != null) {
                    val z = mEnrollParam.enableDistanceDetect
                    mEnrollmentCallback?.onEnrollmentHelp(2, null)
                }
                return
            }
            val enrollmentCallback2 = mEnrollmentCallback
            if (enrollmentCallback2 != null) {
                val i: Int = if (is_ir_detect) {
                    4
                } else {
                    3
                }
                enrollmentCallback2.onEnrollmentHelp(i, null)
            }
        }
    }

    override fun hasEnrolledFaces(): Int {
        tryConnectService()
        return if (mHasFaceData) {
            1
        } else 0
    }

    override fun remove(face: Miuiface, callback: IMiuiFaceManager.RemovalCallback) {
        if (isReleased) {
            e(LOG_TAG, "removeTemplate ignore!")
        } else if (mDatabaseStatus != 2) {
            val str = LOG_TAG
            val stringBuilder = StringBuilder()
            stringBuilder.append("removeTemplate error mDatabaseStatus :")
            stringBuilder.append(mDatabaseStatus)
            d(str, stringBuilder.toString())
        } else {
            val templateItem = findTemplate(face.miuifaceId)
            if (templateItem == null) {
                val str2 = LOG_TAG
                val stringBuilder2 = StringBuilder()
                stringBuilder2.append("removeTemplate findTemplate ")
                stringBuilder2.append(face.miuifaceId)
                stringBuilder2.append(" is null")
                d(str2, stringBuilder2.toString())
                return
            }
            d(LOG_TAG, "removeTemplate")
            mTemplateItemList?.remove(templateItem)
            //            Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_HAS_FEATURE, 0, -2);
            mDatabaseChanged = true
            mRemovalCallback = callback
            mRemovalMiuiface = face
            commitDatabase()
        }
    }

    private fun findTemplate(id: Int): TemplateItem? {
        mTemplateItemList?.let {
            for (i in it) {
                if (i?.id == id) {
                    return i
                }
            }
        }
        return null
    }

    val templatepath: Int
        get() {
            val dbFile = File(TEMPLATE_PATH, "biometric.db")
            if (dbFile.exists()) {
                d(LOG_TAG, "getTemplatepath")
                myDB = SQLiteDatabase.openDatabase(dbFile.path, null, 0)
                myTemplateItemList = ArrayList<TemplateItem?>()
                d(LOG_TAG, "selectTemplate")
                val cursor = myDB?.rawQuery(
                    "select _id,data,template_name,group_id from _template where valid=1",
                    null
                )
                if (cursor?.moveToFirst() == true) {
                    e(LOG_TAG, "xiaomi -->4.3 test")
                    val t = TemplateItem()
                    t.id = cursor.getInt(cursor.getColumnIndex("_id"))
                    t.name = cursor.getString(cursor.getColumnIndex(TABLE_TEMPLATE_COLUMN_NAME))
                    t.group_id = cursor.getInt(cursor.getColumnIndex("group_id"))
                    t.data = cursor.getString(cursor.getColumnIndex("data"))
                    myTemplateItemList?.add(t)
                }
                cursor?.close()
                return myTemplateItemList?.size ?: 0
            }
            d(LOG_TAG, "getTemplatepath faild")
            return 0
        }

    private fun getMessageInfo(msgId: Int): String {
        val msg = "define by 3dclient"
        if (msgId == 34) {
            return "Be cancel"
        }
        val str = LOG_TAG
        val stringBuilder = StringBuilder()
        stringBuilder.append("default msgId: ")
        stringBuilder.append(msgId)
        d(str, stringBuilder.toString())
        return msg
    }

    private fun tryConnectService() {
        if (mDisonnected) {
            e(LOG_TAG, "mDisonnected is true ")
            mDatabaseStatus = 0
            mBiometricClient?.startService(this)
        }
    }

    override fun rename(faceId: Int, name: String) {
        if (isReleased) {
            e(LOG_TAG, "setTemplateName ignore!")
        } else if (mDatabaseStatus != 2) {
            val str = LOG_TAG
            val stringBuilder = StringBuilder()
            stringBuilder.append("setTemplateName error mPrepareDbStatus:")
            stringBuilder.append(mDatabaseStatus)
            d(str, stringBuilder.toString())
        } else {
            val templateItem = findTemplate(faceId)
            if (templateItem == null) {
                val str2 = LOG_TAG
                val stringBuilder2 = StringBuilder()
                stringBuilder2.append("setTemplateName findTemplate ")
                stringBuilder2.append(faceId)
                stringBuilder2.append(" is null")
                d(str2, stringBuilder2.toString())
                return
            }
            d(LOG_TAG, "setTemplateName")
            templateItem.name = name
            mDatabaseChanged = true
            commitDatabase()
        }
    }

    override val enrolledFaces: List<Miuiface?>?
        get() {
            val res: MutableList<Miuiface?> = ArrayList<Miuiface?>()
            e(LOG_TAG, " xiaomi getEnrolledFaces!")
            mTemplateItemList?.let {
                for (i in it) {
                    if (i != null) {
                        res.add(Miuiface(i.name, i.group_id, i.id, 0))
                    }
                }
            }
            return res
        }

    override fun preInitAuthen() {
        tryConnectService()
    }

    private fun cancelAuthentication() {
        val str: String
        val stringBuilder: StringBuilder
        if (isReleased) {
            str = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append("stopVerify ctx:")
            stringBuilder.append(mContext)
            stringBuilder.append(" ignore!")
            e(str, stringBuilder.toString())
            return
        }
        if (mcancelStatus != 0) {
            mAuthenticationCallback?.onAuthenticationError(34, getMessageInfo(34))
            mcancelStatus = 0
        }
        try {
            if (sReleaseFunc != null) {
                sReleaseFunc?.invoke(boostFramework)
            }
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e2: InvocationTargetException) {
            e2.printStackTrace()
        }
        str = LOG_TAG
        stringBuilder = StringBuilder()
        stringBuilder.append("cancelAuthentication ctx:")
        stringBuilder.append(mContext)
        d(str, stringBuilder.toString())
        mAuthenticationCallback = null
        mBiometricClient?.sendCommand(6)
        mHandler.removeMessages(1)
    }

    override fun authenticate(
        cancel: CancellationSignal?,
        flags: Int,
        callback: IMiuiFaceManager.AuthenticationCallback?,
        handler: Handler?,
        timeout: Int
    ) {
        var str = " ignore!"
        val str2 = "start authenticate ctx:"
        val str3: String
        val stringBuilder: StringBuilder
        if (isReleased) {
            str3 = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append(str2)
            stringBuilder.append(mContext)
            stringBuilder.append(str)
            e(str3, stringBuilder.toString())
        } else if (hasEnrolledFaces() == 0) {
            str3 = LOG_TAG
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append("has no enrolled face ctx:")
            stringBuilder2.append(mContext)
            stringBuilder2.append(str)
            e(str3, stringBuilder2.toString())
        } else if (callback != null) {
            if (cancel != null) {
                if (cancel.isCanceled) {
                    d(LOG_TAG, "authentication already canceled")
                    return
                }
                cancel.setOnCancelListener(OnAuthenticationCancelListener())
            }
            try {
                if (sAcquireFunc != null) {
                    val method = sAcquireFunc
                    val obj = boostFramework
                    val objArr = arrayOfNulls<Any>(2)
                    objArr[0] = 2000
                    objArr[1] = intArrayOf(1082130432, 4095, 1086324736, 1)
                    method?.invoke(obj, *objArr)
                }
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e2: InvocationTargetException) {
                e2.printStackTrace()
            }
            str = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append(str2)
            stringBuilder.append(mContext)
            d(str, stringBuilder.toString())
            useHandler(handler)
            mAuthenticationCallback = callback
            tryConnectService()
            mBiometricClient?.sendCommand(5)
            val handler2 = mHandler
            handler2.sendMessageDelayed(handler2.obtainMessage(1), timeout.toLong())
        } else {
            throw IllegalArgumentException("Must supply an authentication callback")
        }
    }

    private fun cancelEnrollment() {
        if (isReleased) {
            e(LOG_TAG, "stopEnroll ignore!")
            return
        }
        d(LOG_TAG, "stopEnroll")
        mBiometricClient?.sendCommand(8)
        mBiometricClient?.sendCommand(4)
        mHandler.removeMessages(0)
    }

    private fun adjustDetectZone(detect_zone: FloatArray?) {
        if (detect_zone != null && detect_zone.size >= 4) {
            for (i in detect_zone.indices) {
                val temp = detect_zone[i]
                if (i % 2 == 1) {
                    if (i < 4) {
                        detect_zone[i] = IrToRgbScale(detect_zone[i], 1.18f)
                    } else {
                        detect_zone[i] = IrToRgbScale(detect_zone[i], 1.06f)
                    }
                    detect_zone[i] = IrToRgbMove(detect_zone[i], -0.067f)
                    detect_zone[i] = IrToRgbRadio(detect_zone[i])
                } else {
                    if (i < 4) {
                        detect_zone[i] = IrToRgbScale(detect_zone[i], 1.16f)
                    } else {
                        detect_zone[i] = IrToRgbScale(detect_zone[i], 1.05f)
                    }
                    detect_zone[i] = IrToRgbMove(detect_zone[i], -0.044f)
                }
                val str = LOG_TAG
                val stringBuilder = StringBuilder()
                stringBuilder.append("adjustDetectZone ")
                stringBuilder.append(i)
                stringBuilder.append(": ")
                stringBuilder.append(temp)
                stringBuilder.append(" to ")
                stringBuilder.append(detect_zone[i])
                d(str, stringBuilder.toString())
            }
        }
    }

    private fun IrToRgbRadio(x: Float): Float {
        return (12.0f * x - 1.0f) / 10.0f
    }

    private fun IrToRgbMove(x: Float, offset: Float): Float {
        return x + offset
    }

    private fun IrToRgbScale(x: Float, zoom: Float): Float {
        return if (x > 0.5f) x * zoom else 0.5f - (0.5f - x) * zoom
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
        var str: String?
        val i: Int
        if (isReleased) {
            e(LOG_TAG, "enroll ignore!")
        }
        val timeout2: Int = if (timeout == 0) {
            180000
        } else {
            timeout
        }
        if (surface == null || timeout2 < 2000) {
            e(LOG_TAG, "enroll error!")
        }
        val str2 = "]"
        val str3 = ","
        if (BiometricConnect.DEBUG_LOG) {
            var str4 = LOG_TAG
            var stringBuilder = StringBuilder()
            stringBuilder.append("xiaomi detectArea data:[")
            stringBuilder.append(detectArea)
            stringBuilder.append(str3)
            stringBuilder.append(detectArea?.left)
            stringBuilder.append(str3)
            stringBuilder.append(detectArea?.right)
            stringBuilder.append(str3)
            stringBuilder.append(detectArea?.top)
            stringBuilder.append(str3)
            stringBuilder.append(detectArea?.bottom)
            stringBuilder.append(str2)
            e(str4, stringBuilder.toString())
            str4 = LOG_TAG
            stringBuilder = StringBuilder()
            stringBuilder.append("xiaomi enrollArea data:[")
            stringBuilder.append(enrollArea)
            stringBuilder.append(str3)
            stringBuilder.append(enrollArea.left)
            stringBuilder.append(str3)
            stringBuilder.append(enrollArea.right)
            stringBuilder.append(str3)
            stringBuilder.append(enrollArea.top)
            stringBuilder.append(str3)
            stringBuilder.append(enrollArea.bottom)
            stringBuilder.append(str2)
            e(str4, stringBuilder.toString())
        }

        if (cancel.isCanceled) {
            d(
                LOG_TAG,
                "enrollment already canceled"
            )
            return
        }
        cancel.setOnCancelListener(OnEnrollCancelListener())

        val bundle_preview = Bundle()
        mEnrollmentCallback = enrollCallback
        bundle_preview.putParcelable("surface", surface)
        bundle_preview.putInt("width", 3)
        bundle_preview.putInt("height", 4)
        tryConnectService()
        mBiometricClient?.sendBundle(1, bundle_preview)
        mBiometricClient?.sendCommand(3, 0)
        detectArea?.let {
            mEnrollParam.rectDetectZones = FRect(
                it.left,
                it.top, it.right, it.bottom
            )
        }
        mEnrollParam.rectEnrollZones = FRect(
            enrollArea.left,
            enrollArea.top,
            enrollArea.right,
            enrollArea.bottom
        )
        val enrollParam = mEnrollParam
        enrollParam.enableDistanceDetect = false
        enrollParam.enableIrFaceDetect = true
        enrollParam.enableDepthmap = false
        enrollParam.enrollWaitUi = true
        var detect_zone = floatArrayOf()
        enrollParam.rectDetectZones?.let {
            detect_zone = floatArrayOf(
                it.top,
                1.0f - it.right,
                it.bottom,
                1.0f - it.left,
                it.top,
                1.0f - it.right,
                it.bottom,
                1.0f - it.left
            )
        }
        adjustDetectZone(detect_zone)
        if (BiometricConnect.DEBUG_LOG) {
            val str5 = LOG_TAG
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append("startEnroll rectDetectZones:[")
            stringBuilder2.append(mEnrollParam.rectDetectZones?.left)
            stringBuilder2.append(str3)
            stringBuilder2.append(mEnrollParam.rectDetectZones?.top)
            stringBuilder2.append(str3)
            stringBuilder2.append(mEnrollParam.rectDetectZones?.right)
            stringBuilder2.append(str3)
            stringBuilder2.append(mEnrollParam.rectDetectZones?.bottom)
            val str6 = "] adjustDetectZone -> ["
            stringBuilder2.append(str6)
            stringBuilder2.append(detect_zone[0])
            stringBuilder2.append(str3)
            stringBuilder2.append(detect_zone[1])
            stringBuilder2.append(str3)
            stringBuilder2.append(detect_zone[2])
            stringBuilder2.append(str3)
            stringBuilder2.append(detect_zone[3])
            stringBuilder2.append(str2)
            d(str5, stringBuilder2.toString())
            str = LOG_TAG
            val stringBuilder3 = StringBuilder()
            stringBuilder3.append("startEnroll rectEnrollZones:[")
            stringBuilder3.append(mEnrollParam.rectEnrollZones?.left)
            stringBuilder3.append(str3)
            stringBuilder3.append(mEnrollParam.rectEnrollZones?.top)
            stringBuilder3.append(str3)
            stringBuilder3.append(mEnrollParam.rectEnrollZones?.right)
            stringBuilder3.append(str3)
            stringBuilder3.append(mEnrollParam.rectEnrollZones?.bottom)
            stringBuilder3.append(str6)
            stringBuilder3.append(detect_zone[4])
            stringBuilder3.append(str3)
            stringBuilder3.append(detect_zone[5])
            stringBuilder3.append(str3)
            stringBuilder3.append(detect_zone[6])
            stringBuilder3.append(str3)
            stringBuilder3.append(detect_zone[7])
            stringBuilder3.append(str2)
            d(str, stringBuilder3.toString())
        }
        val bundle_enroll = Bundle()
        bundle_enroll.putFloatArray(
            BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_ZONE,
            detect_zone
        )
        bundle_enroll.putBoolean(
            BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_FACE,
            mEnrollParam.enableIrFaceDetect
        )
        bundle_enroll.putBoolean(
            BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DISTANCE,
            mEnrollParam.enableDistanceDetect
        )
        bundle_enroll.putBoolean(
            BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_WAITING_UI,
            mEnrollParam.enrollWaitUi
        )
        val z = mEnrollParam.enableDepthmap
        str = BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DEPTHMAP
        if (z) {
            bundle_enroll.putInt(str, mEnrollParam.DepthmpaType)
            i = 0
        } else {
            i = 0
            bundle_enroll.putInt(str, 0)
        }
        mBiometricClient?.sendBundle(6, bundle_enroll)
        mBiometricClient?.sendCommand(7)
        val handler = mHandler
        handler.sendMessageDelayed(handler.obtainMessage(i), timeout2.toLong())
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

    override fun extCmd(cmd: Int, param: Int): Int {
        if (cmd != 0) {
            return -1
        }
        mBiometricClient?.sendCommand(14, param)
        return 0
    }

    override fun addLockoutResetCallback(callback: IMiuiFaceManager.LockoutResetCallback?) {
        if (DEBUG) {
            val str = LOG_TAG
            val stringBuilder = StringBuilder()
            stringBuilder.append("addLockoutResetCallback  callback:")
            stringBuilder.append(callback)
            d(str, stringBuilder.toString())
        }
    }

    override fun resetTimeout(token: ByteArray) {
        if (DEBUG) {
            d(LOG_TAG, "resetTimeout")
        }
    }

    class EnrollParam {
        var DepthmpaType = 0
        var enableDepthmap = false
        var enableDistanceDetect = false
        var enableIrFaceDetect = false
        var enrollWaitUi = false
        var rectDetectZones: FRect? = null
        var rectEnrollZones: FRect? = null
    }

    class FRect(var left: Float, var top: Float, var right: Float, var bottom: Float)
    class FaceInfo {
        var bounds: Rect? = null
        var eye_dist = 0f
        var pitch = 0f
        var points_array: Array<Point?> = arrayOf()
        var roll = 0f
        var yaw = 0f
    }

    class GroupItem {
        var id = 0
        var name: String? = null
    }

    class TemplateItem {
        var data: String? = null
        var group_id = 0
        var id = 0
        var name: String? = null
    }

    private inner class ClientHandler : Handler {
        constructor(context: Context) : super(context.mainLooper)
        constructor(looper: Looper) : super(looper)

        override fun handleMessage(msg: Message) {
            if (DEBUG) {

                val stringBuilder = StringBuilder()
                stringBuilder.append(" handleMessage  callback what:")
                stringBuilder.append(msg.what)
                d(LOG_TAG, stringBuilder.toString())
            }
            val i = msg.what
            if (i != 0) {
                if (i == 1 && mAuthenticationCallback != null) {
                    d(LOG_TAG, "xiaomi ---> RECEIVER_ON_AUTHENTICATION_TIMEOUT")
                    mAuthenticationCallback?.onAuthenticationFailed()
                    cancelAuthentication()
                }
            } else if (mEnrollmentCallback != null) {
                mEnrollmentCallback?.onEnrollmentError(66, null)
                d(LOG_TAG, "RECEIVER_ON_ENROLL_TIMEOUT")
                cancelEnrollment()
            }
        }
    }

    private inner class OnAuthenticationCancelListener :
        CancellationSignal.OnCancelListener {
        override fun onCancel() {
            mcancelStatus = 1
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