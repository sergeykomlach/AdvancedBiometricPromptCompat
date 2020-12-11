package dev.skomlach.biometric.compat.impl;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import java.util.List;

import dev.skomlach.biometric.compat.BiometricPromptCompat;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface IBiometricPromptImpl {

    void authenticate(@NonNull BiometricPromptCompat.Result callback);

    void cancelAuthenticate();

    boolean cancelAuthenticateBecauseOnPause();

    boolean isNightMode();

    BiometricPromptCompat.Builder getBuilder();

    List<String> getUsedPermissions();
}
