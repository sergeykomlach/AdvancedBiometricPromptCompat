package dev.skomlach.biometric.app.signature

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = [29], application = android.app.Application::class, manifest = Config.NONE)
class SignatureCaptureViewTest {
    private val view = SignatureCaptureView(RuntimeEnvironment.getApplication())
    private var downTime = 0L
    private fun touch(action: Int, x: Float, y: Float, flags: Int = 0, stepMs: Long = 20L) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(stepMs))
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        if (flags != 0) {
            val property = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
            val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .5f; size = .4f })
            val obscured = MotionEvent.obtain(downTime, now, action, 1, property, coordinates, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, flags)
            try { view.dispatchTouchEvent(obscured) } finally { obscured.recycle(); event.recycle() }
        } else try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun drawSignature() {
        repeat(2) { stroke ->
            repeat(12) { index -> touch(if (index == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE,
                10f + index * 12f, 20f + (index % 3) * 35f + stroke * 5) }
            touch(MotionEvent.ACTION_UP, 142f, 60f)
        }
    }

    @Test fun ownViewCapturesTwoStrokesWithSmallRelativeTimesAndConsumesOnce() {
        view.beginCapture(); drawSignature()
        val sample = view.takeSample()!!
        assertEquals(setOf(0, 1), sample.strokePoints.map { it.strokeId }.toSet())
        assertTrue(sample.strokePoints.size >= 24)
        assertTrue(sample.strokePoints.all { it.timestampMs in 0..30_000L })
        assertNull(sample.phrase)
        assertTrue(sample.keyDownTimesMs.isEmpty())
        assertNull(view.takeSample())
    }

    @Test fun obscuredInputInvalidatesBeforeFrameworkDropsItAndRequiresRestart() {
        listOf(MotionEvent.FLAG_WINDOW_IS_OBSCURED, MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED).forEach { flags ->
            view.beginCapture(); drawSignature()
            touch(MotionEvent.ACTION_DOWN, 1f, 1f, flags)
            drawSignature()
            assertNull(view.takeSample())
            view.beginCapture(); drawSignature()
            assertNotNull(view.takeSample())
        }
    }

    @Test fun canceledGestureAndExpiredCaptureCannotBeSubmitted() {
        view.beginCapture(); drawSignature()
        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        touch(MotionEvent.ACTION_CANCEL, 10f, 10f)
        assertNull(view.takeSample())
        view.beginCapture(); drawSignature()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(31))
        assertNull(view.takeSample())
    }

    @Test fun rawSignatureIsNotSavedAndAbortClearsIt() {
        view.id = 123
        view.beginCapture(); drawSignature()
        val saved = android.util.SparseArray<android.os.Parcelable>()
        view.saveHierarchyState(saved)
        assertEquals(0, saved.size())
        view.abortCapture()
        assertNull(view.takeSample())
    }

    @Test fun historicalPointsAreRecordedOnceInTheirOriginalTimeOrder() {
        val origin = SystemClock.uptimeMillis()
        view.beginCapture()
        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        ShadowSystemClock.advanceBy(Duration.ofMillis(60))
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, now - 40, MotionEvent.ACTION_MOVE, 20f, 20f, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        event.addBatch(now - 20, 50f, 70f, .5f, .4f, 0)
        event.addBatch(now, 80f, 20f, .5f, .4f, 0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        repeat(16) { touch(MotionEvent.ACTION_MOVE, 20f + it * 7f, (it % 3) * 40f) }
        touch(MotionEvent.ACTION_UP, 132f, 40f)
        val sample = view.takeSample()!!
        assertEquals(listOf(20L, 40L, 60L, 80L), sample.strokePoints.take(4).map { it.timestampMs })
        assertTrue(SystemClock.uptimeMillis() > origin)
    }

    @Test fun multitouchInvalidatesTheWholeCapture() {
        view.beginCapture(); drawSignature()
        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = Array(2) { MotionEvent.PointerCoords().apply { x = 10f; y = 10f; pressure = .5f; size = .4f } }
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
            properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        assertNull(view.takeSample())
    }

    @Test fun tooManyPointsAreRejectedInsteadOfSilentlyTruncated() {
        view.beginCapture(); drawSignature()
        touch(MotionEvent.ACTION_DOWN, 10f, 10f, stepMs = 1)
        repeat(2050) { touch(MotionEvent.ACTION_MOVE, (it % 200).toFloat(), (it % 3) * 40f, stepMs = 1) }
        touch(MotionEvent.ACTION_UP, 10f, 10f, stepMs = 1)
        assertNull(view.takeSample())
    }
}
