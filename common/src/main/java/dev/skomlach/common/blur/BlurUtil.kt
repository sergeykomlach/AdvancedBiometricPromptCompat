/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.common.blur

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewDebug
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.concurrent.futures.ResolvableFuture
import com.google.common.util.concurrent.ListenableFuture
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.cancellation.CancellationException

@SuppressLint("RestrictedApi")
object BlurUtil {
    private const val TAG = "BlurUtil"
    private var m: Method? = try {
        ViewDebug::class.java.getDeclaredMethod(
            "performViewCapture",
            View::class.java,
            Boolean::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }
    } catch (ignore: Throwable) {
        null
    }

    fun interface OnPublishListener {
        fun onBlurredScreenshot(originalBitmap: Bitmap, blurredBitmap: Bitmap?)
    }

    fun interface OnScreenshotListener {
        fun invoke(originalBitmap: Bitmap)
    }

    fun takeScreenshot(window: Window, listener: OnScreenshotListener) {
        GlobalScope.launch(Dispatchers.Main) {
            val bm = window.captureRegionToBitmap()
            bm.addListener({
                listener.invoke(bm.get())
            }, ExecutorHelper.executor)
        }
    }

    suspend fun takeScreenshotSync(window: Window): Bitmap? =
        withContext(Dispatchers.Main) {
            val bitmapDeferred = CompletableDeferred<Bitmap?>()
            try {
                val bm = window.captureRegionToBitmap()
                bitmapDeferred.complete(withContext(Dispatchers.IO) {
                    bm.get()
                })
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                LogCat.log(TAG, "takeScreenshotSync", e)
                bitmapDeferred.complete(null)
            }
            bitmapDeferred.await()
        }

    fun takeScreenshotAndBlur(window: Window, listener: OnPublishListener) {
        GlobalScope.launch(Dispatchers.Main) {
            val bm = window.captureRegionToBitmap()
            bm.addListener({
                blur(window.context, bm.get(), listener)
            }, ExecutorHelper.executor)
        }
    }

    fun takeScreenshot(view: View, listener: OnScreenshotListener) {
        ExecutorHelper.startOnBackground {
            //Crash happens on Blackberry due to mPowerSaveScalingMode is NULL
            val isBlackBerryBug = (Build.BRAND.equals(
                "Blackberry",
                ignoreCase = true
            ) || System.getProperty("os.name").equals("QNX", ignoreCase = true))
                    && try {
                val f =
                    view::class.java.declaredFields.firstOrNull { it.name == "mPowerSaveScalingMode" }
                val isAccessible = f?.isAccessible != false
                var result = false
                try {
                    f?.isAccessible = true
                    result = f?.get(view) == null
                } finally {
                    if (!isAccessible)
                        f?.isAccessible = false
                }
                result
            } catch (ignore: Throwable) {
                false
            }
            if (!isBlackBerryBug) {
                m?.let { method ->
                    val startMs = System.currentTimeMillis()
                    try {
                        (method.invoke(null, view, false) as Bitmap?)?.let { bm ->
                            try {
                                LogCat.log("BlurUtil.takeScreenshot#1 time - ${System.currentTimeMillis() - startMs} ms")
                                ExecutorHelper.post {
                                    listener.invoke(
                                        bm.copy(Bitmap.Config.ARGB_4444, false)
                                    )
                                }
                            } catch (e: Throwable) {
                                LogCat.logException(e)
                            }
                        }
                    } catch (ignore: Throwable) {
                        ExecutorHelper.post {
                            listener.invoke(
                                fallbackViewCapture(view) ?: return@post
                            )
                        }
                    }
                    return@startOnBackground
                }
            }
            ExecutorHelper.post { listener.invoke(fallbackViewCapture(view) ?: return@post) }
        }

    }

    suspend fun takeScreenshotSync(view: View): Bitmap? =
        withContext(Dispatchers.IO) {
            val bitmapDeferred = CompletableDeferred<Bitmap?>()
            //Crash happens on Blackberry due to mPowerSaveScalingMode is NULL
            val isBlackBerryBug = (Build.BRAND.equals(
                "Blackberry",
                ignoreCase = true
            ) || System.getProperty("os.name").equals("QNX", ignoreCase = true))
                    && try {
                val f =
                    view::class.java.declaredFields.firstOrNull { it.name == "mPowerSaveScalingMode" }
                val isAccessible = f?.isAccessible != false
                var result = false
                try {
                    f?.isAccessible = true
                    result = f?.get(view) == null
                } finally {
                    if (!isAccessible)
                        f?.isAccessible = false
                }
                result
            } catch (ignore: Throwable) {
                false
            }
            if (!isBlackBerryBug) {
                m?.let { method ->
                    val startMs = System.currentTimeMillis()
                    try {
                        (method.invoke(null, view, false) as Bitmap?)?.let { bm ->
                            try {
                                LogCat.log("BlurUtil.takeScreenshot#1 time - ${System.currentTimeMillis() - startMs} ms")
                                bitmapDeferred.complete(
                                    bm.copy(Bitmap.Config.ARGB_4444, false)
                                )
                            } catch (e: Throwable) {
                                if (e is CancellationException) {
                                    throw e
                                } else {
                                    bitmapDeferred.complete(null)
                                    LogCat.logException(e)
                                }
                            }
                        }
                    } catch (ignore: Throwable) {
                        if (ignore is CancellationException) {
                            throw ignore
                        } else {
                            bitmapDeferred.complete(null)
                            bitmapDeferred.complete(fallbackViewCapture(view))
                        }
                    }
                }
            }
            bitmapDeferred.complete(fallbackViewCapture(view))
            bitmapDeferred.await()
        }

    fun takeScreenshotAndBlur(view: View, listener: OnPublishListener) {
        ExecutorHelper.startOnBackground {
            //Crash happens on Blackberry due to mPowerSaveScalingMode is NULL
            val isBlackBerryBug = (Build.BRAND.equals(
                "Blackberry",
                ignoreCase = true
            ) || System.getProperty("os.name").equals("QNX", ignoreCase = true))
                    && try {
                val f =
                    view::class.java.declaredFields.firstOrNull { it.name == "mPowerSaveScalingMode" }
                val isAccessible = f?.isAccessible != false
                var result = false
                try {
                    f?.isAccessible = true
                    result = f?.get(view) == null
                } finally {
                    if (!isAccessible)
                        f?.isAccessible = false
                }
                result
            } catch (ignore: Throwable) {
                false
            }
            if (!isBlackBerryBug) {
                m?.let { method ->
                    val startMs = System.currentTimeMillis()
                    try {
                        (method.invoke(null, view, false) as Bitmap?)?.let { bm ->
                            try {
                                LogCat.log("BlurUtil.takeScreenshot#1 time - ${System.currentTimeMillis() - startMs} ms")
                                blur(
                                    view.context,
                                    bm.copy(Bitmap.Config.ARGB_4444, false),
                                    listener
                                )
                            } catch (e: Throwable) {
                                LogCat.logException(e)
                            }
                        }
                    } catch (ignore: Throwable) {
                        blur(
                            view.context,
                            fallbackViewCapture(view) ?: return@startOnBackground,
                            listener
                        )
                    }
                    return@startOnBackground
                }
            }

            blur(view.context, fallbackViewCapture(view) ?: return@startOnBackground, listener)
        }

    }

    private fun fallbackViewCapture(view: View): Bitmap? {
        val startMs = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val bm =
                    Bitmap.createBitmap(
                        view.measuredWidth,
                        view.measuredHeight,
                        Bitmap.Config.ARGB_4444
                    )
                val canvas = Canvas(bm)
                view.draw(canvas)
                LogCat.log("BlurUtil.takeScreenshot#2 time - ${System.currentTimeMillis() - startMs} ms")

                return bm
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
        }

        try {
            val old = view.isDrawingCacheEnabled
            if (!old) {
                view.isDrawingCacheEnabled = true
                view.buildDrawingCache()//WARNING: may produce exceptions in draw()
            }
            try {
                view.drawingCache?.let {
                    val bm = Bitmap.createBitmap(it)
                    LogCat.log("BlurUtil.takeScreenshot#3 time - ${System.currentTimeMillis() - startMs} ms")
                    return bm
                }
            } finally {
                if (!old) {
                    view.destroyDrawingCache()
                    view.isDrawingCacheEnabled = false
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        return null
    }

    private fun blur(context: Context, bkg: Bitmap, listener: OnPublishListener) {
        if (bkg.height == 0 || bkg.width == 0)
            return
        val startMs = System.currentTimeMillis()
        if (Utils.isAtLeastS) {
            ExecutorHelper.post {
                listener.onBlurredScreenshot(bkg, null)
            }
            return
        }

        val overlay = FastBlur.of(
            context, bkg, FastBlurConfig(
                radius = 4,
                sampling = 4,
                width = bkg.width,
                height = bkg.height
            )
        )
        ExecutorHelper.post {
            LogCat.log("BlurUtil.Blurring time - ${System.currentTimeMillis() - startMs} ms")
            listener.onBlurredScreenshot(
                bkg,
                overlay
            )
        }
    }


    class ReflectionException internal constructor(cause: Exception?) :
        Exception("Reflection access failed", cause)

    class ReflectiveMethod<T>(
        private val className: String,
        methodName: String,
        vararg paramTypes: Class<*>?
    ) {
        private val methodName: String
        private val paramTypes: Array<Class<*>>

        // lazy init
        private var initialized = false
        private var method: Method? = null

        /**
         * Creates a ReflectiveMethod.
         *
         * @param className the fully qualified class name that defines the method
         * @param methodName the method name to call
         * @param paramTypes the list of types of the method parameters, in order.
         */
        init {
            this.paramTypes = paramTypes as Array<Class<*>>
            this.methodName = methodName
        }

        /**
         * Invoke the instance method.
         *
         *
         * See [java.lang.reflect.Method.invoke]
         *
         * @param object the object the underlying method is invoked from
         * @param paramValues the arguments used for the method call
         * @return the return value of the method
         * @throws ReflectionException if call could not be completed
         */
        @Throws(ReflectionException::class)
        operator fun invoke(`object`: Any?, vararg paramValues: Any?): T {
            return try {
                initIfNecessary()
                method?.invoke(`object`, *paramValues) as T
            } catch (e: ClassNotFoundException) {
                throw ReflectionException(e)
            } catch (e: InvocationTargetException) {
                throw ReflectionException(e)
            } catch (e: NoSuchMethodException) {
                throw ReflectionException(e)
            } catch (e: IllegalAccessException) {
                throw ReflectionException(e)
            }
        }

        /**
         * Invoke th static method.
         *
         *
         * See [java.lang.reflect.Method.invoke]
         *
         * @param paramValues the arguments used for the method call
         * @return the return value of the method
         * @throws ReflectionException if call could not be completed
         */
        @Throws(ReflectionException::class)
        fun invokeStatic(vararg paramValues: Any?): T {
            return invoke(null, *paramValues)
        }

        @Synchronized
        @Throws(ClassNotFoundException::class, NoSuchMethodException::class)
        private fun initIfNecessary() {
            if (initialized) {
                return
            }
            method = Class.forName(className).getDeclaredMethod(methodName, *paramTypes)
            method?.isAccessible = true
            initialized = true
        }
    }


    object HardwareRendererCompat {
        private const val TAG = "HardwareRendererCompat"
        private val isDrawingEnabledReflectiveCall =
            ReflectiveMethod<Boolean>("android.graphics.HardwareRenderer", "isDrawingEnabled")
        private val setDrawingEnabledReflectiveCall = ReflectiveMethod<Void>(
            "android.graphics.HardwareRenderer",
            "setDrawingEnabled",
            Boolean::class.javaPrimitiveType
        )
        var isDrawingEnabled: Boolean
            /**
             * Call to [HardwareRenderer.isDrawingEnabled]
             *
             *
             * Will always return true if [HardwareRenderer.isDrawingEnabled] does not exist on
             * this platform.
             */
            get() = if (Build.VERSION.SDK_INT < 30) {
                // unsupported on these apis
                true
            } else try {
                isDrawingEnabledReflectiveCall.invokeStatic()
            } catch (e: ReflectionException) {
                LogCat.log(
                    TAG,
                    "Failed to reflectively call HardwareRenderer#isDrawingEnabled. It probably doesn't exist"
                            + " on this platform. Returning true."
                )
                true
            }
            /**
             * Call to [HardwareRenderer.setDrawingEnabled]
             *
             *
             * Has no effective if this method does not exist on this platform.
             */
            set(renderingEnabled) {
                if (Build.VERSION.SDK_INT < 30) {
                    // unsupported on these apis
                    return
                }
                try {
                    setDrawingEnabledReflectiveCall.invokeStatic(renderingEnabled)
                } catch (e: ReflectionException) {
                    LogCat.log(
                        TAG,
                        "Failed to reflectively call HardwareRenderer#setDrawingEnabled.  It probably doesn't"
                                + " exist on this platform. Ignoring."
                    )
                }
            }
    }


    /**
     * Asynchronously captures an image of the underlying view into a [Bitmap].
     *
     * For devices below [Build.VERSION_CODES#O] (or if the view's window cannot be determined), the
     * image is obtained using [View#draw]. Otherwise, [PixelCopy] is used.
     *
     * This method will also enable [HardwareRendererCompat#setDrawingEnabled(boolean)] if required.
     *
     * This API is primarily intended for use in lower layer libraries or frameworks. For test authors,
     * its recommended to use espresso or compose's captureToImage.
     *
     * This API currently does not work for View's hosted in Dialogs on APIs >= 26, as there is no
     * way to find a Dialog's Window. (see b/195673633).
     *
     * This API is currently experimental and subject to change or removal.
     */

    fun View.captureToBitmap(): ListenableFuture<Bitmap> {
        val bitmapFuture: ResolvableFuture<Bitmap> = ResolvableFuture.create()
        val mainExecutor = ExecutorHelper.executor

        // disable drawing again if necessary once work is complete
        if (!HardwareRendererCompat.isDrawingEnabled) {
            HardwareRendererCompat.isDrawingEnabled = true
            bitmapFuture.addListener(
                { HardwareRendererCompat.isDrawingEnabled = false },
                mainExecutor
            )
        }

        mainExecutor.execute {
            val forceRedrawFuture = forceRedraw()
            forceRedrawFuture.addListener({ generateBitmap(bitmapFuture) }, mainExecutor)
        }

        return bitmapFuture
    }

    /**
     * Trigger a redraw of the given view.
     *
     * Should only be called on UI thread.
     *
     * @return a [ListenableFuture] that will be complete once ui drawing is complete
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    fun View.forceRedraw(): ListenableFuture<Void> {
        val future: ResolvableFuture<Void> = ResolvableFuture.create()

        if (Build.VERSION.SDK_INT >= 29 && isHardwareAccelerated) {
            viewTreeObserver.registerFrameCommitCallback { future.set(null) }
        } else {
            viewTreeObserver.addOnDrawListener(
                object : ViewTreeObserver.OnDrawListener {
                    var handled = false

                    override fun onDraw() {
                        if (!handled) {
                            handled = true
                            future.set(null)
                            // cannot remove on draw listener inside of onDraw
                            post {
                                viewTreeObserver.removeOnDrawListener(
                                    this
                                )
                            }
                        }
                    }
                }
            )
        }
        invalidate()
        return future
    }

    private fun View.generateBitmap(bitmapFuture: ResolvableFuture<Bitmap>) {
        if (bitmapFuture.isCancelled) {
            return
        }
        val destBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444)
        when {
            Build.VERSION.SDK_INT < 26 -> generateBitmapFromDraw(destBitmap, bitmapFuture)
            this is SurfaceView -> generateBitmapFromSurfaceViewPixelCopy(destBitmap, bitmapFuture)
            else -> {
                val window = getActivity()?.window
                if (window != null) {
                    try {
                        generateBitmapFromPixelCopy(window, destBitmap, bitmapFuture)
                    } catch (e: IllegalArgumentException) { //Window doesn't have a backing surface
                        LogCat.logError(TAG, "generateBitmap:", e)
                        generateBitmapFromDraw(destBitmap, bitmapFuture) // fall back
                    }
                } else {
                    LogCat.log(
                        "View.captureToImage",
                        "Could not find window for view. Falling back to View#draw instead of PixelCopy"
                    )
                    generateBitmapFromDraw(destBitmap, bitmapFuture)
                }
            }
        }
    }

    @SuppressWarnings("NewApi")
    private fun SurfaceView.generateBitmapFromSurfaceViewPixelCopy(
        destBitmap: Bitmap,
        bitmapFuture: ResolvableFuture<Bitmap>
    ) {
        val onCopyFinished =
            PixelCopy.OnPixelCopyFinishedListener { result ->
                if (result == PixelCopy.SUCCESS) {
                    bitmapFuture.set(destBitmap)
                } else {
                    bitmapFuture.setException(
                        RuntimeException(
                            String.format(
                                "PixelCopy failed: %d",
                                result
                            )
                        )
                    )
                }
            }
        PixelCopy.request(this, null, destBitmap, onCopyFinished, handler)
    }

    private fun View.generateBitmapFromDraw(
        destBitmap: Bitmap,
        bitmapFuture: ResolvableFuture<Bitmap>
    ) {
        destBitmap.density = resources.displayMetrics.densityDpi
        computeScroll()
        val canvas = Canvas(destBitmap)
        canvas.translate((-scrollX).toFloat(), (-scrollY).toFloat())
        draw(canvas)
        bitmapFuture.set(destBitmap)
    }

    fun View.getActivity(): Activity? {
        fun Context.getActivity(): Activity? {
            return when (this) {
                is Activity -> this
                is ContextWrapper -> this.baseContext.getActivity()
                else -> null
            }
        }
        return context.getActivity()
    }

    private fun View.generateBitmapFromPixelCopy(
        window: Window,
        destBitmap: Bitmap,
        bitmapFuture: ResolvableFuture<Bitmap>
    ) {
        val locationInWindow = intArrayOf(0, 0)
        getLocationInWindow(locationInWindow)
        val x = locationInWindow[0]
        val y = locationInWindow[1]
        val boundsInWindow = Rect(x, y, x + width, y + height)

        return window.generateBitmapFromPixelCopy(boundsInWindow, destBitmap, bitmapFuture)
    }

    /**
     * Asynchronously captures an image of the underlying window into a [Bitmap].
     *
     * For devices below [Build.VERSION_CODES#O] the image is obtained using [View#draw] on the windows
     * decorView. Otherwise, [PixelCopy] is used.
     *
     * This method will also enable [HardwareRendererCompat#setDrawingEnabled(boolean)] if required.
     *
     * This API is primarily intended for use in lower layer libraries or frameworks. For test authors,
     * its recommended to use espresso or compose's captureToImage.
     *
     * This API is currently experimental and subject to change or removal.
     */

    fun Window.captureRegionToBitmap(boundsInWindow: Rect? = null): ListenableFuture<Bitmap> {
        val bitmapFuture: ResolvableFuture<Bitmap> = ResolvableFuture.create()
        val mainExecutor = ExecutorHelper.executor

        // disable drawing again if necessary once work is complete
        if (!HardwareRendererCompat.isDrawingEnabled) {
            HardwareRendererCompat.isDrawingEnabled = true
            bitmapFuture.addListener({
                HardwareRendererCompat.isDrawingEnabled = false
            }, mainExecutor)
        }

        mainExecutor.execute {
            val forceRedrawFuture = decorView.forceRedraw()
            forceRedrawFuture.addListener(
                { generateBitmap(boundsInWindow, bitmapFuture) },
                mainExecutor
            )
        }

        return bitmapFuture
    }

    private fun Window.generateBitmap(
        boundsInWindow: Rect? = null,
        bitmapFuture: ResolvableFuture<Bitmap>
    ) {
        val destBitmap =
            Bitmap.createBitmap(
                boundsInWindow?.width() ?: decorView.width,
                boundsInWindow?.height() ?: decorView.height,
                Bitmap.Config.ARGB_4444
            )
        when {
            Build.VERSION.SDK_INT < 26 ->
                // TODO: handle boundsInWindow
                decorView.generateBitmapFromDraw(destBitmap, bitmapFuture)

            else -> try {
                generateBitmapFromPixelCopy(boundsInWindow, destBitmap, bitmapFuture)
            } catch (e: IllegalArgumentException) { //Window doesn't have a backing surface
                decorView.generateBitmapFromDraw(destBitmap, bitmapFuture)
            }
        }
    }

    @SuppressWarnings("NewApi")
    private fun Window.generateBitmapFromPixelCopy(
        boundsInWindow: Rect? = null,
        destBitmap: Bitmap,
        bitmapFuture: ResolvableFuture<Bitmap>
    ) {
        val onCopyFinished =
            PixelCopy.OnPixelCopyFinishedListener { result ->
                if (result == PixelCopy.SUCCESS) {
                    bitmapFuture.set(destBitmap)
                } else {
                    bitmapFuture.setException(
                        RuntimeException(
                            String.format(
                                "PixelCopy failed: %d",
                                result
                            )
                        )
                    )
                }
            }
        PixelCopy.request(
            this,
            boundsInWindow,
            destBitmap,
            onCopyFinished,
            ExecutorHelper.handler
        )
    }

}