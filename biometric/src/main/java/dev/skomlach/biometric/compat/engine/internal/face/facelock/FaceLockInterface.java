package dev.skomlach.biometric.compat.engine.internal.face.facelock;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface FaceLockInterface {

    void onError(int code, String msg);

    void onAuthorized();

    void onConnected();

    void onDisconnected();
}
