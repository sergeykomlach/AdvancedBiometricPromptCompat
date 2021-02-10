package dev.skomlach.biometric.compat.impl.dialogs;

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.skomlach.biometric.compat.BiometricPromptCompat;
import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.biometric.compat.R;
import dev.skomlach.biometric.compat.impl.AuthCallback;
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.misc.ExecutorHelper;
import me.weishu.reflection.Reflection;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricPromptCompatDialogImpl {
    private final boolean isInScreen;
    private final Handler animateHandler;
    private final BiometricPromptCompatDialog dialog;
    private final CharSequence promptText;
    private final CharSequence too_many_attempts;
    private final CharSequence not_recognized;
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final BiometricPromptCompat.Builder compatBuilder;
    private final ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            BiometricLoggerImpl.e("BiometricPromptGenericImpl" + "BiometricPromptGenericImpl.onGlobalLayout - fallback dialog");
            checkInScreenIcon();
        }
    };
    private final AuthCallback authCallback;
    private final WindowFocusChangedListener onWindowFocusChangeListener = new WindowFocusChangedListener() {
        @Override
        public void onStartWatching() {
            BiometricLoggerImpl.e("BiometricPromptGenericImpl.onStartWatching");
        }

        @Override
        public void hasFocus(boolean hasFocus) {
            BiometricLoggerImpl.e("BiometricPromptGenericImpl" + "BiometricPromptGenericImpl.onWindowFocusChanged - fallback dialog " + hasFocus);
            if (hasFocus) {
                startAuth();
            } else {

                if (isMultiWindowHack()) {
                    if (isInScreen && isInScreenUIHackNeeded()) {
                        BiometricLoggerImpl.e("BiometricPromptGenericImpl" + "BiometricPromptGenericImpl.onWindowFocusChanged - do not cancelAuth - inScreenDevice and app on top");
                        return;
                    } else {
                        BiometricLoggerImpl.e("BiometricPromptGenericImpl" + "BiometricPromptGenericImpl.onWindowFocusChanged - do not cancelAuth - regular device in multiwindow");
                        return;
                    }
                }

                BiometricLoggerImpl.e("BiometricPromptGenericImpl" + "BiometricPromptGenericImpl.onWindowFocusChanged - cancelAuth");
                cancelAuth();
            }
        }
    };
    private ColorStateList originalColor;

    public BiometricPromptCompatDialogImpl(BiometricPromptCompat.Builder compatBuilder, AuthCallback authCallback, boolean isInScreen) {
        this.isInScreen = isInScreen;
        this.authCallback = authCallback;
        this.compatBuilder = compatBuilder;
        this.promptText = getDialogTitle();
        this.too_many_attempts = compatBuilder.getContext().getString(androidx.biometric.R.string.fingerprint_error_lockout);
        this.not_recognized = compatBuilder.getContext().getString(androidx.biometric.R.string.fingerprint_not_recognized);

        this.animateHandler = new AnimateHandler(Looper.getMainLooper());
        this.dialog = new BiometricPromptCompatDialog(compatBuilder, isInScreen);

        dialog.setOnDismissListener(dialogInterface -> {
            detachWindowListeners();
            if (inProgress.get()) {
                inProgress.set(false);
                authCallback.stopAuth();
            }
        });

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (authCallback != null)
                    authCallback.cancelAuth();

                detachWindowListeners();
                if (inProgress.get()) {
                    inProgress.set(false);
                    if (authCallback != null) {
                        authCallback.stopAuth();
                    }
                }

            }
        });
        dialog.setOnShowListener(d -> {
            BiometricLoggerImpl.e("BiometricPromptGenericImpl" + "AbstractBiometricPromptCompat. started.");
            if (compatBuilder.title == null) {
                dialog.getTitle().setVisibility(View.GONE);
            } else {
                dialog.getTitle().setText(compatBuilder.title);
            }
            if (compatBuilder.subtitle == null) {
                dialog.getSubtitle().setVisibility(View.GONE);
            } else {
                dialog.getSubtitle().setText(compatBuilder.subtitle);
            }
            if (compatBuilder.description == null) {
                dialog.getDescription().setVisibility(View.GONE);
            } else {
                dialog.getDescription().setText(compatBuilder.description);
            }
            if (compatBuilder.negativeButtonText == null) {
                dialog.getNegativeButton().setVisibility(View.INVISIBLE);
            } else {
                dialog.getNegativeButton().setText(compatBuilder.negativeButtonText);

                dialog.getNegativeButton().setOnClickListener(v -> {
                    dismissDialog();
                    if (authCallback != null)
                        authCallback.cancelAuth();
                });
            }
            dialog.getStatus().setText(promptText);
            originalColor = dialog.getStatus().getTextColors();
            if (dialog.getFingerprintIcon() != null) {
                dialog.getFingerprintIcon().setState(FingerprintIconView.State.ON, false);
            }
            checkInScreenIcon();
            attachWindowListeners();
            startAuth();
        });
    }

    private String getDialogTitle() {
        Reflection.unseal(AndroidContext.getAppContext(), Collections.singletonList("com.android.internal"));
        try {
            Field[] fields = Class.forName("com.android.internal.R$string").getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().contains("biometric") && field.getName().contains("dialog")) {
                    boolean isAccessible = field.isAccessible();
                    try {
                        if (!isAccessible)
                            field.setAccessible(true);
                        return compatBuilder.getContext().getString((int) field.get(null));
                    } finally {
                        if (!isAccessible)
                            field.setAccessible(false);
                    }
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        try {
            Field[] fields = Class.forName("com.android.internal.R$string").getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().contains("fingerprint") && field.getName().contains("dialog")) {
                    boolean isAccessible = field.isAccessible();
                    try {
                        if (!isAccessible)
                            field.setAccessible(true);
                        return compatBuilder.getContext().getString((int) field.get(null));
                    } finally {
                        if (!isAccessible)
                            field.setAccessible(false);
                    }
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }

        return compatBuilder.getContext().getString(androidx.biometric.R.string.fingerprint_dialog_touch_sensor);
    }
    public boolean isNightMode() {
        return dialog.isNightMode();
    }

    public boolean cancelAuthenticateBecauseOnPause() {
        if (isMultiWindowHack()) {
            return false;
        } else {
            dismissDialog();
            return true;
        }
    }

    private void attachWindowListeners() {
        try {
            View v = dialog.findViewById(Window.ID_ANDROID_CONTENT);
            dialog.setWindowFocusChangedListener(onWindowFocusChangeListener);
            v.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
        } catch (Throwable we) {
            BiometricLoggerImpl.e(we);
        }
    }

    private void detachWindowListeners() {
        try {
            View v = dialog.findViewById(Window.ID_ANDROID_CONTENT);
            dialog.setWindowFocusChangedListener(null);
            v.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
        } catch (Throwable we) {
            BiometricLoggerImpl.e(we);
        }
    }

    //in case devices is InScreenScanner and app switched to the SplitScreen mode and app placed on the top of screen
    //we need to show Fingerprint icon
    private boolean isInScreenUIHackNeeded() {
        return compatBuilder.multiWindowSupport.isInMultiWindow() && !compatBuilder.multiWindowSupport.isWindowOnScreenBottom();
    }

    //in case app switched to the SplitScreen mode we need to skip onPause on lost focus cases
    private boolean isMultiWindowHack() {
        if (compatBuilder.multiWindowSupport.isInMultiWindow() && (inProgress.get() && dialog.isShowing())) {
            BiometricLoggerImpl.e("BiometricPromptGenericImpl" + "BiometricPromptGenericImpl.isMultiWindowHack - perform hack");
            this.authCallback.stopAuth();
            this.authCallback.startAuth();

            return true;
        } else {
            BiometricLoggerImpl.e("BiometricPromptGenericImpl" + "BiometricPromptGenericImpl.isMultiWindowHack - do not perform hack");
            return false;
        }
    }

    private void checkInScreenIcon() {
        if (isInScreen && dialog.getFingerprintIcon() != null) {
            if (isInScreenUIHackNeeded()) {
                if (dialog.getFingerprintIcon().getVisibility() != View.VISIBLE) {
                    dialog.getFingerprintIcon().setVisibility(View.VISIBLE);
                }
            } else {
                if (dialog.getFingerprintIcon().getVisibility() != View.INVISIBLE) {
                    dialog.getFingerprintIcon().setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private void startAuth() {
        if (!inProgress.get() && dialog.isShowing()) {
            inProgress.set(true);
            this.authCallback.startAuth();
        }
    }

    private void cancelAuth() {
        if (inProgress.get() && dialog.isShowing()) {
            inProgress.set(false);
            this.authCallback.stopAuth();
        }
    }

    public void showDialog() {
        if (dialog.isShowing()) {
            throw new IllegalArgumentException("BiometricPromptGenericImpl. has been started.");
        }
        dialog.show();
    }

    public View getContainer() {
        return dialog.getContainer();
    }

    public void dismissDialog() {
        if (dialog.isShowing()) {
            detachWindowListeners();
            cancelAuth();
            dialog.dismiss();
        }
    }

    public void onHelp(CharSequence msg) {

        ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
            @Override
            public void run() {

                animateHandler.removeMessages(BiometricPromptCompatDialogImpl.AnimateHandler.WHAT_RESTORE_NORMAL_STATE);
                if (dialog.getFingerprintIcon() != null) {
                    dialog.getFingerprintIcon().setState(FingerprintIconView.State.ERROR);
                }
                dialog.getStatus().setText(msg);
                dialog.getStatus().setTextColor(ContextCompat.getColor(compatBuilder.getContext(), R.color.material_red_500));

                animateHandler.sendEmptyMessageDelayed(BiometricPromptCompatDialogImpl.AnimateHandler.WHAT_RESTORE_NORMAL_STATE, 2000);
            }
        });
    }

    public void onFailure(boolean isLockout, BiometricType type) {
        ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
            @Override
            public void run() {
                dialog.errorType(type);
                animateHandler.removeMessages(BiometricPromptCompatDialogImpl.AnimateHandler.WHAT_RESTORE_NORMAL_STATE);
                if (dialog.getFingerprintIcon() != null) {
                    dialog.getFingerprintIcon().setState(FingerprintIconView.State.ERROR);
                }
                dialog.getStatus().setText(isLockout ? too_many_attempts : not_recognized);
                dialog.getStatus().setTextColor(ContextCompat.getColor(compatBuilder.getContext(), R.color.material_red_500));

                animateHandler.sendEmptyMessageDelayed(BiometricPromptCompatDialogImpl.AnimateHandler.WHAT_RESTORE_NORMAL_STATE, 2000);
            }
        });
    }

    public void onSuccess(BiometricType type) {
        ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
            @Override
            public void run() {
                dialog.successType(type);
            }
        });
    }
    private class AnimateHandler extends Handler {

        static final int WHAT_RESTORE_NORMAL_STATE = 0;

        AnimateHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_RESTORE_NORMAL_STATE:
                    dialog.resetIcons();
                    if (dialog.getFingerprintIcon() != null) {
                        dialog.getFingerprintIcon().setState(FingerprintIconView.State.ON);
                    }
                    dialog.getStatus().setText(promptText);
                    dialog.getStatus().setTextColor(originalColor);
                    break;
            }
        }
    }
}
