package dev.skomlach.biometric.compat.impl.dialogs

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.R
import kotlin.math.max

internal class BehaviorCaptureController(
    private val rootView: View,
    private val builder: BiometricPromptCompat.Builder,
    private val onReady: () -> Unit
) {
    private var prepared = false
    private val keyDowns = ArrayList<Long>()
    private val keyUps = ArrayList<Long>()
    private val points = ArrayList<Float>()
    private lateinit var phraseInput: TextView
    private lateinit var signaturePad: SignaturePad
    private lateinit var modeGroup: RadioGroup
    private lateinit var actionButton: Button
    private lateinit var hintText: TextView
    private lateinit var errorText: TextView
    private var typingKeyboard: View? = null
    private var ownsTypingView = false
    private var externalSignatureContainer: ViewGroup? = null
    private var textWatcher: TextWatcher? = null
    private var overlayView: View? = null
    private var behaviorButton: View? = null

    fun install() {
        val container = rootView.findViewById<FrameLayout>(R.id.auth_content_container) ?: return
        prepareInputs(container.context)
        behaviorButton = createBehaviorButton(container).also { container.addView(it) }
    }

    fun consumePrepared(): Boolean = prepared

    fun dispose() {
        textWatcher?.let { phraseInput.removeTextChangedListener(it) }
        textWatcher = null
        if (ownsTypingView) {
            phraseInput.setOnKeyListener(null)
        }
        externalSignatureContainer?.removeView(signaturePad)
        externalSignatureContainer = null
        overlayView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        behaviorButton?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlayView = null
        behaviorButton = null
    }

    private fun prepareInputs(context: android.content.Context) {
        phraseInput = builder.getBehaviorTypingView() ?: EditText(context).apply {
            hint = "Type phrase"
            isSingleLine = true
            inputType = InputType.TYPE_NULL
            isFocusable = false
            ownsTypingView = true
        }
        attachTypingCapture(phraseInput)

        externalSignatureContainer = builder.getBehaviorSignatureContainer()
        signaturePad = SignaturePad(
            externalSignatureContainer?.context ?: context,
            lineColor = ContextCompat.getColor(context, R.color.material_deep_teal_500)
        ) { x, y, timestamp, pressure, size, strokeId ->
            points.add(x)
            points.add(y)
            points.add(timestamp.toFloat())
            points.add(pressure)
            points.add(size)
            points.add(strokeId.toFloat())
        }
        externalSignatureContainer?.addView(
            signaturePad,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun createBehaviorButton(container: FrameLayout): View {
        val context = container.context
        val size = (48 * context.resources.displayMetrics.density).toInt()
        return TextView(context).apply {
            text = "B"
            textSize = 18f
            gravity = Gravity.CENTER
            contentDescription = "Behavior"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            background = rounded(
                color = ContextCompat.getColor(context, R.color.material_deep_teal_500),
                radius = size / 2f
            )
            elevation = 8f * context.resources.displayMetrics.density
            setOnClickListener {
                showOverlay(container)
            }
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.BOTTOM).apply {
                rightMargin = (12 * context.resources.displayMetrics.density).toInt()
                bottomMargin = (12 * context.resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun showOverlay(container: FrameLayout) {
        if (overlayView != null) return
        overlayView = createOverlay(container).also { container.addView(it) }
    }

    private fun createOverlay(container: FrameLayout): View {
        val context = container.context
        val density = context.resources.displayMetrics.density
        modeGroup = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            addView(RadioButton(context).apply {
                id = MODE_TYPING_ID
                text = "Typing"
            })
            addView(RadioButton(context).apply {
                id = MODE_SIGNATURE_ID
                text = "Signature"
            })
            addView(RadioButton(context).apply {
                id = MODE_COMBINED_ID
                text = "Combined"
            })
            check(MODE_COMBINED_ID)
            setOnCheckedChangeListener { _, _ -> updateModeVisibility() }
        }

        actionButton = Button(context).apply {
            text = if (builder.enroll) "Enroll" else "Verify"
            contentDescription = if (builder.enroll) "Enroll behavior sample" else "Verify behavior sample"
            setOnClickListener { prepareExtras() }
        }
        hintText = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.textColor))
        }
        errorText = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            visibility = View.GONE
            setTextColor(ContextCompat.getColor(context, R.color.material_red_500))
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            background = rounded(
                color = ContextCompat.getColor(context, android.R.color.white),
                radius = 18 * density
            )
            elevation = 10f * density
            addView(TextView(context).apply {
                text = if (builder.enroll) "Create behavior sample" else "Confirm behavior"
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.textColor))
            })
            addView(modeGroup)
            addView(hintText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(errorText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            if (builder.getBehaviorTypingView() == null) {
                addView(phraseInput, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                typingKeyboard = createTypingKeyboard(context).also {
                    addView(it, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ))
                }
            }
            if (builder.getBehaviorSignatureContainer() == null) {
                addView(TextView(context).apply {
                    text = "Draw signature"
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.textColor))
                })
                addView(signaturePad, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    max(180, (180 * density).toInt())
                ))
            }
            addView(actionButton)
            updateModeVisibility()
        }

        return FrameLayout(context).apply {
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener {
                hideOverlay()
            }
            addView(panel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                leftMargin = (12 * density).toInt()
                rightMargin = (12 * density).toInt()
            })
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            panel.setOnClickListener { }
        }
    }

    private fun hideOverlay() {
        overlayView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlayView = null
    }

    private fun attachTypingCapture(view: TextView) {
        if (ownsTypingView) {
            view.setOnKeyListener { _, eventKeyCode, event ->
                if (eventKeyCode == KeyEvent.KEYCODE_UNKNOWN) {
                    return@setOnKeyListener false
                }
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> keyDowns.add(event.eventTime)
                    KeyEvent.ACTION_UP -> keyUps.add(event.eventTime)
                }
                false
            }
            return
        }
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0) {
                    keyDowns.add(System.currentTimeMillis())
                    keyUps.add(System.currentTimeMillis() + TEXT_EVENT_DWELL_MS)
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }.also {
            view.addTextChangedListener(it)
        }
    }

    private fun updateModeVisibility() {
        val mode = selectedMode()
        if (ownsTypingView) {
            phraseInput.visibility = if (mode != MODE_SIGNATURE) View.VISIBLE else View.GONE
            typingKeyboard?.visibility = if (mode != MODE_SIGNATURE) View.VISIBLE else View.GONE
        }
        signaturePad.visibility = if (mode != MODE_TYPING) View.VISIBLE else View.GONE
        hintText.text = when (mode) {
            MODE_TYPING -> "Type at least 5 characters with a natural rhythm."
            MODE_SIGNATURE -> "Draw a clear signature with enough length and shape."
            else -> "Type a short phrase and draw a clear signature."
        }
        clearInputError()
    }

    private fun createTypingKeyboard(context: android.content.Context): View {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            KEY_ROWS.forEach { row ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    row.forEach { key ->
                        addView(createKeyButton(context, key), LinearLayout.LayoutParams(
                            0,
                            (42 * density).toInt(),
                            if (key == KEY_SPACE) 2f else 1f
                        ).apply {
                            leftMargin = (2 * density).toInt()
                            rightMargin = (2 * density).toInt()
                            topMargin = (2 * density).toInt()
                            bottomMargin = (2 * density).toInt()
                        })
                    }
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
        }
    }

    private fun createKeyButton(context: android.content.Context, key: String): TextView {
        var downTime = 0L
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = when (key) {
                KEY_SPACE -> "space"
                KEY_BACKSPACE -> "<"
                else -> key
            }
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.textColor))
            background = rounded(0x11000000, 8 * density)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downTime = event.eventTime
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (key == KEY_BACKSPACE) {
                            removeLastTypedCharacter()
                        } else {
                            appendTypedCharacter(key, downTime, event.eventTime)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        downTime = 0L
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun appendTypedCharacter(key: String, downTime: Long, upTime: Long) {
        phraseInput.append(if (key == KEY_SPACE) " " else key)
        keyDowns.add(downTime)
        keyUps.add(upTime)
    }

    private fun removeLastTypedCharacter() {
        val current = phraseInput.text?.toString().orEmpty()
        if (current.isEmpty()) return
        phraseInput.text = current.dropLast(1)
        if (keyDowns.isNotEmpty()) keyDowns.removeAt(keyDowns.lastIndex)
        if (keyUps.isNotEmpty()) keyUps.removeAt(keyUps.lastIndex)
    }

    private fun prepareExtras() {
        val mode = selectedMode()
        val phrase = phraseInput.text?.toString().orEmpty()
        val hasTyping = phrase.trim().length >= MIN_TYPING_CHARS &&
            keyDowns.size >= MIN_TYPING_EVENTS &&
            keyDowns.size == keyUps.size
        val hasSignature = points.size >= POINT_STRIDE * MIN_SIGNATURE_POINTS
        if ((mode == MODE_TYPING || mode == MODE_COMBINED) && !hasTyping) {
            showInputError("Type at least 5 characters before continuing.")
            return
        }
        if ((mode == MODE_SIGNATURE || mode == MODE_COMBINED) && !hasSignature) {
            showInputError("Draw a longer signature before continuing.")
            return
        }

        val extras = Bundle().apply {
            putString(EXTRA_BEHAVIOR_MODE, mode)
            putString(EXTRA_BEHAVIOR_PHRASE, phrase)
            putLongArray(EXTRA_BEHAVIOR_KEY_DOWNS, keyDowns.toLongArray())
            putLongArray(EXTRA_BEHAVIOR_KEY_UPS, keyUps.toLongArray())
            putFloatArray(EXTRA_BEHAVIOR_POINTS, points.toFloatArray())
            putInt(EXTRA_BEHAVIOR_POINTS_STRIDE, POINT_STRIDE)
            putBoolean(BundleBuilder.ENROLL, builder.enroll)
        }
        prepared = true
        builder.setExtras(extras)
        actionButton.isEnabled = false
        rootView.findViewById<TextView>(R.id.status)?.text = "Checking behavior"
        hideOverlay()
        onReady()
    }

    private fun showInputError(message: CharSequence) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        rootView.findViewById<TextView>(R.id.status)?.text = message
    }

    private fun clearInputError() {
        if (::errorText.isInitialized) {
            errorText.text = null
            errorText.visibility = View.GONE
        }
    }

    private fun selectedMode(): String {
        return when (modeGroup.checkedRadioButtonId) {
            MODE_TYPING_ID -> MODE_TYPING
            MODE_SIGNATURE_ID -> MODE_SIGNATURE
            else -> MODE_COMBINED
        }
    }

    private class SignaturePad(
        context: android.content.Context,
        lineColor: Int,
        private val onPoint: (Float, Float, Long, Float, Float, Int) -> Unit
    ) : View(context) {
        private val path = Path()
        private var strokeId = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = context.resources.displayMetrics.density * 2.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        init {
            background = rounded(
                color = 0x11ffffff,
                radius = 12 * context.resources.displayMetrics.density
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> path.moveTo(event.x, event.y)
                MotionEvent.ACTION_MOVE -> path.lineTo(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> strokeId++
            }
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                for (index in 0 until event.historySize) {
                    onPoint(
                        event.getHistoricalX(index),
                        event.getHistoricalY(index),
                        event.getHistoricalEventTime(index),
                        event.getHistoricalPressure(index),
                        event.getHistoricalSize(index),
                        strokeId
                    )
                }
                onPoint(event.x, event.y, event.eventTime, event.pressure, event.size, strokeId)
                invalidate()
                return true
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawPath(path, paint)
        }
    }

    private companion object {
        const val EXTRA_BEHAVIOR_MODE = "behavior.mode"
        const val EXTRA_BEHAVIOR_PHRASE = "behavior.phrase"
        const val EXTRA_BEHAVIOR_KEY_DOWNS = "behavior.key_downs"
        const val EXTRA_BEHAVIOR_KEY_UPS = "behavior.key_ups"
        const val EXTRA_BEHAVIOR_POINTS = "behavior.points"
        const val EXTRA_BEHAVIOR_POINTS_STRIDE = "behavior.points_stride"

        const val MODE_TYPING = "TYPING"
        const val MODE_SIGNATURE = "SIGNATURE"
        const val MODE_COMBINED = "COMBINED"
        const val MODE_TYPING_ID = 0x510001
        const val MODE_SIGNATURE_ID = 0x510002
        const val MODE_COMBINED_ID = 0x510003
        const val POINT_STRIDE = 6
        const val TEXT_EVENT_DWELL_MS = 60L
        const val MIN_TYPING_CHARS = 5
        const val MIN_TYPING_EVENTS = 5
        const val MIN_SIGNATURE_POINTS = 16
        const val KEY_SPACE = " "
        const val KEY_BACKSPACE = "backspace"
        val KEY_ROWS = listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m", KEY_BACKSPACE),
            listOf(KEY_SPACE)
        )

        fun rounded(color: Int, radius: Float): GradientDrawable {
            return GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius
            }
        }
    }
}
