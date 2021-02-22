package com.samsung.android.camera.iris

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.util.SparseArray
import android.view.View
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

class SemIrisManager {
    fun authenticate(
        cryptoObject: CryptoObject?,
        cancellationSignal: CancellationSignal?,
        i: Int,
        authenticationCallback: AuthenticationCallback?,
        handler: Handler?,
        i2: Int,
        bundle: Bundle?,
        view: View?
    ) {
    }

    fun authenticate(
        cryptoObject: CryptoObject?,
        cancellationSignal: CancellationSignal?,
        i: Int,
        authenticationCallback: AuthenticationCallback?,
        handler: Handler?,
        view: View?
    ) {
    }

    fun authenticate(
        cryptoObject: CryptoObject?,
        cancellationSignal: CancellationSignal?,
        i: Int,
        authenticationCallback: AuthenticationCallback?,
        handler: Handler?,
        view: View?,
        i2: Int
    ) {
        authenticate(
            cryptoObject,
            cancellationSignal,
            i,
            authenticationCallback,
            handler,
            i2,
            null,
            view
        )
    }

    val enrolledIrisUniqueID: SparseArray<*>?
        get() = null
    val enrolledIrises: List<Iris>?
        get() = null

    fun getEnrolledIrises(i: Int): List<Iris>? {
        return null
    }

    fun hasEnrolledIrises(): Boolean {
        return false
    }

    fun hasEnrolledIrises(i: Int): Boolean {
        return false
    }

    val isHardwareDetected: Boolean
        get() = false

    abstract class AuthenticationCallback {
        fun onAuthenticationAcquired(i: Int) {}
        open fun onAuthenticationError(i: Int, charSequence: CharSequence?) {}
        open fun onAuthenticationFailed() {}
        open fun onAuthenticationHelp(i: Int, charSequence: CharSequence?) {}
        open fun onAuthenticationSucceeded(authenticationResult: AuthenticationResult?) {}
        fun onIRImage(bArr: ByteArray?, i: Int, i2: Int) {}
    }

    class AuthenticationResult(val cryptoObject: CryptoObject, val iris: Iris)
    class CryptoObject {
        private val mCrypto: Any
        val fidoRequestData: ByteArray
        var fidoResultData: ByteArray? = null
            private set

        constructor(signature: Signature, bArr: ByteArray) {
            mCrypto = signature
            fidoRequestData = bArr
        }

        constructor(cipher: Cipher, bArr: ByteArray) {
            mCrypto = cipher
            fidoRequestData = bArr
        }

        constructor(mac: Mac, bArr: ByteArray) {
            mCrypto = mac
            fidoRequestData = bArr
        }

        val cipher: Cipher?
            get() = if (mCrypto is Cipher) mCrypto else null

        private fun setFidoResultData(bArr: ByteArray) {
            fidoResultData = bArr
        }

        val mac: Mac?
            get() = if (mCrypto is Mac) mCrypto else null
        val opId: Long
            get() = 0
        val signature: Signature?
            get() = if (mCrypto is Signature) mCrypto else null
    }

    companion object {
        @Synchronized
        @JvmStatic
        fun getSemIrisManager(context: Context?): SemIrisManager? {
            return null
        }
    }
}