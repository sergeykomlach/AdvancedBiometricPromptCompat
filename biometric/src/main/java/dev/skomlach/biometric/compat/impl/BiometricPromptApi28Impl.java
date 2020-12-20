package dev.skomlach.biometric.compat.impl;

import android.annotation.TargetApi;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.skomlach.biometric.compat.BiometricPromptCompat;
import dev.skomlach.biometric.compat.R;
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.BiometricCodes;
import dev.skomlach.biometric.compat.engine.internal.core.RestartPredicatesImpl;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl;
import dev.skomlach.biometric.compat.utils.ActiveWindow;
import dev.skomlach.biometric.compat.utils.BiometricAuthWasCanceledByError;
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix;
import dev.skomlach.biometric.compat.utils.CodeToString;
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs;
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl;
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes;
import dev.skomlach.common.misc.ExecutorHelper;
import dev.skomlach.common.misc.Utils;

@TargetApi(Build.VERSION_CODES.P)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricPromptApi28Impl implements IBiometricPromptImpl, BiometricCodes, AuthCallback {
    private final BiometricPrompt.PromptInfo biometricPromptInfo;
    private final BiometricPrompt biometricPrompt;
    private final BiometricPromptCompat.Builder compatBuilder;
    private final RestartPredicate restartPredicate = RestartPredicatesImpl.defaultPredicate();
    private BiometricPromptCompatDialogImpl dialog = null;
    private BiometricPromptCompat.Result callback;
    
    final BiometricPrompt.AuthenticationCallback authCallback = new BiometricPrompt.AuthenticationCallback() {
        //https://forums.oneplus.com/threads/oneplus-7-pro-fingerprint-biometricprompt-does-not-show.1035821/
        private Boolean onePlusWithBiometricBugFailure = false;

        @Override
        public void onAuthenticationFailed() {
            BiometricLoggerImpl.d("BiometricPromptApi28Impl.onAuthenticationFailed");
            if (DevicesWithKnownBugs.isOnePlusWithBiometricBug()) {
                this.onePlusWithBiometricBugFailure = true;
                cancelAuthenticate();
            } else {
                //...normal failed processing...//
                if (dialog != null)
                    dialog.onFailure(false);
            }
        }

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
            BiometricLoggerImpl.d("BiometricPromptApi28Impl.onAuthenticationError: " + CodeToString.getErrorCode(errorCode) + " " + errString);
            // Authentication failed on OnePlus device with broken BiometricPrompt implementation
            // Present the same screen with additional buttons to allow retry/fail
            if (this.onePlusWithBiometricBugFailure) {
                this.onePlusWithBiometricBugFailure = false;
                //...present retryable error screen...
                return;
            }
            //...present normal failed screen...

            FocusLostDetection.stopListener(compatBuilder.activeWindow);

            ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
                @Override
                public void run() {

                    AuthenticationFailureReason failureReason = AuthenticationFailureReason.UNKNOWN;
                    switch (errorCode) {
                        case BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS:
                            failureReason = AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED;
                            break;
                        case BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT:
                            failureReason = AuthenticationFailureReason.NO_HARDWARE;
                            break;
                        case BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                            failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE;
                            break;
                        case BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT:
                            BiometricErrorLockoutPermanentFix.INSTANCE.setBiometricSensorPermanentlyLocked(compatBuilder.getBiometricAuthRequest().getType());
                            failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE;
                            break;
                        case BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS:
                        case BiometricCodes.BIOMETRIC_ERROR_NO_SPACE:
                            failureReason = AuthenticationFailureReason.SENSOR_FAILED;
                            break;
                        case BiometricCodes.BIOMETRIC_ERROR_TIMEOUT:
                            failureReason = AuthenticationFailureReason.TIMEOUT;
                            break;
                        case BiometricCodes.BIOMETRIC_ERROR_LOCKOUT:
                            HardwareAccessImpl.getInstance(compatBuilder.getBiometricAuthRequest()).lockout();
                            failureReason = AuthenticationFailureReason.LOCKED_OUT;
                            break;
                        case BiometricCodes.BIOMETRIC_ERROR_USER_CANCELED:
                        case BiometricCodes.BIOMETRIC_ERROR_NEGATIVE_BUTTON:
                            if (callback != null)
                                callback.onCanceled();
                            cancelAuthenticate();
                            return;
                        case BiometricCodes.BIOMETRIC_ERROR_CANCELED:
                            // Don't send a cancelled message.
                            return;
                    }

                    if (restartPredicate.invoke(failureReason)) {
                        if (callback != null) {
                            if (dialog != null)
                                dialog.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT);
                            authenticate(callback);
                        }
                    } else {
                        switch (failureReason) {
                            case SENSOR_FAILED:
                            case AUTHENTICATION_FAILED:
                                HardwareAccessImpl.getInstance(compatBuilder.getBiometricAuthRequest()).lockout();
                                failureReason = AuthenticationFailureReason.LOCKED_OUT;
                                break;
                        }

                        if (dialog != null)
                            dialog.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT);
                        if (callback != null)
                            callback.onFailed(failureReason);
                        BiometricAuthWasCanceledByError.INSTANCE.setCanceledByError();

                        if (failureReason == AuthenticationFailureReason.LOCKED_OUT) {
                            ExecutorHelper.INSTANCE.getHandler().postDelayed(() -> {
                                if (callback != null)
                                    callback.onCanceled();
                                cancelAuthenticate();
                            }, 2000);
                        } else {
                            if (callback != null)
                                callback.onCanceled();
                            cancelAuthenticate();
                        }
                    }
                }
            });
        }

        @Override
        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            BiometricLoggerImpl.d("BiometricPromptApi28Impl.onAuthenticationSucceeded:");
            this.onePlusWithBiometricBugFailure = false;
            FocusLostDetection.stopListener(compatBuilder.activeWindow);

            ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    cancelAuthenticate();
                    if (callback != null) {
                        callback.onSucceeded();
                    }
                }
            });
        }
    };

    public BiometricPromptApi28Impl(@NonNull BiometricPromptCompat.Builder compatBuilder) {

        this.compatBuilder = compatBuilder;

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder();
        builder.setTitle(compatBuilder.title);
        if (compatBuilder.subtitle != null) {
            builder.setSubtitle(compatBuilder.subtitle);
        }
        if (compatBuilder.description != null) {
            builder.setDescription(compatBuilder.description);
        }
        if (compatBuilder.negativeButtonText != null) {
            if (Utils.isAtLeastR())
                builder.setNegativeButtonText(compatBuilder.negativeButtonText);
            else
                builder.setNegativeButtonText(
                        getFixedString(compatBuilder.negativeButtonText, ContextCompat.getColor(compatBuilder.getContext(), R.color.material_deep_teal_500)));
        }
        this.biometricPromptInfo = builder.build();
        this.biometricPrompt = new BiometricPrompt(compatBuilder.getContext(),
                ExecutorHelper.INSTANCE.getExecutor(), authCallback);
    }

    private CharSequence getFixedString(CharSequence str, @ColorInt int color) {
        Spannable wordtoSpan = new SpannableString(str);
        wordtoSpan.setSpan(new ForegroundColorSpan(color), 0, wordtoSpan.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return wordtoSpan;
    }

    @Override
    public void authenticate(@NonNull BiometricPromptCompat.Result cbk) {
        try {
            BiometricLoggerImpl.d("BiometricPromptApi28Impl.authenticate():");
            this.callback = cbk;

            //One Plus devices (6T and newer) - Activity do not lost the focus
            FocusLostDetection.attachListener(compatBuilder.activeWindow, new WindowFocusChangedListener() {
                @Override
                public void onStartWatching() {
                    onUiShown();
                    startAuth();
                }

                @Override
                public void hasFocus(boolean hasFocus) {
                    if (hasFocus) {
                        dialog = new BiometricPromptCompatDialogImpl(compatBuilder, BiometricPromptApi28Impl.this, true);
                        dialog.showDialog();
                    }
                }
            });
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            callback.onFailed(AuthenticationFailureReason.UNKNOWN);
        }
    }

    @Override
    public boolean cancelAuthenticateBecauseOnPause() {
        BiometricLoggerImpl.d("BiometricPromptApi28Impl.cancelAuthenticateBecauseOnPause():");
        if (dialog != null) {
            if (dialog.cancelAuthenticateBecauseOnPause()) {
                FocusLostDetection.stopListener(compatBuilder.activeWindow);
                return true;
            } else {
                return false;
            }
        } else {
            cancelAuthenticate();
            return true;
        }
    }

    @Override
    public boolean isNightMode() {
        if (dialog != null)
            return dialog.isNightMode();
        else {
            return DarkLightThemes.isNightMode(compatBuilder.getContext());
        }
    }

    @Override
    public BiometricPromptCompat.Builder getBuilder() {
        return compatBuilder;
    }

    @Override
    public List<String> getUsedPermissions() {
        final Set<String> permission = new HashSet<>();
        permission.add("android.permission.USE_FINGERPRINT");
        if (Build.VERSION.SDK_INT >= 28) {
            permission.add("android.permission.USE_BIOMETRIC");
        }
        return new ArrayList<>(permission);
    }

    @Override
    public void cancelAuthenticate() {
        BiometricLoggerImpl.d("BiometricPromptApi28Impl.cancelAuthenticate():");
        if (dialog != null)
            dialog.dismissDialog();
        else {
            biometricPrompt.cancelAuthentication();
        }
        FocusLostDetection.stopListener(compatBuilder.activeWindow);
    }

    @Override
    public void startAuth() {
        BiometricLoggerImpl.d("BiometricPromptApi28Impl.startAuth():");
        biometricPrompt.authenticate(biometricPromptInfo);
    }

    @Override
    public void stopAuth() {
        BiometricLoggerImpl.d("BiometricPromptApi28Impl.stopAuth():");
        biometricPrompt.cancelAuthentication();
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
}
