package dev.skomlach.biometric.compat.engine.internal.face.miui.impl;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.FeatureParser;
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.MiuiBuild;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class MiuiFaceManagerImpl implements IMiuiFaceManager {
    public static final int ERROR_BINDER_CALL = 2100;
    public static final int ERROR_CANCELED = 2000;
    public static final int ERROR_SERVICE_IS_BUSY = 2001;
    public static final int ERROR_SERVICE_IS_IDLE = 2002;
    public static final int ERROR_TIME_OUT = 2003;
    public static final int MG_ATTR_BLUR = 20;
    public static final int MG_ATTR_EYE_CLOSE = 22;
    public static final int MG_ATTR_EYE_OCCLUSION = 21;
    public static final int MG_ATTR_MOUTH_OCCLUSION = 23;
    public static final int MG_OPEN_CAMERA_FAIL = 1000;
    public static final int MG_OPEN_CAMERA_SUCCESS = 1001;
    public static final int MG_UNLOCK_BAD_LIGHT = 26;
    public static final int MG_UNLOCK_COMPARE_FAILURE = 12;
    public static final int MG_UNLOCK_DARKLIGHT = 30;
    public static final int MG_UNLOCK_FACE_BAD_QUALITY = 4;
    public static final int MG_UNLOCK_FACE_BLUR = 28;
    public static final int MG_UNLOCK_FACE_DOWN = 18;
    public static final int MG_UNLOCK_FACE_MULTI = 27;
    public static final int MG_UNLOCK_FACE_NOT_COMPLETE = 29;
    public static final int MG_UNLOCK_FACE_NOT_FOUND = 5;
    public static final int MG_UNLOCK_FACE_NOT_ROI = 33;
    public static final int MG_UNLOCK_FACE_OFFSET_BOTTOM = 11;
    public static final int MG_UNLOCK_FACE_OFFSET_LEFT = 8;
    public static final int MG_UNLOCK_FACE_OFFSET_RIGHT = 10;
    public static final int MG_UNLOCK_FACE_OFFSET_TOP = 9;
    public static final int MG_UNLOCK_FACE_RISE = 16;
    public static final int MG_UNLOCK_FACE_ROTATED_LEFT = 15;
    public static final int MG_UNLOCK_FACE_ROTATED_RIGHT = 17;
    public static final int MG_UNLOCK_FACE_SCALE_TOO_LARGE = 7;
    public static final int MG_UNLOCK_FACE_SCALE_TOO_SMALL = 6;
    public static final int MG_UNLOCK_FAILURE = 3;
    public static final int MG_UNLOCK_FEATURE_MISS = 24;
    public static final int MG_UNLOCK_FEATURE_VERSION_ERROR = 25;
    public static final int MG_UNLOCK_HALF_SHADOW = 32;
    public static final int MG_UNLOCK_HIGHLIGHT = 31;
    public static final int MG_UNLOCK_INVALID_ARGUMENT = 1;
    public static final int MG_UNLOCK_INVALID_HANDLE = 2;
    public static final int MG_UNLOCK_KEEP = 19;
    public static final int MG_UNLOCK_LIVENESS_FAILURE = 14;
    public static final int MG_UNLOCK_LIVENESS_WARNING = 13;
    public static final int MG_UNLOCK_OK = 0;
    private static final int CODE_ADD_LOCKOUT_RESET_CALLBACK = 16;
    private static final int CODE_AUTHENTICATE = 3;
    private static final int CODE_CANCEL_AUTHENTICATE = 4;
    private static final int CODE_CANCEL_ENROLL = 6;
    private static final int CODE_ENROLL = 5;
    private static final int CODE_EXT_CMD = 101;
    private static final int CODE_GET_AUTHENTICATOR_ID = 14;
    private static final int CODE_GET_ENROLLED_FACE_LIST = 9;
    private static final int CODE_GET_VENDOR_INFO = 17;
    private static final int CODE_HAS_ENROLLED_FACES = 12;
    private static final int CODE_POST_ENROLL = 11;
    private static final int CODE_PRE_ENROLL = 10;
    private static final int CODE_PRE_INIT_AUTHEN = 2;
    private static final int CODE_REMOVE = 7;
    private static final int CODE_RENAME = 8;
    private static final int CODE_RESET_TIMEOUT = 15;
    private static final int FACEUNLOCK_CURRENT_USE_INVALID_MODEL = 2;
    private static final int FACEUNLOCK_CURRENT_USE_RGB_MODEL = 1;
    private static final int FACEUNLOCK_CURRENT_USE_STRUCTURE_MODEL = 0;
    private static final String FACEUNLOCK_SUPPORT_SUPERPOWER = "faceunlock_support_superpower";
    private static final String FACE_UNLOCK_3D_HAS_FEATURE = "face_unlock_has_feature_sl";
    private static final String FACE_UNLOCK_HAS_FEATURE = "face_unlock_has_feature";
    private static final String FACE_UNLOCK_HAS_FEATURE_URI = "content://settings/secure/face_unlock_has_feature";
    private static final String FACE_UNLOCK_MODEL = "face_unlock_model";
    private static final String FACE_UNLOCK_VALID_FEATURE = "face_unlock_valid_feature";
    private static final String FACE_UNLOCK_VALID_FEATURE_URI = "content://settings/secure/face_unlock_valid_feature";
    private static final String POWERMODE_SUPERSAVE_OPEN = "power_supersave_mode_open";
    private static final String POWERMODE_SUPERSAVE_OPEN_URI = "content://settings/secure/power_supersave_mode_open";
    private static final int RECEIVER_ON_AUTHENTICATION_FAILED = 204;
    private static final int RECEIVER_ON_AUTHENTICATION_SUCCEEDED = 203;
    private static final int RECEIVER_ON_ENROLL_RESULT = 201;
    private static final int RECEIVER_ON_ERROR = 205;
    private static final int RECEIVER_ON_EXT_CMD = 301;
    private static final int RECEIVER_ON_LOCKOUT_RESET = 261;
    private static final int RECEIVER_ON_ON_ACQUIRED = 202;
    private static final int RECEIVER_ON_PRE_INIT = 207;
    private static final int RECEIVER_ON_REMOVED = 206;
    private static final int VERSION_1 = 1;
    private static final boolean DEBUG = true;
    private static final String RECEIVER_DESCRIPTOR = "receiver.FaceService";
    private static final String TAG = "FaceManagerImpl";
    private static volatile IMiuiFaceManager INSTANCE = null;
    private static String SERVICE_DESCRIPTOR = null;
    private static String SERVICE_NAME = null;

    static {
        String str = "miui.face.FaceService";
        SERVICE_NAME = str;
        SERVICE_DESCRIPTOR = str;
    }

    private final Object mBinderLock = new Object();
    private final Context mContext;
    private final IBinder mToken = new Binder();
    private AuthenticationCallback mAuthenticationCallback;
    private EnrollmentCallback mEnrollmentCallback;
    private int mFaceUnlockModel;
    private Handler mHandler;
    private boolean mHasFaceData;
    private boolean mHasInit;
    private final IBinder mServiceReceiver = new Binder() {
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            int i = code;
            Parcel parcel = data;
            String TAG = MiuiFaceManagerImpl.TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("mServiceReceiver callback: ");
            stringBuilder.append(i);
            BiometricLoggerImpl.d(TAG, stringBuilder.toString());
            if (i == 261) {
                parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                MiuiFaceManagerImpl.this.mHandler.obtainMessage(261, data.readInt()).sendToTarget();
                reply.writeNoException();
                return true;
            } else if (i != 301) {
                long readLong;
                switch (i) {
                    case 201:
                        parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                        long devId = data.readLong();
                        i = data.readInt();
                        MiuiFaceManagerImpl.this.mHandler.obtainMessage(201, data.readInt(), 0, new Miuiface(null, data.readInt(), i, devId)).sendToTarget();
                        reply.writeNoException();
                        return true;
                    case 202:
                        parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                        MiuiFaceManagerImpl.this.mHandler.obtainMessage(202, data.readInt(), data.readInt(), data.readLong()).sendToTarget();
                        reply.writeNoException();
                        return true;
                    case 203:
                        parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                        readLong = data.readLong();
                        Miuiface face = null;
                        if (data.readInt() != 0) {
                            face = Miuiface.CREATOR.createFromParcel(parcel);
                        }
                        MiuiFaceManagerImpl.this.mHandler.obtainMessage(203, data.readInt(), 0, face).sendToTarget();
                        reply.writeNoException();
                        return true;
                    case 204:
                        parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                        readLong = data.readLong();
                        MiuiFaceManagerImpl.this.mHandler.obtainMessage(204).sendToTarget();
                        reply.writeNoException();
                        return true;
                    case 205:
                        parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                        MiuiFaceManagerImpl.this.mHandler.obtainMessage(205, data.readInt(), data.readInt(), Long.valueOf(data.readLong())).sendToTarget();
                        reply.writeNoException();
                        return true;
                    case 206:
                        parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                        long devId2 = data.readLong();
                        int faceId = data.readInt();
                        int groupId = data.readInt();
                        int remaining = data.readInt();
                        Miuiface miuiface = new Miuiface(null, groupId, faceId, devId2);
                        Handler access$1300 = MiuiFaceManagerImpl.this.mHandler;
                        i = 206;
                        access$1300.obtainMessage(i, remaining, 0, miuiface).sendToTarget();
                        reply.writeNoException();
                        return true;
                    case 207:
                        parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                        MiuiFaceManagerImpl.this.mHasInit = data.readInt() == 1;
                        reply.writeNoException();
                        return true;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
            } else {
                parcel.enforceInterface(MiuiFaceManagerImpl.RECEIVER_DESCRIPTOR);
                MiuiFaceManagerImpl.this.mHandler.obtainMessage(301, data.readInt()).sendToTarget();
                reply.writeNoException();
                return true;
            }
        }
    };
    private boolean mIsSuperPower;
    private boolean mIsValid;
    private LockoutResetCallback mLockoutResetCallback;
    private IBinder mMiuiFaceService;
    private final DeathRecipient mBinderDied = new DeathRecipient() {
        public void binderDied() {
            synchronized (MiuiFaceManagerImpl.this.mBinderLock) {
                BiometricLoggerImpl.e(MiuiFaceManagerImpl.TAG, "mMiuiFaceService Service Died.");
                MiuiFaceManagerImpl.this.mMiuiFaceService = null;
            }
        }
    };
    private RemovalCallback mRemovalCallback;
    private Miuiface mRemovalMiuiface;

    private MiuiFaceManagerImpl(Context con) {
        this.mContext = con.getApplicationContext();
        this.mHandler = new ClientHandler(this.mContext);
//        boolean equals = "ursa".equals(Build.DEVICE);
//        String str = FACE_UNLOCK_VALID_FEATURE;
//        String str2 = FACE_UNLOCK_HAS_FEATURE;
//        if (equals) {
//            ContentResolver contentResolver = this.mContext.getContentResolver();
//            String str3 = FACE_UNLOCK_MODEL;
//            this.mFaceUnlockModel = Secure.getIntForUser(contentResolver, str3, 1, -2);
//            if (this.mFaceUnlockModel != 2) {
//                Secure.putIntForUser(this.mContext.getContentResolver(), str3, 2, -2);
//                if (this.mFaceUnlockModel == 0) {
//                    Secure.putIntForUser(this.mContext.getContentResolver(), FACE_UNLOCK_3D_HAS_FEATURE, Secure.getIntForUser(this.mContext.getContentResolver(), str2, 0, -2), -2);
//                    Secure.putIntForUser(this.mContext.getContentResolver(), str2, 0, -2);
//                    Secure.putIntForUser(this.mContext.getContentResolver(), str, 1, -2);
//                }
//            }
//        }
//        Secure.putIntForUser(this.mContext.getContentResolver(), FACEUNLOCK_SUPPORT_SUPERPOWER, 1, -2);
//        FaceObserver faceObserver = new FaceObserver(this.mHandler);
//        this.mContext.getContentResolver().registerContentObserver(Secure.getUriFor(str2), false, faceObserver, 0);
//        faceObserver.onChange(false, Uri.parse(FACE_UNLOCK_HAS_FEATURE_URI));
//        this.mContext.getContentResolver().registerContentObserver(Secure.getUriFor(str), false, faceObserver, 0);
//        faceObserver.onChange(false, Uri.parse(FACE_UNLOCK_VALID_FEATURE_URI));
//        this.mContext.getContentResolver().registerContentObserver(System.getUriFor("power_supersave_mode_open"), false, faceObserver, 0);
//        faceObserver.onChange(false, Uri.parse(POWERMODE_SUPERSAVE_OPEN_URI));
    }

    public static IMiuiFaceManager getInstance(Context con) {
        if (INSTANCE == null) {
            synchronized (MiuiFaceManagerImpl.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MiuiFaceManagerImpl(con);
                }
            }
        }
        return INSTANCE;
    }

    private void initService() throws RemoteException {
        synchronized (this.mBinderLock) {
            if (this.mMiuiFaceService == null) {
                try {
                    this.mMiuiFaceService = (IBinder) Class.forName("android.os.ServiceManager")
                            .getMethod("getService", String.class).invoke(null, SERVICE_NAME);
                } catch (Exception ignore) {}
                if (this.mMiuiFaceService != null) {
                    this.mMiuiFaceService.linkToDeath(this.mBinderDied, 0);
                }
            }
        }
    }

    public boolean isFaceFeatureSupport() {
        if (this.mIsSuperPower) {
            BiometricLoggerImpl.d(TAG, "enter super power mode, isFaceFeatureSupport:false");
            return false;
        }
        String[] supportRegion;
        boolean res = false;
        if (MiuiBuild.IS_INTERNATIONAL_BUILD) {
            supportRegion = FeatureParser.getStringArray("support_face_unlock_region_global");
        } else {
            supportRegion = FeatureParser.getStringArray("support_face_unlock_region_dom");
        }
        if (supportRegion != null && (Arrays.asList(supportRegion).contains(MiuiBuild.getRegion()) || Arrays.asList(supportRegion).contains("ALL"))) {
            res = true;
        }
        if (DEBUG) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("inernational:");
            stringBuilder.append(MiuiBuild.IS_INTERNATIONAL_BUILD);
            stringBuilder.append(" supportR:");
            stringBuilder.append(Arrays.toString(supportRegion));
            stringBuilder.append(" nowR:");
            stringBuilder.append(MiuiBuild.getRegion());
            BiometricLoggerImpl.d(TAG, stringBuilder.toString());
        }
        return res;
    }

    public boolean isSupportScreenOnDelayed() {
        boolean res = FeatureParser.getBoolean("support_screen_on_delayed", false);
        if (DEBUG) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("isSupportScreenOnDelayed:");
            stringBuilder.append(res);
            BiometricLoggerImpl.d(TAG, stringBuilder.toString());
        }
        return res;
    }

    public boolean isFaceUnlockInited() {
        return this.mHasInit;
    }

    private void cancelAuthentication() {
        if (DEBUG) {
            BiometricLoggerImpl.d(TAG, "cancelAuthentication ");
        }
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                binderCallCancelAuthention(this.mMiuiFaceService, this.mToken, this.mContext.getPackageName());
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void cancelEnrollment() {
        if (DEBUG) {
            BiometricLoggerImpl.d(TAG, "cancelEnrollment ");
        }
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                binderCallCancelEnrollment(this.mMiuiFaceService, this.mToken);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public int getManagerVersion() {
        return 1;
    }

    public String getVendorInfo() {
        String res = "";
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                res = binderCallGetVendorInfo(this.mMiuiFaceService, this.mContext.getPackageName());
            }
        } catch (RemoteException e) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("transact fail. ");
            stringBuilder.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder.toString());
        }
        if (DEBUG) {

            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("getVendorInfo, res:");
            stringBuilder2.append(res);
            BiometricLoggerImpl.d(TAG, stringBuilder2.toString());
        }
        return res;
    }

    public void authenticate(CancellationSignal cancel, int flags, AuthenticationCallback callback, Handler handler, int timeout) {
        CancellationSignal cancellationSignal = cancel;
        AuthenticationCallback authenticationCallback = callback;
        if (DEBUG) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("authenticate mServiceReceiver:");
            stringBuilder.append(this.mServiceReceiver);
            BiometricLoggerImpl.d(TAG, stringBuilder.toString());
        }
        if (authenticationCallback != null) {
            if (cancellationSignal != null) {
                if (cancel.isCanceled()) {
                    BiometricLoggerImpl.d(TAG, "authentication already canceled");
                    return;
                }
                cancellationSignal.setOnCancelListener(new OnAuthenticationCancelListener());
            }
            useHandler(handler);
            this.mAuthenticationCallback = authenticationCallback;
            this.mEnrollmentCallback = null;
            try {
                initService();
                if (this.mMiuiFaceService != null) {
                    binderCallAuthenticate(this.mMiuiFaceService, this.mToken, -1, -1, this.mServiceReceiver, flags, this.mContext.getPackageName(), timeout);
                } else {
                    BiometricLoggerImpl.d(TAG, "mMiuiFaceService is null");
                    authenticationCallback.onAuthenticationError(2100, getMessageInfo(2100));
                }
            } catch (Exception e) {
                BiometricLoggerImpl.e(TAG, "Remote exception while authenticating: ", e);
                authenticationCallback.onAuthenticationError(2100, getMessageInfo(2100));
            }
            return;
        }
        throw new IllegalArgumentException("Must supply an authentication callback");
    }

    public void enroll(byte[] cryptoToken, CancellationSignal cancel, int flags, EnrollmentCallback enrollCallback, Surface surface, Rect detectArea, int timeout) {
        enroll(cryptoToken, cancel, flags, enrollCallback, surface, null, new RectF(detectArea), timeout);
    }

    public void enroll(byte[] cryptoToken, CancellationSignal cancel, int flags, EnrollmentCallback enrollCallback, Surface surface, RectF detectArea, RectF enrollArea, int timeout) {
        int i;
        RemoteException e;
        CancellationSignal cancellationSignal = cancel;
        EnrollmentCallback enrollmentCallback = enrollCallback;
        if (enrollmentCallback != null) {
            if (cancellationSignal != null) {
                if (cancel.isCanceled()) {
                    BiometricLoggerImpl.d(TAG, "enrollment already canceled");
                    return;
                }
                cancellationSignal.setOnCancelListener(new OnEnrollCancelListener());
            }
            try {
                initService();
                if (this.mMiuiFaceService != null) {
                    this.mEnrollmentCallback = enrollmentCallback;
                    i = 2100;
                    try {
                        binderCallEnroll(this.mMiuiFaceService, this.mToken, cryptoToken, 0, this.mServiceReceiver, flags, this.mContext.getPackageName(), surface, enrollArea, timeout);
                    } catch (RemoteException e2) {
                        e = e2;
                        BiometricLoggerImpl.e(TAG, "exception in enroll: ", e);
                        enrollmentCallback.onEnrollmentError(i, getMessageInfo(i));
                        return;
                    }
                }
                i = 2100;
                BiometricLoggerImpl.d(TAG, "mMiuiFaceService is null");
                enrollmentCallback.onEnrollmentError(i, getMessageInfo(i));
            } catch (RemoteException e3) {
                e = e3;
                i = 2100;
                BiometricLoggerImpl.e(TAG, "exception in enroll: ", e);
                enrollmentCallback.onEnrollmentError(i, getMessageInfo(i));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("Must supply an enrollment callback");
    }

    public int extCmd(int cmd, int param) {
        int res = -1;
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                res = binderCallExtCmd(this.mMiuiFaceService, this.mToken, this.mServiceReceiver, cmd, param, this.mContext.getPackageName());
            }
        } catch (RemoteException e) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("transact fail. ");
            stringBuilder.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder.toString());
        }
        if (DEBUG) {

            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("extCmd  cmd:");
            stringBuilder2.append(cmd);
            stringBuilder2.append(" param:");
            stringBuilder2.append(param);
            stringBuilder2.append(" res:");
            stringBuilder2.append(res);
            BiometricLoggerImpl.d(TAG, stringBuilder2.toString());
        }
        return res;
    }

    public void remove(Miuiface face, RemovalCallback callback) {
        if (DEBUG) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("remove  faceId:");
            stringBuilder.append(face.getMiuifaceId());
            stringBuilder.append("  callback:");
            stringBuilder.append(callback);
            BiometricLoggerImpl.d(TAG, stringBuilder.toString());
        }
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                this.mRemovalMiuiface = face;
                this.mRemovalCallback = callback;
                this.mEnrollmentCallback = null;
                this.mAuthenticationCallback = null;
                binderCallRemove(this.mMiuiFaceService, this.mToken, face.getMiuifaceId(), face.getGroupId(), 0, this.mServiceReceiver);
                return;
            }
            BiometricLoggerImpl.d(TAG, "mMiuiFaceService is null");
            callback.onRemovalError(face, 2100, getMessageInfo(2100));
        } catch (RemoteException e) {

            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("transact fail. ");
            stringBuilder2.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder2.toString());
        }
    }

    public void rename(int faceId, String name) {
        if (DEBUG) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("rename  faceId:");
            stringBuilder.append(faceId);
            stringBuilder.append(" name:");
            stringBuilder.append(name);
            BiometricLoggerImpl.d(TAG, stringBuilder.toString());
        }
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                binderCallRename(this.mMiuiFaceService, faceId, 0, name);
            }
        } catch (RemoteException e) {

            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("transact fail. ");
            stringBuilder2.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder2.toString());
        }
    }

    public void addLockoutResetCallback(LockoutResetCallback callback) {
        if (DEBUG) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("addLockoutResetCallback  callback:");
            stringBuilder.append(callback);
            BiometricLoggerImpl.d(TAG, stringBuilder.toString());
        }
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                this.mLockoutResetCallback = callback;
                binderCallAddLoackoutResetCallback(this.mMiuiFaceService, this.mServiceReceiver);
            }
        } catch (RemoteException e) {

            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("transact fail. ");
            stringBuilder2.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder2.toString());
        }
    }

    public void resetTimeout(byte[] token) {
        if (DEBUG) {
            BiometricLoggerImpl.d(TAG, "resetTimeout");
        }
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                binderCallRestTimeout(this.mMiuiFaceService, token);
            }
        } catch (RemoteException e) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("transact fail. ");
            stringBuilder.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder.toString());
        }
    }

    public List<Miuiface> getEnrolledFaces() {
        StringBuilder stringBuilder;
        List<Miuiface> res = new ArrayList<>();
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                res = binderCallGetEnrolledFaces(this.mMiuiFaceService, 0, this.mContext.getPackageName());
            }
        } catch (RemoteException e) {

            stringBuilder = new StringBuilder();
            stringBuilder.append("transact fail. ");
            stringBuilder.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder.toString());
        }
        if (DEBUG) {
            String str2;

            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("getEnrolledFaces   res:");
            if (res == null || res.size() == 0) {
                str2 = " is null";
            } else {
                stringBuilder = new StringBuilder();
                stringBuilder.append(" ");
                stringBuilder.append(res.size());
                str2 = stringBuilder.toString();
            }
            stringBuilder2.append(str2);
            BiometricLoggerImpl.d(TAG, stringBuilder2.toString());
        }
        return res;
    }

    public int hasEnrolledFaces() {
        try {
            if (this.mHasFaceData && this.mIsValid) {
                return 1;
            }
            if (this.mHasFaceData) {
                return -1;
            }
            return 0;
        } catch (Exception e) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("transact fail. ");
            stringBuilder.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder.toString());
            return -2;
        }
    }

    public void preInitAuthen() {
        try {
            initService();
            if (this.mMiuiFaceService != null) {
                this.mHasInit = false;
                binderCallPpreInitAuthen(this.mMiuiFaceService, this.mToken, this.mContext.getPackageName(), this.mServiceReceiver);
            }
        } catch (RemoteException e) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("transact fail. ");
            stringBuilder.append(e);
            BiometricLoggerImpl.e(TAG, stringBuilder.toString());
        }
    }

    public boolean isReleased() {
        return false;
    }

    public void release() {
    }

    private void binderCallPpreInitAuthen(IBinder service, IBinder token, String packName, IBinder receiver) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeStrongBinder(token);
        request.writeString(packName);
        request.writeStrongBinder(receiver);
        service.transact(2, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private int binderCallAuthenticate(IBinder service, IBinder token, long sessionId, int userId, IBinder receiver, int flags, String packName, int timeout) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        IBinder iBinder = null;
        request.writeStrongBinder(token);
        request.writeLong(sessionId);
        request.writeInt(userId);
        if (receiver != null) {
            iBinder = receiver;
        }
        request.writeStrongBinder(iBinder);
        request.writeInt(flags);
        request.writeString(packName);
        request.writeInt(timeout);
        service.transact(3, request, reply, 0);
        reply.readException();
        int res = reply.readInt();
        request.recycle();
        reply.recycle();
        return res;
    }

    private void binderCallCancelAuthention(IBinder service, IBinder token, String packName) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeStrongBinder(token);
        request.writeString(packName);
        service.transact(4, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private void binderCallEnroll(IBinder service, IBinder token, byte[] cryptoToken, int groupId, IBinder receiver, int flags, String packName, Surface surface, RectF detectArea, int timeout) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        IBinder iBinder = null;
        request.writeStrongBinder(token);
        request.writeByteArray(cryptoToken);
        request.writeInt(groupId);
        if (receiver != null) {
            iBinder = receiver;
        }
        request.writeStrongBinder(iBinder);
        request.writeInt(flags);
        request.writeString(packName);
        if (surface != null) {
            request.writeInt(1);
            surface.writeToParcel(request, 0);
        } else {
            request.writeInt(0);
        }
        if (detectArea != null) {
            request.writeInt(1);
            detectArea.writeToParcel(request, 0);
        } else {
            request.writeInt(0);
        }
        request.writeInt(timeout);
        service.transact(5, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private void binderCallCancelEnrollment(IBinder service, IBinder token) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeStrongBinder(token);
        service.transact(6, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private void binderCallRemove(IBinder service, IBinder token, int faceId, int groupId, int userId, IBinder receiver) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        IBinder iBinder = null;
        request.writeStrongBinder(token);
        request.writeInt(faceId);
        request.writeInt(groupId);
        request.writeInt(userId);
        if (receiver != null) {
            iBinder = receiver;
        }
        request.writeStrongBinder(iBinder);
        service.transact(7, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private void binderCallRename(IBinder service, int faceId, int groupId, String name) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeInt(faceId);
        request.writeInt(groupId);
        request.writeString(name);
        service.transact(8, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private List<Miuiface> binderCallGetEnrolledFaces(IBinder service, int groupId, String packName) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeInt(groupId);
        request.writeString(packName);
        service.transact(9, request, reply, 0);
        reply.readException();
        List<Miuiface> res = reply.createTypedArrayList(Miuiface.CREATOR);
        request.recycle();
        reply.recycle();
        return res;
    }

    private void binderCallPreEnroll(IBinder service, IBinder token) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeStrongBinder(token);
        service.transact(10, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private void binderCallPostEnroll(IBinder service, IBinder token) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeStrongBinder(token);
        service.transact(11, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private int binderCallHasEnrolledFaces(IBinder service, int groupId, String packName) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeInt(groupId);
        request.writeString(packName);
        service.transact(12, request, reply, 0);
        reply.readException();
        int res = reply.readInt();
        request.recycle();
        reply.recycle();
        return res;
    }

    private long binderCallAuthenticatorId(IBinder service, String packName) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeString(packName);
        service.transact(14, request, reply, 0);
        reply.readException();
        long res = reply.readLong();
        request.recycle();
        reply.recycle();
        return res;
    }

    private void binderCallRestTimeout(IBinder service, byte[] cryptoToken) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeByteArray(cryptoToken);
        service.transact(15, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private void binderCallAddLoackoutResetCallback(IBinder service, IBinder callback) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeStrongBinder(callback);
        service.transact(16, request, reply, 0);
        reply.readException();
        request.recycle();
        reply.recycle();
    }

    private String binderCallGetVendorInfo(IBinder service, String packName) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        request.writeString(packName);
        service.transact(17, request, reply, 0);
        reply.readException();
        String res = reply.readString();
        request.recycle();
        reply.recycle();
        return res;
    }

    private int binderCallExtCmd(IBinder service, IBinder token, IBinder receiver, int cmd, int param, String packName) throws RemoteException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        request.writeInterfaceToken(SERVICE_DESCRIPTOR);
        IBinder iBinder = null;
        request.writeStrongBinder(token);
        if (receiver != null) {
            iBinder = receiver;
        }
        request.writeStrongBinder(iBinder);
        request.writeInt(cmd);
        request.writeInt(param);
        request.writeString(packName);
        service.transact(101, request, reply, 0);
        reply.readException();
        int res = reply.readInt();
        request.recycle();
        reply.recycle();
        return res;
    }

    private void useHandler(Handler handler) {
        if (handler != null) {
            this.mHandler = new ClientHandler(handler.getLooper());
        } else if (this.mHandler.getLooper() != this.mContext.getMainLooper()) {
            this.mHandler = new ClientHandler(this.mContext.getMainLooper());
        }
    }

    private void sendRemovedResult(Miuiface face, int remaining) {
        if (this.mRemovalCallback != null) {
            if (face == null || this.mRemovalMiuiface == null) {
                BiometricLoggerImpl.d(TAG, "Received MSG_REMOVED, but face or mRemovalMiuiface is null, ");
                return;
            }

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("sendRemovedResult faceId:");
            stringBuilder.append(face.getMiuifaceId());
            stringBuilder.append("  remaining:");
            stringBuilder.append(remaining);
            BiometricLoggerImpl.d(TAG, stringBuilder.toString());
            int faceId = face.getMiuifaceId();
            int reqFaceId = this.mRemovalMiuiface.getMiuifaceId();
            if (reqFaceId == 0 || faceId == 0 || faceId == reqFaceId) {
                this.mRemovalCallback.onRemovalSucceeded(face, remaining);
                return;
            }

            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("Face id didn't match: ");
            stringBuilder2.append(faceId);
            stringBuilder2.append(" != ");
            stringBuilder2.append(reqFaceId);
            BiometricLoggerImpl.d(TAG, stringBuilder2.toString());
        }
    }

    private void sendErrorResult(long deviceId, int errMsgId, int vendorCode) {
        String errorMsg = MiuiCodeToString.getErrorString(errMsgId, vendorCode);//getMessageInfo(errMsgId);
        EnrollmentCallback enrollmentCallback = this.mEnrollmentCallback;
        if (enrollmentCallback != null) {
            enrollmentCallback.onEnrollmentError(errMsgId, errorMsg);
            return;
        }
        AuthenticationCallback authenticationCallback = this.mAuthenticationCallback;
        if (authenticationCallback != null) {
            authenticationCallback.onAuthenticationError(errMsgId, errorMsg);
            return;
        }
        RemovalCallback removalCallback = this.mRemovalCallback;
        if (removalCallback != null) {
            removalCallback.onRemovalError(this.mRemovalMiuiface, errMsgId, errorMsg);
        }
    }

    private void sendEnrollResult(Miuiface face, int remaining) {
        EnrollmentCallback enrollmentCallback = this.mEnrollmentCallback;
        if (enrollmentCallback != null) {
            enrollmentCallback.onEnrollmentProgress(remaining, face.getMiuifaceId());
        }
    }

    private void sendAuthenticatedSucceeded(Miuiface face, int userId) {
        AuthenticationCallback authenticationCallback = this.mAuthenticationCallback;
        if (authenticationCallback != null) {
            authenticationCallback.onAuthenticationSucceeded(face);
        }
    }

    private void sendAuthenticatedFailed() {
        AuthenticationCallback authenticationCallback = this.mAuthenticationCallback;
        if (authenticationCallback != null) {
            authenticationCallback.onAuthenticationFailed();
        }
    }

    private void sendLockoutReset() {
        LockoutResetCallback lockoutResetCallback = this.mLockoutResetCallback;
        if (lockoutResetCallback != null) {
            lockoutResetCallback.onLockoutReset();
        }
    }

    private void sendAcquiredResult(long deviceId, int clientInfo, int vendorCode) {
        String msg = MiuiCodeToString.getAcquiredString(clientInfo, vendorCode);//getMessageInfo(clientInfo);
        EnrollmentCallback enrollmentCallback = this.mEnrollmentCallback;
        if (enrollmentCallback != null) {
            enrollmentCallback.onEnrollmentHelp(clientInfo, msg);
            return;
        }
        AuthenticationCallback authenticationCallback = this.mAuthenticationCallback;
        if (authenticationCallback != null) {
            authenticationCallback.onAuthenticationHelp(clientInfo, msg);
        }
    }

    private String getMessageInfo(int msgId) {
        String msg = "define by client";
        if (msgId == 1000) {
            return "Failed to open camera";
        }
        if (msgId == 1001) {
            return "Camera opened successfully";
        }
        if (msgId == 2000) {
            return "Cancel success";
        }
        if (msgId == 2100) {
            return "binder Call exception";
        }
        switch (msgId) {
            case 1:
                return "Invalid parameter";
            case 2:
                return "Handler Incorrect";
            case 3:
                return "Unlock failure (internal error)";
            case 4:
                return "Incoming data quality is not good";
            case 5:
                return "No face detected";
            case 6:
                return "Face is too small";
            case 7:
                return "Face too big";
            case 8:
                return "Face left";
            case 9:
                return "Face up";
            case 10:
                return "Face right";
            case 11:
                return "Face down";
            case 12:
                return "Comparison failed";
            case 13:
                return "Live attack warning";
            case 14:
                return "Vitality test failed";
            case 15:
                return "Turn left";
            case 16:
                return "Look up";
            case 17:
                return "Turn right";
            case 18:
                return "Look down";
            case 19:
                return "Continue to call incoming data";
            case 20:
                return "Picture is blurred";
            case 21:
                return "Eye occlusion";
            case 22:
                return "Eyes closed";
            case 23:
                return "Mouth occlusion";
            case 24:
                return "Handling Feature read exception";
            case 25:
                return "Feature version error";
            case 26:
                return "Bad light";
            case 27:
                return "Multiple faces";
            case 28:
                return "Blurred face";
            case 29:
                return "Incomplete face";
            case 30:
                return "The light is too dark";
            case 31:
                return "The light is too bright";
            case 32:
                return "Yin Yang face";
            default:
                if (!DEBUG) {
                    return msg;
                }

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("default msgId: ");
                stringBuilder.append(msgId);
                BiometricLoggerImpl.d(TAG, stringBuilder.toString());
                return msg;
        }
    }

    private class ClientHandler extends Handler {
        private ClientHandler(Context context) {
            super(context.getMainLooper());
        }

        private ClientHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (MiuiFaceManagerImpl.DEBUG) {
                String TAG = MiuiFaceManagerImpl.TAG;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(" handleMessage  callback what:");
                stringBuilder.append(msg.what);
                BiometricLoggerImpl.d(TAG, stringBuilder.toString());
            }
            int i = msg.what;
            if (i == 261) {
                MiuiFaceManagerImpl.this.sendLockoutReset();
            } else if (i != 301) {
                switch (i) {
                    case 201:
                        MiuiFaceManagerImpl.this.sendEnrollResult((Miuiface) msg.obj, msg.arg1);
                        return;
                    case 202:
                        MiuiFaceManagerImpl.this.sendAcquiredResult((Long) msg.obj, msg.arg1, msg.arg2);
                        return;
                    case 203:
                        MiuiFaceManagerImpl.this.sendAuthenticatedSucceeded((Miuiface) msg.obj, msg.arg1);
                        return;
                    case 204:
                        MiuiFaceManagerImpl.this.sendAuthenticatedFailed();
                        return;
                    case 205:
                        MiuiFaceManagerImpl.this.sendErrorResult(((Long) msg.obj).longValue(), msg.arg1, msg.arg2);
                        return;
                    case 206:
                        MiuiFaceManagerImpl.this.sendRemovedResult((Miuiface) msg.obj, msg.arg1);
                        return;
                    default:
                        return;
                }
            }
        }
    }

    private class OnAuthenticationCancelListener implements OnCancelListener {
        private OnAuthenticationCancelListener() {
        }

        public void onCancel() {
            MiuiFaceManagerImpl.this.cancelAuthentication();
        }
    }

    private class OnEnrollCancelListener implements OnCancelListener {
        private OnEnrollCancelListener() {
        }

        public void onCancel() {
            MiuiFaceManagerImpl.this.cancelEnrollment();
        }
    }
}
