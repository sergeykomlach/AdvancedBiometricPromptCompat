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

package dev.skomlach.biometric.compat.utils

import android.annotation.SuppressLint
import android.os.SystemClock
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.collection.LruCache
import com.google.gson.Gson
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.impl.dialogs.NativeDialogStyleApplier
import dev.skomlach.biometric.compat.impl.dialogs.SystemBiometricDialogResources
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.storage.SharedPreferenceProvider

/** Keeps the historical single-line approximation of Android 12 prompt text on other versions. */
object TruncatedTextFix {
    private val cache = LruCache<String, TruncatedText>(5)
    private val pref by lazy { SharedPreferenceProvider.getPreferences("TruncatedText_v3") }
    private val gson by lazy { Gson() }
    private val writer = LatestSnapshotWriter<Map<String, TruncatedText>>(
        execute = ExecutorHelper::startOnBackground,
        write = { snapshot ->
            // This preference file contains only this cache. Keep the same five configurations
            // on disk as in memory, and serialize entirely on the worker.
            val editor = pref.edit().clear()
            snapshot.forEach { (key, value) -> editor.putString(key, gson.toJson(value)) }
            editor.apply()
        },
        onFailure = { BiometricLoggerImpl.e(it) }
    )

    interface OnTruncateChecked {
        fun onDone()
    }

    private class PromptText(
        val field: PromptTextField,
        val source: CharSequence?,
        val viewId: Int,
        val reserve: Int,
        val apply: (CharSequence?) -> Unit
    ) {
        // Equal characters with different spans can have different widths.
        val cacheable = source !is Spanned
        val key = buildPromptTextCacheKey(field, source?.toString().orEmpty())
    }

    @MainThread
    @SuppressLint("InflateParams")
    fun recalculateTexts(
        builder: BiometricPromptCompat.Builder,
        onTruncateChecked: OnTruncateChecked
    ) {
        val startedAt = SystemClock.uptimeMillis()
        var mode = "unavailable"
        var measurements = 0
        var cacheHits = 0
        try {
            val activity = builder.getActivity() ?: return
            val host = activity.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT) ?: return
            val windowSize = builder.getMultiWindowSupport().currentWindowSize()
            val nativeStyle = SystemBiometricDialogResources.cached(host.context)
            val availableWidth = host.width.takeIf { it > 0 } ?: windowSize.x
            val width = nativeStyle?.windowWidth(availableWidth, host.resources.getDimensionPixelSize(R.dimen.dialog_width))
                ?: availableWidth
            if (width <= 0) return
            val cacheKey = buildTruncatedTextCacheKey(
                configurationKey = "${host.resources.configuration}|nativeStyle=${nativeStyle?.hashCode() ?: 0}",
                windowWidthPx = width,
                windowHeightPx = windowSize.y
            )
            val texts = listOf(
                PromptText(PromptTextField.TITLE, builder.getTitle(), R.id.title,
                    if (Utils.isAtLeastS) 1 else 7) { builder.setTitle(it) },
                PromptText(PromptTextField.SUBTITLE, builder.getSubtitle(), R.id.subtitle,
                    1) { builder.setSubtitle(it) },
                PromptText(PromptTextField.DESCRIPTION, builder.getDescription(), R.id.description,
                    if (Utils.isAtLeastS) 0 else 2) { builder.setDescription(it) },
                PromptText(PromptTextField.NEGATIVE_BUTTON, builder.getNegativeButtonText(), android.R.id.button1,
                    4) { builder.setNegativeButtonText(it) }
            )
            if (texts.all { it.source.isNullOrEmpty() }) {
                mode = "empty"
                return
            }
            val map = getTruncatedText(cacheKey).map
            val pending = texts.filter { text ->
                when {
                    text.source.isNullOrEmpty() -> false
                    text.cacheable && map.containsKey(text.key) -> {
                        cacheHits++
                        text.apply(map[text.key])
                        false
                    }
                    else -> true
                }
            }
            if (pending.isEmpty()) {
                mode = "cache"
                return
            }
            val updatedMap = LinkedHashMap(map)

            // Use the actual text styles and button transformation, but never attach the probe
            // (including its SurfaceView) to the Activity or wait for a Choreographer traversal.
            val layout = LayoutInflater.from(host.context)
                .inflate(R.layout.biometric_prompt_dialog_content, null, false)
            nativeStyle?.takeIf { it.fitsWindow(width) }?.let { NativeDialogStyleApplier.apply(layout, it, false) }
            // Measure against the available width. A shrinking wrap_content search candidate must
            // not change the width available to another field.
            layout.findViewById<View>(R.id.dialogLayout).layoutParams.width =
                ViewGroup.LayoutParams.MATCH_PARENT
            texts.forEach { text -> layout.findViewById<TextView>(text.viewId).text = text.source }
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            layout.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightSpec)
            layout.layout(0, 0, layout.measuredWidth, layout.measuredHeight)

            var shortened = false
            pending.forEach { text ->
                val view = layout.findViewById<TextView>(text.viewId)
                val source = text.source ?: return@forEach
                val fieldWidth = view.width
                val result = if (fieldWidth <= 0) source else {
                    val widthSpec = View.MeasureSpec.makeMeasureSpec(fieldWidth, View.MeasureSpec.EXACTLY)
                    truncatePromptText(source, text.reserve) { candidate ->
                        measurements++
                        view.text = candidate
                        view.measure(widthSpec, heightSpec)
                        val measured = view.layout
                        measured != null && measured.lineCount <= 1 &&
                                (measured.lineCount == 0 || measured.getEllipsisCount(0) == 0)
                    }
                }
                if (result.toString() != source.toString()) shortened = true
                text.apply(result)
                if (text.cacheable && fieldWidth > 0) updatedMap[text.key] = result.toString()
            }
            mode = if (shortened) "truncate" else "fits"
            setTruncatedText(cacheKey, TruncatedText(boundPromptTextEntries(updatedMap)))
        } catch (error: Throwable) {
            mode = "error"
            BiometricLoggerImpl.e(error)
        } finally {
            BiometricLoggerImpl.d {
                "TruncatedTextTiming: mode=$mode cacheHits=$cacheHits measurements=$measurements " +
                        "uptimeMs=$startedAt durationMs=${SystemClock.uptimeMillis() - startedAt}"
            }
            onTruncateChecked.onDone()
        }
    }

    private fun getTruncatedText(cacheKey: String): TruncatedText {
        cache.get(cacheKey)?.let { return it }
        return (try {
            val json = pref.getString(cacheKey, null)
            if (json.isNullOrEmpty()) TruncatedText(emptyMap())
            else TruncatedText(boundPromptTextEntries(
                gson.fromJson(json, TruncatedText::class.java)?.map ?: emptyMap()
            ))
        } catch (error: Throwable) {
            BiometricLoggerImpl.e(error)
            pref.edit().remove(cacheKey).apply()
            TruncatedText(emptyMap())
        }).also { cache.put(cacheKey, it) }
    }

    private fun setTruncatedText(cacheKey: String, truncatedText: TruncatedText) {
        cache.put(cacheKey, truncatedText)
        writer.offer(cache.snapshot())
    }

    data class TruncatedText(val map: Map<String, String?>)
}

internal enum class PromptTextField { TITLE, SUBTITLE, DESCRIPTION, NEGATIVE_BUTTON }

internal fun buildPromptTextCacheKey(field: PromptTextField, text: String): String = "${field.name}:$text"

/** The fit predicate measures styled text in pixels; there is no universal character-count limit. */
internal fun truncatePromptText(
    text: CharSequence,
    reserve: Int,
    fits: (CharSequence) -> Boolean
): CharSequence {
    if (text.isEmpty() || fits(text)) return text
    val suffix = ".."
    if (!fits(suffix)) return ""
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = low + (high - low + 1) / 2
        if (fits(text.subSequence(0, safePromptPrefixEnd(text, mid)))) low = mid
        else high = mid - 1
    }
    var end = safePromptPrefixEnd(text, (low - suffix.length - reserve.coerceAtLeast(0)).coerceAtLeast(0))
    while (true) {
        val result = text.subSequence(0, end).toString() + suffix
        if (fits(result) || end == 0) return result
        end = safePromptPrefixEnd(text, end - 1)
    }
}

private fun safePromptPrefixEnd(text: CharSequence, end: Int): Int =
    if (end > 0 && end < text.length && Character.isHighSurrogate(text[end - 1]) &&
        Character.isLowSurrogate(text[end])) end - 1 else end

internal fun buildTruncatedTextCacheKey(
    configurationKey: String,
    windowWidthPx: Int,
    windowHeightPx: Int
): String {
    return "$configurationKey|window=${windowWidthPx.coerceAtLeast(0)}x${windowHeightPx.coerceAtLeast(0)}"
}
