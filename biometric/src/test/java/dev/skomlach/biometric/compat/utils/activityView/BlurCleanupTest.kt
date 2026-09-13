package dev.skomlach.biometric.compat.utils.activityView

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlurCleanupTest {

    @Test
    fun fastCaptureWaitsUntilTheTwentyFpsDeadline() {
        val latch = BlurCaptureLatch(minCaptureIntervalMillis = 50L)
        val token = latch.tryStart(1_000L)!!
        assertNull(latch.delayUntilReady(1_010L))
        assertEquals(true, latch.finish(token, 1_010L))
        assertEquals(40L, latch.delayUntilReady(1_010L))
        assertNull(latch.tryStart(1_049L))
        assertEquals(true, latch.tryStart(1_050L) != null)
    }

    @Test
    fun repeatedDrawsDoNotPostponeThePendingCaptureDeadline() {
        val latch = BlurCaptureLatch(minCaptureIntervalMillis = 50L)
        val token = latch.tryStart(0L)!!
        latch.finish(token, 5L)
        for (now in 10L until 50L step 10L) {
            assertNull(latch.tryStart(now))
            assertEquals(50L - now, latch.delayUntilReady(now))
        }
        assertEquals(true, latch.tryStart(50L) != null)
    }

    @Test
    fun slowCaptureLeavesHeadroomAndFastCaptureRecovers() {
        val latch = BlurCaptureLatch(minCaptureIntervalMillis = 50L)
        val slow = latch.tryStart(0L)!!
        latch.finish(slow, 80L)
        assertEquals(80L, latch.delayUntilReady(80L))
        val fast = latch.tryStart(160L)!!
        latch.finish(fast, 165L)
        assertEquals(45L, latch.delayUntilReady(165L))
    }

    @Test
    fun longCaptureDoesNotAddAnUnboundedDelayAfterCompletion() {
        val latch = BlurCaptureLatch(minCaptureIntervalMillis = 50L)
        val token = latch.tryStart(0L)!!
        assertNull(latch.tryStart(300L))
        latch.finish(token, 300L)
        assertEquals(0L, latch.delayUntilReady(300L))
    }

    @Test
    fun restartClearsTheDeadlineAndRejectsTheOldCallback() {
        val latch = BlurCaptureLatch(minCaptureIntervalMillis = 50L)
        val old = latch.tryStart(1_000L)!!
        latch.reset()
        val current = latch.tryStart(1_010L)!!
        assertEquals(false, latch.finish(old, 1_020L))
        assertNull(latch.delayUntilReady(1_020L))
        assertEquals(true, latch.finish(current, 1_025L))
        assertEquals(35L, latch.delayUntilReady(1_025L))
    }

    @Test
    fun androidSRenderEffectDoesNotNeedBitmapCapture() {
        assertEquals(false, shouldCaptureBlurBitmap(isAtLeastS = true))
    }

    @Test
    fun legacyBlurStillNeedsBitmapCapture() {
        assertEquals(true, shouldCaptureBlurBitmap(isAtLeastS = false))
    }

    @Test
    fun androidSCapturesBackdropPaletteOnceWithoutContinuousBitmapBlur() {
        assertEquals(true, shouldCaptureBackdropPalette(isAtLeastS = true))
        assertEquals(false, shouldCaptureBackdropPalette(isAtLeastS = false))
    }

    @Test
    fun timedOutBlurCaptureDoesNotCompleteANewerCapture() {
        val latch = BlurCaptureLatch()
        val first = latch.tryStart()

        assertEquals(true, first != null)
        assertEquals(false, latch.tryStart() != null)
        assertEquals(true, latch.finish(first!!))

        val second = latch.tryStart()
        assertEquals(true, second != null)
        assertEquals(false, latch.finish(first))
        assertEquals(false, latch.tryStart() != null)
        assertEquals(true, latch.finish(second!!))
        assertEquals(true, latch.tryStart() != null)
    }

    @Test
    fun cleanupClearsEffectBeforeRemovingOverlayAndInvalidatingHost() {
        val calls = mutableListOf<String>()

        runBlurCleanup(
            clearRenderEffect = { calls += "clear-effect" },
            removeOverlay = { calls += "remove-overlay" },
            invalidateHost = { calls += "invalidate-host" },
            onFailure = { throw it }
        )

        assertEquals(
            listOf("clear-effect", "remove-overlay", "invalidate-host"),
            calls
        )
    }

    @Test
    fun cleanupStillRemovesOverlayAndInvalidatesWhenEffectClearFails() {
        val calls = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()

        runBlurCleanup(
            clearRenderEffect = {
                calls += "clear-effect"
                error("clear failed")
            },
            removeOverlay = { calls += "remove-overlay" },
            invalidateHost = { calls += "invalidate-host" },
            onFailure = { failures += it }
        )

        assertEquals(
            listOf("clear-effect", "remove-overlay", "invalidate-host"),
            calls
        )
        assertEquals(listOf("clear failed"), failures.map { it.message })
    }
}
