package dev.skomlach.biometric.compat.engine;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface BiometricInitListener {
    void initFinished(BiometricMethod method, BiometricModule module);

    void onBiometricReady();
}
