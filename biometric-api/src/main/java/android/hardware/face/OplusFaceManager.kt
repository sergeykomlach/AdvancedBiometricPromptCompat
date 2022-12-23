package android.hardware.face

import android.content.Context
import android.hardware.biometrics.CryptoObject
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicReference

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
class OplusFaceManager(context: Context) {

    companion object {
        const val TAG = "OplusFaceManager"
        private val IOplusFaceManagerSingleton =
            AtomicReference<IOplusFaceManager?>(null) //IOplusFaceManager.Stub.asInterface(ServiceManager.getService("face").getExtension()));
        val service: IOplusFaceManager?
            get() = IOplusFaceManagerSingleton.get()
    }

    /* access modifiers changed from: private */
    var mClientCallback: OplusAuthenticationCallback? = null
    var mFaceAuthenticationCallback: FaceManager.AuthenticationCallback =
        object : FaceManager.AuthenticationCallback() {
            override fun onAuthenticationAcquired(i: Int) {
                mClientCallback?.onAuthenticationAcquired(i)
            }

            override fun onAuthenticationError(i: Int, charSequence: CharSequence?) {
                mClientCallback?.onAuthenticationError(i, charSequence)
            }

            override fun onAuthenticationFailed() {
                mClientCallback?.onAuthenticationFailed()
            }

            override fun onAuthenticationHelp(i: Int, charSequence: CharSequence?) {
                mClientCallback?.onAuthenticationHelp(i, charSequence)
            }

            override fun onAuthenticationSucceeded(authenticationResult: FaceManager.AuthenticationResult?) {
                mClientCallback?.onAuthenticationSucceeded()
            }
        }
    private val mFaceManager: FaceManager

    init {
        mFaceManager = context.getSystemService(FaceManager::class.java) as FaceManager
    }

    /* access modifiers changed from: private */
    fun cancelAONAuthentication(cryptoObject: CryptoObject?) {
        Log.d(TAG, "OplusFaceManager#cancelAONAuthentication")
    }

    fun authenticateAON(
        cryptoObject: CryptoObject?,
        cancellationSignal: CancellationSignal?,
        i: Int,
        oplusAuthenticationCallback: OplusAuthenticationCallback?,
        i2: Int,
        bArr: ByteArray?,
        handler: Handler?
    ) {
        mClientCallback = oplusAuthenticationCallback
        mFaceManager.authenticate(
            cryptoObject,
            cancellationSignal,
            mFaceAuthenticationCallback,
            handler,
            i2,
            false
        )
    }

    val faceProcessMemory: Int
        get() = try {
            service?.faceProcessMemory ?: -1
        } catch (e: RemoteException) {
            Log.e(TAG, "getFaceProcessMemory : $e")
            -1
        } catch (e2: Exception) {
            Log.e(TAG, Log.getStackTraceString(e2))
            -1
        }
    val failedAttempts: Int
        get() = try {
            service?.failedAttempts ?: -1
        } catch (e: RemoteException) {
            Log.e(TAG, "getFailedAttempts : $e")
            -1
        } catch (e2: Exception) {
            Log.e(TAG, Log.getStackTraceString(e2))
            -1
        }

    fun getLockoutAttemptDeadline(i: Int): Long {
        return try {
            service?.getLockoutAttemptDeadline(i) ?: -1
        } catch (e: RemoteException) {
            Log.e(TAG, "getLockoutAttemptDeadline : $e")
            -1
        } catch (e2: Exception) {
            Log.e(TAG, Log.getStackTraceString(e2))
            -1
        }
    }

    fun regsiterFaceCmdCallback(faceCommandCallback: FaceCommandCallback): Int {
        return try {
            service?.regsiterFaceCmdCallback(object : IFaceCommandCallback.Stub() {
                override fun onFaceCmd(i: Int, bArr: ByteArray) {
                    faceCommandCallback.onFaceCmd(i, bArr)
                }
            }) ?: -1
        } catch (e: RemoteException) {
            Log.e(TAG, "regsiterFaceCmdCallback : $e")
            -1
        } catch (e2: Exception) {
            Log.e(TAG, Log.getStackTraceString(e2))
            -1
        }
    }

    fun resetFaceDaemon() {
        try {
            service?.resetFaceDaemon()
        } catch (e: RemoteException) {
            Log.e(TAG, "resetFaceDaemon : $e")
        } catch (e2: Exception) {
            Log.e(TAG, Log.getStackTraceString(e2))
        }
    }

    fun sendFaceCmd(i: Int, i2: Int, bArr: ByteArray?): Int {
        return try {
            service?.sendFaceCmd(i, i2, bArr) ?: -1
        } catch (e: RemoteException) {
            Log.e(TAG, "Remote exception in sendFaceCmd(): $e")
            -1
        } catch (e2: Exception) {
            Log.e(TAG, Log.getStackTraceString(e2))
            -1
        }
    }

    fun unregsiterFaceCmdCallback(faceCommandCallback: FaceCommandCallback): Int {
        return try {
            service?.unregsiterFaceCmdCallback(object : IFaceCommandCallback.Stub() {
                override fun onFaceCmd(i: Int, bArr: ByteArray) {
                    faceCommandCallback.onFaceCmd(i, bArr)
                }
            }) ?: -1
        } catch (e: RemoteException) {
            Log.e(TAG, "unregsiterFaceCmdCallback : $e")
            -1
        } catch (e2: Exception) {
            Log.e(TAG, Log.getStackTraceString(e2))
            -1
        }
    }

    interface FaceCommandCallback {
        fun onFaceCmd(i: Int, bArr: ByteArray?)
    }

    protected inner class OnAONAuthenticationCancelListener internal constructor(private val mCrypto: CryptoObject) :
        CancellationSignal.OnCancelListener {
        override fun onCancel() {
            cancelAONAuthentication(mCrypto)
        }
    }

    abstract class OplusAuthenticationCallback {
        fun onAuthenticationAcquired(i: Int) {}
        fun onAuthenticationError(i: Int, charSequence: CharSequence?) {}
        fun onAuthenticationFailed() {}
        fun onAuthenticationHelp(i: Int, charSequence: CharSequence?) {}
        fun onAuthenticationSucceeded() {}
    }

}