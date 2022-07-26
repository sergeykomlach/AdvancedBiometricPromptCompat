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
import android.os.Build
import android.view.View
import android.view.ViewDebug
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import java.lang.reflect.Method


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
                val isAccessible = f?.isAccessible ?: true
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
                                BiometricLoggerImpl.d("BlurUtil.takeScreenshot#1 time - ${System.currentTimeMillis() - startMs} ms")
                                blur(
                                    view,
                                    bm.copy(Bitmap.Config.RGB_565, false),
                                    listener
                                )
                            } catch (e: Throwable) {
                                BiometricLoggerImpl.e(e)
                            }
                        }
                    } catch (ignore: Throwable) {
                        fallbackViewCapture(view, listener)
                    }
                    return@startOnBackground
                }
            }

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
                BiometricLoggerImpl.d("BlurUtil.takeScreenshot#2 time - ${System.currentTimeMillis() - startMs} ms")
                blur(view, bm, listener)
                return
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
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
                    BiometricLoggerImpl.d("BlurUtil.takeScreenshot#3 time - ${System.currentTimeMillis() - startMs} ms")
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

    private fun blur(view: View, bkg: Bitmap, listener: OnPublishListener) {
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
            view.context, bkg, FastBlurConfig(
                radius = 4,
                sampling = 4,
                width = bkg.width,
                height = bkg.height
            )
        )
        ExecutorHelper.post {
            BiometricLoggerImpl.d("BlurUtil.Blurring time - ${System.currentTimeMillis() - startMs} ms")
            listener.onBlurredScreenshot(
                bkg,
                overlay
            )
        }
    }
}