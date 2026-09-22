package dev.skomlach.biometric.compat.impl.dialogs

import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.BiometricTitle
import dev.skomlach.biometric.compat.utils.activityView.FeedbackBounds
import dev.skomlach.biometric.compat.utils.activityView.reconstructFeedbackDialogBounds

/** Resource-derived estimate, NOT observation of SystemUI. Never instantiate foreign view code. */
internal class SystemPromptGeometry(private val builder: BiometricPromptCompat.Builder) {
    private var lastKey: List<Any?>? = null
    private var estimate: FeedbackBounds? = null

    fun bounds(): FeedbackBounds? {
        if (!builder.systemPromptOwnsUi || builder.forceDeviceCredential()) return null
        val context = builder.getActivity() ?: return null
        val style = SystemBiometricDialogResources.cached(context) ?: run {
            SystemBiometricDialogResources.warmUp(context)
            return null
        }
        // This profile explicitly keeps app-sized geometry instead of the runtime native width.
        if (style.preserveLocalCardWidth) return null
        // Even a supported profile remains an estimate: live SystemUI state and physical sensor
        // offsets are not observed. Use the regular mapped native icon, not our UDF placeholders.
        return try {
            val screen = if (Build.VERSION.SDK_INT >= 30) {
                val metrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
                val rect = metrics.bounds
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                FeedbackBounds(rect.left + insets.left, rect.top + insets.top, rect.right - insets.right, rect.bottom - insets.bottom)
            } else {
                val support = builder.getMultiWindowSupport()
                val size = support.realScreenSize
                FeedbackBounds(0, support.statusBarHeight, size.x - support.navigationBarWidth, size.y - support.navigationBarHeight)
            }
            val title = builder.getTitle()?.toString()
            val subtitle = builder.getSubtitle()?.toString()
            val description = builder.getDescription()?.toString()
            val negative = builder.getNegativeButtonText()?.toString() ?: context.getString(android.R.string.cancel)
            val prompt = BiometricTitle.getRelevantTitle(context, builder.getPrimaryAvailableTypes())
            val key = listOf(style, screen, title, subtitle, description, negative, prompt,
                SystemBiometricDialogResources.configurationKey(context))
            if (key == lastKey) return estimate
            lastKey = key
            estimate = null
            val themed = ContextThemeWrapper(context, R.style.Theme_BiometricPromptDialog)
            val root = LayoutInflater.from(themed).inflate(R.layout.biometric_prompt_dialog_content, null, false)
            // The local camera SurfaceView is MATCH_PARENT; it is not SystemUI content and would
            // otherwise inflate the AT_MOST measurement to the entire display height.
            root.findViewById<View>(R.id.auth_preview).visibility = View.GONE
            fun bind(id: Int, text: String?) {
                root.findViewById<TextView>(id).apply {
                    this.text = text
                    visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
            }
            bind(R.id.title, title); bind(R.id.subtitle, subtitle); bind(R.id.description, description)
            bind(android.R.id.button1, negative); bind(R.id.status, prompt)
            val session = NativeDialogStyleApplier.Session(root)
            session.apply(style, false)
            session.limitHeight(screen.height)
            val width = style.windowWidth(screen.width, context.resources.getDimensionPixelSize(R.dimen.dialog_width))
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(screen.height, View.MeasureSpec.AT_MOST))
            val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val padding = style.outerInsets
            estimate = reconstructFeedbackDialogBounds(screen, width, root.measuredHeight,
                if (rtl) padding.end else padding.start, padding.top,
                if (rtl) padding.start else padding.end, padding.bottom,
                style.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL)
            estimate
        } catch (_: Exception) { null } catch (_: LinkageError) { null }
    }
}
