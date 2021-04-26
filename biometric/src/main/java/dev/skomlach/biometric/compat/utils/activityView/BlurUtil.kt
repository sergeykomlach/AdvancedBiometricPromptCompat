/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.utils.activityView

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.View
import android.view.ViewDebug
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.lang.reflect.Method
import kotlin.math.roundToInt

@SuppressLint("PrivateApi")
object BlurUtil {
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

    interface OnPublishListener {
        fun onBlurredScreenshot(originalBitmap: Bitmap, blurredBitmap: Bitmap)
    }

    @Synchronized
    fun takeScreenshotAndBlur(view: View, listener: OnPublishListener) {
        m?.let { method ->
            val startMs = System.currentTimeMillis()
            ExecutorHelper.INSTANCE.startOnBackground {
                try {
                    (method.invoke(null, view, false) as Bitmap?)?.let { bm ->
                        ExecutorHelper.INSTANCE.handler.post {
                            try {
                                BiometricLoggerImpl.d("BlurUtil.takeScreenshot time - ${System.currentTimeMillis() - startMs} ms")
                                blur(
                                    view,
                                    bm.copy(Bitmap.Config.RGB_565, false),
                                    listener
                                )
                            } catch (e: Throwable) {
                                BiometricLoggerImpl.e(e)
                            }
                        }
                    }
                } catch (ignore: Throwable) {
                    ExecutorHelper.INSTANCE.handler.post {
                        fallbackViewCapture(view, listener)
                    }
                }
            }
        } ?: run {
            fallbackViewCapture(view, listener)
        }
    }

    private fun fallbackViewCapture(view: View, listener: OnPublishListener) {
        val startMs = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val bm =
                    Bitmap.createBitmap(
                        view.measuredWidth,
                        view.measuredHeight,
                        Bitmap.Config.RGB_565
                    )
                val canvas = Canvas(bm)
                view.draw(canvas)
                blur(view, bm, listener)
                BiometricLoggerImpl.d("BlurUtil.takeScreenshotAndBlur time - ${System.currentTimeMillis() - startMs} ms")
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        } else {
            try {
                val old = view.isDrawingCacheEnabled
                if (!old) {
                    view.isDrawingCacheEnabled = true
                    view.buildDrawingCache()//WARNING: may produce exceptions in draw()
                }
                try {
                    view.drawingCache?.let {
                        val bm = Bitmap.createBitmap(it)
                        BiometricLoggerImpl.d("BlurUtil.takeScreenshot time - ${System.currentTimeMillis() - startMs} ms")
                        blur(view, bm, listener)
                    }
                } finally {
                    if (!old) {
                        view.destroyDrawingCache()
                        view.isDrawingCacheEnabled = false
                    }
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
    }

    private fun blur(view: View, bkg: Bitmap, listener: OnPublishListener) {

        val startMs = System.currentTimeMillis()
        val scaleFactor = 8f
        val radius = 2f
        var overlay = Bitmap.createBitmap(
            (view.measuredWidth / scaleFactor).toInt(),
            (view.measuredHeight / scaleFactor).toInt(), Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(overlay)
        canvas.translate(-view.left / scaleFactor, -view.top / scaleFactor)
        canvas.scale(1 / scaleFactor, 1 / scaleFactor)
        val paint = Paint()
        paint.flags = Paint.FILTER_BITMAP_FLAG
        canvas.drawBitmap(bkg, 0f, 0f, paint)

        overlay = FastBlur.doBlur(overlay, radius.roundToInt(), true)
        BiometricLoggerImpl.d("BlurUtil.Blurring time - ${System.currentTimeMillis() - startMs} ms")
        listener.onBlurredScreenshot(bkg, overlay)
    }
}