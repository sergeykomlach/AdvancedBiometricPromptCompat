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

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

object BlurUtil {
    interface OnPublishListener {
        fun onBlurredScreenshot(originalBitmap: Bitmap, blurredBitmap: Bitmap)
    }

    fun takeScreenshotAndBlur(view: View, listener: OnPublishListener) {
        val startMs = System.currentTimeMillis()
        val isDone = AtomicBoolean(false)
        val takeScreenshot = {
            val decorView: View? = (view.context as Activity).window.peekDecorView()
            decorView?.let {
                if (Build.VERSION.SDK_INT >= 28) {
                    try {
                        val old = view.isDrawingCacheEnabled
                        if (!old) {
                            view.isDrawingCacheEnabled = true
                            view.buildDrawingCache()//WARNING: may produce exceptions in draw()
                        }
                        try {
                            view.drawingCache?.let {
                                val bm = Bitmap.createBitmap(it)
                                blur(view, bm, listener)
                                isDone.set(true)
                                BiometricLoggerImpl.d("BlurUtil.takeScreenshotAndBlur time - ${System.currentTimeMillis() - startMs} ms")
                            }
                        } finally {
                            if (!old) {
                                view.destroyDrawingCache()
                                view.isDrawingCacheEnabled = false
                            }
                        }
                    } catch (ex1: Throwable) {
                    }
                } else {
                    try {
                        val bm =
                            Bitmap.createBitmap(
                                view.width,
                                view.height,
                                Bitmap.Config.ARGB_8888
                            )
                        val canvas = Canvas(bm)
                        view.draw(canvas)
                        blur(view, bm, listener)
                        isDone.set(true)
                        BiometricLoggerImpl.d("BlurUtil.takeScreenshotAndBlur time - ${System.currentTimeMillis() - startMs} ms")
                    } catch (ex2: Throwable) {
                    }
                }
            }
        }
        takeScreenshot.invoke()
        if (!isDone.get()) view.viewTreeObserver.addOnDrawListener(object :
            ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (view.viewTreeObserver.isAlive) {
                    takeScreenshot.invoke()
                    if (isDone.get()) {
                        val onDrawListener = this
                        view.post {
                            try {
                            view.viewTreeObserver.removeOnDrawListener(onDrawListener)
                        } catch (e: Throwable) {
                            BiometricLoggerImpl.e(e)
                        }
                        }
                    }
                }
            }
        })
    }

    fun blur(view: View, bkg: Bitmap, listener: OnPublishListener) {

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