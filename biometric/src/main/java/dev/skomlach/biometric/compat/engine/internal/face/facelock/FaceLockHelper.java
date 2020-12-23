package dev.skomlach.biometric.compat.engine.internal.face.facelock;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.lang.reflect.InvocationTargetException;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class FaceLockHelper {

    public static final int FACELOCK_UNABLE_TO_BIND = 1;
    public static final int FACELOCK_API_NOT_FOUND = 2;
    public static final int FACELOCK_CANNT_START = 3;
    public static final int FACELOCK_NOT_SETUP = 4;
    public static final int FACELOCK_CANCELED = 5;
    public static final int FACELOCK_NO_FACE_FOUND = 6;
    public static final int FACELOCK_FAILED_ATTEMPT = 7;
    public static final int FACELOCK_TIMEOUT = 8;
    protected static final String TAG = "FaceIdHelper";
    private final FaceLockInterface faceLockInterface;
    private Context context = null;
    private View targetView = null;
    private FaceLock mFaceLock;
    private boolean mFaceLockServiceRunning;
    private boolean mBoundToFaceLockService;
    private IFaceLockCallback mCallback;
    private ServiceConnection mServiceConnection;

    protected FaceLockHelper(Context context, FaceLockInterface faceLockInterface) {
        this.context = context;
        this.faceLockInterface = faceLockInterface;
    }

    public static String getMessage(int code) {

        switch (code) {
            case FACELOCK_UNABLE_TO_BIND:
                return "Unable to bind to FaceId";
            case FACELOCK_API_NOT_FOUND:
                return TAG + ". not found";
            case FACELOCK_CANNT_START:
                return "Can not start FaceId";
            case FACELOCK_NOT_SETUP:
                return TAG + ". not set up";
            case FACELOCK_CANCELED:
                return TAG + ". canceled";
            case FACELOCK_NO_FACE_FOUND:
                return "No face found";
            case FACELOCK_FAILED_ATTEMPT:
                return "Failed attempt";
            case FACELOCK_TIMEOUT:
                return "Timeout";
            default:
                return "Unknown error (" + code + ")";
        }
    }

    synchronized void destroy() {
        targetView = null;
        mFaceLock = null;
        mCallback = null;
        mServiceConnection = null;
    }

    synchronized void initFacelock() {
        BiometricLoggerImpl.d(TAG + ".initFacelock");
        try {

            if (mFaceLock == null) {
                mFaceLock = new FaceLock(context);
            }
            mCallback = new IFaceLockCallback() {

                private boolean mStarted = false;

                @Override
                public void unlock() throws RemoteException {
                    BiometricLoggerImpl.d(TAG + ".IFaceIdCallback.unlock");
                    stopFaceLock();
                    BiometricLoggerImpl.d(TAG + ".IFaceIdCallback.exec onAuthorized");
                    faceLockInterface.onAuthorized();
                    mStarted = false;
                }

                @Override
                public void cancel() throws RemoteException {
                    BiometricLoggerImpl.d(TAG + ".IFaceIdCallback.cancel");
                    if (mBoundToFaceLockService) {
                        if (mStarted) {
                            BiometricLoggerImpl.d(TAG + ".timeout");
                            faceLockInterface.onError(FACELOCK_TIMEOUT, getMessage(FACELOCK_TIMEOUT));
                            stopFaceLock();
                            mStarted = false;
                        } else {
                            BiometricLoggerImpl.d(TAG + ".canceled");
                            faceLockInterface.onError(FACELOCK_CANCELED, getMessage(FACELOCK_CANCELED));
                            stopFaceLock();
                            mStarted = false;
                        }
                    }
                }

                @Override
                public void reportFailedAttempt() throws RemoteException {
                    BiometricLoggerImpl.d(TAG + ".IFaceIdCallback.reportFailedAttempt");
                    faceLockInterface.onError(FACELOCK_FAILED_ATTEMPT, getMessage(FACELOCK_FAILED_ATTEMPT));
                }

                @Override
                public void exposeFallback() throws RemoteException {
                    BiometricLoggerImpl.d(TAG + ".IFaceIdCallback.exposeFallback");
                    mStarted = true;
                }

                @Override
                public void pokeWakelock() throws RemoteException {
                    BiometricLoggerImpl.d(TAG + ".IFaceIdCallback.pokeWakelock");
                    mStarted = true;
                    try {
                        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                        PowerManager.WakeLock screenLock = pm
                                .newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK,
                                        getClass().getName());
                        screenLock.acquire(25000L);

                        if (screenLock.isHeld()) {
                            screenLock.release();
                        }
                    } catch (Throwable e) {
                        BiometricLoggerImpl.e(e);
                    }
                }
            };
            mServiceConnection = new ServiceConnection() {

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    BiometricLoggerImpl.d(TAG + ".ServiceConnection.onServiceDisconnected");
                    try {
                        mFaceLock.unregisterCallback(mCallback);
                    } catch (Exception e) {

                        if (e instanceof InvocationTargetException) {
                            BiometricLoggerImpl.e(e, TAG + ("Caught invocation exception registering callback: "
                                    + ((InvocationTargetException) e)
                                    .getTargetException()));
                        } else {
                            BiometricLoggerImpl.e(e, TAG + ("Caught exception registering callback: " + e.toString()));
                        }
                    }
                    mFaceLock = null;
                    mFaceLockServiceRunning = false;
                    mBoundToFaceLockService = false;
                    faceLockInterface.onDisconnected();
                }

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    BiometricLoggerImpl.d(TAG + ".ServiceConnection.onServiceConnected");
                    mBoundToFaceLockService = true;
                    try {
                        mFaceLock.registerCallback(mCallback);
                    } catch (Exception e) {

                        if (e instanceof InvocationTargetException) {
                            BiometricLoggerImpl.e(e, TAG + ("Caught invocation exception registering callback: "
                                    + ((InvocationTargetException) e)
                                    .getTargetException()));
                        } else {
                            BiometricLoggerImpl.e(e, TAG + ("Caught exception registering callback: " + e.toString()));
                        }
                        mFaceLock = null;
                        mBoundToFaceLockService = false;
                    }
                    faceLockInterface.onConnected();
                }
            };

            if (!mBoundToFaceLockService) {
                if (!mFaceLock.bind(mServiceConnection)) {
                    this.faceLockInterface
                            .onError(FACELOCK_UNABLE_TO_BIND, getMessage(FACELOCK_UNABLE_TO_BIND));
                } else {
                    BiometricLoggerImpl.d(TAG + ".Binded, waiting for connection");
                    return;
                }
            } else {
                BiometricLoggerImpl.d(TAG + ".Already mBoundToFaceLockService");
            }
        } catch (Exception e) {
            BiometricLoggerImpl.e(e, TAG + ("Caught exception creating FaceId: " + e.toString()));
            this.faceLockInterface.onError(FACELOCK_API_NOT_FOUND, getMessage(FACELOCK_API_NOT_FOUND));
        }
        BiometricLoggerImpl.d(TAG + ".init failed");
    }

    // Tells the FaceId service to stop displaying its UI and stop recognition
    synchronized void stopFaceLock() {
        BiometricLoggerImpl.d(TAG + ".stopFaceLock");
        if (mFaceLockServiceRunning) {
            try {
                BiometricLoggerImpl.d(TAG + ".Stopping FaceId");
                mFaceLock.stopUi();
            } catch (Exception e) {
                BiometricLoggerImpl.e(e, TAG + ("Caught exception stopping FaceId: " + e.toString()));
            }
            mFaceLockServiceRunning = false;
        }

        if (mBoundToFaceLockService) {
            mFaceLock.unbind();
            BiometricLoggerImpl.d(TAG + ".FaceId.unbind()");
            mBoundToFaceLockService = false;
        }
    }

    // Tells the FaceId service to start displaying its UI and perform recognition
    private void startFaceAuth(View targetView) {
        BiometricLoggerImpl.d(TAG + ".startFaceLock");

        if (!mFaceLockServiceRunning) {
            try {
                BiometricLoggerImpl.d(TAG + ".Starting FaceId");
                Rect rect = new Rect();
                targetView.getGlobalVisibleRect(rect);
                BiometricLoggerImpl.d(TAG + (" rect: " + rect));
                mFaceLock.startUi(targetView.getWindowToken(),
                        rect.left, rect.top,
                        rect.width(), rect.height()
                );
            } catch (Exception e) {
                BiometricLoggerImpl.e(e, TAG + ("Caught exception starting FaceId: " + e.getMessage()));
                faceLockInterface.onError(FACELOCK_CANNT_START, getMessage(FACELOCK_CANNT_START));
                return;
            }
            mFaceLockServiceRunning = true;
        } else {
            BiometricLoggerImpl.e(TAG + ".startFaceLock() attempted while running");
        }
    }

    synchronized void startFaceLockWithUi(@Nullable View view) {
        BiometricLoggerImpl.d(TAG + ".startFaceLockWithUi");

        this.targetView = view;

        if (targetView != null) {
            startFaceAuth(targetView);
        }
    }
}