package android.hardware.face

import android.os.CancellationSignal
import android.os.Handler
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

class OppoMirrorFaceManager {
    fun authenticate(
        crypto: CryptoObject?,
        cancel: CancellationSignal?,
        flags: Int,
        callback: AuthenticationCallback?,
        handler: Handler?
    ) {
    }

    fun authenticate(
        crypto: CryptoObject?,
        cancel: CancellationSignal?,
        flags: Int,
        callback: AuthenticationCallback?,
        handler: Handler?,
        userId: Int
    ) {
        throw IllegalArgumentException("Must supply an authentication callback")
    }

    class CryptoObject {
        val signature: Signature?
            get() = null
        val cipher: Cipher?
            get() = null
        val mac: Mac?
            get() = null
    }

    fun getEnrolledFaces(userId: Int): List<Face>? {
        return null
    }

    val enrolledFaces: List<Face>?
        get() = getEnrolledFaces(0)

    fun hasEnrolledTemplates(): Boolean {
        return false
    }

    fun hasEnrolledTemplates(userId: Int): Boolean {
        return false
    }

    val isHardwareDetected: Boolean
        get() = false

    class AuthenticationResult(val cryptoObject: CryptoObject, val face: Face, val userId: Int)
    abstract class AuthenticationCallback {
        open fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {}
        open fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {}
        open fun onAuthenticationSucceeded(result: AuthenticationResult?) {}
        open fun onAuthenticationFailed() {}
        fun onAuthenticationAcquired(acquireInfo: Int) {}
        fun onProgressChanged(progressInfo: Int) {}
    }
}