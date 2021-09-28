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

package dev.skomlach.biometric.compat.impl.dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.annotation.RestrictTo
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setState(State.OFF, false, BiometricType.BIOMETRIC_FINGERPRINT)
    }

    private var state = State.OFF
    fun setState(state: State, type : BiometricType) {
        setState(state, true, type)
    }

    fun setState(state: State, animate: Boolean, type : BiometricType) {
        if (state == this.state) return
        if(type == BiometricType.BIOMETRIC_FINGERPRINT || type == BiometricType.BIOMETRIC_ANY) {
            @DrawableRes val resId = getDrawable(this.state, state, animate)
            if (resId == 0) {
                setImageDrawable(null)
            } else {
                var icon: Drawable? = null
                try {
                    if (animate) {
                        icon = AnimatedVectorDrawableCompat.create(context, resId)
                    }
                    if (icon == null) {
                        icon = VectorDrawableCompat.create(resources, resId, context.theme)
                    }
                } catch (ignore: Throwable) {
                    icon = ContextCompat.getDrawable(context, resId)
                }
                setImageDrawable(icon)
                if (icon is Animatable) {
                    (icon as Animatable).start()
                }
            }
        } else{
            val prevDrawable = drawable ?: ColorDrawable(Color.TRANSPARENT)
            val resId = getDrawable(this.state, state, false)
            if (resId == 0) {
                setImageDrawable(null)
            } else {
                val currentImage = if (state == State.ON)
                    try {
                        VectorDrawableCompat.create(resources, type.iconId, context.theme)?.apply {
                            setTintList(
                                ColorStateList.valueOf(
                                    ContextCompat.getColor(
                                        context,
                                        R.color.material_blue_500
                                    )
                                )
                            )
                        }
                    } catch (ignore: Throwable) {
                        ContextCompat.getDrawable(context, type.iconId)?.apply {
                            DrawableCompat.setTintList(
                                this,
                                ColorStateList.valueOf(
                                    ContextCompat.getColor(
                                        context,
                                        R.color.material_blue_500
                                    )
                                )
                            )
                        }
                    }
                else {
                    try {
                        VectorDrawableCompat.create(resources, resId, context.theme)
                    } catch (ignore: Throwable) {
                        ContextCompat.getDrawable(context, resId)
                    }
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
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    enum class State {
        OFF, ON, ERROR
    }

    companion object {
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