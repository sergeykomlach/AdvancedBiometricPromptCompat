package android.hardware.face

import android.os.CancellationSignal
import android.os.Handler
import android.hardware.biometrics.CryptoObject
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
class OplusFaceManager {
    fun cancelAONAuthentication(cryptoObject: CryptoObject?) {

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

    }

    val faceProcessMemory: Int
        get() = 0
    val failedAttempts: Int
        get() = 0

    fun getLockoutAttemptDeadline(i: Int): Long {
        return 0
    }

    fun regsiterFaceCmdCallback(faceCommandCallback: FaceCommandCallback): Int {
        return 0
    }

    fun resetFaceDaemon() {

    }

    fun sendFaceCmd(i: Int, i2: Int, bArr: ByteArray?): Int {
        return 0
    }

    fun unregsiterFaceCmdCallback(faceCommandCallback: FaceCommandCallback): Int {
        return 0
    }

    interface FaceCommandCallback {
        fun onFaceCmd(i: Int, bArr: ByteArray?)
    }

    abstract class OplusAuthenticationCallback {
        fun onAuthenticationAcquired(i: Int) {}
        open fun onAuthenticationError(i: Int, charSequence: CharSequence?) {}
        open fun onAuthenticationFailed() {}
        open fun onAuthenticationHelp(i: Int, charSequence: CharSequence?) {}
        open fun onAuthenticationSucceeded() {}
    }

}