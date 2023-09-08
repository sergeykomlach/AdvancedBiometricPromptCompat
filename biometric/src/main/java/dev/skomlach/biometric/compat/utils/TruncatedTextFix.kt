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
import android.content.res.Configuration
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.google.gson.Gson
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.util.concurrent.atomic.AtomicInteger

/*
* Truncate strings due to appearance limitations on some devices/AOS versions (Android 12)
* */
object TruncatedTextFix {
    //Magic constants
    private var TITLE_SHIFT = 7
    private var SUBTITLE_SHIFT = 1
    private var DESCRIPTION_SHIFT = 2
    private var NEGATIVE_BUTTON_SHIFT = 4
    private val FINALIZED_STRING = ".."
    private var truncatedText: TruncatedText? = null

    init {
        //Title and description should be fixed a bit for Android 12
        if (Utils.isAtLeastS) {
            TITLE_SHIFT = 1
            DESCRIPTION_SHIFT = 0
        }

    }

    interface OnTruncateChecked {
        fun onDone()
    }

    @SuppressLint("InflateParams")
    fun recalculateTexts(
        builder: BiometricPromptCompat.Builder,
        onTruncateChecked: OnTruncateChecked
    ) {
        val config = AndroidContext.configuration ?: AndroidContext.appContext.resources.configuration
        val cache =
            truncatedText ?: getTruncatedText(config).also {
                truncatedText = it
            }
        val map = cache?.map?.toMutableMap() ?: HashMap()
        if (map.isNotEmpty()) {
            var totalCount = 0
            var changedCount = 0
            var title = builder.getTitle()
            var subtitle = builder.getSubtitle()
            var description = builder.getDescription()
            var negativeButton = builder.getNegativeButtonText()
            if (!title.isNullOrEmpty()) {
                totalCount++
                map[title]?.let {
                    changedCount++
                    title = it
                }

            }
            if (!subtitle.isNullOrEmpty()) {
                totalCount++
                map[subtitle]?.let {
                    changedCount++
                    subtitle = it
                }
            }
            if (!description.isNullOrEmpty()) {
                totalCount++
                map[description]?.let {
                    changedCount++
                    description = it
                }
            }
            if (!negativeButton.isNullOrEmpty()) {
                totalCount++
                map[negativeButton]?.let {
                    changedCount++
                    negativeButton = it
                }
            }
            if (totalCount == changedCount) {
                builder.setTitle(title).setSubtitle(subtitle).setDescription(description).setNegativeButtonText(negativeButton)
                onTruncateChecked.onDone()
                return
            }
        }
        val windowView = builder.getActivity().findViewById(Window.ID_ANDROID_CONTENT) as ViewGroup
        val layout = LayoutInflater.from(builder.getContext())
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
            setTruncatedText(config, TruncatedText(map).also {
                truncatedText = it
            })
            onTruncateChecked.onDone()
        }
        val counter = AtomicInteger(4)

        if (map.contains(builder.getTitle())) {
            builder.setTitle(map[builder.getTitle()])
            if (counter.decrementAndGet() == 0) {
                action.invoke()
            }
        } else
            getMaxStringForCurrentConfig(builder.getTitle(), title, { str ->
                builder.getTitle()?.let {
                    map.put(it.toString(), str)
                }
                builder.setTitle(str)
                if (counter.decrementAndGet() == 0) {
                    action.invoke()
                }
            }, TITLE_SHIFT)

        if (map.contains(builder.getSubtitle())) {
            builder.setSubtitle(map[builder.getSubtitle()])
            if (counter.decrementAndGet() == 0) {
                action.invoke()
            }
        } else
            getMaxStringForCurrentConfig(
                builder.getSubtitle(),
                subtitle,
                { str ->
                    builder.getSubtitle()?.let {
                        map.put(it.toString(), str)
                    }
                    builder.setSubtitle(str)
                    if (counter.decrementAndGet() == 0) {
                        action.invoke()
                    }
                },
                SUBTITLE_SHIFT
            )
        if (map.contains(builder.getDescription())) {
            builder.setDescription(map[builder.getDescription()])
            if (counter.decrementAndGet() == 0) {
                action.invoke()
            }
        } else
            getMaxStringForCurrentConfig(
                builder.getDescription(),
                description,
                { str ->
                    builder.getDescription()?.let {
                        map.put(it.toString(), str)
                    }
                    builder.setDescription(str)
                    if (counter.decrementAndGet() == 0) {
                        action.invoke()
                    }
                },
                DESCRIPTION_SHIFT
            )
        if (map.contains(builder.getNegativeButtonText())) {
            builder.setNegativeButtonText(map[builder.getNegativeButtonText()])
            if (counter.decrementAndGet() == 0) {
                action.invoke()
            }
        } else
            getMaxStringForCurrentConfig(
                builder.getNegativeButtonText(),
                negativeButton,
                { str ->
                    builder.getNegativeButtonText()?.let {
                        map.put(it.toString(), str)
                    }
                    builder.setNegativeButtonText(str)
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
                    try {
                        if (tv.layout == null)
                            return
                        if (tv.text == it) {
                            if (!isTextTruncated(tv)) {
                                callback.invoke(s.toString())
                                if (vto.isAlive)
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

                            if (vto.isAlive)
                                vto.removeOnGlobalLayoutListener(this)
                        }
                    } catch (e: Throwable) {
                        callback.invoke(s.toString())
                        if (vto.isAlive)
                            vto.removeOnGlobalLayoutListener(this)
                        BiometricLoggerImpl.e(e)
                    }
                }
            })
            tv.text = it
        } ?: run {
            callback.invoke(null)
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

    private fun getTruncatedText(config: Configuration): TruncatedText? {
        val data = config.toString()
        return try {
            val json =
                SharedPreferenceProvider.getPreferences("TruncatedText_v2").getString(data, null)
            if (json.isNullOrEmpty()) {
                TruncatedText(HashMap())
            } else {
                Gson().fromJson(json, TruncatedText::class.java)
            }
        } catch (e: Throwable) {
            SharedPreferenceProvider.getPreferences("TruncatedText_v2").edit().remove(data).apply()
            TruncatedText(HashMap())
        }
    }

    private fun setTruncatedText(config: Configuration, truncatedText: TruncatedText) {
        val json = Gson().toJson(truncatedText, TruncatedText::class.java)
        val data: String = config.toString()
        SharedPreferenceProvider.getPreferences("TruncatedText_v2").edit().putString(data, json)
            .apply()
    }

    data class TruncatedText(val map: Map<String, String?>)
}