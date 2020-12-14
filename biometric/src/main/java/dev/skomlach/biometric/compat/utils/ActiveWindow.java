package dev.skomlach.biometric.compat.utils;

import android.os.Build;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.RestrictTo;
import androidx.fragment.app.FragmentActivity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

@RestrictTo(RestrictTo.Scope.LIBRARY)

@SuppressWarnings("unchecked")
public class ActiveWindow {
    public static View getActiveView(FragmentActivity activity) {
        List<ViewParent> list = getViewRoots();
        View topView = null;
        for (int i = 0; i < list.size(); i++) {
            ViewParent viewParent = list.get(i);
            try {
                Class<?> clazz = Class.forName("android.view.ViewRootImpl");
                View view = (View) clazz.getMethod("getView").invoke(viewParent);
                int type = ((WindowManager.LayoutParams) view.getLayoutParams()).type;
                if(type >= WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW) {
                    continue;
                }
                if (topView == null) {
                    topView = view;
                } else {
                    int topViewType = ((WindowManager.LayoutParams) topView.getLayoutParams()).type;
                    if (type > topViewType) {
                        topView = view;
                    }
                    else if(view.hasWindowFocus() && !topView.hasWindowFocus()) {
                        topView = view;
                    }
                }
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, "ActiveWindow");
            }
        }
        if (topView != null) {
            return topView;
        } else {
            return activity.findViewById(Window.ID_ANDROID_CONTENT);
        }
    }

    private static List<ViewParent> getViewRoots() {

        List<ViewParent> viewRoots = new ArrayList<>();

        try {
            Object windowManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                windowManager = Class.forName("android.view.WindowManagerGlobal")
                        .getMethod("getInstance").invoke(null);
            } else {
                windowManager = Class.forName("android.view.WindowManagerImpl")
                        .getMethod("getDefault").invoke(null);
            }

            Field rootsField = windowManager.getClass().getDeclaredField("mRoots");
            rootsField.setAccessible(true);

            Field stoppedField = Class.forName("android.view.ViewRootImpl")
                    .getDeclaredField("mStopped");
            boolean isAccessible = stoppedField.isAccessible();
            try {
                if (!isAccessible)
                    stoppedField.setAccessible(true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    List<ViewParent> viewParents = (List<ViewParent>) rootsField.get(windowManager);
                    // Filter out inactive view roots
                    for (ViewParent viewParent : viewParents) {
                        boolean stopped = (boolean) stoppedField.get(viewParent);
                        if (!stopped) {
                            viewRoots.add(viewParent);
                        }
                    }
                } else {
                    ViewParent[] viewParents = (ViewParent[]) rootsField.get(windowManager);
                    // Filter out inactive view roots
                    for (ViewParent viewParent : viewParents) {
                        boolean stopped = (boolean) stoppedField.get(viewParent);
                        if (!stopped) {
                            viewRoots.add(viewParent);
                        }
                    }
                }
            } finally {
                if (!isAccessible)
                    stoppedField.setAccessible(false);
            }
        } catch (Exception e) {
            BiometricLoggerImpl.e(e, "ActiveWindow");
        }

        return viewRoots;
    }
}
