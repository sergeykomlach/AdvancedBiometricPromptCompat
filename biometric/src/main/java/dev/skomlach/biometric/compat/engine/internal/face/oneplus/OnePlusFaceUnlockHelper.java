package dev.skomlach.biometric.compat.engine.internal.face.oneplus;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class OnePlusFaceUnlockHelper {

    public static final int FACEUNLOCK_UNABLE_TO_BIND = -1;
    public static final int FACEUNLOCK_API_NOT_FOUND = -2;
    public static final int FACEUNLOCK_CANNT_START = -3;

    public static final int FACEUNLOCK_TIMEOUT = 2;
    public static final int FACEUNLOCK_CAMERA_ERROR = 3;
    public static final int FACEUNLOCK_NO_PERMISSION = 4;
    public static final int FACEUNLOCK_FAILED_ATTEMPT = 5;

    protected static final String TAG = OnePlusFaceUnlockHelper.class.getSimpleName();
    private final FaceLockInterface onePlusFaceUnlockInterface;
    private final boolean hasHardware;
    private Context context = null;
    private OnePlusFaceUnlock mOnePlusFaceUnlock;
    private boolean mOnePlusFaceUnlockServiceRunning;
    private boolean mBoundToOnePlusFaceUnlockService;
    private IOPFacelockCallback mCallback;
    private ServiceConnection mServiceConnection;

    protected OnePlusFaceUnlockHelper(Context context, FaceLockInterface onePlusFaceUnlockInterface) {
        this.context = context;
        this.onePlusFaceUnlockInterface = onePlusFaceUnlockInterface;

        try {
            mOnePlusFaceUnlock = new OnePlusFaceUnlock(context);
        } catch (Throwable e) {
            mOnePlusFaceUnlock = null;
        }
        hasHardware = mOnePlusFaceUnlock != null;
    }

    public static String getMessage(int code) {

        switch (code) {
            case FACEUNLOCK_UNABLE_TO_BIND:
                return "Unable to bind to OnePlusFaceUnlock";
            case FACEUNLOCK_API_NOT_FOUND:
                return TAG + ". not found";
            case FACEUNLOCK_CANNT_START:
                return "Can not start OnePlusFaceUnlock";
            case FACEUNLOCK_CAMERA_ERROR:
                return TAG + " camera error";
            case FACEUNLOCK_NO_PERMISSION:
                return TAG + " no permission";
            case FACEUNLOCK_FAILED_ATTEMPT:
                return "Failed attempt";
            case FACEUNLOCK_TIMEOUT:
                return "Timeout";
            default:
                return "Unknown error (" + code + ")";
        }
    }

    public boolean faceUnlockAvailable() {
        return hasHardware;
    }

    synchronized void destroy() {

        mCallback = null;
        mServiceConnection = null;
    }

    synchronized void initFacelock() {
        BiometricLoggerImpl.d(TAG + ".initFacelock");
        try {

            mCallback = new IOPFacelockCallback() {
                @Override
                public void onBeginRecognize(int faceId) throws RemoteException {
                    BiometricLoggerImpl.d(TAG + ".IOnePlusFaceUnlockCallback.onBeginRecognize - " + faceId);
                }

                @Override
                public void onCompared(int faceId, int userId, int result, int compareTimeMillis, int score) throws RemoteException {
                    BiometricLoggerImpl.d(TAG + ".IOnePlusFaceUnlockCallback.onCompared - " + faceId + "/" + userId + "/" + result);
//                    if (result != 0) {
//                        onePlusFaceUnlockInterface.onError(result, getMessage(result));
//                    }
                }

                @Override
                public void onEndRecognize(int faceId, int userId, int result) throws RemoteException {
                    BiometricLoggerImpl.d(TAG + ".IOnePlusFaceUnlockCallback.onEndRecognize - " + faceId + "/" + userId + "/" + result);
                    if (result == 0) {
                        stopFaceLock();
                        BiometricLoggerImpl.d(TAG + ".IOnePlusFaceUnlockCallback.exec onAuthorized");
                        onePlusFaceUnlockInterface.onAuthorized();
                    } else {
                        BiometricLoggerImpl.d(TAG + ".IOnePlusFaceUnlockCallback.unlock");
                        stopFaceLock();
                        BiometricLoggerImpl.d(TAG + ".IOnePlusFaceUnlockCallback.exec onError");
                        onePlusFaceUnlockInterface.onError(result, getMessage(result));
                    }
                }
            };
            mServiceConnection = new ServiceConnection() {

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    BiometricLoggerImpl.d(TAG + ".ServiceConnection.onServiceDisconnected");
                    mOnePlusFaceUnlockServiceRunning = false;
                    mBoundToOnePlusFaceUnlockService = false;
                    onePlusFaceUnlockInterface.onDisconnected();
                }

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    BiometricLoggerImpl.d(TAG + ".ServiceConnection.onServiceConnected");
                    mBoundToOnePlusFaceUnlockService = true;
                    onePlusFaceUnlockInterface.onConnected();
                }
            };

            if (!mBoundToOnePlusFaceUnlockService) {
                if (!mOnePlusFaceUnlock.bind(mServiceConnection)) {
                    this.onePlusFaceUnlockInterface
                            .onError(FACEUNLOCK_UNABLE_TO_BIND, getMessage(FACEUNLOCK_UNABLE_TO_BIND));
                } else {
                    BiometricLoggerImpl.d(TAG + ".Binded, waiting for connection");
                    return;
                }
            } else {
                BiometricLoggerImpl.d(TAG + ".Already mBoundToOnePlusFaceUnlockService");
            }
        } catch (Exception e) {
            BiometricLoggerImpl.e(e, TAG + ("Caught exception creating OnePlusFaceUnlock: " + e.toString()));
            this.onePlusFaceUnlockInterface.onError(FACEUNLOCK_API_NOT_FOUND, getMessage(FACEUNLOCK_API_NOT_FOUND));
        }
        BiometricLoggerImpl.d(TAG + ".init failed");
    }

    // Tells the OnePlusFaceUnlock service to stop displaying its UI and stop recognition
    synchronized void stopFaceLock() {
        BiometricLoggerImpl.d(TAG + ".stopFaceLock");
        if (mOnePlusFaceUnlockServiceRunning) {
            try {
                BiometricLoggerImpl.d(TAG + ".Stopping OnePlusFaceUnlock");
                mOnePlusFaceUnlock.unregisterCallback(mCallback);
                mOnePlusFaceUnlock.stopFaceUnlock();
            } catch (Exception e) {
                BiometricLoggerImpl.e(e, TAG + ("Caught exception stopping OnePlusFaceUnlock: " + e.toString()));
            }
            mOnePlusFaceUnlockServiceRunning = false;
        }

        if (mBoundToOnePlusFaceUnlockService) {
            mOnePlusFaceUnlock.unbind();
            BiometricLoggerImpl.d(TAG + ".OnePlusFaceUnlock.unbind()");
            mBoundToOnePlusFaceUnlockService = false;
        }
    }

    synchronized void startFaceLock() {
        BiometricLoggerImpl.d(TAG + ".startOnePlusFaceUnlockWithUi");

        if (!mOnePlusFaceUnlockServiceRunning) {
            try {
                BiometricLoggerImpl.d(TAG + ".Starting OnePlusFaceUnlock");
                mOnePlusFaceUnlock.registerCallback(mCallback);
                mOnePlusFaceUnlock.startFaceUnlock();
            } catch (Exception e) {
                BiometricLoggerImpl.e(e, TAG + ("Caught exception starting OnePlusFaceUnlock: " + e.getMessage()));
                onePlusFaceUnlockInterface.onError(FACEUNLOCK_CANNT_START, getMessage(FACEUNLOCK_CANNT_START));
                return;
            }
            mOnePlusFaceUnlockServiceRunning = true;
        } else {
            BiometricLoggerImpl.e(TAG + ".startOnePlusFaceUnlock() attempted while running");
        }
    }

    synchronized boolean hasBiometric() {
        try {
            return mOnePlusFaceUnlock.checkState() == 0;
        } catch (Throwable e) {}
        return false;
    }
}