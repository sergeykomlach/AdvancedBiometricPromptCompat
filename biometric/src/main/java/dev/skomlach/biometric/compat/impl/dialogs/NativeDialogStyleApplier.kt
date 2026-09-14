package dev.skomlach.biometric.compat.impl.dialogs

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.children
import dev.skomlach.biometric.compat.R

/** Apply to our own views before their first measure; retain auth feedback, icons and preview. */
internal object NativeDialogStyleApplier {
    fun apply(root: View, style: NativeDialogStyle, inScreen: Boolean) {
        Session(root).apply(style, inScreen)
    }

    /** Owned by the dialog view lifecycle; never retained in the resource cache. */
    class Session(private val root: View) {
        private val card = root.findViewById<LinearLayout>(R.id.dialogLayout)
        private val originalChildren = card.children.toList()
        private val restore = snapshot(root)
        private var panes: NativeDialogPaneLayout? = null

        fun apply(style: NativeDialogStyle?, inScreen: Boolean) {
            if (panes != null) {
                originalChildren.forEach { (it.parent as? ViewGroup)?.removeView(it) }
                card.removeAllViews()
                originalChildren.forEach(card::addView)
                panes = null
            }
            restore.forEach { it() }
            if (style == null) return
            applyValues(root, style, inScreen)
            style.modern?.let { modern ->
                val header = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL }
                val identity = LinearLayout(root.context).apply {
                    orientation = if (modern.twoPane) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                    gravity = if (modern.twoPane) Gravity.START else Gravity.CENTER_HORIZONTAL
                }
                val logo = ImageView(root.context).apply {
                    setImageDrawable(context.applicationInfo.loadIcon(context.packageManager))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                identity.addView(logo, LinearLayout.LayoutParams(modern.logoWidth, modern.logoHeight))
                val label = TextView(root.context).apply {
                    text = context.applicationInfo.loadLabel(context.packageManager)
                    setTextColor(root.findViewById<TextView>(R.id.title).textColors)
                    layoutParams = LinearLayout.LayoutParams(-1, -2)
                }
                text(label, modern.logoDescription)
                if (modern.twoPane) label.layoutParams.width = 0
                (label.layoutParams as LinearLayout.LayoutParams).weight = if (modern.twoPane) 1f else 0f
                identity.addView(label)
                header.addView(identity, LinearLayout.LayoutParams(-1, -2))
                listOf(R.id.title, R.id.subtitle, R.id.description).forEach { id ->
                    val view = root.findViewById<View>(id)
                    (view.parent as ViewGroup).removeView(view)
                    header.addView(view)
                }
                with(modern.contentPadding) { header.setPaddingRelative(start, top, end, bottom) }
                val auth = root.findViewById<View>(R.id.auth_content_container)
                val button = root.findViewById<TextView>(android.R.id.button1)
                val bar = (button.parent as View).parent as LinearLayout
                card.removeView(auth)
                card.removeView(bar)
                // The original views, SurfaceView and listeners remain owned by authentication.
                val body = NativeDialogPaneLayout(root.context, modern.twoPane).apply {
                    addView(scroll(header))
                    addView(scroll(auth))
                    addView(bar)
                }
                // SystemUI's panel is a sibling background View: its padding does not inset content.
                card.addView(body, LinearLayout.LayoutParams(-1, -2))
                panes = body
            }
        }

        fun limitHeight(height: Int) {
            val body = panes ?: return
            body.maximumHeight = (height - root.paddingTop - root.paddingBottom -
                card.paddingTop - card.paddingBottom).coerceAtLeast(1)
        }

        private fun scroll(child: View) = ScrollView(root.context).apply {
            isFillViewport = false
            clipToPadding = false
            addView(child, FrameLayout.LayoutParams(-1, -2))
        }
    }

    private fun applyValues(root: View, style: NativeDialogStyle, inScreen: Boolean) {
        val card = root.findViewById<LinearLayout>(R.id.dialogLayout)
        with(style.outerInsets) { root.setPaddingRelative(start, top, end, bottom) }
        if (!style.preserveLocalCardWidth) card.minimumWidth = 0
        card.layoutParams = card.layoutParams.apply {
            if (!style.preserveLocalCardWidth) width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        card.setPadding(0, 0, 0, 0)
        (card.background?.mutate() as? GradientDrawable)?.cornerRadius = style.corner.toFloat()
        val preserveWidth = style.preserveLocalCardWidth
        text(root.findViewById(R.id.title), style.title, preserveWidth)
        text(root.findViewById(R.id.subtitle), style.subtitle, preserveWidth)
        text(root.findViewById(R.id.description), style.description, preserveWidth)
        style.indicator?.let { text(root.findViewById(R.id.status), it, preserveWidth) }
        val button = root.findViewById<TextView>(android.R.id.button1)
        text(button, style.button, preserveWidth)
        val holder = button.parent as LinearLayout
        val bar = holder.parent as LinearLayout
        with(style.buttonBarPadding) { bar.setPaddingRelative(start, top, end, bottom) }
        style.buttonBarHeight?.let {
            bar.layoutParams.height = it
            holder.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            button.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        if (style.modern != null) {
            bar.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.layoutParams = LinearLayout.LayoutParams(-1, -2)
            bar.children.filter { it !== holder }.forEach { it.visibility = View.GONE }
            button.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            style.modern.buttonMinHeight?.let {
                button.minimumHeight = it
                button.minHeight = it
            }
            root.findViewById<TextView>(R.id.status).apply { minHeight = 0; minimumHeight = 0 }
        }
        if (style.centeredButton) {
            holder.layoutParams = (holder.layoutParams as LinearLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            bar.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            for (i in 0 until bar.childCount) if (bar.getChildAt(i) !== holder) bar.getChildAt(i).visibility = View.GONE
            button.background = RippleDrawable(
                ColorStateList.valueOf((button.currentTextColor and 0x00ffffff) or 0x20000000), null, null
            )
            button.minimumHeight = (48 * root.resources.displayMetrics.density).toInt()
            button.minHeight = button.minimumHeight
            button.minimumWidth = 0
            button.minWidth = 0
        }
        // The invisible placeholders of the under-display variant reserve the physical sensor area.
        if (!inScreen && style.iconWidth != null && style.iconHeight != null) {
            val icon = root.findViewById<View>(R.id.fingerprint_icon)
            icon.layoutParams = icon.layoutParams.apply { width = style.iconWidth; height = style.iconHeight }
            val parent = icon.parent as ViewGroup
            val group = if (parent is FrameLayout) {
                parent.background = null
                parent.setPadding(0, 0, 0, 0)
                parent.parent as LinearLayout
            } else parent as LinearLayout
            group.setPadding(0, style.iconTop, 0, style.iconBottom)
        }
    }

    // Capture appearance/geometry only. Never roll back auth text, visibility or icon feedback.
    private fun snapshot(root: View): List<() -> Unit> {
        val states = ArrayList<() -> Unit>()
        fun visit(view: View) {
            val params = view.layoutParams?.let(::copyParams)
            val padding = intArrayOf(view.paddingStart, view.paddingTop, view.paddingEnd, view.paddingBottom)
            val minWidth = view.minimumWidth
            val minHeight = view.minimumHeight
            val background = view.background?.constantState
            states.add {
                if (params != null) view.layoutParams = copyParams(params)
                view.setPaddingRelative(padding[0], padding[1], padding[2], padding[3])
                view.minimumWidth = minWidth; view.minimumHeight = minHeight
                view.background = background?.newDrawable(view.resources)?.mutate()
            }
            if (view is TextView) {
                val size = view.textSize; val gravity = view.gravity; val fontPadding = view.includeFontPadding
                val typeface = view.typeface; val transform = view.transformationMethod
                val textMinWidth = view.minWidth; val textMinHeight = view.minHeight
                states.add {
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
                    view.gravity = gravity; view.includeFontPadding = fontPadding
                    view.typeface = typeface; view.transformationMethod = transform
                    view.minWidth = textMinWidth; view.minHeight = textMinHeight
                }
            }
            if (view is ViewGroup) view.children.forEach(::visit)
        }
        visit(root)
        val holder = root.findViewById<View>(android.R.id.button1).parent as View
        (holder.parent as ViewGroup).children.filter { it !== holder }.forEach { spacer ->
            val visibility = spacer.visibility
            states.add { spacer.visibility = visibility }
        }
        return states
    }

    private fun copyParams(params: ViewGroup.LayoutParams): ViewGroup.LayoutParams = when (params) {
        is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(params)
        is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(params)
        is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(params)
        else -> ViewGroup.LayoutParams(params)
    }

    private fun text(view: TextView, style: NativeTextStyle, preserveWidth: Boolean = false) {
        style.size?.let { view.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
        style.gravity?.let { view.gravity = it }
        style.includeFontPadding?.let { view.includeFontPadding = it }
        style.allCaps?.let(view::setAllCaps)
        style.textStyle?.let { view.setTypeface(view.typeface, it) }
        with(style.padding) { view.setPaddingRelative(start, top, end, bottom) }
        view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply {
            if (!preserveWidth) width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            gravity = if (preserveWidth) {
                style.gravity?.and(Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) ?: Gravity.CENTER_HORIZONTAL
            } else Gravity.CENTER_HORIZONTAL
            with(style.margin) {
                marginStart = start; topMargin = top; marginEnd = end; bottomMargin = bottom
            }
        }
    }
}
