package dev.skomlach.biometric.compat.impl;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;

import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.skomlach.biometric.compat.utils.BiometricAuthWasCanceledByError;
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

@TargetApi(Build.VERSION_CODES.P)
class FocusLostDetection {
    private static final LruCache<String, Runnable> lruMap = new LruCache<>(10);

    static void stopListener(View activityContext) {
        Runnable runnable = lruMap.get(activityContext.toString());
        if (runnable != null) {
            activityContext.removeCallbacks(runnable);
            lruMap.remove(activityContext.toString());
        }
    }

    static void attachListener(View activityContext, final WindowFocusChangedListener listener) {
        final SoftReference<View> activityRef = new SoftReference<>(activityContext);
        if (!activityRef.get().isAttachedToWindow()) {
            activityRef.get().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    activityRef.get().removeOnAttachStateChangeListener(this);
                    checkForFocusAndStart(activityRef, listener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    activityRef.get().removeOnAttachStateChangeListener(this);
                }
            });
        } else {
            checkForFocusAndStart(activityRef, listener);
        }
    }

    private static void checkForFocusAndStart(@NonNull final SoftReference<View> activityRef, final WindowFocusChangedListener listener) {

        if (!activityRef.get().hasWindowFocus()) {
            ViewTreeObserver.OnWindowFocusChangeListener windowFocusChangeListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
                @Override
                public void onWindowFocusChanged(boolean focus) {
                    if (activityRef.get().hasWindowFocus()) {
                        activityRef.get().getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                        attachListenerInner(activityRef, listener);
                    }
                }
            };
            activityRef.get().getViewTreeObserver().addOnWindowFocusChangeListener(windowFocusChangeListener);
        } else {
            attachListenerInner(activityRef, listener);
        }
    }

    private static void attachListenerInner(final SoftReference<View> activityRef, final WindowFocusChangedListener listener) {
        try {

            stopListener(activityRef.get());

            final AtomicBoolean catchFocus = new AtomicBoolean(false);
            final AtomicBoolean currentFocus = new AtomicBoolean(activityRef.get().hasWindowFocus());
            ViewTreeObserver.OnWindowFocusChangeListener windowFocusChangeListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
                @Override
                public void onWindowFocusChanged(boolean focus) {
                    try {

                        boolean hasFocus = activityRef.get().hasWindowFocus();
                        if (currentFocus.get() != hasFocus) {
                            stopListener(activityRef.get());
                            //focus was changed
                            BiometricLoggerImpl.e("WindowFocusChangedListener" + ("View.hasFocus(1) - " + hasFocus));
                            catchFocus.set(true);
                            activityRef.get().getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                            listener.hasFocus(hasFocus);
                        }
                    } catch (Throwable e) {
                        BiometricLoggerImpl.e(e);
                    }
                }
            };

            activityRef.get().getViewTreeObserver().addOnWindowFocusChangeListener(windowFocusChangeListener);
            executeWithDelay(activityRef.get(), () -> {
                try {
                    if (!catchFocus.get()) {

                        boolean hasFocus = activityRef.get().hasWindowFocus();
                        //focus was changed
                        BiometricLoggerImpl.e("WindowFocusChangedListener" + ("View.hasFocus(2) - " + hasFocus));
                        activityRef.get().getViewTreeObserver().removeOnWindowFocusChangeListener(windowFocusChangeListener);
                        listener.hasFocus(hasFocus);
                        stopListener(activityRef.get());
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

    private static void executeWithDelay(View window, final Runnable runnable) {
        if (runnable == null)
            return;

        int delay;

        if (BiometricAuthWasCanceledByError.INSTANCE.isCanceledByError()) {
            delay = 2500;
            BiometricAuthWasCanceledByError.INSTANCE.resetCanceledByError();
        } else {
            delay = window.getResources().getInteger(android.R.integer.config_longAnimTime);
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
        window.postDelayed(runnable1, delay);
    }
}
