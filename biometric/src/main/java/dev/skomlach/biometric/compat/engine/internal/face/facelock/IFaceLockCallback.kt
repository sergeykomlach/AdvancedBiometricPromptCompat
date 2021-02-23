package dev.skomlach.biometric.compat.engine.internal.face.facelock

import android.os.RemoteException
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface IFaceLockCallback {
    @Throws(RemoteException::class)
    fun unlock()

    @Throws(RemoteException::class)
    fun cancel()

    @Throws(RemoteException::class)
    fun reportFailedAttempt()

    @Throws(RemoteException::class)
    fun exposeFallback()

    @Throws(RemoteException::class)
    fun pokeWakelock()
}