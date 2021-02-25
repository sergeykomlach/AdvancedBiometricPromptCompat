package dev.skomlach.biometric.compat.impl

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import androidx.collection.LruCache
import androidx.core.view.ViewCompat
import dev.skomlach.biometric.compat.utils.BiometricAuthWasCanceledByError
import dev.skomlach.biometric.compat.utils.WindowFocusChangedListener
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object FocusLostDetection {
    private val lruMap = LruCache<String, Runnable>(10)
    fun stopListener(activityContext: View?) {
        val runnable = lruMap[activityContext.toString()]
        if (runnable != null) {
            activityContext?.removeCallbacks(runnable)
            lruMap.remove(activityContext.toString())
        }
    }

    fun attachListener(activityContext: View, listener: WindowFocusChangedListener) {
        val activityRef = SoftReference(activityContext)
        if (!ViewCompat.isAttachedToWindow(activityRef.get()?:activityContext)) {
            activityRef.get()?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        activityRef.get()?.removeOnAttachStateChangeListener(this)
                        checkForFocusAndStart(activityRef, listener)
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        activityRef.get()?.removeOnAttachStateChangeListener(this)
                    }
                })
        } else {
            checkForFocusAndStart(activityRef, listener)
        }
    }

    private fun checkForFocusAndStart(
        activityRef: SoftReference<View>,
        listener: WindowFocusChangedListener
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && activityRef.get()?.hasWindowFocus() == false
        ) {
            val windowFocusChangeListener: OnWindowFocusChangeListener =
                object : OnWindowFocusChangeListener {
                    override fun onWindowFocusChanged(focus: Boolean) {
                        if (activityRef.get()?.hasWindowFocus() == true) {
                            activityRef.get()?.viewTreeObserver?.removeOnWindowFocusChangeListener(
                                this
                            )
                            attachListenerInner(activityRef, listener)
                        }
                    }
                }
            activityRef.get()?.viewTreeObserver?.addOnWindowFocusChangeListener(
                windowFocusChangeListener
            )
        } else {
            attachListenerInner(activityRef, listener)
        }
    }

    private fun attachListenerInner(
        activityRef: SoftReference<View>,
        listener: WindowFocusChangedListener
    ) {
        try {
            stopListener(activityRef.get())
            val catchFocus = AtomicBoolean(false)
            val currentFocus = AtomicBoolean(
                activityRef.get()?.hasWindowFocus() == true
            )
            val task = AtomicReference<Runnable?>(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val windowFocusChangeListener: OnWindowFocusChangeListener =
                    object : OnWindowFocusChangeListener {
                        override fun onWindowFocusChanged(focus: Boolean) {
                            try {
                                val hasFocus = activityRef.get()?.hasWindowFocus()
                                if (currentFocus.get() != hasFocus) {
                                    stopListener(activityRef.get())
                                    //focus was changed
                                    e("WindowFocusChangedListenerView.hasFocus(1) - $hasFocus")
                                    catchFocus.set(true)
                                    activityRef.get()?.viewTreeObserver?.removeOnWindowFocusChangeListener(
                                        this
                                    )
                                    listener.hasFocus(hasFocus == true)
                                }
                            } catch (e: Throwable) {
                                e(e)
                            }
                        }
                    }
                activityRef.get()?.viewTreeObserver?.addOnWindowFocusChangeListener(
                    windowFocusChangeListener
                )
                task.set(Runnable {
                    try {
                        if (!catchFocus.get()) {
                            val hasFocus = activityRef.get()?.hasWindowFocus()
                            //focus was changed
                            e("WindowFocusChangedListenerView.hasFocus(2) - $hasFocus")
                            activityRef.get()?.viewTreeObserver?.removeOnWindowFocusChangeListener(
                                windowFocusChangeListener
                            )
                            listener.hasFocus(hasFocus == true)
                            stopListener(activityRef.get())
                        }
                    } catch (e: Throwable) {
                        e(e)
                    }
                }) //wait when show animation end
            } else {
                task.set(Runnable {
                    try {
                        if (!catchFocus.get()) {
                            val hasFocus = activityRef.get()?.hasWindowFocus()
                            //focus was changed
                            e("WindowFocusChangedListenerView.hasFocus(2) - $hasFocus")
                            listener.hasFocus(hasFocus == true)
                            stopListener(activityRef.get())
                        }
                    } catch (e: Throwable) {
                        e(e)
                    }
                }) //wait when show animation end
            }
            executeWithDelay(activityRef.get(), task.get())
        } catch (e: Throwable) {
            e(e)
        }
        listener.onStartWatching()
    }

    private fun executeWithDelay(window: View?, runnable: Runnable?) {
        if (runnable == null) return
        val delay: Int
        if (BiometricAuthWasCanceledByError.INSTANCE.isCanceledByError) {
            delay = 2500
            BiometricAuthWasCanceledByError.INSTANCE.resetCanceledByError()
        } else {
            delay = window?.resources?.getInteger(android.R.integer.config_longAnimTime)?:500
        }
        val runnableSoftReference = SoftReference(runnable)
        val runnable1 = Runnable {
            val r = runnableSoftReference.get()
            r?.run()
        }
        lruMap.put(window.toString(), runnable1)
        window?.postDelayed(runnable1, delay.toLong())
    }
}