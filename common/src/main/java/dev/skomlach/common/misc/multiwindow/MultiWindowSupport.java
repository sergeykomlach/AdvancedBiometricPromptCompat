package dev.skomlach.common.misc.multiwindow;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.collection.LruCache;
import androidx.core.util.ObjectsCompat;
import com.jakewharton.rxrelay2.PublishRelay;
import java.lang.reflect.Method;

import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.logging.LogCat;
import dev.skomlach.common.misc.ActivityToolsKt;
import dev.skomlach.common.misc.ExecutorHelper;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class MultiWindowSupport {

    private static final LruCache<Configuration, Point> realScreenSize = new LruCache<>(1);
    private static final PublishRelay<Activity> activityResumedRelay = PublishRelay.create();
    private static final PublishRelay<Activity> activityDestroyedRelay = PublishRelay.create();
    private static DisplayManager displayManager;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                displayManager = (DisplayManager) AndroidContext.getAppContext().getSystemService(Context.DISPLAY_SERVICE);
            } catch (Throwable ignore) {
            }
        }

        AndroidContext.getAppContext()
                .registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

                    }

                    @Override
                    public void onActivityStarted(Activity activity) {

                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        activityResumedRelay.accept(activity);
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {

                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        activityDestroyedRelay.accept(activity);
                    }
                });
    }

    private final Activity activity;
    private final Disposable subscribeOnResume;
    private final Disposable subscribeOnDestroy;
    private boolean isMultiWindow = false;
    private boolean isWindowOnScreenBottom = false;
    private DisplayManager.DisplayListener displayListener;

    private final Consumer<Activity> onDestroyListener = new Consumer<Activity>() {
        @Override
        public void accept(Activity activity1) {
            if (ObjectsCompat.equals(activity1, activity)) {
                try {
                    unregisterDualScreenListeners();
                    subscribeOnResume.dispose();
                    subscribeOnDestroy.dispose();
                } catch (Exception e) {
                    LogCat.logException(e);
                }
            }
        }
    };

    private Configuration currentConfiguration = null;
    private final Consumer<Activity> onResumedListener = new Consumer<Activity>() {
        @Override
        public void accept(Activity activity1) {
            if (ObjectsCompat.equals(activity1, activity)) {
                try {
                    if (!ActivityToolsKt.isActivityFinished(activity)) {
                        updateState();
                    }
                } catch (Exception e) {
                    LogCat.logException(e);
                }
            }
        }
    };

    public MultiWindowSupport(Activity activity) {
        this.activity = activity;

        registerDualScreenListeners();
        this.subscribeOnResume = subscribeOnResume();
        this.subscribeOnDestroy = subscribeOnDestroy();
    }

    private Disposable subscribeOnResume() {
        return activityResumedRelay.subscribe(onResumedListener);
    }

    private Disposable subscribeOnDestroy() {
        return activityDestroyedRelay.subscribe(onDestroyListener);
    }

    private void registerDualScreenListeners() {
        unregisterDualScreenListeners();
        try {
            if (displayManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayManager.registerDisplayListener(displayListener, ExecutorHelper.INSTANCE.getHandler());
            }
        } catch (Throwable e) {
            LogCat.logException(e);
        }
    }

    private void unregisterDualScreenListeners() {
        try {
            if (displayManager != null && displayListener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayManager.unregisterDisplayListener(displayListener);
            }

        } catch (Throwable e) {
            LogCat.logException(e);
        }

        displayListener = null;
    }

    private void updateState() {
        if (!ActivityToolsKt.isActivityFinished(activity)) {
            checkIsInMultiWindow();
        } else {
            isMultiWindow = false;
            isWindowOnScreenBottom = false;
        }
    }

    //Unlike Android N method, this one support also non-Nougat+ multiwindow modes (like Samsung/LG/Huawei/etc solutions)
    private void checkIsInMultiWindow() {

        Rect rect =  new Rect();
            final ViewGroup decorView = activity.findViewById(Window.ID_ANDROID_CONTENT);
            decorView.getGlobalVisibleRect(rect);
            if (rect.width() == 0 && rect.height() == 0) {
                return;
            }

        Point realScreenSize = getRealScreenSize();

        int statusBarHeight = getStatusBarHeight();

        int navigationBarHeight = getNavigationBarHeight();
        int navigationBarWidth = getNavigationBarWidth();

        int h = realScreenSize.y - rect.height() - statusBarHeight - navigationBarHeight;

        int w = realScreenSize.x - rect.width();

        boolean isSmartphone = diagonalSize() < 7.0d;

        if (isSmartphone && getScreenOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
            h = h + navigationBarHeight;
            w = w - navigationBarWidth;
        }
        currentConfiguration = activity.getResources().getConfiguration();

        isMultiWindow = h != 0 || w != 0;

        int topPart = (realScreenSize.y / 5);

        isWindowOnScreenBottom = isSmartphone && (rect.top >= topPart || (isMultiWindow && rect.top == 0));
    }

    public boolean isConfigurationChanged() {
        //Configuration change can happens when Activity Window Size was changed, but Multiwindow was not switched
        if (!ActivityToolsKt.isActivityFinished(activity)) {
            return !ObjectsCompat.equals(activity.getResources().getConfiguration(), currentConfiguration);
        } else {
            return false;
        }
    }

    public boolean isWindowOnScreenBottom() {
        updateState();
        return isWindowOnScreenBottom;
    }

    public boolean isInMultiWindow() {

        //Should work on API24+ and support almost all devices types, include Chromebooks and foldable devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if (!ActivityToolsKt.isActivityFinished(activity)) {
                return activity.isInMultiWindowMode();
            }
        }
        //http://open-wiki.flyme.cn/index.php?title=%E5%88%86%E5%B1%8F%E9%80%82%E9%85%8D%E6%96%87%E6%A1%A3
        try {
            Class<?> clazz = Class.forName("meizu.splitmode.FlymeSplitModeManager");
            Method b = clazz.getMethod("getInstance", Context.class);
            Object instance = b.invoke(null, activity);
            Method m = clazz.getMethod("isSplitMode");
            return (boolean) m.invoke(instance);
        } catch (Throwable ignore) {

        }
        updateState();
        //general way - for OEM devices (Samsung, LG, Huawei) and/or in case API24 not fired for some reasons
        return isMultiWindow;
    }

    private int getNavigationBarHeight() {
        if (!hasNavBar()) {
            return 0;
        }

        Resources resources = activity.getResources();
        int orientation = getScreenOrientation();

        boolean isSmartphone = diagonalSize() < 7.0d;

        int resourceId;
        if (!isSmartphone) {
            resourceId = resources.getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ?
                    "navigation_bar_height" : "navigation_bar_height_landscape", "dimen", "android");
        } else {
            resourceId = resources.getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ?
                    "navigation_bar_height" : "navigation_bar_width", "dimen", "android");
        }

        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }

        return 0;
    }

    private int getNavigationBarWidth() {
        if (!hasNavBar()) {
            return 0;
        }

        Resources resources = activity.getResources();
        int orientation = getScreenOrientation();

        boolean isSmartphone = diagonalSize() < 7.0d;

        int resourceId;
        if (!isSmartphone) {
            resourceId = resources.getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ?
                    "navigation_bar_height_landscape" : "navigation_bar_height", "dimen", "android");
        } else {
            resourceId = resources.getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ?
                    "navigation_bar_width" : "navigation_bar_height", "dimen", "android");
        }

        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }

        return 0;
    }

    private boolean hasNavBar() {

        Point realSize = getRealScreenSize();
        int realHeight = realSize.y;
        int realWidth = realSize.x;
        WindowManager windowManager = (WindowManager) activity
                .getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        display.getMetrics(displayMetrics);

        int displayHeight = displayMetrics.heightPixels;
        int displayWidth = displayMetrics.widthPixels;
        if ((realWidth - displayWidth) > 0 || (realHeight - displayHeight) > 0) {
            return true;
        }

        boolean hasMenuKey = ViewConfiguration.get(activity).hasPermanentMenuKey();
        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
        boolean hasHomeKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME);
        boolean hasNoCapacitiveKeys = !hasMenuKey && !hasBackKey && !hasHomeKey;
        Resources resources = activity.getResources();
        int id = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        boolean hasOnScreenNavBar = id > 0 && resources.getBoolean(id);
        return hasOnScreenNavBar || hasNoCapacitiveKeys;
    }

    private int getStatusBarHeight() {
        // status bar height
        int statusBarHeight = 0;
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight;
    }

    private Point getRealScreenSize() {
        Configuration configuration = activity.getResources().getConfiguration();
        Point point = realScreenSize.get(configuration);
        if (point != null) {
            return point;
        } else {
            WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            int realWidth;
            int realHeight;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                //new pleasant way to get real metrics
                DisplayMetrics realMetrics = new DisplayMetrics();
                display.getRealMetrics(realMetrics);
                realWidth = realMetrics.widthPixels;
                realHeight = realMetrics.heightPixels;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                //reflection for this weird in-between time
                try {
                    Method mGetRawH = Display.class.getMethod("getRawHeight");
                    Method mGetRawW = Display.class.getMethod("getRawWidth");
                    realWidth = (Integer) mGetRawW.invoke(display);
                    realHeight = (Integer) mGetRawH.invoke(display);
                } catch (Exception e) {
                    //this may not be 100% accurate, but it's all we've got
                    realWidth = display.getWidth();
                    realHeight = display.getHeight();
                }
            } else {
                //This should be close, as lower API devices should not have window navigation bars
                realWidth = display.getWidth();
                realHeight = display.getHeight();
            }
            final Point size = new Point(realWidth, realHeight);
            realScreenSize.put(configuration, size);
            return size;
        }
    }

    private int getScreenOrientation() {
        int orientation = activity.getResources().getConfiguration().orientation;


        if (orientation == Configuration.ORIENTATION_UNDEFINED) {
            WindowManager windowManager = (WindowManager) activity
                    .getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            if (display.getWidth() == display.getHeight()) {
                orientation = Configuration.ORIENTATION_SQUARE;
            } else {
                if (display.getWidth() < display.getHeight()) {
                    orientation = Configuration.ORIENTATION_PORTRAIT;
                } else {
                    orientation = Configuration.ORIENTATION_LANDSCAPE;
                }
            }
        }
        return orientation;
    }

    private double diagonalSize() {

        // Compute real screen size
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();

        Point realSize = getRealScreenSize();

        float screenWidth = realSize.x / dm.xdpi;

        float screenHeight = realSize.y / dm.ydpi;

        return Math.sqrt(Math.pow(screenWidth, 2) + Math.pow(screenHeight, 2));
    }
}
