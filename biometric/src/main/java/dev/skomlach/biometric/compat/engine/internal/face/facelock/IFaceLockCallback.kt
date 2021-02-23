package dev.skomlach.biometric.compat.engine.internal.face.facelock;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface IFaceLockCallback {

    void unlock() throws android.os.RemoteException;

    void cancel() throws android.os.RemoteException;

    void reportFailedAttempt() throws android.os.RemoteException;

    void exposeFallback() throws android.os.RemoteException;

    void pokeWakelock() throws android.os.RemoteException;
}