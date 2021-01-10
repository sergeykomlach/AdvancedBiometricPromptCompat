package dev.skomlach.biometric.compat.impl;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.skomlach.biometric.compat.BiometricPromptCompat;
import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.AuthenticationHelpReason;
import dev.skomlach.biometric.compat.engine.BiometricAuthentication;
import dev.skomlach.biometric.compat.engine.BiometricAuthenticationListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl;
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl;
import dev.skomlach.common.misc.ExecutorHelper;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricPromptGenericImpl implements IBiometricPromptImpl, AuthCallback {

    private final BiometricPromptCompatDialogImpl dialog;
    private final BiometricAuthenticationListener fmAuthCallback
            = new BiometricAuthenticationCallbackImpl();
    private final BiometricPromptCompat.Builder compatBuilder;
    private BiometricPromptCompat.Result callback;

    public BiometricPromptGenericImpl(BiometricPromptCompat.Builder compatBuilder) {

        this.compatBuilder = compatBuilder;
        dialog = new BiometricPromptCompatDialogImpl(compatBuilder, BiometricPromptGenericImpl.this, false);
    }

    @Override
    public void authenticate(@NonNull BiometricPromptCompat.Result callback) {
        if (this.callback == null) {
            this.callback = callback;
        }

        dialog.showDialog();
    }

    @Override
    public void cancelAuthenticate() {

        dialog.dismissDialog();
    }

    @Override
    public boolean isNightMode() {

        return dialog.isNightMode();
    }

    @Override
    public BiometricPromptCompat.Builder getBuilder() {
        return compatBuilder;
    }

    @Override
    public List<String> getUsedPermissions() {
        final Set<String> permission = new HashSet<>();
        List<BiometricMethod> biometricMethodList = new ArrayList<>();
        if (compatBuilder.getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY) {
            biometricMethodList.addAll(BiometricAuthentication.getAvailableBiometricMethods());
        } else {
            for (BiometricMethod m : BiometricAuthentication.getAvailableBiometricMethods()) {
                if (m.getBiometricType() == compatBuilder.getBiometricAuthRequest().getType()) {
                    biometricMethodList.add(m);
                }
            }
        }
        for (BiometricMethod method : biometricMethodList) {
            switch (method) {

                case DUMMY_BIOMETRIC:
                    permission.add("android.permission.CAMERA");
                    break;

                case IRIS_ANDROIDAPI:
                    permission.add("android.permission.USE_IRIS");
                    break;
                case IRIS_SAMSUNG:
                    permission.add("com.samsung.android.camera.iris.permission.USE_IRIS");
                    break;

                case FACELOCK:
                    permission.add("android.permission.WAKE_LOCK");
                    break;
                case FACE_SOTERAPI:
                case FACE_HUAWEI_EMUI_10:
                    permission.add("android.permission.CAMERA");
                    permission.add("android.permission.USE_FACERECOGNITION");
                    break;
                case FACE_ANDROIDAPI:
                    permission.add("android.permission.USE_FACE_AUTHENTICATION");
                    break;
                case FACE_SAMSUNG:
                    permission.add("com.samsung.android.bio.face.permission.USE_FACE");
                    break;
                case FACE_OPPO:
                    permission.add("oppo.permission.USE_FACE");
                    break;
                 //TODO: check permissions
//                case FACE_VIVO: break;
//                case FACE_ONEPLUS: break;


                case FINGERPRINT_API23:
                case FINGERPRINT_SUPPORT:
                    permission.add("android.permission.USE_FINGERPRINT");
                    break;
                case FINGERPRINT_FLYME:
                    permission.add("com.fingerprints.service.ACCESS_FINGERPRINT_MANAGER");
                    break;
                case FINGERPRINT_SAMSUNG:
                    permission.add("com.samsung.android.providers.context.permission.WRITE_USE_APP_FEATURE_SURVEY");
                    break;
                //TODO: check permissions
//                case FINGERPRINT_SOTERAPI: break
            }
        }
        return new ArrayList<>(permission);
    }

    @Override
    public boolean cancelAuthenticateBecauseOnPause() {

        return dialog.cancelAuthenticateBecauseOnPause();
    }

    @Override
    public void startAuth() {
        final List<BiometricType> types = compatBuilder.getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY ?
                BiometricAuthentication.getAvailableBiometrics() :
                Collections.singletonList(compatBuilder.getBiometricAuthRequest().getType());

        BiometricAuthentication.authenticate(dialog.getContainer(), types, fmAuthCallback);
    }

    @Override
    public void stopAuth() {
        BiometricAuthentication.cancelAuthentication();
    }

    @Override
    public void cancelAuth() {
        if (callback != null)
            callback.onCanceled();
    }

    @Override
    public void onUiShown() {
        if (callback != null)
            callback.onUIShown();
    }

    private class BiometricAuthenticationCallbackImpl implements BiometricAuthenticationListener {
        @Override
        public void onSuccess(BiometricType module) {

            ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    cancelAuthenticate();
                    callback.onSucceeded();
                }
            });
        }

        @Override
        public void onHelp(AuthenticationHelpReason helpReason, String msg) {
            if (helpReason != AuthenticationHelpReason.BIOMETRIC_ACQUIRED_GOOD && !TextUtils.isEmpty(msg)) {

                dialog.onHelp(msg);
            }
        }

        @Override
        public void onFailure(AuthenticationFailureReason failureReason, BiometricType module) {

            dialog.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT);
            if (failureReason != AuthenticationFailureReason.LOCKED_OUT) {
                //non fatal
                switch (failureReason) {
                    case SENSOR_FAILED:
                    case AUTHENTICATION_FAILED:
                        return;
                }
                ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        cancelAuthenticate();
                        callback.onFailed(failureReason);
                    }
                });
            } else {
                HardwareAccessImpl.getInstance(compatBuilder.getBiometricAuthRequest()).lockout();
                ExecutorHelper.INSTANCE.getHandler().postDelayed(() -> {
                    cancelAuthenticate();
                    callback.onFailed(failureReason);
                }, 2000);
            }
        }
    }
}
