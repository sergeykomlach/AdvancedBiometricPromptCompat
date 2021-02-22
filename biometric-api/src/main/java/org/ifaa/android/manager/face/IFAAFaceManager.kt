package org.ifaa.android.manager.face

abstract class IFAAFaceManager {
    abstract fun authenticate(reqId: Int, flags: Int, authenticatorCallback: AuthenticatorCallback?)
    abstract fun cancel(reqId: Int): Int
    abstract val version: Int

    abstract class AuthenticatorCallback {
        fun onAuthenticationError(errorCode: Int) {}
        fun onAuthenticationStatus(status: Int) {}
        fun onAuthenticationSucceeded() {}
        fun onAuthenticationFailed(errCode: Int) {}
    }
}