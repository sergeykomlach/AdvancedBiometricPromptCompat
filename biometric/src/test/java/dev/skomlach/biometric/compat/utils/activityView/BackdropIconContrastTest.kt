package dev.skomlach.biometric.compat.utils.activityView

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropIconContrastTest {
    @Test
    fun paletteStillUpdatesWhileTheNextFrameIsBeingAnalyzed() {
        val results = BackdropPaletteResults()
        val first = results.nextRequest()
        val pending = results.nextRequest()
        assertTrue(results.accept(first))
        assertTrue(results.accept(pending))
    }

    @Test
    fun latePaletteCannotOverwriteANewerAppliedColor() {
        val results = BackdropPaletteResults()
        val old = results.nextRequest()
        val recent = results.nextRequest()
        assertTrue(results.accept(recent))
        assertFalse(results.accept(old))
        assertFalse(results.accept(recent))
    }

    @Test
    fun paletteFromThePreviousShowingIsRejectedAfterReopen() {
        val results = BackdropPaletteResults()
        val old = results.nextRequest()
        results.reset()
        val recent = results.nextRequest()
        assertFalse(results.accept(old))
        assertTrue(results.accept(recent))
    }

    @Test
    fun middleGrayNeedsDarkIconsDespiteLuminanceBelowOneHalf() {
        // #808080: black has 5.32:1 contrast, white only 3.95:1.
        assertTrue(useDarkIcons(0.21586))
    }

    @Test
    fun saturatedRedNeedsDarkIconsButBlueNeedsLightIcons() {
        assertTrue(useDarkIcons(0.2126))
        assertFalse(useDarkIcons(0.0722))
    }

    @Test
    fun nearBlackAndNearWhiteNeedTheOppositeIconColor() {
        assertFalse(useDarkIcons(0.001))
        assertTrue(useDarkIcons(0.98))
    }

    @Test
    fun blackAndWhiteCrossoverIsNearPointOneEight() {
        assertFalse(useDarkIcons(0.17))
        assertTrue(useDarkIcons(0.18))
    }

    @Test
    fun actualThemeShadesCanChangeWhichColorHasBetterContrast() {
        assertTrue(useDarkIcons(0.19))
        assertFalse(shouldUseDarkBackdropIcons(
            backgroundLuminance = 0.19, lightIconLuminance = 0.9, darkIconLuminance = 0.03
        ))
    }

    @Test
    fun backgroundsOutsideTheThemeShadeRangeStillCompareBothRatios() {
        assertFalse(shouldUseDarkBackdropIcons(
            backgroundLuminance = 0.0, lightIconLuminance = 0.9, darkIconLuminance = 0.03
        ))
        assertTrue(shouldUseDarkBackdropIcons(
            backgroundLuminance = 1.0, lightIconLuminance = 0.9, darkIconLuminance = 0.03
        ))
    }

    private fun useDarkIcons(backgroundLuminance: Double): Boolean =
        shouldUseDarkBackdropIcons(backgroundLuminance, lightIconLuminance = 1.0, darkIconLuminance = 0.0)
}
