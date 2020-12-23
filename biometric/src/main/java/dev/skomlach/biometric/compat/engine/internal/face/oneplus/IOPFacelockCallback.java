package dev.skomlach.biometric.compat.engine.internal.face.oneplus;

import android.os.RemoteException;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface IOPFacelockCallback {

    void onBeginRecognize(int faceId) throws RemoteException;

    void onCompared(int faceId, int userId, int result, int compareTimeMillis, int score) throws RemoteException;

    void onEndRecognize(int faceId, int userId, int result) throws RemoteException;
}