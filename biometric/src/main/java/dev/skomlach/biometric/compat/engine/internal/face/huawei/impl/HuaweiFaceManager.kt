package dev.skomlach.biometric.compat.engine.internal.face.huawei.impl

abstract class HuaweiFaceManager {
    abstract fun authenticate(reqId: Int, flag: Int, authenticatorCallback: AuthenticatorCallback?)
    abstract fun cancel(i: Int): Int
    abstract val version: Int
    abstract val isHardwareDetected: Boolean
    abstract fun hasEnrolledTemplates(): Boolean
    abstract class AuthenticatorCallback {
        open fun onAuthenticationError(errorCode: Int) {}
        open fun onAuthenticationStatus(status: Int) {}
        open fun onAuthenticationSucceeded() {}
        open fun onAuthenticationFailed() {}
    }
}