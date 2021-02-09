package dev.skomlach.biometric.compat.impl;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface AuthCallback {
    void startAuth();

    void stopAuth();

    void cancelAuth();

    void onUiOpened();

    void onUiClosed();
}
