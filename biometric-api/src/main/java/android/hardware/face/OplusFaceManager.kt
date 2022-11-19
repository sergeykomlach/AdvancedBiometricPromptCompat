package android.hardware.face;

import android.content.Context;
import android.hardware.biometrics.CryptoObject;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.concurrent.atomic.AtomicReference;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class OplusFaceManager {
    public static final String TAG = "OplusFaceManager";
    private static final AtomicReference<IOplusFaceManager> IOplusFaceManagerSingleton =
            new AtomicReference<IOplusFaceManager>(null);//IOplusFaceManager.Stub.asInterface(ServiceManager.getService("face").getExtension()));
    /* access modifiers changed from: private */
    public OplusAuthenticationCallback mClientCallback;
    FaceManager.AuthenticationCallback mFaceAuthenticationCallback = new FaceManager.AuthenticationCallback() {
        @Override
        public void onAuthenticationAcquired(int i) {
            OplusFaceManager.this.mClientCallback.onAuthenticationAcquired(i);
        }
        @Override
        public void onAuthenticationError(int i, CharSequence charSequence) {
            OplusFaceManager.this.mClientCallback.onAuthenticationError(i, charSequence);
        }
        @Override
        public void onAuthenticationFailed() {
            OplusFaceManager.this.mClientCallback.onAuthenticationFailed();
        }
        @Override
        public void onAuthenticationHelp(int i, CharSequence charSequence) {
            OplusFaceManager.this.mClientCallback.onAuthenticationHelp(i, charSequence);
        }
        @Override
        public void onAuthenticationSucceeded(FaceManager.AuthenticationResult authenticationResult) {
            OplusFaceManager.this.mClientCallback.onAuthenticationSucceeded();
        }
    };

    private final FaceManager mFaceManager;

    public OplusFaceManager(Context context) {
        this.mFaceManager = (FaceManager) context.getSystemService(FaceManager.class);
    }

    public static IOplusFaceManager getService() {
        return (IOplusFaceManager) IOplusFaceManagerSingleton.get();
    }

    /* access modifiers changed from: private */
    public void cancelAONAuthentication(CryptoObject cryptoObject) {
        Log.d(TAG, "OplusFaceManager#cancelAONAuthentication");
    }

    public void authenticateAON(CryptoObject cryptoObject, CancellationSignal cancellationSignal, int i, OplusAuthenticationCallback oplusAuthenticationCallback, int i2, byte[] bArr, Handler handler) {
        this.mClientCallback = oplusAuthenticationCallback;
        this.mFaceManager.authenticate(cryptoObject, cancellationSignal, this.mFaceAuthenticationCallback, handler, i2, false);
    }

    public int getFaceProcessMemory() {
        try {
            return getService().getFaceProcessMemory();
        } catch (RemoteException e) {
            Log.e(TAG, "getFaceProcessMemory : " + e);
            return -1;
        } catch (Exception e2) {
            Log.e(TAG, Log.getStackTraceString(e2));
            return -1;
        }
    }

    public int getFailedAttempts() {
        try {
            return getService().getFailedAttempts();
        } catch (RemoteException e) {
            Log.e(TAG, "getFailedAttempts : " + e);
            return -1;
        } catch (Exception e2) {
            Log.e(TAG, Log.getStackTraceString(e2));
            return -1;
        }
    }

    public long getLockoutAttemptDeadline(int i) {
        try {
            return getService().getLockoutAttemptDeadline(i);
        } catch (RemoteException e) {
            Log.e(TAG, "getLockoutAttemptDeadline : " + e);
            return -1;
        } catch (Exception e2) {
            Log.e(TAG, Log.getStackTraceString(e2));
            return -1;
        }
    }

    public int regsiterFaceCmdCallback(final FaceCommandCallback faceCommandCallback) {
        try {
            return getService().regsiterFaceCmdCallback(new IFaceCommandCallback.Stub() {
                @Override
                public void onFaceCmd(int i, byte[] bArr) {
                    faceCommandCallback.onFaceCmd(i, bArr);
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "regsiterFaceCmdCallback : " + e);
            return -1;
        } catch (Exception e2) {
            Log.e(TAG, Log.getStackTraceString(e2));
            return -1;
        }
    }

    public void resetFaceDaemon() {
        try {
            getService().resetFaceDaemon();
        } catch (RemoteException e) {
            Log.e(TAG, "resetFaceDaemon : " + e);
        } catch (Exception e2) {
            Log.e(TAG, Log.getStackTraceString(e2));
        }
    }

    public int sendFaceCmd(int i, int i2, byte[] bArr) {
        try {
            return getService().sendFaceCmd(i, i2, bArr);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception in sendFaceCmd(): " + e);
            return -1;
        } catch (Exception e2) {
            Log.e(TAG, Log.getStackTraceString(e2));
            return -1;
        }
    }

    public int unregsiterFaceCmdCallback(final FaceCommandCallback faceCommandCallback) {
        try {
            return getService().unregsiterFaceCmdCallback(new IFaceCommandCallback.Stub() {
                @Override
                public void onFaceCmd(int i, byte[] bArr) {
                    faceCommandCallback.onFaceCmd(i, bArr);
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "unregsiterFaceCmdCallback : " + e);
            return -1;
        } catch (Exception e2) {
            Log.e(TAG, Log.getStackTraceString(e2));
            return -1;
        }
    }

    public interface FaceCommandCallback {
        void onFaceCmd(int i, byte[] bArr);
    }

    protected class OnAONAuthenticationCancelListener implements CancellationSignal.OnCancelListener {
        private final CryptoObject mCrypto;

        OnAONAuthenticationCancelListener(CryptoObject cryptoObject) {
            this.mCrypto = cryptoObject;
        }
        @Override
        public void onCancel() {
            OplusFaceManager.this.cancelAONAuthentication(this.mCrypto);
        }
    }

    public abstract static class OplusAuthenticationCallback {
        public OplusAuthenticationCallback() {
        }

        public void onAuthenticationAcquired(int i) {
        }

        public void onAuthenticationError(int i, CharSequence charSequence) {
        }

        public void onAuthenticationFailed() {
        }

        public void onAuthenticationHelp(int i, CharSequence charSequence) {
        }

        public void onAuthenticationSucceeded() {
        }
    }
}
