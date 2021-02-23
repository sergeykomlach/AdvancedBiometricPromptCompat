package dev.skomlach.biometric.compat.engine.internal.face.miui.impl;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.provider.Settings;
import android.view.Surface;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.BiometricConnect;
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.ContentResolverHelper;
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.MiuiBuild;
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.SettingsSecure;
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.SettingsSystem;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class Miui3DFaceManagerImpl implements IMiuiFaceManager, BiometricClient.ServiceCallback {
    public static final int COMMAND_ENROLL_RESUME_ENROLL_LOGIC = 0;
    public static final int MSG_AUTHENTICATION_HELP_ALL_BLOCKED = 28;
    public static final int MSG_AUTHENTICATION_HELP_BAD_AMBIENT_LIGHT = 32;
    public static final int MSG_AUTHENTICATION_HELP_BOTH_EYE_BLOCKED = 25;
    public static final int MSG_AUTHENTICATION_HELP_BOTH_EYE_CLOSE = 31;
    public static final int MSG_AUTHENTICATION_HELP_FACE_AUTH_FAILD = 70;
    public static final int MSG_AUTHENTICATION_HELP_FACE_DETECT_FAIL = 20;
    public static final int MSG_AUTHENTICATION_HELP_FACE_DETECT_OK = 10;
    public static final int MSG_AUTHENTICATION_HELP_FACE_TOO_NEER = 33;
    public static final int MSG_AUTHENTICATION_HELP_LEFTEYE_MOUSE_BLOCKED = 26;
    public static final int MSG_AUTHENTICATION_HELP_LEFT_EYE_BLOCKED = 22;
    public static final int MSG_AUTHENTICATION_HELP_LEFT_EYE_CLOSE = 29;
    public static final int MSG_AUTHENTICATION_HELP_LIVING_BODY_DETECTION_FAILED = 63;
    public static final int MSG_AUTHENTICATION_HELP_MOUSE_BLOCKED = 24;
    public static final int MSG_AUTHENTICATION_HELP_RIGHTEYE_MOUSE_BLOCKED = 27;
    public static final int MSG_AUTHENTICATION_HELP_RIGHT_EYE_BLOCKED = 23;
    public static final int MSG_AUTHENTICATION_HELP_RIGHT_EYE_CLOSE = 30;
    public static final int MSG_AUTHENTICATION_STOP = 34;
    public static final int MSG_ENROLL_ENROLL_TIMEOUT = 66;
    public static final int MSG_ENROLL_ERROR_CREATE_FOLDER_FAILED = 52;
    public static final int MSG_ENROLL_ERROR_DISABLE_FAIL = 57;
    public static final int MSG_ENROLL_ERROR_ENABLE_FAIL = 50;
    public static final int MSG_ENROLL_ERROR_FACE_LOST = 62;
    public static final int MSG_ENROLL_ERROR_FLOOD_ITO_ERR = 41;
    public static final int MSG_ENROLL_ERROR_IR_CAM_CLOSED = 6;
    public static final int MSG_ENROLL_ERROR_LASER_ITO_ERR = 40;
    public static final int MSG_ENROLL_ERROR_LIVING_BODY_DETECTION_FAILED = 63;
    public static final int MSG_ENROLL_ERROR_NOT_SAME_PERSON = 58;
    public static final int MSG_ENROLL_ERROR_PREVIEW_CAM_ERROR = 5;
    public static final int MSG_ENROLL_ERROR_RTMV_IC_ERR = 53;
    public static final int MSG_ENROLL_ERROR_SAVE_TEMPLATE_FAILED = 51;
    public static final int MSG_ENROLL_ERROR_SDK_ERROR = 59;
    public static final int MSG_ENROLL_ERROR_SYSTEM_EXCEPTION = 54;
    public static final int MSG_ENROLL_ERROR_TEMLATE_FILE_NOT_EXIST = 56;
    public static final int MSG_ENROLL_ERROR_TOF_BE_COVERED = 64;
    public static final int MSG_ENROLL_ERROR_TOF_NOT_MOUNT = 65;
    public static final int MSG_ENROLL_ERROR_UNLOCK_FAIL = 55;
    public static final int MSG_ENROLL_FACE_IR_FOUND = 2;
    public static final int MSG_ENROLL_FACE_IR_NOT_FOUND = 4;
    public static final int MSG_ENROLL_FACE_RGB_FOUND = 1;
    public static final int MSG_ENROLL_FACE_RGB_NOT_FOUND = 3;
    public static final int MSG_ENROLL_HELP_ALL_BLOCKED = 28;
    public static final int MSG_ENROLL_HELP_BAD_AMBIENT_LIGHT = 32;
    public static final int MSG_ENROLL_HELP_BOTH_EYE_BLOCKED = 25;
    public static final int MSG_ENROLL_HELP_BOTH_EYE_CLOSE = 31;
    public static final int MSG_ENROLL_HELP_DIRECTION_DOWN = 13;
    public static final int MSG_ENROLL_HELP_DIRECTION_LEFT = 14;
    public static final int MSG_ENROLL_HELP_DIRECTION_RIGHT = 15;
    public static final int MSG_ENROLL_HELP_DIRECTION_UP = 12;
    public static final int MSG_ENROLL_HELP_FACE_DETECT_FAIL_NOT_IN_ROI = 21;
    public static final int MSG_ENROLL_HELP_FACE_DETECT_OK = 10;
    public static final int MSG_ENROLL_HELP_FACE_TOO_NEER = 33;
    public static final int MSG_ENROLL_HELP_IR_CAM_OPEND = 2;
    public static final int MSG_ENROLL_HELP_LEFTEYE_MOUSE_BLOCKED = 26;
    public static final int MSG_ENROLL_HELP_LEFT_EYE_BLOCKED = 22;
    public static final int MSG_ENROLL_HELP_LEFT_EYE_CLOSE = 29;
    public static final int MSG_ENROLL_HELP_MOUSE_BLOCKED = 24;
    public static final int MSG_ENROLL_HELP_PREVIEW_CAM_OPEND = 1;
    public static final int MSG_ENROLL_HELP_RIGHTEYE_MOUSE_BLOCKED = 27;
    public static final int MSG_ENROLL_HELP_RIGHT_EYE_BLOCKED = 23;
    public static final int MSG_ENROLL_HELP_RIGHT_EYE_CLOSE = 30;
    public static final int MSG_ENROLL_PROGRESS_SUCCESS = 0;
    public static final String TABLE_TEMPLATE_COLUMN_DATA = "data";
    public static final String TABLE_TEMPLATE_COLUMN_GROUP_ID = "group_id";
    public static final String TABLE_TEMPLATE_COLUMN_ID = "_id";
    public static final String TABLE_TEMPLATE_COLUMN_NAME = "template_name";
    public static final String TABLE_TEMPLATE_COLUMN_VALID = "valid";
    public static final String TABLE_TEMPLATE_NAME = "_template";
    private static final int CANCEL_STATUS_DONE = 1;
    private static final int CANCEL_STATUS_NONE = 0;
    private static final int DB_STATUS_NONE = 0;
    private static final int DB_STATUS_PREPARED = 2;
    private static final int DB_STATUS_PREPARING = 1;
    private static final String FACEUNLOCK_SUPPORT_SUPERPOWER = "faceunlock_support_superpower";
    private static final String FACE_UNLOCK_HAS_FEATURE = "face_unlock_has_feature_sl";
    private static final String FACE_UNLOCK_HAS_FEATURE_URI = "content://settings/secure/face_unlock_has_feature_sl";
    private static final String POWERMODE_SUPERSAVE_OPEN = "power_supersave_mode_open";
    private static final String POWERMODE_SUPERSAVE_OPEN_URI = "content://settings/secure/power_supersave_mode_open";
    private static final int RECEIVER_ON_AUTHENTICATION_TIMEOUT = 1;
    private static final int RECEIVER_ON_ENROLL_TIMEOUT = 0;
    private static final String TEMPLATE_PATH = "/data/user/0/com.xiaomi.biometric/files/";
    private static final int VERSION_1 = 1;
    private static final int height = 4;
    private static final int width = 3;
    private static final boolean DEBUG = true;
    private static final String LOG_TAG = "3DFaceManagerImpl";
    private static volatile IMiuiFaceManager INSTANCE = null;
    private final int hasEnrollFace = 0;
    private final Object mBinderLock = new Object();
    private final Context mContext;
    private final EnrollParam mEnrollParam = new EnrollParam();
    private final ContentObserver mSuperPowerOpenObserver = new ContentObserver(this.mHandler) {
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mIsSuperPower = SettingsSystem.getIntForUser(mContext.getContentResolver(), POWERMODE_SUPERSAVE_OPEN, 0, 0) != 0;
        }
    };
    private final ContentObserver mHasFaceDataObserver = new ContentObserver(this.mHandler) {
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mHasFaceData =
                    SettingsSecure.getIntForUser(mContext.getContentResolver(),
                            Miui3DFaceManagerImpl.FACE_UNLOCK_HAS_FEATURE, 0, 0) != 0;
        }
    };
    private final List<Miuiface> mMiuifaceList = null;
    private Object boostFramework = null;
    private AuthenticationCallback mAuthenticationCallback;
    private BiometricClient mBiometricClient = null;
    private boolean mDatabaseChanged = false;
    private int mDatabaseStatus = 0;
    private boolean mDisonnected = false;
    private EnrollmentCallback mEnrollmentCallback;
    private FaceInfo mFaceInfo = null;
    private int mGroupIdMax = 0;
    private List<GroupItem> mGroupItemList = null;
    private Handler mHandler;
    private boolean mHasFaceData;

    private boolean mIsSuperPower;
    private boolean mReleased = false;
    private RemovalCallback mRemovalCallback;
    private Miuiface mRemovalMiuiface;
    private int mTemplateIdMax = 0;
    private List<TemplateItem> mTemplateItemList = null;
    private int mcancelStatus = 0;
    private SQLiteDatabase myDB = null;
    private List<TemplateItem> myTemplateItemList = null;
    private Method sAcquireFunc = null;
    private Class<?> sPerfClass = null;
    private Method sReleaseFunc = null;

    private Miui3DFaceManagerImpl(Context ctx) {
        this.mContext = ctx;
        this.mDisonnected = true;
        this.mReleased = false;
        this.mRemovalCallback = null;
        this.mAuthenticationCallback = null;
        this.mEnrollmentCallback = null;
        try {

//        Secure.putIntForUser(this.mContext.getContentResolver(), FACEUNLOCK_SUPPORT_SUPERPOWER, 1, -2);
            ContentResolverHelper.registerContentObserver(this.mContext.getContentResolver(), Settings.Secure.getUriFor(FACE_UNLOCK_HAS_FEATURE),
                    false, this.mHasFaceDataObserver, 0);
            this.mHasFaceDataObserver.onChange(false);
            ContentResolverHelper.registerContentObserver(this.mContext.getContentResolver(), Settings.System.getUriFor(POWERMODE_SUPERSAVE_OPEN),
                    false, this.mSuperPowerOpenObserver, 0);
            this.mSuperPowerOpenObserver.onChange(false);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        this.mHandler = new ClientHandler(ctx);
        this.mBiometricClient = new BiometricClient(ctx);
        this.mBiometricClient.startService(this);
        preloadBoostFramework();
    }

    public static IMiuiFaceManager getInstance(Context con) {
        if (INSTANCE != null && INSTANCE.isReleased()) {
            INSTANCE = null;
        }
        if (INSTANCE == null) {
            synchronized (MiuiFaceManagerImpl.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Miui3DFaceManagerImpl(con);
                }
            }
        }
        return INSTANCE;
    }

    private void preloadBoostFramework() {
        try {
            this.sPerfClass = Class.forName("android.util.BoostFramework");
            Constructor<?> constuctor = this.sPerfClass.getConstructor();
            this.boostFramework = constuctor.newInstance();
            this.sAcquireFunc = this.sPerfClass.getMethod("perfLockAcquire", Integer.TYPE, int[].class);
            this.sReleaseFunc = this.sPerfClass.getMethod("perfLockRelease");
            BiometricLoggerImpl.d(LOG_TAG, "preload BoostFramework succeed.");
        } catch (Exception e) {
            BiometricLoggerImpl.e(LOG_TAG, "preload class android.util.BoostFramework failed");
        }
    }

    public void onBiometricServiceConnected() {
        String str = LOG_TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("onBiometricServiceConnected ");
        stringBuilder.append(this);
        BiometricLoggerImpl.d(str, stringBuilder.toString());
        this.mDisonnected = false;
        prepareDatabase();
    }

    public void onBiometricServiceDisconnected() {
        String str = LOG_TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("onBiometricServiceDisconnected ");
        stringBuilder.append(this);
        BiometricLoggerImpl.d(str, stringBuilder.toString());
        if (!this.mDisonnected) {
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("xiaomi--> set mDisonnected true ");
            stringBuilder.append(this);
            BiometricLoggerImpl.d(str, stringBuilder.toString());
            this.mDisonnected = true;
            release();
        }
    }

    public void onBiometricEventClassLoader(Bundle bundle) {
        if (BiometricConnect.DEBUG_LOG) {
            BiometricLoggerImpl.d(LOG_TAG, "onBiometricEventClassLoader");
        }
        bundle.setClassLoader(BiometricConnect.class.getClassLoader());
    }

    public boolean isReleased() {
        return this.mReleased;
    }

    public void release() {
        String str;
        StringBuilder stringBuilder;
        if (this.mReleased) {
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("release ignore ");
            stringBuilder.append(this);
            BiometricLoggerImpl.e(str, stringBuilder.toString());
            return;
        }
        str = LOG_TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("release ctx:");
        stringBuilder.append(this.mContext);
        stringBuilder.append(", this:");
        stringBuilder.append(this);
        BiometricLoggerImpl.d(str, stringBuilder.toString());
        this.mReleased = true;
    }

    public void onBiometricEventCallback(int module_id, int event, int arg1, int arg2) {
        String str;
        StringBuilder stringBuilder;
        if (module_id != 1) {
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("onBiometricEventCallback ignore - module_id:+");
            stringBuilder.append(module_id);
            stringBuilder.append(" event: ");
            stringBuilder.append(event);
            stringBuilder.append(", arg1:");
            stringBuilder.append(arg1);
            stringBuilder.append(", arg2:");
            stringBuilder.append(arg2);
            BiometricLoggerImpl.e(str, stringBuilder.toString());
        } else if (this.mDisonnected) {
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("onBiometricEventCallback mDisonnected:");
            stringBuilder.append(this.mDisonnected);
            stringBuilder.append(" ignore event: ");
            stringBuilder.append(event);
            stringBuilder.append(", arg1:");
            stringBuilder.append(arg1);
            stringBuilder.append(", arg2:");
            stringBuilder.append(arg2);
            BiometricLoggerImpl.e(str, stringBuilder.toString());
        } else {
            String str2;
            StringBuilder stringBuilder2;
            if (BiometricConnect.DEBUG_LOG) {
                str2 = LOG_TAG;
                stringBuilder2 = new StringBuilder();
                stringBuilder2.append("onBiometricEventCallback - event: ");
                stringBuilder2.append(event);
                stringBuilder2.append(", arg1:");
                stringBuilder2.append(arg1);
                stringBuilder2.append(", arg2:");
                stringBuilder2.append(arg2);
                BiometricLoggerImpl.d(str2, stringBuilder2.toString());
            }
            if (event == 0) {
                EnrollmentCallback enrollmentCallback = this.mEnrollmentCallback;
                if (enrollmentCallback != null) {
                    enrollmentCallback.onEnrollmentHelp(1, null);
                }
            } else if (event != 1) {
                if (!(event == 2 || event == 3)) {
                    EnrollmentCallback enrollmentCallback2;
                    if (event != 4) {
                        switch (event) {
                            case 20:
                                enrollmentCallback2 = this.mEnrollmentCallback;
                                if (enrollmentCallback2 != null) {
                                    enrollmentCallback2.onEnrollmentHelp(2, null);
                                    break;
                                }
                                break;
                            case 21:
                                if (BiometricConnect.DEBUG_LOG) {
                                    str = LOG_TAG;
                                    stringBuilder = new StringBuilder();
                                    stringBuilder.append("onBiometricEventCallback  MSG_CB_EVENT_IR_CAM_PREVIEW_SIZE arg1:");
                                    stringBuilder.append(arg1);
                                    stringBuilder.append(",arg2:");
                                    stringBuilder.append(arg2);
                                    BiometricLoggerImpl.d(str, stringBuilder.toString());
                                    break;
                                }
                                break;
                            case 22:
                                break;
                            case 23:
                                BiometricLoggerImpl.d(LOG_TAG, "MSG_CB_EVENT_IR_CAM_CLOSED");
                                break;
                            case 24:
                                enrollmentCallback2 = this.mEnrollmentCallback;
                                if (enrollmentCallback2 != null) {
                                    enrollmentCallback2.onEnrollmentError(6, null);
                                    cancelEnrollment();
                                    break;
                                }
                                break;
                            default:
                                switch (event) {
                                    case 30:
                                        str2 = LOG_TAG;
                                        stringBuilder2 = new StringBuilder();
                                        stringBuilder2.append("onBiometricEventCallback  MSG_CB_EVENT_ENROLL_SUCCESS mEnrollmentCallback:");
                                        stringBuilder2.append(this.mEnrollmentCallback);
                                        BiometricLoggerImpl.d(str2, stringBuilder2.toString());
                                        EnrollmentCallback enrollmentCallback3 = this.mEnrollmentCallback;
                                        if (enrollmentCallback3 != null) {
                                            enrollmentCallback3.onEnrollmentProgress(0, arg1);
//                                            Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_HAS_FEATURE, 1, -2);
                                            synchronized (this.mBinderLock) {
                                                this.mDatabaseStatus = 0;
                                                prepareDatabase();
                                            }
                                            break;
                                        }
                                        break;
                                    case 31:
                                        enrollmentCallback2 = this.mEnrollmentCallback;
                                        if (enrollmentCallback2 != null) {
                                            enrollmentCallback2.onEnrollmentHelp(arg1, null);
                                            str = LOG_TAG;
                                            stringBuilder = new StringBuilder();
                                            stringBuilder.append("MSG_CB_EVENT_ENROLL_ERROR arg1 = ");
                                            stringBuilder.append(arg1);
                                            BiometricLoggerImpl.d(str, stringBuilder.toString());
                                            break;
                                        }
                                        break;
                                    case 32:
                                        str = LOG_TAG;
                                        stringBuilder2 = new StringBuilder();
                                        stringBuilder2.append("MSG_CB_EVENT_ENROLL_INFO arg1 = ");
                                        stringBuilder2.append(arg1);
                                        BiometricLoggerImpl.d(str, stringBuilder2.toString());
                                        enrollmentCallback2 = this.mEnrollmentCallback;
                                        if (enrollmentCallback2 != null) {
                                            enrollmentCallback2.onEnrollmentHelp(arg1, null);
                                            break;
                                        }
                                        break;
                                    default:
                                        AuthenticationCallback authenticationCallback;
                                        switch (event) {
                                            case 40:
                                                str = LOG_TAG;
                                                stringBuilder2 = new StringBuilder();
                                                stringBuilder2.append("onBiometricEventCallback  MSG_CB_EVENT_VERIFY_SUCCESS mAuthenticationCallback:");
                                                stringBuilder2.append(this.mAuthenticationCallback);
                                                BiometricLoggerImpl.d(str, stringBuilder2.toString());
                                                authenticationCallback = this.mAuthenticationCallback;
                                                if (authenticationCallback != null) {
                                                    authenticationCallback.onAuthenticationSucceeded(null);
                                                    cancelAuthentication();
                                                    break;
                                                }
                                                break;
                                            case 41:
                                                authenticationCallback = this.mAuthenticationCallback;
                                                if (authenticationCallback != null) {
                                                    authenticationCallback.onAuthenticationHelp(70, null);
                                                    break;
                                                }
                                                break;
                                            case 42:
                                                authenticationCallback = this.mAuthenticationCallback;
                                                if (authenticationCallback != null) {
                                                    authenticationCallback.onAuthenticationHelp(arg1, null);
                                                    break;
                                                }
                                                break;
                                            case 43:
                                                RemovalCallback removalCallback = this.mRemovalCallback;
                                                if (removalCallback != null) {
                                                    removalCallback.onRemovalSucceeded(this.mRemovalMiuiface, this.mMiuifaceList.size());
                                                    this.mRemovalCallback = null;
                                                    this.mRemovalMiuiface = null;
                                                    break;
                                                }
                                                break;
                                        }
                                        break;
                                }
                        }
                    }
                    enrollmentCallback2 = this.mEnrollmentCallback;
                    if (enrollmentCallback2 != null) {
                        enrollmentCallback2.onEnrollmentError(5, null);
                        cancelEnrollment();
                    }
                }
            } else if (BiometricConnect.DEBUG_LOG) {
                str = LOG_TAG;
                stringBuilder = new StringBuilder();
                stringBuilder.append("onBiometricEventCallback  MSG_CB_EVENT_RGB_CAM_PREVIEW_SIZE mEnrollmentCallback:");
                stringBuilder.append(this.mEnrollmentCallback);
                stringBuilder.append(" arg1:");
                stringBuilder.append(arg1);
                stringBuilder.append(",arg2:");
                stringBuilder.append(arg2);
                BiometricLoggerImpl.d(str, stringBuilder.toString());
            }
        }
    }

    public void onBiometricBundleCallback(int module_id, int key, Bundle bundle) {
        String str;
        StringBuilder stringBuilder;
        if (module_id != 1) {
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("onBiometricBundleCallback ignore module_id:");
            stringBuilder.append(module_id);
            stringBuilder.append(", key:");
            stringBuilder.append(key);
            BiometricLoggerImpl.e(str, stringBuilder.toString());
        } else if (this.mDisonnected) {
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("onBiometricBundleCallback mDisonnected:");
            stringBuilder.append(this.mDisonnected);
            stringBuilder.append(" ignore key:");
            stringBuilder.append(key);
            BiometricLoggerImpl.e(str, stringBuilder.toString());
        } else {
            if (BiometricConnect.DEBUG_LOG) {
                str = LOG_TAG;
                stringBuilder = new StringBuilder();
                stringBuilder.append("onBiometricBundleCallback key:");
                stringBuilder.append(key);
                BiometricLoggerImpl.d(str, stringBuilder.toString());
            }
            if (key == 2) {
                handlerFace(bundle);
            } else if (key == 3) {
                handlerDatabase(bundle);
            } else if (key != 5) {
            }
        }
    }

    public boolean isFaceFeatureSupport() {
        if (this.mIsSuperPower) {
            BiometricLoggerImpl.d(LOG_TAG, "enter super power mode, isFaceFeatureSupport:false");
            return false;
        } else if (MiuiBuild.IS_INTERNATIONAL_BUILD) {
            return false;
        } else {
            return "ursa".equals(MiuiBuild.DEVICE);
        }
    }

    public boolean isSupportScreenOnDelayed() {
        return false;
    }

    public boolean isFaceUnlockInited() {
        return false;
    }

    public int getManagerVersion() {
        return 1;
    }

    public String getVendorInfo() {
        return "3D Structure Light";
    }

    private void handlerDatabase(Bundle bundle) {
        if (this.mDatabaseStatus != 1) {
            BiometricLoggerImpl.e(LOG_TAG, "handlerDatabase mDatabaseStatus ignore");
            return;
        }
        if (BiometricConnect.DEBUG_LOG) {
            BiometricLoggerImpl.d(LOG_TAG, "handlerDatabase ");
        }
        this.mTemplateIdMax = bundle.getInt(BiometricConnect.MSG_CB_BUNDLE_DB_TEMPLATE_ID_MAX);
        this.mGroupIdMax = bundle.getInt(BiometricConnect.MSG_CB_BUNDLE_DB_GROUP_ID_MAX);
        ArrayList<Parcelable> listGroup = bundle.getParcelableArrayList("group");
        if (BiometricConnect.DEBUG_LOG) {
            String str = LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("handlerDatabase listGroup:");
            stringBuilder.append(listGroup.size());
            BiometricLoggerImpl.d(str, stringBuilder.toString());
        }
        ArrayList<Parcelable> listTemplate = bundle.getParcelableArrayList(BiometricConnect.MSG_CB_BUNDLE_DB_TEMPLATE);
        if (BiometricConnect.DEBUG_LOG) {
            String str2 = LOG_TAG;
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("handlerDatabase list:");
            stringBuilder2.append(listTemplate.size());
            BiometricLoggerImpl.d(str2, stringBuilder2.toString());
        }
        initClientDB(listGroup, listTemplate);
    }

    private void initClientDB(ArrayList<Parcelable> listGroup, ArrayList<Parcelable> list) {
        if (this.mDatabaseStatus != 2 || this.mDatabaseChanged) {
            BiometricLoggerImpl.d(LOG_TAG, "initClientDB begin");
            this.mTemplateItemList = new ArrayList();
            Iterator it = list.iterator();
            Class<?> clazz = null;
            while (it.hasNext()) {
                Object i = it.next();
                try {
                    if (clazz == null) {
                        clazz = i.getClass();
                    }
                    TemplateItem item = new TemplateItem();
                    item.id = clazz.getField("mId").getInt(i);
                    item.name = (String) clazz.getField("mName").get(i);
                    item.group_id = clazz.getField("mGroupId").getInt(i);
                    item.data = (String) clazz.getField("mData").get(i);
                    this.mTemplateItemList.add(item);
                } catch (Throwable e) {
                    BiometricLoggerImpl.e(e);
                }
            }
            this.mGroupItemList = new ArrayList();
            it = listGroup.iterator();
            clazz = null;
            while (it.hasNext()) {
                Object group = it.next();
                try {
                    if (clazz == null) {
                        clazz = group.getClass();
                    }
                    GroupItem groupItem = new GroupItem();
                    groupItem.id = clazz.getField("mId").getInt(group);
                    groupItem.name = (String) clazz.getField("mName").get(group);
                    this.mGroupItemList.add(groupItem);
                } catch (Throwable e) {
                    BiometricLoggerImpl.e(e);
                }
            }
            clazz = null;
            this.mDatabaseStatus = 2;
            this.mDatabaseChanged = false;
            BiometricLoggerImpl.d(LOG_TAG, "initClientDB ok");
        }
    }

    private void prepareDatabase() {
        if (this.mDatabaseStatus != 0) {
            BiometricLoggerImpl.e(LOG_TAG, "prepareDatabase ignore!");
            return;
        }
        BiometricLoggerImpl.d(LOG_TAG, "prepareDatabase");
        this.mDatabaseStatus = 1;
        this.mBiometricClient.sendCommand(9);
    }

    private void resetDatabase() {
        if (this.mDatabaseStatus != 2) {
            BiometricLoggerImpl.e(LOG_TAG, "resetDatabase ignore!");
            return;
        }
        BiometricLoggerImpl.d(LOG_TAG, "resetDatabase");
        this.mDatabaseStatus = 1;
        this.mBiometricClient.sendCommand(10);
    }

    private void commitDatabase() {
        if (this.mReleased) {
            BiometricLoggerImpl.e(LOG_TAG, "commitDatabase ignore!");
        } else if (this.mDatabaseChanged) {
            BiometricLoggerImpl.d(LOG_TAG, "commitDatabase");
            Bundle out_bundle = new Bundle();
            out_bundle.setClassLoader(BiometricConnect.class.getClassLoader());
            ArrayList<Parcelable> listGroup = new ArrayList();
            for (GroupItem g : this.mGroupItemList) {
                listGroup.add(BiometricConnect.getDBGroup(g.id, g.name));
            }
            out_bundle.putParcelableArrayList("group", listGroup);
            ArrayList<Parcelable> listTemplate = new ArrayList();
            for (TemplateItem i : this.mTemplateItemList) {
                listTemplate.add(BiometricConnect.getDBTemplate(i.id, i.name, i.data, i.group_id));
            }
            out_bundle.putParcelableArrayList(BiometricConnect.MSG_CB_BUNDLE_DB_TEMPLATE, listTemplate);
            this.mBiometricClient.sendBundle(4, out_bundle);
        }
    }

    private void handlerFace(Bundle bundle) {
        if (this.mEnrollmentCallback != null) {
            if (BiometricConnect.DEBUG_LOG) {
                BiometricLoggerImpl.d(LOG_TAG, "handlerFace ");
            }
            boolean is_ir_detect = bundle.getBoolean(BiometricConnect.MSG_CB_BUNDLE_FACE_IS_IR);
            if (bundle.getBoolean(BiometricConnect.MSG_CB_BUNDLE_FACE_HAS_FACE)) {
                if (this.mFaceInfo == null) {
                    this.mFaceInfo = new FaceInfo();
                }
                Parcelable parcelable = bundle.getParcelable(BiometricConnect.MSG_CB_BUNDLE_FACE_RECT_BOUND);
                if (parcelable instanceof Rect) {
                    this.mFaceInfo.bounds = (Rect) parcelable;
                    if (BiometricConnect.DEBUG_LOG) {
                        String str = LOG_TAG;
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("handlerFace detect face:");
                        stringBuilder.append(this.mFaceInfo.bounds.toString());
                        BiometricLoggerImpl.d(str, stringBuilder.toString());
                    }
                }
                this.mFaceInfo.yaw = bundle.getFloat(BiometricConnect.MSG_CB_BUNDLE_FACE_FLOAT_YAW);
                this.mFaceInfo.pitch = bundle.getFloat("pitch");
                this.mFaceInfo.roll = bundle.getFloat(BiometricConnect.MSG_CB_BUNDLE_FACE_FLOAT_ROLL);
                this.mFaceInfo.eye_dist = bundle.getFloat(BiometricConnect.MSG_CB_BUNDLE_FACE_FLOAT_EYE_DIST);
                Parcelable[] parcelables = bundle.getParcelableArray(BiometricConnect.MSG_CB_BUNDLE_FACE_POINTS_ARRAY);
                if (parcelables instanceof Point[]) {
                    this.mFaceInfo.points_array = (Point[]) parcelables;
                }
                if (!is_ir_detect) {
                    EnrollmentCallback enrollmentCallback = this.mEnrollmentCallback;
                    if (enrollmentCallback != null) {
                        enrollmentCallback.onEnrollmentHelp(1, null);
                        return;
                    }
                }
                if (is_ir_detect && this.mEnrollmentCallback != null) {
                    boolean z = this.mEnrollParam.enableDistanceDetect;
                    this.mEnrollmentCallback.onEnrollmentHelp(2, null);
                }
                return;
            }
            EnrollmentCallback enrollmentCallback2 = this.mEnrollmentCallback;
            if (enrollmentCallback2 != null) {
                int i;
                if (is_ir_detect) {
                    i = 4;
                } else {
                    i = 3;
                }
                enrollmentCallback2.onEnrollmentHelp(i, null);
            }
        }
    }

    public int hasEnrolledFaces() {
        tryConnectService();
        if (this.mHasFaceData) {
            return 1;
        }
        return 0;
    }

    public void remove(Miuiface face, RemovalCallback callback) {
        if (this.mReleased) {
            BiometricLoggerImpl.e(LOG_TAG, "removeTemplate ignore!");
        } else if (this.mDatabaseStatus != 2) {
            String str = LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("removeTemplate error mDatabaseStatus :");
            stringBuilder.append(this.mDatabaseStatus);
            BiometricLoggerImpl.d(str, stringBuilder.toString());
        } else {
            TemplateItem templateItem = findTemplate(face.getMiuifaceId());
            if (templateItem == null) {
                String str2 = LOG_TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("removeTemplate findTemplate ");
                stringBuilder2.append(face.getMiuifaceId());
                stringBuilder2.append(" is null");
                BiometricLoggerImpl.d(str2, stringBuilder2.toString());
                return;
            }
            BiometricLoggerImpl.d(LOG_TAG, "removeTemplate");
            this.mTemplateItemList.remove(templateItem);
//            Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_HAS_FEATURE, 0, -2);
            this.mDatabaseChanged = true;
            this.mRemovalCallback = callback;
            this.mRemovalMiuiface = face;
            commitDatabase();
        }
    }

    private TemplateItem findTemplate(int id) {
        for (TemplateItem i : this.mTemplateItemList) {
            if (i.id == id) {
                return i;
            }
        }
        return null;
    }

    public int getTemplatepath() {
        File dbFile = new File(TEMPLATE_PATH, "biometric.db");
        if (dbFile.exists()) {
            BiometricLoggerImpl.d(LOG_TAG, "getTemplatepath");
            this.myDB = SQLiteDatabase.openDatabase(dbFile.getPath(), null, 0);
            this.myTemplateItemList = new ArrayList();
            BiometricLoggerImpl.d(LOG_TAG, "selectTemplate");
            Cursor cursor = this.myDB.rawQuery("select _id,data,template_name,group_id from _template where valid=1", null);
            if (cursor.moveToFirst()) {
                BiometricLoggerImpl.e(LOG_TAG, "xiaomi -->4.3 test");
                TemplateItem t = new TemplateItem();
                t.id = cursor.getInt(cursor.getColumnIndex("_id"));
                t.name = cursor.getString(cursor.getColumnIndex(TABLE_TEMPLATE_COLUMN_NAME));
                t.group_id = cursor.getInt(cursor.getColumnIndex("group_id"));
                t.data = cursor.getString(cursor.getColumnIndex("data"));
                this.myTemplateItemList.add(t);
            }
            cursor.close();
            return this.myTemplateItemList.size();
        }
        BiometricLoggerImpl.d(LOG_TAG, "getTemplatepath faild");
        return 0;
    }

    private String getMessageInfo(int msgId) {
        String msg = "define by 3dclient";
        if (msgId == 34) {
            return "Be cancel";
        }
        String str = LOG_TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("default msgId: ");
        stringBuilder.append(msgId);
        BiometricLoggerImpl.d(str, stringBuilder.toString());
        return msg;
    }

    private void tryConnectService() {
        if (this.mDisonnected) {
            BiometricLoggerImpl.e(LOG_TAG, "mDisonnected is true ");
            this.mDatabaseStatus = 0;
            this.mBiometricClient.startService(this);
        }
    }

    public void rename(int faceId, String name) {
        if (this.mReleased) {
            BiometricLoggerImpl.e(LOG_TAG, "setTemplateName ignore!");
        } else if (this.mDatabaseStatus != 2) {
            String str = LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("setTemplateName error mPrepareDbStatus:");
            stringBuilder.append(this.mDatabaseStatus);
            BiometricLoggerImpl.d(str, stringBuilder.toString());
        } else {
            TemplateItem templateItem = findTemplate(faceId);
            if (templateItem == null) {
                String str2 = LOG_TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("setTemplateName findTemplate ");
                stringBuilder2.append(faceId);
                stringBuilder2.append(" is null");
                BiometricLoggerImpl.d(str2, stringBuilder2.toString());
                return;
            }
            BiometricLoggerImpl.d(LOG_TAG, "setTemplateName");
            templateItem.name = name;
            this.mDatabaseChanged = true;
            commitDatabase();
        }
    }

    public List<Miuiface> getEnrolledFaces() {
        List<Miuiface> res = new ArrayList();
        BiometricLoggerImpl.e(LOG_TAG, " xiaomi getEnrolledFaces!");
        for (TemplateItem i : this.mTemplateItemList) {
            res.add(new Miuiface(i.name, i.group_id, i.id, 0));
        }
        return res;
    }

    public void preInitAuthen() {
        tryConnectService();
    }

    private void cancelAuthentication() {
        String str;
        StringBuilder stringBuilder;
        if (this.mReleased) {
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("stopVerify ctx:");
            stringBuilder.append(this.mContext);
            stringBuilder.append(" ignore!");
            BiometricLoggerImpl.e(str, stringBuilder.toString());
            return;
        }
        if (this.mcancelStatus != 0) {
            this.mAuthenticationCallback.onAuthenticationError(34, getMessageInfo(34));
            this.mcancelStatus = 0;
        }
        try {
            if (this.sReleaseFunc != null) {
                this.sReleaseFunc.invoke(this.boostFramework);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e2) {
            e2.printStackTrace();
        }
        str = LOG_TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("cancelAuthentication ctx:");
        stringBuilder.append(this.mContext);
        BiometricLoggerImpl.d(str, stringBuilder.toString());
        this.mAuthenticationCallback = null;
        this.mBiometricClient.sendCommand(6);
        this.mHandler.removeMessages(1);
    }

    public void authenticate(CancellationSignal cancel, int flags, AuthenticationCallback callback, Handler handler, int timeout) {
        String str = " ignore!";
        String str2 = "start authenticate ctx:";
        String str3;
        StringBuilder stringBuilder;
        if (this.mReleased) {
            str3 = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append(str2);
            stringBuilder.append(this.mContext);
            stringBuilder.append(str);
            BiometricLoggerImpl.e(str3, stringBuilder.toString());
        } else if (hasEnrolledFaces() == 0) {
            str3 = LOG_TAG;
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("has no enrolled face ctx:");
            stringBuilder2.append(this.mContext);
            stringBuilder2.append(str);
            BiometricLoggerImpl.e(str3, stringBuilder2.toString());
        } else if (callback != null) {
            if (cancel != null) {
                if (cancel.isCanceled()) {
                    BiometricLoggerImpl.d(LOG_TAG, "authentication already canceled");
                    return;
                }
                cancel.setOnCancelListener(new OnAuthenticationCancelListener());
            }
            try {
                if (this.sAcquireFunc != null) {
                    Method method = this.sAcquireFunc;
                    Object obj = this.boostFramework;
                    Object[] objArr = new Object[2];
                    objArr[0] = 2000;
                    objArr[1] = new int[]{1082130432, 4095, 1086324736, 1};
                    method.invoke(obj, objArr);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e2) {
                e2.printStackTrace();
            }
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append(str2);
            stringBuilder.append(this.mContext);
            BiometricLoggerImpl.d(str, stringBuilder.toString());
            useHandler(handler);
            this.mAuthenticationCallback = callback;
            tryConnectService();
            this.mBiometricClient.sendCommand(5);
            Handler handler2 = this.mHandler;
            handler2.sendMessageDelayed(handler2.obtainMessage(1), timeout);
        } else {
            throw new IllegalArgumentException("Must supply an authentication callback");
        }
    }

    private void cancelEnrollment() {
        if (this.mReleased) {
            BiometricLoggerImpl.e(LOG_TAG, "stopEnroll ignore!");
            return;
        }
        BiometricLoggerImpl.d(LOG_TAG, "stopEnroll");
        this.mBiometricClient.sendCommand(8);
        this.mBiometricClient.sendCommand(4);
        this.mHandler.removeMessages(0);
    }

    private void adjustDetectZone(float[] detect_zone) {
        if (detect_zone != null && detect_zone.length >= 4) {
            for (int i = 0; i < detect_zone.length; i++) {
                float temp = detect_zone[i];
                if (i % 2 == 1) {
                    if (i < 4) {
                        detect_zone[i] = IrToRgbScale(detect_zone[i], 1.18f);
                    } else {
                        detect_zone[i] = IrToRgbScale(detect_zone[i], 1.06f);
                    }
                    detect_zone[i] = IrToRgbMove(detect_zone[i], -0.067f);
                    detect_zone[i] = IrToRgbRadio(detect_zone[i]);
                } else {
                    if (i < 4) {
                        detect_zone[i] = IrToRgbScale(detect_zone[i], 1.16f);
                    } else {
                        detect_zone[i] = IrToRgbScale(detect_zone[i], 1.05f);
                    }
                    detect_zone[i] = IrToRgbMove(detect_zone[i], -0.044f);
                }
                String str = LOG_TAG;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("adjustDetectZone ");
                stringBuilder.append(i);
                stringBuilder.append(": ");
                stringBuilder.append(temp);
                stringBuilder.append(" to ");
                stringBuilder.append(detect_zone[i]);
                BiometricLoggerImpl.d(str, stringBuilder.toString());
            }
        }
    }

    private float IrToRgbRadio(float x) {
        return ((12.0f * x) - 1.0f) / 10.0f;
    }

    private float IrToRgbMove(float x, float offset) {
        return x + offset;
    }

    private float IrToRgbScale(float x, float zoom) {
        return x > 0.5f ? x * zoom : 0.5f - ((0.5f - x) * zoom);
    }

    public void enroll(byte[] cryptoToken, CancellationSignal cancel, int flags, EnrollmentCallback enrollCallback, Surface surface, Rect detectArea, int timeout) {
    }

    public void enroll(byte[] cryptoToken, CancellationSignal cancel, int flags, EnrollmentCallback enrollCallback, Surface surface, RectF detectArea, RectF enrollArea, int timeout) {
        int timeout2;
        String str;
        int i;
        CancellationSignal cancellationSignal = cancel;
        Surface surface2 = surface;
        RectF rectF = detectArea;
        RectF rectF2 = enrollArea;
        if (this.mReleased) {
            BiometricLoggerImpl.e(LOG_TAG, "enroll ignore!");
        }
        if (timeout == 0) {
            timeout2 = 180000;
        } else {
            timeout2 = timeout;
        }
        if (surface2 == null || timeout2 < 2000) {
            BiometricLoggerImpl.e(LOG_TAG, "enroll error!");
        }
        String str2 = "]";
        String str3 = ",";
        if (BiometricConnect.DEBUG_LOG) {
            String str4 = LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("xiaomi detectArea data:[");
            stringBuilder.append(rectF);
            stringBuilder.append(str3);
            stringBuilder.append(rectF.left);
            stringBuilder.append(str3);
            stringBuilder.append(rectF.right);
            stringBuilder.append(str3);
            stringBuilder.append(rectF.top);
            stringBuilder.append(str3);
            stringBuilder.append(rectF.bottom);
            stringBuilder.append(str2);
            BiometricLoggerImpl.e(str4, stringBuilder.toString());
            str4 = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("xiaomi enrollArea data:[");
            stringBuilder.append(rectF2);
            stringBuilder.append(str3);
            stringBuilder.append(rectF2.left);
            stringBuilder.append(str3);
            stringBuilder.append(rectF2.right);
            stringBuilder.append(str3);
            stringBuilder.append(rectF2.top);
            stringBuilder.append(str3);
            stringBuilder.append(rectF2.bottom);
            stringBuilder.append(str2);
            BiometricLoggerImpl.e(str4, stringBuilder.toString());
        }
        if (cancellationSignal != null) {
            if (cancel.isCanceled()) {
                BiometricLoggerImpl.d(LOG_TAG, "enrollment already canceled");
                return;
            }
            cancellationSignal.setOnCancelListener(new OnEnrollCancelListener());
        }
        Bundle bundle_preview = new Bundle();
        this.mEnrollmentCallback = enrollCallback;
        bundle_preview.putParcelable("surface", surface2);
        bundle_preview.putInt("width", 3);
        bundle_preview.putInt("height", 4);
        tryConnectService();
        this.mBiometricClient.sendBundle(1, bundle_preview);
        this.mBiometricClient.sendCommand(3, 0);
        this.mEnrollParam.rectDetectZones = new FRect(rectF.left, rectF.top, rectF.right, rectF.bottom);
        this.mEnrollParam.rectEnrollZones = new FRect(rectF2.left, rectF2.top, rectF2.right, rectF2.bottom);
        EnrollParam enrollParam = this.mEnrollParam;
        enrollParam.enableDistanceDetect = false;
        enrollParam.enableIrFaceDetect = true;
        enrollParam.enableDepthmap = false;
        enrollParam.enrollWaitUi = true;
        float[] detect_zone = new float[]{enrollParam.rectDetectZones.top, 1.0f - this.mEnrollParam.rectDetectZones.right, this.mEnrollParam.rectDetectZones.bottom, 1.0f - this.mEnrollParam.rectDetectZones.left, this.mEnrollParam.rectEnrollZones.top, 1.0f - this.mEnrollParam.rectEnrollZones.right, this.mEnrollParam.rectEnrollZones.bottom, 1.0f - this.mEnrollParam.rectEnrollZones.left};
        adjustDetectZone(detect_zone);
        if (BiometricConnect.DEBUG_LOG) {
            String str5 = LOG_TAG;
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("startEnroll rectDetectZones:[");
            stringBuilder2.append(this.mEnrollParam.rectDetectZones.left);
            stringBuilder2.append(str3);
            stringBuilder2.append(this.mEnrollParam.rectDetectZones.top);
            stringBuilder2.append(str3);
            stringBuilder2.append(this.mEnrollParam.rectDetectZones.right);
            stringBuilder2.append(str3);
            stringBuilder2.append(this.mEnrollParam.rectDetectZones.bottom);
            String str6 = "] adjustDetectZone -> [";
            stringBuilder2.append(str6);
            stringBuilder2.append(detect_zone[0]);
            stringBuilder2.append(str3);
            stringBuilder2.append(detect_zone[1]);
            stringBuilder2.append(str3);
            stringBuilder2.append(detect_zone[2]);
            stringBuilder2.append(str3);
            stringBuilder2.append(detect_zone[3]);
            stringBuilder2.append(str2);
            BiometricLoggerImpl.d(str5, stringBuilder2.toString());
            str = LOG_TAG;
            StringBuilder stringBuilder3 = new StringBuilder();
            stringBuilder3.append("startEnroll rectEnrollZones:[");
            stringBuilder3.append(this.mEnrollParam.rectEnrollZones.left);
            stringBuilder3.append(str3);
            stringBuilder3.append(this.mEnrollParam.rectEnrollZones.top);
            stringBuilder3.append(str3);
            stringBuilder3.append(this.mEnrollParam.rectEnrollZones.right);
            stringBuilder3.append(str3);
            stringBuilder3.append(this.mEnrollParam.rectEnrollZones.bottom);
            stringBuilder3.append(str6);
            stringBuilder3.append(detect_zone[4]);
            stringBuilder3.append(str3);
            stringBuilder3.append(detect_zone[5]);
            stringBuilder3.append(str3);
            stringBuilder3.append(detect_zone[6]);
            stringBuilder3.append(str3);
            stringBuilder3.append(detect_zone[7]);
            stringBuilder3.append(str2);
            BiometricLoggerImpl.d(str, stringBuilder3.toString());
        }
        Bundle bundle_enroll = new Bundle();
        bundle_enroll.putFloatArray(BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_ZONE, detect_zone);
        bundle_enroll.putBoolean(BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_FACE, this.mEnrollParam.enableIrFaceDetect);
        bundle_enroll.putBoolean(BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DISTANCE, this.mEnrollParam.enableDistanceDetect);
        bundle_enroll.putBoolean(BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_WAITING_UI, this.mEnrollParam.enrollWaitUi);
        boolean z = this.mEnrollParam.enableDepthmap;
        str = BiometricConnect.MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DEPTHMAP;
        if (z) {
            bundle_enroll.putInt(str, this.mEnrollParam.DepthmpaType);
            i = 0;
        } else {
            i = 0;
            bundle_enroll.putInt(str, 0);
        }
        this.mBiometricClient.sendBundle(6, bundle_enroll);
        this.mBiometricClient.sendCommand(7);
        Handler handler = this.mHandler;
        handler.sendMessageDelayed(handler.obtainMessage(i), timeout2);
    }

    private void useHandler(Handler handler) {
        if (handler != null) {
            this.mHandler = new ClientHandler(handler.getLooper());
        } else if (this.mHandler.getLooper() != this.mContext.getMainLooper()) {
            this.mHandler = new ClientHandler(this.mContext.getMainLooper());
        }
    }

    public int extCmd(int cmd, int param) {
        if (cmd != 0) {
            return -1;
        }
        this.mBiometricClient.sendCommand(14, param);
        return 0;
    }

    public void addLockoutResetCallback(LockoutResetCallback callback) {
        if (DEBUG) {
            String str = LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("addLockoutResetCallback  callback:");
            stringBuilder.append(callback);
            BiometricLoggerImpl.d(str, stringBuilder.toString());
        }
    }

    public void resetTimeout(byte[] token) {
        if (DEBUG) {
            BiometricLoggerImpl.d(LOG_TAG, "resetTimeout");
        }
    }

    public static class EnrollParam {
        public int DepthmpaType;
        public boolean enableDepthmap;
        public boolean enableDistanceDetect;
        public boolean enableIrFaceDetect;
        public boolean enrollWaitUi;
        public FRect rectDetectZones;
        public FRect rectEnrollZones;
    }

    public static class FRect {
        public float bottom;
        public float left;
        public float right;
        public float top;

        public FRect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    public static class FaceInfo {
        public Rect bounds;
        public float eye_dist;
        public float pitch;
        public Point[] points_array;
        public float roll;
        public float yaw;
    }

    public static class GroupItem {
        public int id;
        public String name;
    }

    public static class TemplateItem {
        public String data;
        public int group_id;
        public int id;
        public String name;
    }

    private class ClientHandler extends Handler {
        private ClientHandler(Context context) {
            super(context.getMainLooper());
        }

        private ClientHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (Miui3DFaceManagerImpl.DEBUG) {
                String access$1100 = Miui3DFaceManagerImpl.LOG_TAG;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(" handleMessage  callback what:");
                stringBuilder.append(msg.what);
                BiometricLoggerImpl.d(access$1100, stringBuilder.toString());
            }
            int i = msg.what;
            if (i != 0) {
                if (i == 1 && Miui3DFaceManagerImpl.this.mAuthenticationCallback != null) {
                    BiometricLoggerImpl.d(Miui3DFaceManagerImpl.LOG_TAG, "xiaomi ---> RECEIVER_ON_AUTHENTICATION_TIMEOUT");
                    Miui3DFaceManagerImpl.this.mAuthenticationCallback.onAuthenticationFailed();
                    Miui3DFaceManagerImpl.this.cancelAuthentication();
                }
            } else if (Miui3DFaceManagerImpl.this.mEnrollmentCallback != null) {
                Miui3DFaceManagerImpl.this.mEnrollmentCallback.onEnrollmentError(66, null);
                BiometricLoggerImpl.d(Miui3DFaceManagerImpl.LOG_TAG, "RECEIVER_ON_ENROLL_TIMEOUT");
                Miui3DFaceManagerImpl.this.cancelEnrollment();
            }
        }
    }

    private class OnAuthenticationCancelListener implements OnCancelListener {
        private OnAuthenticationCancelListener() {
        }

        public void onCancel() {
            Miui3DFaceManagerImpl.this.mcancelStatus = 1;
            Miui3DFaceManagerImpl.this.cancelAuthentication();
        }
    }

    private class OnEnrollCancelListener implements OnCancelListener {
        private OnEnrollCancelListener() {
        }

        public void onCancel() {
            Miui3DFaceManagerImpl.this.cancelEnrollment();
        }
    }
}
