package dev.skomlach.biometric.app.signature

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import dev.skomlach.biometric.app.R
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.engine.internal.behavior.*
import dev.skomlach.biometric.compat.utils.ScreenProtection

/** Application-owned renderer and compatibility observations; never a trusted-capture assertion. */
class SignatureCaptureView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var onInputRejected: (() -> Unit)? = null
    private val path = Path()
    private val points = ArrayList<BehaviorPoint>()
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.purple_500)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var startedElapsed = 0L
    private var startedUptime = 0L
    private var capturing = false
    private var activePointer: Int? = null
    private var stroke = 0

    init {
        isSaveEnabled = false
        isSaveFromParentEnabled = false
        filterTouchesWhenObscured = true
        minimumHeight = (220 * resources.displayMetrics.density).toInt()
        contentDescription = context.getString(R.string.signature_system_pad_description)
    }

    fun beginCapture() {
        abortCapture()
        startedElapsed = SystemClock.elapsedRealtime()
        startedUptime = SystemClock.uptimeMillis()
        capturing = true
    }

    fun abortCapture() {
        capturing = false
        activePointer = null
        stroke = 0
        points.clear()
        path.reset()
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    fun takeSample(): BehaviorSample? {
        if (!capturing) return null
        if (expired()) { reject(); return null }
        if (activePointer != null || points.isEmpty()) return null
        val sample = BehaviorSample(BehaviorMode.SIGNATURE, null, emptyList(), emptyList(), points.toList())
        if (sample.qualityIssue() != BehaviorQualityIssue.NONE) return null
        abortCapture()
        return sample
    }

    private fun expired(): Boolean = SystemClock.elapsedRealtime() - startedElapsed !in 0 until 30_000L
    private fun reject() { abortCapture(); onInputRejected?.invoke() }

    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        if (!capturing || !isEnabled) return false
        if (event.flags and (MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0 ||
            event.pointerCount != 1 || expired() || BiometricPromptCompat.hasDetectedRuntimeHooks()) {
            reject()
            return false
        }
        return super.onFilterTouchEventForSecurity(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!onFilterTouchEventForSecurity(event)) return false
        val pointer = event.getPointerId(0)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (activePointer != null) { reject(); return false }
                activePointer = pointer
                parent?.requestDisallowInterceptTouchEvent(true)
                path.moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointer != pointer) { reject(); return false }
                path.lineTo(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                if (activePointer != pointer) { reject(); return false }
                activePointer = null
                stroke++
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            else -> { reject(); return false }
        }
        for (index in 0 until event.historySize) {
            if (!record(event.getHistoricalX(index), event.getHistoricalY(index), event.getHistoricalEventTime(index),
                    event.getHistoricalPressure(index), event.getHistoricalSize(index))) return false
        }
        if (!record(event.x, event.y, event.eventTime, event.pressure, event.size)) return false
        invalidate()
        return true
    }

    private fun record(x: Float, y: Float, timestamp: Long, pressure: Float, size: Float): Boolean {
        val relative = timestamp - startedUptime
        if (!x.isFinite() || !y.isFinite() || kotlin.math.abs(x) > 100_000f || kotlin.math.abs(y) > 100_000f ||
            !pressure.isFinite() || !size.isFinite() || pressure < 0f || size < 0f ||
            timestamp > SystemClock.uptimeMillis() || relative < 0L ||
            relative < (points.lastOrNull()?.timestampMs ?: 0L) || points.size >= 2048) {
            reject()
            return false
        }
        points += BehaviorPoint(x, y, relative, pressure, size, stroke)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawPath(path, ink) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); ScreenProtection.applyProtectionInView(this) }
    override fun onDetachedFromWindow() {
        abortCapture()
        ScreenProtection.applyProtectionInView(this, false)
        super.onDetachedFromWindow()
    }
}
