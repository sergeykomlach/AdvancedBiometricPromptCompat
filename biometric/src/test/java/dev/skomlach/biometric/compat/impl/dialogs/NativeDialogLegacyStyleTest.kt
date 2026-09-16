package dev.skomlach.biometric.compat.impl.dialogs

import dev.skomlach.biometric.compat.impl.dialogs.SystemBiometricDialogResources.Element
import dev.skomlach.biometric.compat.impl.dialogs.SystemBiometricDialogResources.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDialogLegacyStyleTest {
    private val text = NativeTextStyle(31.5f, 17, NativeInsets(), NativeInsets(), null, null, null)

    private fun node(tag: String, parent: Element? = null, width: Int? = -1, height: Int = -2,
                     style: NativeTextStyle = text) =
        Element(tag, width, height, null, style, null, null, null, null, parent)

    // Geometry from MEmu Android 9's fingerprint_dialog at density 2.25.
    private fun fixture(family: String = "fingerprint", width: Int? = 0): Layout {
        val root = node("LinearLayout", height = -1)
        val card = node("LinearLayout", root, width)
        val bar = node("LinearLayout", card, height = 162, style = text.copy(padding = NativeInsets(top = 54)))
        return Layout("LinearLayout", mapOf(
            "dialog" to card,
            "title" to node("TextView", card, style = text.copy(size = 45f)),
            "subtitle" to node("TextView", card),
            "description" to node("TextView", card),
            "${family}_icon" to node("ImageView", card, 144, 144),
            "error" to node("TextView", card),
            "button2" to node("Button", bar, style = text.copy(margin = NativeInsets(start = -27))),
            "leftSpacer" to node("Space", bar, 54)
        ), emptySet())
    }

    private fun read(layout: Layout, name: String = "fingerprint_dialog", dimensions: Map<String, Int> =
        mapOf("fingerprint_dialog_corner_size" to 9, "biometric_dialog_corner_size" to 12)) =
        NativeDialogLegacyStyle.read("com.android.systemui", name, layout, dimensions::get)
            ?.takeIf { it.isValid(2.25f, 2.25f) }

    @Test fun runtimeWidthKeepsFingerprintStyleAndUsesSafeLocalWidth() {
        val style = requireNotNull(read(fixture()))
        assertEquals("com.android.systemui/fingerprint_dialog", style.source)
        assertNull(style.contentWidth)
        assertTrue(style.preserveLocalCardWidth)
        assertEquals(800, style.windowWidth(1920, 800))
        assertEquals(500, style.windowWidth(500, 800))
        assertEquals(9, style.corner)
        assertEquals(144, style.iconWidth)
        assertEquals(45f, style.title.size)
        assertEquals(162, style.buttonBarHeight)
        assertEquals(NativeInsets(top = 54), style.buttonBarPadding)
        assertEquals(27, style.button.margin.start)
    }

    @Test fun biometricFamilyStillUsesItsOwnCornerEvenUnderAnOemFingerprintFilename() {
        val style = requireNotNull(read(fixture("biometric", -1), "oem_fingerprint_dialog"))
        assertEquals(12, style.corner)
        assertNull(style.contentWidth)
        assertFalse(style.preserveLocalCardWidth)
    }

    @Test fun explicitWidthIsPreservedButMissingOrInvalidWidthIsRejected() {
        assertEquals(720, read(fixture(width = 720))?.contentWidth)
        assertFalse(requireNotNull(read(fixture(width = 720))).preserveLocalCardWidth)
        assertNotNull(read(fixture(width = -2)))
        assertNull(read(fixture(width = null)))
        assertNull(read(fixture(width = -3)))
        assertNull(read(fixture(width = 1)))
        assertNull(read(fixture(width = Int.MAX_VALUE)))
    }

    @Test fun missingOrAmbiguousIconsDoNotProduceAStyle() {
        val layout = fixture()
        assertNull(read(layout.copy(elements = layout.elements - "fingerprint_icon")))
        assertNull(read(layout.copy(elements = layout.elements + ("biometric_icon" to layout.elements.getValue("fingerprint_icon")))))
    }

    @Test fun unrelatedTextAndIconNodesCannotSatisfyThePromptSchema() {
        for (id in listOf("title", "subtitle", "description", "error", "fingerprint_icon", "button2")) {
            val layout = fixture()
            val foreign = layout.elements.getValue(id).copy(parent = node("LinearLayout"))
            assertNull(id, read(layout.copy(elements = layout.elements + (id to foreign))))
        }
    }

    @Test fun cornerFromAnotherFamilyCannotSilentlyReplaceTheMissingFingerprintCorner() {
        assertNull(read(fixture(), dimensions = mapOf("biometric_dialog_corner_size" to 12)))
        assertNull(read(fixture(), dimensions = mapOf("fingerprint_dialog_corner_size" to -1)))
    }

    @Test fun paneNamesAndOtherRootStructuresCannotUseTheLegacyReader() {
        assertNull(read(fixture(), "fingerprint_two_pane_dialog"))
        assertNull(read(fixture(), "fingerprint_onepane_dialog"))
        assertNull(read(fixture(), "fingerprint_onepane_twopane_dialog"))
        assertNull(read(fixture().copy(root = "androidx.constraintlayout.widget.ConstraintLayout")))
    }
}
