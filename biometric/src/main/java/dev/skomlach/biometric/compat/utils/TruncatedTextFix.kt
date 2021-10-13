/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.utils

import android.annotation.SuppressLint
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.R
import java.util.concurrent.atomic.AtomicInteger

/*
* Truncate strings due to appearance limitations on some devices/AOS versions (Android 12)
* */
object TruncatedTextFix {
    //Magic constants
    private const val TITLE_SHIFT = 7
    private const val SUBTITLE_SHIFT = 1
    private const val DESCRIPTION_SHIFT = -6
    private const val NEGATIVE_BUTTON_SHIFT = 4
    private val FINALIZED_STRING = ".."

    interface OnTruncateChecked {
        fun onDone()
    }

    @SuppressLint("InflateParams")
    fun recalculateTexts(
        builder: BiometricPromptCompat.Builder,
        onTruncateChecked: OnTruncateChecked
    ) {
        val windowView = builder.context.findViewById(Window.ID_ANDROID_CONTENT) as ViewGroup
        val layout = LayoutInflater.from(builder.context)
            .inflate(R.layout.biometric_prompt_dialog_content, null).apply {
                this.visibility = View.INVISIBLE
                windowView.addView(
                    this,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        x = Int.MAX_VALUE.toFloat()
                        y = Int.MAX_VALUE.toFloat()
                    })
            }

        val rootView: View? = layout?.findViewById(R.id.dialogContent)
        val title: TextView? = rootView?.findViewById(R.id.title)
        val subtitle: TextView? = rootView?.findViewById(R.id.subtitle)
        val description: TextView? = rootView?.findViewById(R.id.description)
        val negativeButton: Button? = rootView?.findViewById(android.R.id.button1)
        val action = {
            windowView.removeView(layout)
            onTruncateChecked.onDone()
        }
        val counter = AtomicInteger(4)
        getMaxStringForCurrentConfig(builder.title, title, { str ->
            builder.title = str
            if (counter.decrementAndGet() == 0) {
                action.invoke()
            }
        }, TITLE_SHIFT)
        getMaxStringForCurrentConfig(
            builder.subtitle,
            subtitle,
            { str ->
                builder.subtitle = str
                if (counter.decrementAndGet() == 0) {
                    action.invoke()
                }
            },
            SUBTITLE_SHIFT
        )
        getMaxStringForCurrentConfig(
            builder.description,
            description,
            { str ->
                builder.description = str
                if (counter.decrementAndGet() == 0) {
                    action.invoke()
                }
            },
            DESCRIPTION_SHIFT
        )
        getMaxStringForCurrentConfig(
            builder.negativeButtonText,
            negativeButton,
            { str ->
                builder.negativeButtonText = str
                if (counter.decrementAndGet() == 0) {
                    action.invoke()
                }
            },
            NEGATIVE_BUTTON_SHIFT
        )

    }

    private fun getMaxStringForCurrentConfig(
        s: CharSequence?,
        tv: TextView?,
        callback: (str: String?) -> Unit,
        truncateFromEnd: Int
    ) {
        if (tv == null) {
            callback.invoke(s?.toString())
            return
        }
        s?.let {
            var low = 0
            var high = s.length - 1
            var mid: Int = (low + high) / 2
            val vto: ViewTreeObserver = tv.viewTreeObserver

            vto.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (tv.layout == null)
                        return
                    if (tv.text == it) {
                        if (!isTextTruncated(tv)) {
                            callback.invoke(s.toString())
                            vto.removeOnGlobalLayoutListener(this)
                        } else {
                            tv.text = it.substring(0, mid)
                        }
                        return
                    }
                    if (low <= high) {
                        if (isTextTruncated(tv)) {
                            high = mid - 1
                        } else {
                            low = mid + 1
                        }
                        mid = (low + high) / 2
                        tv.text = it.substring(0, mid)
                    } else {

                        val str = it.substring(
                            0,
                            mid - FINALIZED_STRING.length - truncateFromEnd
                        ) + FINALIZED_STRING
                        callback.invoke(str)

                        vto.removeOnGlobalLayoutListener(this)
                    }
                }
            })
            tv.text = it
        } ?: run {
            callback.invoke(s?.toString())
        }

    }

    private fun isTextTruncated(textView: TextView?): Boolean {
        textView?.let {
            it.layout?.let { l ->
                val lines: Int = l.lineCount
                if (lines > 0) {
                    val ellipsisCount: Int = l.getEllipsisCount(lines - 1)
                    return ellipsisCount > 0 || lines > 1
                }
            }
        }
        return false
    }
}