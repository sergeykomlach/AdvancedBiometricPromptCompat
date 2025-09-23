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

package dev.skomlach.biometric.compat.impl.dialogs

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.R

class FingerprintIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(
    context, attrs
) {

    private var state = State.OFF
    private var type = BiometricType.BIOMETRIC_FINGERPRINT
    private var color: Int? = R.color.material_blue_500

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setState(state, false, type)
    }

    fun tintColor(color: Int?) {
        this.color = color
        setTint(state)
    }

    fun setState(state: State, type: BiometricType) {
        setState(state, true, type)
    }

    private fun setTint(state: State) {
        if (state == State.ON) {
            color?.let {
                setColorFilter(color ?: return)
                return
            }
        }
        setColorFilter(0)
    }

    fun setState(state: State, animate: Boolean, type: BiometricType) {
        if (state == this.state) return
        setTint(state)
        if (type == BiometricType.BIOMETRIC_FINGERPRINT || type == BiometricType.BIOMETRIC_ANY) {
            @DrawableRes val resId = getDrawable(this.state, state, animate)
            if (resId == 0) {
                setImageDrawable(null)
            } else {
                var icon: Drawable? = null

                if (animate) {
                    try {
                        icon = AnimatedVectorDrawableCompat.create(context, resId)
                    } catch (ignore: Throwable) {
                    }
                }
                if (icon == null) {
                    icon = getDrawable(context, resId, context.theme)
                }

                setImageDrawable(icon)
                if (icon is Animatable) {
                    (icon as Animatable).start()
                }
            }
        } else {
            val prevDrawable = drawable ?: ColorDrawable(Color.TRANSPARENT)
            val resId = getDrawable(this.state, state, false)
            if (resId == 0) {
                setImageDrawable(null)
            } else {
                val currentImage = if (state == State.ON)
                    getDrawable(context, type.iconId, context.theme)
                else {
                    getDrawable(context, resId, context.theme)
                }
                val transitionDrawable = TransitionDrawable(
                    arrayOf(
                        prevDrawable,
                        currentImage
                    )
                )
                transitionDrawable.isCrossFadeEnabled = true
                setImageDrawable(transitionDrawable)
                transitionDrawable.startTransition(context.resources.getInteger(android.R.integer.config_shortAnimTime))
            }
        }
        this.state = state
    }

    // Keep in sync with attrs.

    enum class State {
        OFF, ON, ERROR
    }

    companion object {
        //fix java.lang.IllegalStateException: Software rendering doesn't support hardware bitmaps
        //solution from https://stackoverflow.com/a/50015989
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
        private val isAndroidO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        private val format =
            Bitmap.Config.ARGB_4444//if (isAndroidO) Bitmap.Config.ARGB_8888 else Bitmap.Config.ARGB_4444

        @RequiresApi(Build.VERSION_CODES.P)
        private fun getDrawableAndroidR(context: Context, resId: Int): Drawable? {
            var dr: Drawable? = null
            try {
                val source = ImageDecoder.createSource(context.resources, resId)
                dr =
                    ImageDecoder.decodeDrawable(source) { decoder, info, source ->
                        //https://developer.android.com/reference/android/graphics/ImageDecoder#ALLOCATOR_HARDWARE
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
            } catch (ex: Throwable) {
                dr = null
            }
            return dr
        }

        private fun getDrawable(
            context: Context,
            @DrawableRes resId: Int,
            theme: Resources.Theme?
        ): Drawable? {
            var dr: Drawable?
            dr = try {
                AppCompatResources.getDrawable(context, resId)
            } catch (e: Exception) {
                null
            }
            if (dr == null) dr = try {
                ContextCompat.getDrawable(context, resId)
            } catch (e: Exception) {
                null
            }
            if (dr == null) try {
                var targetThemeRes = 0
                try {
                    val wrapper: Class<*> = Context::class.java
                    val method = wrapper.getMethod("getThemeResId")
                    method.isAccessible = true
                    targetThemeRes = method.invoke(context) as Int
                } catch (ex: Exception) {
                }
                if (targetThemeRes == 0) targetThemeRes = R.style.Theme_BiometricPromptDialog
                dr = VectorDrawableCompat.create(
                    context.resources,
                    resId,
                    theme ?: ContextThemeWrapper(context, targetThemeRes).theme
                )
            } catch (e: Exception) {
                dr = null
            }

            //try to use ImageDecoder API (Android P+)
            if (dr == null && Build.VERSION.SDK_INT >= 28) {
                dr = getDrawableAndroidR(context, resId)
            } else if (dr == null) try {
                val options = BitmapFactory.Options()
                options.inPreferredConfig = format
                if (Build.VERSION.SDK_INT >= 26) {
                    options.outConfig = format
                }
                val bm = BitmapFactory.decodeResource(context.resources, resId, options)
                dr = BitmapDrawable(bm)
            } catch (e: Exception) {
                dr = null
            }
            return dr
        }

        @DrawableRes
        private fun getDrawable(currentState: State, newState: State, animate: Boolean): Int {
            return when (newState) {
                State.OFF -> {
                    if (animate) {
                        if (currentState == State.ON) {
                            return R.drawable.fingerprint_draw_off_animation
                        } else if (currentState == State.ERROR) {
                            return R.drawable.fingerprint_error_off_animation
                        }
                    }
                    0
                }

                State.ON -> {
                    if (animate) {
                        if (currentState == State.OFF) {
                            return R.drawable.fingerprint_draw_on_animation
                        } else if (currentState == State.ERROR) {
                            return R.drawable.fingerprint_error_state_to_fp_animation
                        }
                    }
                    R.drawable.fingerprint_fingerprint
                }

                State.ERROR -> {
                    if (animate) {
                        if (currentState == State.ON) {
                            return R.drawable.fingerprint_fp_to_error_state_animation
                        } else if (currentState == State.OFF) {
                            return R.drawable.fingerprint_error_on_animation
                        }
                    }
                    R.drawable.fingerprint_error
                }

                else -> throw IllegalArgumentException("Unknown state: $newState")
            }
        }
    }
}