package dev.skomlach.biometric.compat.impl.dialogs

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Header and auth content scroll independently; the cancel action always stays reachable. */
internal class NativeDialogPaneLayout(context: Context, private val twoPane: Boolean) : ViewGroup(context) {
    var maximumHeight: Int = Int.MAX_VALUE
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val bounded = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED || maximumHeight != Int.MAX_VALUE
        val available = minOf(maximumHeight, if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            Int.MAX_VALUE else MeasureSpec.getSize(heightMeasureSpec))
        val header = getChildAt(0)
        val auth = getChildAt(1)
        val buttons = getChildAt(2)
        fun measure(view: View, w: Int, h: Int) = view.measure(
            MeasureSpec.makeMeasureSpec(w.coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(if (bounded) h.coerceAtLeast(0) else 0,
                if (bounded) MeasureSpec.AT_MOST else MeasureSpec.UNSPECIFIED)
        )
        val bodyWidth = if (twoPane) width / 2 else width
        measure(buttons, bodyWidth, available)
        val bodyHeight = (available - buttons.measuredHeight).coerceAtLeast(0)
        // In two-pane SystemUI the actions belong to the text column only.
        measure(auth, if (twoPane) width - bodyWidth else width, if (twoPane) available else bodyHeight)
        measure(header, bodyWidth, if (twoPane) bodyHeight else (bodyHeight - auth.measuredHeight).coerceAtLeast(0))
        // Leave room for the header on short windows even when the icon alone fills the viewport.
        if (!twoPane && bounded && header.measuredHeight == 0 && bodyHeight > 0) {
            measure(auth, width, bodyHeight / 2)
            measure(header, width, bodyHeight - auth.measuredHeight)
        }
        val height = if (twoPane) {
            // The landscape panel spans the available window, with independently scrolling columns.
            if (bounded) available else maxOf(header.measuredHeight + buttons.measuredHeight, auth.measuredHeight)
        } else header.measuredHeight + auth.measuredHeight + buttons.measuredHeight
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val header = getChildAt(0); val auth = getChildAt(1); val buttons = getChildAt(2)
        val bodyBottom = measuredHeight - buttons.measuredHeight
        if (twoPane) {
            val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
            val headerLeft = if (rtl) measuredWidth - header.measuredWidth else 0
            val authLeft = if (rtl) 0 else header.measuredWidth
            header.layout(headerLeft, 0, headerLeft + header.measuredWidth, header.measuredHeight)
            val authTop = ((measuredHeight - auth.measuredHeight) / 2).coerceAtLeast(0)
            auth.layout(authLeft, authTop, authLeft + auth.measuredWidth, authTop + auth.measuredHeight)
            buttons.layout(headerLeft, bodyBottom, headerLeft + header.measuredWidth, measuredHeight)
        } else {
            header.layout(0, 0, measuredWidth, header.measuredHeight)
            auth.layout(0, header.measuredHeight, measuredWidth, bodyBottom)
            buttons.layout(0, bodyBottom, measuredWidth, measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
}
