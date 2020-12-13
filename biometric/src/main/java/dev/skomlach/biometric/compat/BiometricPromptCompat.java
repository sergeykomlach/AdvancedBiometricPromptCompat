package dev.skomlach.biometric.compat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.ColorRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.BiometricAuthentication;
import dev.skomlach.biometric.compat.engine.BiometricInitListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule;
import dev.skomlach.biometric.compat.impl.BiometricPromptApi28Impl;
import dev.skomlach.biometric.compat.impl.BiometricPromptGenericImpl;
import dev.skomlach.biometric.compat.impl.IBiometricPromptImpl;
import dev.skomlach.biometric.compat.impl.PermissionsFragment;
import dev.skomlach.biometric.compat.utils.ActiveWindow;
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix;
import dev.skomlach.biometric.compat.utils.DeviceUnlockedReceiver;
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;
import dev.skomlach.common.misc.multiwindow.MultiWindowSupport;

import static dev.skomlach.common.misc.Utils.startActivity;

public final class BiometricPromptCompat {
    private static final List<Runnable> pendingTasks = new ArrayList<>();
    private volatile static boolean init = false;
    private volatile static boolean initInProgress = false;

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @NonNull
    private final IBiometricPromptImpl impl;

    private BiometricPromptCompat(@NonNull IBiometricPromptImpl impl) {
        this.impl = impl;
    }

    public static boolean isInit() {
        return init;
    }

    public static void init(@Nullable Runnable execute) {

        if (init) {
            if (initInProgress) {
                pendingTasks.add(execute);
            } else
                ExecutorHelper.INSTANCE.getHandler().post(execute);
        } else {
            initInProgress = true;
            pendingTasks.add(execute);
            BiometricLoggerImpl.e("BiometricPromptCompat.init()");

            BiometricAuthentication.init(new BiometricInitListener() {
                @Override
                public void initFinished(BiometricMethod method, BiometricModule module) {

                }

                @Override
                public void onBiometricReady() {
                    init = true;
                    initInProgress = false;
                    for (Runnable task : pendingTasks) {
                        ExecutorHelper.INSTANCE.getHandler().post(task);
                    }
                    pendingTasks.clear();
                }
            });

            DeviceUnlockedReceiver.registerDeviceUnlockListener();
        }
    }

    public static boolean isBiometricSensorPermanentlyLocked(BiometricAuthRequest api) {
        if (!isInit())
            throw new IllegalStateException("Please call BiometricPromptCompat.init(null);  first");
        return BiometricErrorLockoutPermanentFix.INSTANCE.isBiometricSensorPermanentlyLocked(api.getType());
    }

    public static boolean isHardwareDetected(BiometricAuthRequest api) {
        if (!isInit())
            throw new IllegalStateException("Please call BiometricPromptCompat.init(null);  first");
        return HardwareAccessImpl.getInstance(api).isHardwareAvailable();
    }

    public static boolean hasEnrolled(BiometricAuthRequest api) {
        if (!isInit())
            throw new IllegalStateException("Please call BiometricPromptCompat.init(null);  first");
        return HardwareAccessImpl.getInstance(api).isBiometricEnrolled();
    }

    public static boolean isLockOut(BiometricAuthRequest api) {
        if (!isInit())
            throw new IllegalStateException("Please call BiometricPromptCompat.init(null);  first");
        return HardwareAccessImpl.getInstance(api).isLockedOut();
    }

    public static boolean isNewBiometricApi(BiometricAuthRequest api) {
        if (!isInit())
            throw new IllegalStateException("Please call BiometricPromptCompat.init(null);  first");
        return HardwareAccessImpl.getInstance(api).isNewBiometricApi();
    }

    public static void openSettings(Activity activity, BiometricAuthRequest api) {
        if (!isInit())
            throw new IllegalStateException("Please call BiometricPromptCompat.init(null);  first");

        if (!HardwareAccessImpl.getInstance(api).isNewBiometricApi()) {
            BiometricAuthentication.openSettings(activity);
        } else {
            //for unknown reasons on some devices happens SecurityException - "Permission.MANAGE_BIOMETRIC required" - but not should be
            if (startActivity(new Intent("android.settings.BIOMETRIC_ENROLL"), activity)) {
                return;
            }
            if (startActivity(new Intent("android.settings.BIOMETRIC_SETTINGS"), activity)) {
                return;
            }
            if (startActivity(new Intent().setComponent(
                    new ComponentName("com.android.settings", "com.android.settings.Settings$BiometricsAndSecuritySettingsActivity")), activity)) {
                return;
            }
            if (startActivity(new Intent().setComponent(
                    new ComponentName("com.android.settings", "com.android.settings.Settings$SecuritySettingsActivity")), activity)) {
                return;
            }

            startActivity(
                    new Intent(Settings.ACTION_SETTINGS), activity);
        }
    }

    public void authenticate(@NonNull Result callback) {

        if (!isHardwareDetected(impl.getBuilder().biometricAuthRequest)) {
            callback.onFailed(AuthenticationFailureReason.NO_HARDWARE);
            return;
        }
        if (!hasEnrolled(impl.getBuilder().biometricAuthRequest)) {
            callback.onFailed(AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED);
            return;
        }
        if (isLockOut(impl.getBuilder().biometricAuthRequest)) {
            callback.onFailed(AuthenticationFailureReason.LOCKED_OUT);
            return;
        }

        if (isBiometricSensorPermanentlyLocked(impl.getBuilder().biometricAuthRequest)) {
            callback.onFailed(AuthenticationFailureReason.HARDWARE_UNAVAILABLE);
            return;
        }

        PermissionsFragment.askForPermissions(impl.getBuilder().context, impl.getUsedPermissions(), new Runnable() {
            @Override
            public void run() {
                authenticateInternal(callback);
            }
        });
    }

    private void authenticateInternal(@NonNull Result callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View d = ActiveWindow.getActiveView(impl.getBuilder().context);
            if (!d.isAttachedToWindow()) {
                d.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        d.removeOnAttachStateChangeListener(this);
                        checkForFocusAndStart(callback);
                        d.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(View v) {

                            }

                            @Override
                            public void onViewDetachedFromWindow(View v) {
                                d.removeOnAttachStateChangeListener(this);
                                impl.cancelAuthenticate();
                            }
                        });
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        d.removeOnAttachStateChangeListener(this);
                    }
                });
            } else {
                checkForFocusAndStart(callback);
            }
        } else {
            impl.authenticate(callback);
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void checkForFocusAndStart(@NonNull Result callback) {
        View activity = ActiveWindow.getActiveView(impl.getBuilder().context);
        if (!activity.hasWindowFocus()) {
            ViewTreeObserver.OnWindowFocusChangeListener windowFocusChangeListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
                @Override
                public void onWindowFocusChanged(boolean focus) {
                    if (activity.hasWindowFocus()) {
                        activity.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                        impl.authenticate(callback);
                    }
                }
            };
            activity.getViewTreeObserver().addOnWindowFocusChangeListener(windowFocusChangeListener);
        } else {
            impl.authenticate(callback);
        }
    }

    public void cancelAuthenticate() {
        impl.cancelAuthenticate();
    }

    public boolean cancelAuthenticateBecauseOnPause() {
        return impl.cancelAuthenticateBecauseOnPause();
    }

    @ColorRes
    public Integer getDialogMainColor() {

        if (impl.isNightMode()) {
            return android.R.color.black;
        } else {
            return R.color.material_grey_50;
        }
    }

    public interface Result {
        @MainThread
        void onSucceeded();

        @MainThread
        void onCanceled();

        @MainThread
        void onFailed(AuthenticationFailureReason reason);

        @MainThread
        void onUIShown();
    }

    public static final class Builder {

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @NonNull
        public final FragmentActivity context;
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @NonNull
        public final BiometricAuthRequest biometricAuthRequest;
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Nullable
        public CharSequence title;
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Nullable
        public CharSequence subtitle;
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Nullable
        public CharSequence description;
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Nullable
        public CharSequence negativeButtonText;
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Nullable
        public DialogInterface.OnClickListener negativeButtonListener;
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        public MultiWindowSupport multiWindowSupport;

        public Builder(@NonNull FragmentActivity context) {
            this(new BiometricAuthRequest(BiometricApi.AUTO, BiometricType.BIOMETRIC_UNDEFINED), context);
        }

        public Builder(@NonNull BiometricAuthRequest request, @NonNull FragmentActivity context) {
            this.biometricAuthRequest = request;
            this.context = context;
            multiWindowSupport = new MultiWindowSupport(context);
        }

        @NonNull
        public Builder setTitle(CharSequence title) {
            this.title = title;
            return this;
        }

        @NonNull
        public Builder setTitle(@StringRes int titleRes) {
            this.title = context.getString(titleRes);
            return this;
        }

        @NonNull
        public Builder setSubtitle(CharSequence subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        @NonNull
        public Builder setSubtitle(@StringRes int subtitleRes) {
            this.subtitle = context.getString(subtitleRes);
            return this;
        }

        @NonNull
        public Builder setDescription(CharSequence description) {
            this.description = description;
            return this;
        }

        @NonNull
        public Builder setDescription(@StringRes int descriptionRes) {
            this.description = context.getString(descriptionRes);
            return this;
        }

        @NonNull
        public Builder setNegativeButton(@NonNull CharSequence text,
                                         @Nullable DialogInterface.OnClickListener listener) {
            this.negativeButtonText = text;
            this.negativeButtonListener = listener;
            return this;
        }

        @NonNull
        public Builder setNegativeButton(@StringRes int textResId,
                                         @Nullable DialogInterface.OnClickListener listener) {
            this.negativeButtonText = context.getString(textResId);
            this.negativeButtonListener = listener;
            return this;
        }

        @NonNull
        public BiometricPromptCompat build() {
            if (!isInit())
                throw new IllegalStateException("Please call BiometricPromptCompat.init(null);  first");

            if (title == null) {
                throw new IllegalArgumentException("You should set a title for BiometricPrompt.");
            }
            if (negativeButtonText == null) {
                throw new IllegalArgumentException("You should set a negativeButtonText for BiometricPrompt.");
            }
            if (biometricAuthRequest.getApi() == BiometricApi.BIOMETRIC_API
                    || (biometricAuthRequest.getApi() == BiometricApi.AUTO
                    && isNewBiometricApi(biometricAuthRequest))) {
                return new BiometricPromptCompat(new BiometricPromptApi28Impl(this));
            } else {
                return new BiometricPromptCompat(new BiometricPromptGenericImpl(this));
            }
        }
    }
}
