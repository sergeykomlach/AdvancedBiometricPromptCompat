package dev.skomlach.biometric.compat.impl.dialogs;

import android.app.UiModeManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.content.ContextCompat;
import androidx.core.os.BuildCompat;
import androidx.core.view.ViewCompat;

import dev.skomlach.biometric.compat.R;
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes;

class BiometricPromptCompatDialog extends AppCompatDialog {

    private final TransitionDrawable crossfader;
    @LayoutRes private final int res;
    private TextView mTitle, mSubtitle, mDescription, mStatus;
    private Button mNegativeButton;
    private FingerprintIconView mFingerprintIcon;
    private View container = null;
    private View rootView = null;
    private WindowFocusChangedListener focusListener;

    BiometricPromptCompatDialog(@NonNull Context context, boolean isInscreenLayout) {
        super(context, R.style.Theme_BiometricPromptDialog);
        res = (isInscreenLayout ?
                R.layout.biometric_prompt_dialog_content_inscreen :
                R.layout.biometric_prompt_dialog_content);

        int NIGHT_MODE;

        int currentMode = DarkLightThemes.getNightMode(getContext());
        if (currentMode == UiModeManager.MODE_NIGHT_YES) {
            NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_YES;
        } else if (currentMode == UiModeManager.MODE_NIGHT_AUTO) {
            if (BuildCompat.isAtLeastP()) {
                //Android 9+ deal with dark mode natively
                NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            } else {
                NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_AUTO_TIME;
            }
        } else {
            if (BuildCompat.isAtLeastP()) {
                //Android 9+ deal with dark mode natively
                NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            } else {
                NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_NO;
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
            getDelegate().setLocalNightMode(NIGHT_MODE);

        crossfader = new TransitionDrawable(new Drawable[]{
                new ColorDrawable(Color.TRANSPARENT),
                new ColorDrawable(ContextCompat.getColor(getContext(), R.color.window_bg))
        });
        crossfader.setCrossFadeEnabled(true);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        BiometricLoggerImpl.e("WindowFocusChangedListener" + ("Dialog.hasFocus(1) - " + hasFocus));
        if (focusListener != null) {
            View root = findViewById(Window.ID_ANDROID_CONTENT);
            if (root != null) {
                if (ViewCompat.isAttachedToWindow(root)) {
                    focusListener.hasFocus(root.hasWindowFocus());
                }
            }
        }
    }

    @Override
    public void dismiss() {
        if (isShowing()) {
            Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.move_out);
            ((View) rootView.getParent()).setBackground(crossfader);
            crossfader.reverseTransition((int) animation.getDuration());
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (isShowing()) {
                        BiometricPromptCompatDialog.super.dismiss();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            rootView.startAnimation(animation);
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        setContentView(res);

        rootView = findViewById(R.id.dialogContent);

        rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {

            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (isShowing())
                    BiometricPromptCompatDialog.super.dismiss();
            }
        });

        mTitle = rootView.findViewById(R.id.title);
        mSubtitle = rootView.findViewById(R.id.subtitle);
        mDescription = rootView.findViewById(R.id.description);
        mStatus = rootView.findViewById(R.id.status);
        mNegativeButton = rootView.findViewById(android.R.id.button1);
        mFingerprintIcon = rootView.findViewById(R.id.fingerprint_icon);
        container = rootView.findViewById(R.id.auth_content_container);

        if (this.mFingerprintIcon != null) {
            this.mFingerprintIcon.setState(FingerprintIconView.State.ON, false);
        }
        ((View) rootView.getParent()).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancel();
            }
        });
        rootView.setOnClickListener(null);

        Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.move_in);
        ((View) rootView.getParent()).setBackground(crossfader);
        crossfader.startTransition((int) animation.getDuration());
        rootView.startAnimation(animation);
    }

    void setWindowFocusChangedListener(WindowFocusChangedListener listener) {
        this.focusListener = listener;
    }

    //https://developer.android.com/preview/features/darktheme#configuration_changes
    boolean isNightMode() {
        return "dark_theme".equals(rootView.getTag());
    }

    public TextView getTitle() {
        return mTitle;
    }

    public TextView getSubtitle() {
        return mSubtitle;
    }

    public TextView getDescription() {
        return mDescription;
    }

    public TextView getStatus() {
        return mStatus;
    }

    public Button getNegativeButton() {
        return mNegativeButton;
    }

    public FingerprintIconView getFingerprintIcon() {
        return mFingerprintIcon;
    }

    public View getContainer() {
        return container;
    }

    public View getRootView() {
        return rootView;
    }
}
