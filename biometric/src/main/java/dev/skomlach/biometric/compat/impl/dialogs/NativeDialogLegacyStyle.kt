package dev.skomlach.biometric.compat.impl.dialogs

import dev.skomlach.biometric.compat.impl.dialogs.SystemBiometricDialogResources.Element
import dev.skomlach.biometric.compat.impl.dialogs.SystemBiometricDialogResources.Layout

/** Maps parsed values only; a legacy prompt never requires loading SystemUI's view classes. */
internal object NativeDialogLegacyStyle {
    fun read(pkg: String, name: String, layout: Layout, dimension: (String) -> Int?): NativeDialogStyle? {
        if (layout.root != "FrameLayout" && layout.root != "LinearLayout") return null
        // Pane-named XMLs must satisfy the modern pane schema, never the legacy reader.
        val compactName = name.replace("_", "")
        if ("onepane" in compactName || "twopane" in compactName) return null
        val nodes = layout.elements
        val card = nodes["dialog"]?.takeIf { it.tag == "LinearLayout" && it.height == -2 } ?: return null
        if (card.width == null || card.width < -2) return null
        val title = nodes["title"]?.takeIf { it.isText() && it.parent === card } ?: return null
        val subtitle = nodes["subtitle"]?.takeIf { it.isText() && it.parent === card } ?: return null
        val description = nodes["description"]?.takeIf { it.isText() && it.parent === card } ?: return null
        // Match the resource family to the actual icon, not the OEM's layout filename.
        // Two candidate icons are ambiguous; do not mix dimensions from unrelated families.
        val family = listOf("biometric", "fingerprint").singleOrNull { prefix ->
            nodes["${prefix}_icon"]?.let { it.tag == "ImageView" && it.parent === card } == true
        } ?: return null
        val icon = nodes.getValue("${family}_icon")
        val indicator = nodes["error"]?.takeIf { it.isText() && it.parent === card } ?: return null
        val button = nodes["button2"]?.takeIf { it.isButton() } ?: return null
        val bar = button.parent?.takeIf { it.tag == "LinearLayout" && it.parent === card } ?: return null
        val leftSpacer = nodes["leftSpacer"]?.takeIf { it.parent === bar }?.width?.coerceAtLeast(0) ?: 0
        return NativeDialogStyle(
            source = "$pkg/$name", border = card.text.margin.start,
            corner = dimension("${family}_dialog_corner_size") ?: return null,
            // 0dp is a runtime-sized card. Keep valid native styling, but use our safe window width.
            gravity = 81, contentWidth = card.width.takeIf { it > 0 },
            title = title.text, subtitle = subtitle.text, description = description.text,
            button = button.text.copy(margin = button.text.margin.copy(start = button.text.margin.start + leftSpacer)),
            indicator = indicator.text, iconWidth = icon.width, iconHeight = icon.height,
            iconTop = icon.text.margin.top + icon.text.padding.top,
            iconBottom = icon.text.margin.bottom + icon.text.padding.bottom, centeredButton = false,
            outerInsets = card.text.margin, buttonBarHeight = bar.height?.takeIf { it > 0 },
            buttonBarPadding = bar.text.padding, preserveLocalCardWidth = card.width == 0
        )
    }

    private fun Element.isText() = tag == "TextView" || tag.endsWith(".TextView")
    private fun Element.isButton() = tag == "Button" || tag.endsWith(".Button")
}
