package dev.skomlach.biometric.compat.utils.statusbar;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.ColorInt;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

import static dev.skomlach.biometric.compat.utils.statusbar.HelperTool.isVisible;
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class StatusBarTools {

    /* //TODO: Display cutout support
     * https://github.com/QMUI/QMUI_Android/tree/2689199dda27a6c9163fe54faa05e2d3a8447416/qmui/src/main/java/com/qmuiteam/qmui/util
     * https://open.oppomobile.com/wiki/doc#id=10159
     * https://mini.eastday.com/bdmip/180411011257629.html
     * https://com-it.tech/archives/55704
     * https://blog.csdn.net/sinat_29874521/article/details/80224447
     * https://developer.huawei.com/consumer/en/devservice/doc/30210
     *
     * http://thoughtnerds.com/2018/03/10-things-you-should-know-about-android-p/
     * */
    private final static boolean TURNOFF_TINT = false;
    private final static boolean translucentNavBar = false;
    private final static boolean translucentStatusBar = false;
    private final static float alpha = 0.65f;

    //setSystemUiVisibility has effect only if View is visible
    public static void setNavBarAndStatusBarColors(Activity activity, @ColorInt int colorNavBar, @ColorInt int colorStatusBar) {
        final Runnable runnable = () -> {
            setStatusBarColor(activity, colorStatusBar);
            setNavBarColor(activity, colorNavBar);
        };

        final View view = activity.getWindow().getDecorView();
        if (isVisible(view, 100)) {
            view.post(runnable);
        } else {
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (isVisible(view, 100)) {
                        if (view.getViewTreeObserver().isAlive()) {
                            view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            view.post(runnable);
                        }
                    }
                }
            });
        }
    }

    private static void setNavBarColor(Activity activity, @ColorInt int color) {
        try {

            if (TURNOFF_TINT)
                return;

            if (translucentNavBar)
                color = Color.TRANSPARENT;

            Window window = activity.getWindow();

            boolean isDark = ColorUtil.trueDarkColor(color);

            //emulate navbar color via translucent and custom views
            //On Android6+ and some OEM device we can enable DarkIcons
            if (!StatusBarIconsDarkMode.setDarkIconMode(window, !isDark, BarType.NAVBAR)) { //in other cases - make color a bit 'darker'
                if (!isDark) {
                    color = ColorUtil.blend(color, Color.BLACK, alpha);
                }
            }
            if (Build.VERSION.SDK_INT >= 21) {
                window.setNavigationBarColor(color);
            }
            //add divider for android 9
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.setNavigationBarDividerColor(ContextCompat.getColor(window.getContext(), android.R.color.darker_gray));
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }

    private static void setStatusBarColor(Activity activity, @ColorInt int color) {
        try {

            if (TURNOFF_TINT)
                return;

            if (translucentStatusBar)
                color = Color.TRANSPARENT;

            Window window = activity.getWindow();
            boolean isDark = ColorUtil.trueDarkColor(color);

            //emulate statusbar color via translucent and custom views
            //On Android6+ and some OEM device we can enable DarkIcons
            if (!StatusBarIconsDarkMode.setDarkIconMode(window, !isDark, BarType.STATUSBAR)) { //in other cases - make color a bit 'darker'
                if (!isDark) {
                    color = ColorUtil.blend(color, Color.BLACK, alpha);
                }
            }
            if (Build.VERSION.SDK_INT >= 21) {
                window.setStatusBarColor(color);
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }
}
