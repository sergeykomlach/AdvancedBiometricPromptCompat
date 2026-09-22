package dev.skomlach.biometric.compat.utils.activityView

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.custom.BiometricFeedbackOptions
import dev.skomlach.biometric.compat.impl.dialogs.SystemBiometricDialogResources
import dev.skomlach.biometric.compat.impl.dialogs.SystemPromptGeometry
import dev.skomlach.biometric.compat.utils.DialogMainColor
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes

/** App-window content only. No window creation, overlay permissions, focus or auth ownership. */
internal class ForegroundFeedbackView(
    private val builder: BiometricPromptCompat.Builder,
    private val root: FrameLayout,
    icons: View,
    private val session: ForegroundFeedbackSession?
) {
    private val options = builder.getBiometricFeedbackOptions()
    private val context = root.context
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private val card = TextView(context).apply {
        textSize = 16f
        gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(16), dp(10), dp(16), dp(10))
        visibility = View.INVISIBLE
        // Reserve three lines so showing/hiding a hint never moves the icons.
        minHeight = lineHeight * 3 + paddingTop + paddingBottom
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val group = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(card, LinearLayout.LayoutParams(-1, card.minHeight))
        addView(icons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    private val geometry = SystemPromptGeometry(builder)
    private var attached = false
    private var shownId: Long? = null
    private var timeout: Runnable? = null
    private var inline = false
    private var cardShown = false
    private val update = { render() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { render() }
    private val resourcesObserver = Observer<String> { render() }

    init {
        root.addView(group, FrameLayout.LayoutParams(-1, -2))
        if (!options.enabled) card.visibility = View.GONE
    }

    fun start() {
        if (attached) return
        attached = true
        session?.onChanged = update
        root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        SystemBiometricDialogResources.updates.observeForever(resourcesObserver)
        SystemBiometricDialogResources.warmUp(context)
        render()
    }

    fun stop() {
        attached = false
        if (session?.onChanged === update) session.onChanged = null
        root.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        SystemBiometricDialogResources.updates.removeObserver(resourcesObserver)
        timeout?.let(root::removeCallbacks)
        timeout = null
        card.animate().cancel()
        card.text = null
        card.visibility = View.INVISIBLE
        cardShown = false
        shownId = null
        if (inline) session?.showInline(null)
        inline = false
    }

    private fun render() {
        try {
            renderContent()
        } catch (error: Exception) {
            BiometricLoggerImpl.e(error)
        } catch (error: LinkageError) {
            BiometricLoggerImpl.e(error)
        }
    }

    private fun renderContent() {
        if (!attached || root.width <= 0 || root.height <= 0) return
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val visible = Rect()
        root.getWindowVisibleDisplayFrame(visible)
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val decor = root.rootView
        val decorLocation = IntArray(2)
        decor.getLocationOnScreen(decorLocation)
        val viewport = FeedbackBounds(
            maxOf(0, visible.left - location[0], decorLocation[0] + (insets?.left ?: 0) - location[0]),
            maxOf(0, visible.top - location[1], decorLocation[1] + (insets?.top ?: 0) - location[1]),
            minOf(root.width, visible.right - location[0], decorLocation[0] + decor.width - (insets?.right ?: 0) - location[0]),
            minOf(root.height, visible.bottom - location[1], decorLocation[1] + decor.height - (insets?.bottom ?: 0) - location[1]))
        val owned = session?.dialogView?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
        val bounds = if (owned != null) {
            val position = IntArray(2)
            owned.getLocationOnScreen(position)
            FeedbackBounds(position[0], position[1], position[0] + owned.width, position[1] + owned.height)
        } else geometry.bounds()
        val relative = bounds?.relativeTo(location[0], location[1])
        val margin = dp(16)
        card.textSize = 16f
        val reservedHeight = card.lineHeight * 3 + card.paddingTop + card.paddingBottom
        if (card.layoutParams.height != reservedHeight) card.layoutParams = card.layoutParams.apply { height = reservedHeight }
        val width = minOf((viewport.width - margin * 2).coerceAtLeast(1), dp(420))
        group.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val top = feedbackGroupTop(viewport, relative, group.measuredHeight, if (owned == null && bounds != null) dp(32) else dp(12))
        val params = group.layoutParams as FrameLayout.LayoutParams
        val left = viewport.left + (viewport.width - width) / 2
        if (params.width != width || params.leftMargin != left || top != null && params.topMargin != top) {
            params.width = width; params.leftMargin = left
            if (top != null) params.topMargin = top
            group.layoutParams = params
        }
        group.visibility = if (top == null) View.INVISIBLE else View.VISIBLE
        val message = session?.state?.current?.takeIf { options.enabled }
        val useInline = top == null && owned != null && message != null
        if (useInline || inline) session?.showInline(if (useInline) message.status else null)
        inline = useInline

        bindForegroundFeedbackText(message?.status, card.text) { card.text = it }

        if (shownId != message?.id) {
            shownId = message?.id
            timeout?.let(root::removeCallbacks)
            timeout = null
            if (message != null && (!message.status.persistent || message.status.terminal)) {
                val id = message.id
                timeout = Runnable {
                    if (attached) { session.state.expire(id); render() }
                }.also { root.postDelayed(it, accessibleTimeout()) }
            }
        }
        val dark = DarkLightThemes.isNightModeCompatWithInscreen(context)
        val color = DialogMainColor.getColor(context, dark)
        val corner = SystemBiometricDialogResources.cached(context)?.corner?.toFloat() ?: dp(12).toFloat()
        val drawable = card.background as? GradientDrawable
        if (drawable == null || card.tag != Pair(color, corner)) {
            card.background = GradientDrawable().apply { setColor(color); cornerRadius = corner }
            card.tag = Pair(color, corner)
        }
        card.setTextColor(if (message?.status?.terminal == true) ContextCompat.getColor(context, R.color.material_red_500)
            else DialogMainColor.getColor(context, !dark))
        showCard(message != null && top != null)
    }

    private fun accessibleTimeout(): Long {
        val original = options.timeoutMillis.toInt()
        return if (Build.VERSION.SDK_INT >= 29) {
            try {
                (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
                    ?.getRecommendedTimeoutMillis(original, AccessibilityManager.FLAG_CONTENT_TEXT)?.toLong()
                    ?.coerceAtLeast(options.timeoutMillis) ?: options.timeoutMillis
            } catch (_: LinkageError) { options.timeoutMillis }
        } else options.timeoutMillis
    }

    private fun showCard(show: Boolean) {
        if (!options.enabled || cardShown == show) return
        cardShown = show
        card.animate().cancel()
        val animate = options.animation != BiometricFeedbackOptions.Animation.NONE &&
            (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
        if (!animate) {
            card.alpha = if (show) 1f else 0f
            card.scaleY = 1f
            card.visibility = if (show) View.VISIBLE else View.INVISIBLE
            if (!show) card.text = null
            return
        }
        val fold = options.animation == BiometricFeedbackOptions.Animation.FOLD
        card.pivotY = card.height.toFloat()
        if (show) {
            if (card.visibility != View.VISIBLE) { card.alpha = 0f; card.scaleY = if (fold) 0f else 1f }
            card.visibility = View.VISIBLE
        }
        card.animate().alpha(if (show) 1f else 0f).scaleY(if (fold && !show) 0f else 1f)
            .setDuration(180L).withEndAction {
                if (!cardShown) { card.visibility = View.INVISIBLE; card.text = null }
            }.start()
    }
}
