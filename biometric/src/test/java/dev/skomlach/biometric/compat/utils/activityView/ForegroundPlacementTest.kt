package dev.skomlach.biometric.compat.utils.activityView

import org.junit.Assert.*
import org.junit.Test

class ForegroundPlacementTest {
    private val viewport = FeedbackBounds(0, 24, 1080, 1900)

    @Test fun positionsWholeGroupAboveMeasuredCard() {
        assertEquals(780, feedbackGroupTop(viewport, FeedbackBounds(20, 1000, 1060, 1880), 200, 20))
    }

    @Test fun unknownGeometryUsesInsetAwareTop() {
        assertEquals(44, feedbackGroupTop(viewport, null, 200, 20))
    }

    @Test fun noSpaceDoesNotOverlapKnownDialog() {
        assertNull(feedbackGroupTop(viewport, FeedbackBounds(0, 180, 1080, 1900), 200, 20))
    }

    @Test fun dialogOnAnotherPartOfDisplayDoesNotHideHostFeedback() {
        assertEquals(44, feedbackGroupTop(viewport, FeedbackBounds(1100, 0, 2000, 1900), 200, 20))
    }

    @Test fun reconstructsBottomCardIncludingAsymmetricInsets() {
        assertEquals(FeedbackBounds(112, 1320, 964, 1880), reconstructFeedbackDialogBounds(
            viewport, 880, 600, 12, 20, 16, 20, centered = false))
    }

    @Test fun reconstructsCenteredCardWithinInsetViewport() {
        assertEquals(FeedbackBounds(100, 662, 980, 1262), reconstructFeedbackDialogBounds(
            viewport, 880, 600, 0, 0, 0, 0, centered = true))
    }

    @Test fun screenToHostCoordinatesIncludeWindowOrigin() {
        assertEquals(FeedbackBounds(10, 100, 510, 700),
            FeedbackBounds(210, 400, 710, 1000).relativeTo(200, 300))
    }

    @Test fun invalidOrOversizedGeometryIsNotPresentedAsAUsableEstimate() {
        assertNull(reconstructFeedbackDialogBounds(viewport, 0, 600, 0, 0, 0, 0, false))
        assertNull(reconstructFeedbackDialogBounds(viewport, 900, 2000, 0, 0, 0, 0, false))
        assertNull(feedbackGroupTop(FeedbackBounds(0, 0, 10, 10), null, 30, 5))
    }
}
