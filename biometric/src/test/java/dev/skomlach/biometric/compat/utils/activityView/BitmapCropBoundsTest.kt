package dev.skomlach.biometric.compat.utils.activityView

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitmapCropBoundsTest {

    @Test
    fun globalTargetRectIsConvertedToBitmapLocalCrop() {
        val crop = resolveBitmapCropBounds(
            targetScreenLeft = 120,
            targetScreenTop = 250,
            targetWidth = 90,
            targetHeight = 80,
            bitmapHostScreenLeft = 100,
            bitmapHostScreenTop = 200,
            bitmapWidth = 300,
            bitmapHeight = 400
        )

        assertEquals(BitmapCropBounds(left = 20, top = 50, width = 90, height = 80), crop)
    }

    @Test
    fun cropBoundsAreClampedToBitmap() {
        val crop = resolveBitmapCropBounds(
            targetScreenLeft = 80,
            targetScreenTop = 180,
            targetWidth = 90,
            targetHeight = 80,
            bitmapHostScreenLeft = 100,
            bitmapHostScreenTop = 200,
            bitmapWidth = 60,
            bitmapHeight = 50
        )

        assertEquals(BitmapCropBounds(left = 0, top = 0, width = 60, height = 50), crop)
    }

    @Test
    fun cropOutsideBitmapReturnsNull() {
        val crop = resolveBitmapCropBounds(
            targetScreenLeft = 500,
            targetScreenTop = 500,
            targetWidth = 90,
            targetHeight = 80,
            bitmapHostScreenLeft = 100,
            bitmapHostScreenTop = 200,
            bitmapWidth = 60,
            bitmapHeight = 50
        )

        assertNull(crop)
    }
}
