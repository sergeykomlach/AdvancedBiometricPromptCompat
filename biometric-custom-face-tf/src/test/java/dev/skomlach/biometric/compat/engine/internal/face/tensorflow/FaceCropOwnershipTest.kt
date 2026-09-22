package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.*
import org.junit.Test

class FaceCropOwnershipTest {
    private class Image { var recycled = false }

    @Test fun fullFrameAliasNeverRecyclesBorrowedCameraFrame() {
        val frame = Image()
        val result = Image()
        assertSame(result, transformFaceCrop(frame, frame, { it.recycled = true }) { result })
        assertFalse(frame.recycled)
        assertFalse(result.recycled)
    }

    @Test fun identityScaleRetainsReturnedCropUntilItsConsumerReleasesIt() {
        val frame = Image()
        val crop = Image()
        assertSame(crop, transformFaceCrop(frame, crop, { it.recycled = true }) { it })
        assertFalse(frame.recycled)
        assertFalse(crop.recycled)
    }

    @Test fun distinctIntermediateIsReleasedButNotTheOutput() {
        val frame = Image()
        val crop = Image()
        val output = Image()
        assertSame(output, transformFaceCrop(frame, crop, { it.recycled = true }) { output })
        assertTrue(crop.recycled)
        assertFalse(frame.recycled)
        assertFalse(output.recycled)
    }

    @Test fun failedTransformReleasesOnlyOwnedIntermediate() {
        val frame = Image()
        val crop = Image()
        assertThrows(IllegalStateException::class.java) {
            transformFaceCrop<Image>(frame, crop, { it.recycled = true }) { error("failed") }
        }
        assertTrue(crop.recycled)
        assertFalse(frame.recycled)
    }
}
