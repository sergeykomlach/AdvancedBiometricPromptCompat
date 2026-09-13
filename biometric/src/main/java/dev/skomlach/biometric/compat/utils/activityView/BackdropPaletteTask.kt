package dev.skomlach.biometric.compat.utils.activityView

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicReference

/** Cancellation drops the UI owner immediately; a running worker retains only its own pixels. */
internal class BackdropPaletteTask(
    bitmap: Bitmap,
    private val crop: BitmapCropBounds,
    onColor: (Int) -> Unit
) {
    private val pendingBitmap = AtomicReference<Bitmap?>(bitmap)
    private val callback = AtomicReference<((Int) -> Unit)?>(onColor)

    fun start() {
        ExecutorHelper.startOnBackground {
            val original = pendingBitmap.getAndSet(null) ?: return@startOnBackground
            var cropped: Bitmap? = null
            val color = try {
                cropped = Bitmap.createBitmap(original, crop.left, crop.top, crop.width, crop.height)
                Palette.from(cropped).clearFilters().clearTargets().generate()
                    .getDominantColor(Color.TRANSPARENT)
            } catch (error: Throwable) {
                BiometricLoggerImpl.e(error)
                Color.TRANSPARENT
            } finally {
                if (cropped !== original) cropped?.recycle()
                original.recycle()
            }
            ExecutorHelper.post { callback.getAndSet(null)?.invoke(color) }
        }
    }

    fun cancel() {
        callback.set(null)
        pendingBitmap.getAndSet(null)?.recycle()
    }
}
