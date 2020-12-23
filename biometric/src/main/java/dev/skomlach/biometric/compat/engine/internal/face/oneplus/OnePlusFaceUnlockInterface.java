package dev.skomlach.biometric.compat.engine.internal.face.oneplus;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface OnePlusFaceUnlockInterface {

    void onError(int code, String msg);

    void onAuthorized();

    void onConnected();

    void onDisconnected();
}
