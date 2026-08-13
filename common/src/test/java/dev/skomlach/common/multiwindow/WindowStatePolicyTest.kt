package dev.skomlach.common.multiwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowStatePolicyTest {

    @Test
    fun constrainedCurrentBoundsAreTreatedAsMultiWindowWhenPlatformFlagIsFalse() {
        val state = windowStateForTest(
            current = WindowBoundsPx(left = 0, top = 0, width = 900, height = 1600),
            maximum = WindowBoundsPx(left = 0, top = 0, width = 1800, height = 1600),
            isPlatformMultiWindow = false
        )

        assertTrue(state.isInWindowedMode)
    }

    @Test
    fun bubbleIsTreatedAsWindowedEvenWhenBoundsMatchMaximumBounds() {
        val state = windowStateForTest(
            current = WindowBoundsPx(left = 0, top = 0, width = 480, height = 640),
            maximum = WindowBoundsPx(left = 0, top = 0, width = 480, height = 640),
            isLaunchedFromBubble = true
        )

        assertTrue(state.isInWindowedMode)
    }

    @Test
    fun pictureInPictureIsTreatedAsWindowedEvenWhenPlatformMultiWindowFlagIsFalse() {
        val state = windowStateForTest(
            current = WindowBoundsPx(left = 0, top = 0, width = 420, height = 260),
            maximum = WindowBoundsPx(left = 0, top = 0, width = 420, height = 260),
            isPictureInPicture = true
        )

        assertTrue(state.isInWindowedMode)
    }

    @Test
    fun bottomHalfCalculationUsesWindowHeight() {
        val state = windowStateForTest(
            current = WindowBoundsPx(left = 0, top = 700, width = 1600, height = 100),
            maximum = WindowBoundsPx(left = 0, top = 0, width = 1600, height = 1600),
            isPlatformMultiWindow = true
        )

        assertFalse(state.isWindowOnScreenBottom)
    }

    @Test
    fun squareWindowsStaySquareForAdaptiveLayoutDecisions() {
        val state = windowStateForTest(
            current = WindowBoundsPx(left = 0, top = 0, width = 1000, height = 1000),
            maximum = WindowBoundsPx(left = 0, top = 0, width = 1000, height = 1000)
        )

        assertEquals(WindowOrientation.SQUARE, state.orientation)
    }

    @Test
    fun dialogWidthUsesSafeCurrentWindowWidth() {
        val width = resolveDialogWidthPx(
            configuredWidthPx = 765,
            currentBounds = WindowBoundsPx(left = 0, top = 0, width = 700, height = 900),
            safeInsets = WindowInsetsPx(left = 24, top = 0, right = 36, bottom = 0)
        )

        assertEquals(640, width)
    }

    @Test
    fun dialogWidthFallsBackToMatchParentWhenConfiguredWidthIsZero() {
        val width = resolveDialogWidthPx(
            configuredWidthPx = 0,
            currentBounds = WindowBoundsPx(left = 0, top = 0, width = 700, height = 900),
            safeInsets = WindowInsetsPx(left = 24, top = 0, right = 36, bottom = 0)
        )

        assertEquals(MATCH_PARENT_WIDTH, width)
    }

    private fun windowStateForTest(
        current: WindowBoundsPx,
        maximum: WindowBoundsPx,
        safeInsets: WindowInsetsPx = WindowInsetsPx.EMPTY,
        isPlatformMultiWindow: Boolean = false,
        isPictureInPicture: Boolean = false,
        isLaunchedFromBubble: Boolean = false,
        isLegacyMultiWindow: Boolean = false
    ): WindowState {
        return calculateWindowState(
            currentBounds = current,
            maximumBounds = maximum,
            physicalDisplayBounds = maximum,
            safeInsets = safeInsets,
            isPlatformMultiWindow = isPlatformMultiWindow,
            isPictureInPicture = isPictureInPicture,
            isLaunchedFromBubble = isLaunchedFromBubble,
            isLegacyMultiWindow = isLegacyMultiWindow
        )
    }
}
