package dev.skomlach.biometric.compat.utils.statusbar;

import android.graphics.Rect;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class HelperTool {

    /**
     * Whether the view is at least certain % visible
     */
    public static boolean isVisible(//@Nullable final View rootView,
                                    @Nullable final View view, final int minPercentageViewed) {
        // ListView & GridView both call detachFromParent() for views that can be recycled for
        // new data. This is one of the rare instances where a view will have a null parent for
        // an extended period of time and will not be the main window.
        // view.getGlobalVisibleRect() doesn't check that case, so if the view has visibility
        // of View.VISIBLE but it's group has no parent it is likely in the recycle bin of a
        // ListView / GridView and not on screen.
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }

        Rect mClipRect = new Rect();
        if (!view.getGlobalVisibleRect(mClipRect)) {
            // Not visible
            return false;
        }

        // % visible check - the cast is to avoid int overflow for large views.
        final long visibleViewArea = (long) mClipRect.height() * mClipRect.width();
        final long totalViewArea = (long) view.getHeight() * view.getWidth();

        if (totalViewArea <= 0) {
            return false;
        }

        return 100 * visibleViewArea >= minPercentageViewed * totalViewArea;
    }

    //Set UI visibility flags if not set yet
    public static boolean setUIVisibilityFlag(Window window, boolean set, int bits) {
        View view = window.getDecorView();
        int oldVis = view.getSystemUiVisibility();
        int newVis = oldVis;
        if (set) {
            newVis |= bits;
        } else {
            newVis &= ~bits;
        }
        if (newVis != oldVis) {
            view.setSystemUiVisibility(newVis);
            return true;
        } else {
            //already set
            if (set && (newVis & bits) == bits)
                return true;
            return !set && (newVis & bits) != bits;
        }
    }

    //Set Window flags (if not set yet) in "general" way
    public static boolean setFlag(Window window, boolean set, int bits) {
        WindowManager.LayoutParams lp = window.getAttributes();
        int oldVis = lp.flags;
        int newVis = oldVis;
        if (set) {
            newVis |= bits;
        } else {
            newVis &= ~bits;
        }
        if (newVis != oldVis) {
            lp.flags = newVis;
            window.setAttributes(lp);
            return true;
        } else {
            //already set
            if (set && (newVis & bits) == bits)
                return true;
            return !set && (newVis & bits) != bits;
        }
    }

    /*
     * Note:
     * Next code very similar to "general" BUT used BitFlags not compatible with "general" API and leads to unexpected bugs.
     * - For example flags from MIUI set window in non-touchable mode :)
     *
     * See also:
     * https://www.programcreek.com/java-api-examples/?code=Lingzh0ng/BrotherWeather/BrotherWeather-master/weather/src/main/java/com/wearapay/brotherweather/common/utils/StatusBarTextColorUtils.java
     * https://dev.mi.com/doc/p=4769/
     * */
    //Set Window flags (if not set yet) in Flyme-specific way
    public static boolean setFlameFlag(Window window, boolean set, int bits) {

        try {
            WindowManager.LayoutParams lp = window.getAttributes();
            Field meizuFlags = WindowManager.LayoutParams.class.getDeclaredField("meizuFlags");
            meizuFlags.setAccessible(true);
            int oldVis = meizuFlags.getInt(lp);

            int newVis = oldVis;

            if (set) {
                newVis |= bits;
            } else {
                newVis &= ~bits;
            }
            if (newVis != oldVis) {
                meizuFlags.setInt(lp, newVis);// field is a part of WindowManager.LayoutParams, so at first - change value for the field
                window.setAttributes(lp); //and update window attributes
                return true;
            } else {
                //already set
                if (set && (newVis & bits) == bits)
                    return true;
                if (!set && (newVis & bits) != bits)
                    return true;
            }
        } catch (Throwable ignore) {

        }
        return false;
    }

    //Set Window flags (if not set yet) in MIUI-specific way
    public static boolean setMIUIFlag(Window window, boolean set, int bits) {
        //I found that clearExtraFlags/addExtraFlags available at least for MIUI10, not sure about older
        try {
            Class<? extends Window> clazz = window.getClass();
            Method extraFlagField1 = clazz.getMethod("clearExtraFlags", int.class);
            extraFlagField1.invoke(window, bits);
            if (set) {
                Method extraFlagField2 = clazz.getMethod("addExtraFlags", int.class);
                extraFlagField2.invoke(window, bits);
            }
            return true;
        } catch (Throwable ignore) {
        }
        //try to use solution from https://dev.mi.com/doc/p=4769/
        try {
            Class<? extends Window> clazz = window.getClass();
            Method extraFlagField = clazz.getMethod("setExtraFlags", int.class, int.class);
            extraFlagField.invoke(window, set ? bits : 0, bits);
            return true;
        } catch (Throwable ignore) {
        }
        return false;
    }
}
