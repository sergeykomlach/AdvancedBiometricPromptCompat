package dev.skomlach.biometric.compat.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface BiometricInitListener {
    void initFinished(@NonNull BiometricMethod method, @Nullable BiometricModule module);

    void onBiometricReady();
}
