package dev.skomlach.biometric.compat.engine.internal.face.facelock

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface FaceLockInterface {
    fun onError(code: Int, msg: String)
    fun onAuthorized()
    fun onConnected()
    fun onDisconnected()
}