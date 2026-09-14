package dev.skomlach.biometric.compat.impl.dialogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDialogStyleTest {
    private val text = NativeTextStyle(48f, 17, NativeInsets(top = 24), NativeInsets(), true, false, null)
    private val style = NativeDialogStyle("test", 36, 72, 17, null, text, text, text, text,
        null, 123, 123, 54, 0, true)
    private val modern = NativeDialogModernStyle(false, NativeInsets(), NativeInsets(), 96, 96, text, 144)

    @Test fun foldUsesTheOnePaneXmlCapInsteadOfTheOld830dpFallback() {
        val fold = style.copy(contentWidth = 1560, outerInsets = NativeInsets(10, 10, 10, 10),
            modern = modern, iconWidth = 439, iconHeight = 439)
        assertTrue(fold.isValid(2.4375f, 2.4375f))
        assertEquals(1580, fold.windowWidth(2076, 2023))
    }

    @Test fun twoPaneWithoutAnXmlCapUsesTheAvailableWindow() {
        assertEquals(2400, style.copy(modern = modern.copy(twoPane = true)).windowWidth(2400, 1890))
    }

    @Test fun asymmetricLegacyInsetsDoNotAssumeFourEqualMargins() {
        val legacy = style.copy(contentWidth = 800, outerInsets = NativeInsets(12, 0, 24, 12))
        assertEquals(836, legacy.windowWidth(1080, 0))
        assertFalse(legacy.fitsWindow(36))
    }

    @Test fun invalidLogoOrButtonGeometryRejectsTheModernProfile() {
        assertFalse(style.copy(modern = modern.copy(logoWidth = 0)).isValid(3f, 3f))
        assertFalse(style.copy(modern = modern.copy(buttonMinHeight = Int.MAX_VALUE)).isValid(3f, 3f))
        assertFalse(style.copy(outerInsets = NativeInsets(end = -1)).isValid(3f, 3f))
    }

    @Test fun buttonHeightInheritedFromSystemUiThemeCanKeepTheLocalDefault() {
        assertTrue(style.copy(modern = modern.copy(buttonMinHeight = null)).isValid(3f, 3f))
    }

    @Test fun fixedButtonBarMustLeaveSpaceForItsActionAfterPadding() {
        assertTrue(style.copy(buttonBarHeight = 264, buttonBarPadding = NativeInsets(top = 72)).isValid(3f, 3f))
        assertFalse(style.copy(buttonBarHeight = 264, buttonBarPadding = NativeInsets(top = 264)).isValid(3f, 3f))
        assertFalse(style.copy(buttonBarPadding = NativeInsets(top = -1)).isValid(3f, 3f))
    }

    @Test fun oneplusUsesContainerMarginsNotTheUnused240dpDimension() {
        assertTrue(style.isValid(3f, 3f))
        val window = style.windowWidth(1080, 0)
        assertEquals(1080, window)
        assertEquals(1008, window - style.border * 2)
    }

    @Test fun explicitXmlContentWidthIncludesOuterMargins() {
        assertEquals(912, style.copy(contentWidth = 840).windowWidth(1080, 0))
    }

    @Test fun nativeWidthCannotEscapeCurrentMultiwindowBounds() {
        assertEquals(600, style.copy(contentWidth = 840).windowWidth(600, 0))
    }

    @Test fun existingTabletWidthCapStillAppliesWithoutAnExplicitXmlWidth() {
        assertEquals(1890, style.windowWidth(2400, 1890))
    }

    @Test fun zeroSizedWindowNeverProducesMatchParentOrNegativeWidth() {
        assertEquals(1, style.windowWidth(0, 0))
        assertFalse(style.fitsWindow(0))
        assertFalse(style.fitsWindow(72))
    }

    @Test fun densityAndFontScaleAreValidatedSeparately() {
        assertTrue(style.copy(title = text.copy(size = 120f)).isValid(3f, 4.5f))
        assertFalse(style.copy(title = text.copy(size = 180f)).isValid(3f, 3f))
    }

    @Test fun malformedDimensionRejectsTheWholeProfile() {
        assertFalse(style.copy(border = -1).isValid(3f, 3f))
        assertFalse(style.copy(corner = Int.MAX_VALUE).isValid(3f, 3f))
        assertFalse(style.copy(iconWidth = 0).isValid(3f, 3f))
        assertFalse(style.copy(contentWidth = 2).isValid(3f, 3f))
        assertFalse(style.copy(description = text.copy(padding = NativeInsets(top = -5))).isValid(3f, 3f))
    }

    @Test fun nonFiniteMetricsAndFontSizeAreRejected() {
        assertFalse(style.isValid(Float.NaN, 3f))
        assertFalse(style.isValid(3f, Float.POSITIVE_INFINITY))
        assertFalse(style.copy(title = text.copy(size = Float.NaN)).isValid(3f, 3f))
    }

    @Test fun missingOptionalIconAndTextSizeCanPreserveOurOwnValues() {
        assertTrue(style.copy(iconWidth = null, iconHeight = null,
            button = text.copy(size = null)).isValid(3f, 3f))
    }

    @Test fun unsupportedWindowPlacementDoesNotBecomeAnOemProfile() {
        assertFalse(style.copy(gravity = 3).isValid(3f, 3f))
        assertTrue(style.copy(gravity = 81).isValid(3f, 3f))
    }
}
