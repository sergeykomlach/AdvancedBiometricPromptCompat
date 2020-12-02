package dev.skomlach.biometric.compat.impl;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;

import dev.skomlach.biometric.compat.utils.BiometricAuthWasCanceledByError;
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;


import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.skomlach.common.misc.ActivityToolsKt.hasWindowFocus;

@TargetApi(Build.VERSION_CODES.P)
class FocusLostDetection {
    private static final LruCache<String, Runnable> lruMap = new LruCache<>(10);

    static void stopListener(Activity activityContext) {
        Runnable runnable = lruMap.get(activityContext.getWindow().toString());
        if (runnable != null) {
            View view = activityContext.findViewById(Window.ID_ANDROID_CONTENT);
            view.removeCallbacks(runnable);
            lruMap.remove(activityContext.getWindow().toString());
        }
    }

    static void attachListener(Activity activityContext, final WindowFocusChangedListener listener) {
        final SoftReference<Activity> activityRef = new SoftReference<>(activityContext);
        View d = activityContext.findViewById(Window.ID_ANDROID_CONTENT);
        if (!d.isAttachedToWindow()) {
            d.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    d.removeOnAttachStateChangeListener(this);
                    checkForFocusAndStart(activityRef, listener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    d.removeOnAttachStateChangeListener(this);
                }
            });
        } else {
            checkForFocusAndStart(activityRef, listener);
        }
    }

    private static void checkForFocusAndStart(@NonNull final SoftReference<Activity> activityRef, final WindowFocusChangedListener listener) {
        Activity activityContext = activityRef.get();

        if (!hasWindowFocus(activityContext)) {
            View d = activityContext.findViewById(Window.ID_ANDROID_CONTENT);
            ViewTreeObserver.OnWindowFocusChangeListener windowFocusChangeListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
                @Override
                public void onWindowFocusChanged(boolean focus) {
                    if (hasWindowFocus(activityRef.get())) {
                        d.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                        attachListenerInner(activityRef, listener);
                    }
                }
            };
            d.getViewTreeObserver().addOnWindowFocusChangeListener(windowFocusChangeListener);
        } else {
            attachListenerInner(activityRef, listener);
        }
    }

    private static void attachListenerInner(final SoftReference<Activity> activityRef, final WindowFocusChangedListener listener) {
        try {
            Activity activityContext = activityRef.get();
            stopListener(activityContext);

            final AtomicBoolean catchFocus = new AtomicBoolean(false);
            final AtomicBoolean currentFocus = new AtomicBoolean(hasWindowFocus(activityContext));
            ViewTreeObserver.OnWindowFocusChangeListener windowFocusChangeListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
                @Override
                public void onWindowFocusChanged(boolean focus) {
                    try {
                        Activity activity = activityRef.get();
                        boolean hasFocus = hasWindowFocus(activity);
                        if (currentFocus.get() != hasFocus) {
                            stopListener(activity);
                            //focus was changed
                            BiometricLoggerImpl.e("WindowFocusChangedListener" + ("Activity.hasFocus(1) - " + hasFocus));
                            catchFocus.set(true);
                            activity.findViewById(Window.ID_ANDROID_CONTENT).getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                            listener.hasFocus(hasFocus);
                        }
                    } catch (Throwable e) {
                        BiometricLoggerImpl.e(e);
                    }
                }
            };

            activityContext.findViewById(Window.ID_ANDROID_CONTENT).getViewTreeObserver().addOnWindowFocusChangeListener(windowFocusChangeListener);
            executeWithDelay(activityContext, () -> {
                try {
                    if (!catchFocus.get()) {
                        Activity activity = activityRef.get();
                        boolean hasFocus = hasWindowFocus(activity);
                        //focus was changed
                        BiometricLoggerImpl.e("WindowFocusChangedListener" + ("Activity.hasFocus(2) - " + hasFocus));
                        activity.findViewById(Window.ID_ANDROID_CONTENT).getViewTreeObserver().removeOnWindowFocusChangeListener(windowFocusChangeListener);
                        listener.hasFocus(hasFocus);
                        stopListener(activity);
                    }
                } catch (Throwable e) {
                    BiometricLoggerImpl.e(e);
                }
            });//wait when show animation end
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        listener.onStartWatching();
    }

    private static void executeWithDelay(Activity window, final Runnable runnable) {
        if (runnable == null)
            return;
        View view = window.findViewById(Window.ID_ANDROID_CONTENT);

        int delay;

        if (BiometricAuthWasCanceledByError.INSTANCE.isCanceledByError()) {
            delay = 2500;
            BiometricAuthWasCanceledByError.INSTANCE.resetCanceledByError();
        } else {
            delay = view.getResources().getInteger(android.R.integer.config_longAnimTime);
        }

        SoftReference<Runnable> runnableSoftReference = new SoftReference<>(runnable);

        Runnable runnable1 = new Runnable() {
            @Override
            public void run() {
                Runnable r = runnableSoftReference.get();
                if (r != null)
                    r.run();
            }
        };
        lruMap.put(window.toString(), runnable1);
        view.postDelayed(runnable1, delay);
    }
}
