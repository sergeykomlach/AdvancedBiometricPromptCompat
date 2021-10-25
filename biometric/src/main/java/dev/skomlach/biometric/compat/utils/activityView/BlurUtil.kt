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
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
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
        fun onBlurredScreenshot(originalBitmap: Bitmap, blurredBitmap: Bitmap?)
    }

    @Synchronized
    fun takeScreenshotAndBlur(view: View, listener: OnPublishListener) {

        //Crash happens on Blackberry due to mPowerSaveScalingMode is NULL
        val isBlackBerryBug = try {
            val f =
                view::class.java.declaredFields.firstOrNull { it.name == "mPowerSaveScalingMode" }
            val isAccessible = f?.isAccessible ?: true
            var value: Any? = null
            try {
                f?.isAccessible = true
                value = f?.get(view)
            } finally {
                if (!isAccessible)
                    f?.isAccessible = false
            }
            value == null
        } catch (ignore: Throwable) {
            false
        }

        System.gc()
        if (!isBlackBerryBug) {
            m?.let { method ->
                val startMs = System.currentTimeMillis()
                ExecutorHelper.startOnBackground {
                    try {
                        (method.invoke(null, view, false) as Bitmap?)?.let { bm ->
                            ExecutorHelper.handler.post {
                                try {
                                    BiometricLoggerImpl.d("BlurUtil.takeScreenshot time - ${System.currentTimeMillis() - startMs} ms")
                                    blur(
                                        view,
                                        bm.copy(Bitmap.Config.ARGB_8888, false),
                                        listener
                                    )
                                } catch (e: Throwable) {
                                    BiometricLoggerImpl.e(e)
                                }
                            }
                        }
                    } catch (ignore: Throwable) {
                        ExecutorHelper.handler.post {
                            fallbackViewCapture(view, listener)
                        }
                    }
                }
                return
            }
        }
        fallbackViewCapture(view, listener)
    }

    private fun fallbackViewCapture(view: View, listener: OnPublishListener) {
        val startMs = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val bm =
                    Bitmap.createBitmap(
                        view.measuredWidth,
                        view.measuredHeight,
                        Bitmap.Config.ARGB_8888
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
        if (bkg.height == 0 || bkg.width == 0)
            return
        val startMs = System.currentTimeMillis()
        if (Utils.isAtLeastS) {
            listener.onBlurredScreenshot(bkg, null)
            return
        }

        val scaleFactor = 1f
        val radius = 8f
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