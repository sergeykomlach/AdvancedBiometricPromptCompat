package dev.skomlach.biometric.compat.engine.internal.behavior

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.os.Build
import android.os.SystemClock
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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.R as BiometricR
import dev.skomlach.biometric.custom.behavior.R
import dev.skomlach.biometric.compat.utils.ScreenProtection
import dev.skomlach.common.protection.A11yDetection
import dev.skomlach.common.translate.LocalizationHelper
import kotlin.math.max

/** Exclusive input ownership also prevents restoring another capture's privacy settings. */
private object CaptureInputOwnership {
    private val owners = java.util.WeakHashMap<Any, Any>()
    @Synchronized fun acquire(keys: List<Any>, owner: Any): Boolean {
        if (keys.any { owners.containsKey(it) && owners[it] !== owner }) return false
        keys.forEach { owners[it] = owner }
        return true
    }
    @Synchronized fun release(keys: List<Any>, owner: Any) {
        keys.forEach { if (owners[it] === owner) owners.remove(it) }
    }
}

internal class BehaviorCaptureController(
    private val rootView: View,
    private val builder: BiometricPromptCompat.Builder,
    private val enroll: Boolean,
    private val onInputError: ((CharSequence) -> Unit)? = null,
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
    private var typingPolicy: BehaviorTypingInputPolicy? = null
    private var sessionToken: BehaviorCaptureSessionToken? = null
    private var captureInvalidated = false
    private val protectedOverlayViews = ArrayList<View>()
    private val inputProtection = ArrayList<InputProtection>()
    private var disposed = false
    private var consumerMode: BehaviorMode? = null
    private val inputOwner = Any()
    private var ownedInputKeys = emptyList<Any>()

    internal fun installConsumer(mode: BehaviorMode) {
        check(!disposed && consumerMode == null)
        require(mode == BehaviorMode.SIGNATURE || builder.getBehaviorTypingView() != null)
        require(mode == BehaviorMode.TYPING || builder.getBehaviorSignatureContainer() != null)
        consumerMode = mode
        prepareInputs(rootView.context)
        startCaptureSession()
    }

    internal fun submitConsumer(): Boolean {
        if (!disposed && consumerMode != null) prepareExtras()
        return prepared
    }

    internal fun restartConsumer() {
        if (!disposed && consumerMode != null) startCaptureSession()
    }

    fun install() {
        val container = rootView.findViewById<FrameLayout>(R.id.auth_content_container) ?: return
        installIn(container)
    }

    internal fun installIn(container: FrameLayout) {
        if (overlayView != null || disposed || prepared) return
        prepareInputs(container.context)
        resetCapturedSample()
        overlayView = createContent(container).also { container.addView(it) }
        startCaptureSession()
    }

    fun consumePrepared(): Boolean = prepared

    fun dispose() {
        if (disposed) return
        disposed = true
        try { disposeCapture() } finally {
            CaptureInputOwnership.release(ownedInputKeys, inputOwner)
            ownedInputKeys = emptyList()
        }
    }

    private fun disposeCapture() {
        textWatcher?.let { phraseInput.removeTextChangedListener(it) }
        textWatcher = null
        if (ownsTypingView) {
            phraseInput.setOnKeyListener(null)
        }
        hideOverlay()
        inputProtection.forEach { state ->
            state.view.filterTouchesWhenObscured = state.originalFilter
            if (state.acquiredProtection) ScreenProtection.applyProtectionInView(state.view, false)
        }
        inputProtection.clear()
        typingPolicy?.close()
        typingPolicy = null
        if (::signaturePad.isInitialized) {
            ScreenProtection.applyProtectionInView(signaturePad, false)
        }
        if (::signaturePad.isInitialized) externalSignatureContainer?.removeView(signaturePad)
        externalSignatureContainer = null
        BehaviorCaptureSessionRegistry.invalidate(sessionToken)
        sessionToken = null
        captureInvalidated = true
        overlayView = null
        builder.getExtras()?.let { extras ->
            if (extras.getLong(EXTRA_BEHAVIOR_SESSION_NONCE, Long.MIN_VALUE) == submittedNonce) {
                builder.setExtras(android.os.Bundle(extras).apply { clearBehaviorInput(this) })
            }
        }
    }

    private fun prepareInputs(context: android.content.Context) {
        val keys = (listOf<Any>(builder) + listOfNotNull(
            builder.getBehaviorTypingView(), builder.getBehaviorSignatureContainer())).distinct()
        check(CaptureInputOwnership.acquire(keys, inputOwner)) { "Behavior input is already being captured" }
        ownedInputKeys = keys
        phraseInput = builder.getBehaviorTypingView() ?: EditText(context).apply {
            hint = localized(R.string.biometriccompat_behavior_phrase_hint)
            isSingleLine = true
            inputType = InputType.TYPE_NULL
            isFocusable = false
            ownsTypingView = true
        }
        typingPolicy = BehaviorTypingInputPolicy(phraseInput)
        protectInput(phraseInput)
        attachTypingCapture(phraseInput)

        externalSignatureContainer = builder.getBehaviorSignatureContainer()
        externalSignatureContainer?.let {
            protectInput(it)
        }
        signaturePad = SignaturePad(
            context = externalSignatureContainer?.context ?: context,
            lineColor = ContextCompat.getColor(context, R.color.material_deep_teal_500),
            acceptsTouch = { acceptCaptureTouch(it) },
            onPoint = { x, y, timestamp, pressure, size, strokeId ->
                val token = sessionToken
                if (token != null && points.size + BehaviorSample.POINT_STRIDE <= MAX_CAPTURED_POINT_FLOATS) {
                    points.add(x)
                    points.add(y)
                    points.add(token.relativeUptime(timestamp).toFloat())
                    points.add(pressure)
                    points.add(size)
                    points.add(strokeId.toFloat())
                }
            },
            onInvalidated = ::interruptCapture
        )
        externalSignatureContainer?.addView(
            signaturePad,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun startCaptureSession() {
        BehaviorCaptureSessionRegistry.invalidate(sessionToken)
        resetCapturedSample()
        clearInputError()
        sessionToken = BehaviorCaptureSessionRegistry.start(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())
        if (!runtimeCaptureAllowed()) return
        if (isStrictBuiltInCapture() && evaluateBehaviorAccessibility(
                strict = true,
                hasWhitelistedService = A11yDetection.hasWhiteListedService(rootView.context),
                hasUntrustedService = !A11yDetection.shouldWeTrustA11y(rootView.context)
            ) == BehaviorAccessibilityDecision.REJECT_UNTRUSTED_SERVICE) {
            invalidateCapture()
            showInputError(localized(R.string.biometriccompat_behavior_error_accessibility))
        }
    }

    private fun createContent(container: FrameLayout): View {
        val context = container.context
        val density = context.resources.displayMetrics.density
        val accent = TypedValue().let {
            context.theme.resolveAttribute(android.R.attr.colorAccent, it, true)
            if (it.resourceId != 0) ContextCompat.getColor(context, it.resourceId) else it.data
        }
        modeGroup = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            addView(RadioButton(context).apply {
                id = MODE_TYPING_ID
                text = localized(R.string.biometriccompat_behavior_mode_typing)
            })
            addView(RadioButton(context).apply {
                id = MODE_SIGNATURE_ID
                text = localized(R.string.biometriccompat_behavior_mode_signature)
            })
            addView(RadioButton(context).apply {
                id = MODE_COMBINED_ID
                text = localized(R.string.biometriccompat_behavior_mode_combined)
            })
            check(MODE_COMBINED_ID)
            setOnCheckedChangeListener { _, _ -> updateModeVisibility() }
        }
        val checked = intArrayOf(android.R.attr.state_checked)
        for (index in 0 until modeGroup.childCount) {
            (modeGroup.getChildAt(index) as RadioButton).apply {
                buttonDrawable = null
                gravity = Gravity.CENTER
                textSize = 12f
                minHeight = (48 * density).toInt()
                setPadding((4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
                background = rounded(0xFFFFFFFF.toInt(), 12f * density)
                backgroundTintList = ColorStateList(arrayOf(checked, intArrayOf()),
                    intArrayOf((accent and 0x00FFFFFF) or 0x30000000, 0x00000000))
                setTextColor(ColorStateList(arrayOf(checked, intArrayOf()),
                    intArrayOf(accent, ContextCompat.getColor(context, R.color.textColor))))
                layoutParams = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        }

        actionButton = Button(context).apply {
            text = if (enroll) {
                localized(R.string.biometriccompat_behavior_action_enroll)
            } else {
                localized(R.string.biometriccompat_behavior_action_verify)
            }
            contentDescription = text
            backgroundTintList = ColorStateList.valueOf(accent)
            setTextColor(android.graphics.Color.WHITE)
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
                (phraseInput.parent as? ViewGroup)?.removeView(phraseInput)
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
                (signaturePad.parent as? ViewGroup)?.removeView(signaturePad)
                addView(TextView(context).apply {
                    text = localized(R.string.biometriccompat_behavior_signature_label)
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.textColor))
                })
                addView(signaturePad, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    max(180, (180 * density).toInt())
                ))
            }
            updateModeVisibility()
        }

        return object : FrameLayout(context) {
            override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean =
                acceptCaptureTouch(event, requireActive = false) && super.onFilterTouchEventForSecurity(event)

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val frame = Rect()
                getWindowVisibleDisplayFrame(frame)
                val windowHeight = frame.height().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                val parentHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
                    windowHeight else MeasureSpec.getSize(heightMeasureSpec)
                val height = minOf((600 * density).toInt(), windowHeight, parentHeight).coerceAtLeast(1)
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            }
        }.apply {
            filterTouchesWhenObscured = true
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.biometric_capture_panel_bg)
                addView(ScrollView(context).apply {
                    isFillViewport = true
                    addView(panel)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(Button(context).apply {
                        text = localized(R.string.biometriccompat_behavior_action_restart)
                        minWidth = 0
                        setOnClickListener { startCaptureSession() }
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    actionButton.minWidth = 0
                    addView(actionButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                })
            }
            addView(card, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ).apply {
                leftMargin = (12 * density).toInt()
                rightMargin = (12 * density).toInt()
                topMargin = (12 * density).toInt()
                bottomMargin = (12 * density).toInt()
            })
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            card.setOnClickListener { }
            protectOverlayView(this)
            protectOverlayView(panel)
            protectOverlayView(card)
            protectOverlayView(actionButton)
            protectOverlayView(modeGroup)
            typingKeyboard?.let(::protectOverlayView)
        }
    }

    private fun isStrictBuiltInCapture(): Boolean =
        ownsTypingView && externalSignatureContainer == null

    private fun protectOverlayView(view: View) {
        ScreenProtection.applyProtectionInView(view)
        protectedOverlayViews += view
    }

    private fun hideOverlay(invalidateSession: Boolean = true) {
        overlayView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlayView = null
        if (ownsTypingView && ::phraseInput.isInitialized) (phraseInput.parent as? ViewGroup)?.removeView(phraseInput)
        if (externalSignatureContainer == null && ::signaturePad.isInitialized)
            (signaturePad.parent as? ViewGroup)?.removeView(signaturePad)
        protectedOverlayViews.forEach { ScreenProtection.applyProtectionInView(it, false) }
        protectedOverlayViews.clear()
        clearCapturedData()
        if (invalidateSession) {
            BehaviorCaptureSessionRegistry.invalidate(sessionToken)
            sessionToken = null
        }
    }

    private fun resetCapturedSample() {
        prepared = false
        captureInvalidated = false
        clearCapturedData()
        if (::actionButton.isInitialized) actionButton.isEnabled = true
    }

    private fun clearCapturedData() {
        keyDowns.clear()
        keyUps.clear()
        points.clear()
        if (::signaturePad.isInitialized) {
            signaturePad.clear()
        }
        if (ownsTypingView && ::phraseInput.isInitialized) {
            phraseInput.text = ""
        }
    }

    private fun attachTypingCapture(view: TextView) {
        if (ownsTypingView) {
            view.setOnKeyListener { _, eventKeyCode, event ->
                if (!runtimeCaptureAllowed()) return@setOnKeyListener false
                val token = sessionToken ?: return@setOnKeyListener false
                if (eventKeyCode == KeyEvent.KEYCODE_UNKNOWN) {
                    return@setOnKeyListener false
                }
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> keyDowns.add(token.relativeUptime(event.eventTime))
                    KeyEvent.ACTION_UP -> keyUps.add(token.relativeUptime(event.eventTime))
                }
                false
            }
            return
        }
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!runtimeCaptureAllowed()) return
                if (count > 0 && keyDowns.size < MAX_CAPTURED_TYPING_EVENTS) {
                    val token = sessionToken ?: return
                    val nowMs = token.relativeUptime(SystemClock.uptimeMillis())
                    keyDowns.add((nowMs - TEXT_EVENT_DWELL_MS).coerceAtLeast(0L))
                    keyUps.add(nowMs)
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
            phraseInput.visibility = if (mode != BehaviorMode.SIGNATURE) View.VISIBLE else View.GONE
            typingKeyboard?.visibility = if (mode != BehaviorMode.SIGNATURE) View.VISIBLE else View.GONE
        }
        signaturePad.visibility = if (mode != BehaviorMode.TYPING) View.VISIBLE else View.GONE
        hintText.text = when (mode) {
            BehaviorMode.TYPING -> localized(R.string.biometriccompat_behavior_hint_typing)
            BehaviorMode.SIGNATURE -> localized(R.string.biometriccompat_behavior_hint_signature)
            BehaviorMode.COMBINED -> localized(R.string.biometriccompat_behavior_hint_combined)
        }
        if (!captureInvalidated) clearInputError()
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
        var downTime: Long? = null
        var downNonce: Long? = null
        val density = context.resources.displayMetrics.density
        return object : TextView(context) {
            override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean =
                acceptCaptureTouch(event) && super.onFilterTouchEventForSecurity(event)
        }.apply {
            text = when (key) {
                KEY_SPACE -> localized(R.string.biometriccompat_behavior_key_space)
                KEY_BACKSPACE -> localized(R.string.biometriccompat_behavior_key_backspace)
                else -> key
            }
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.textColor))
            background = ContextCompat.getDrawable(context, R.drawable.biometric_capture_surface_bg)
            filterTouchesWhenObscured = true
            setOnTouchListener { _, event ->
                if (!acceptCaptureTouch(event)) return@setOnTouchListener true
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downTime = event.eventTime
                        downNonce = sessionToken?.nonce
                        parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        parent?.requestDisallowInterceptTouchEvent(false)
                        val down = downTime
                        downTime = null
                        if (down == null || downNonce != sessionToken?.nonce) {
                            interruptCapture()
                            return@setOnTouchListener true
                        }
                        if (key == KEY_BACKSPACE) {
                            removeLastTypedCharacter()
                        } else {
                            appendTypedCharacter(key, down, event.eventTime)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        downTime = null
                        parent?.requestDisallowInterceptTouchEvent(false)
                        interruptCapture()
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun appendTypedCharacter(key: String, downTime: Long, upTime: Long) {
        val token = sessionToken ?: return
        if (phraseInput.text?.length ?: 0 >= MAX_CAPTURED_TYPING_CHARS ||
            keyDowns.size >= MAX_CAPTURED_TYPING_EVENTS
        ) {
            showInputError(localized(R.string.biometriccompat_behavior_error_phrase_too_long))
            return
        }
        phraseInput.append(if (key == KEY_SPACE) " " else key)
        keyDowns.add(token.relativeUptime(downTime))
        keyUps.add(token.relativeUptime(upTime))
    }

    private fun removeLastTypedCharacter() {
        val current = phraseInput.text?.toString().orEmpty()
        if (current.isEmpty()) return
        phraseInput.text = current.dropLast(1)
        if (keyDowns.isNotEmpty()) keyDowns.removeAt(keyDowns.lastIndex)
        if (keyUps.isNotEmpty()) keyUps.removeAt(keyUps.lastIndex)
    }

    private fun invalidateCapture() {
        captureInvalidated = true
        BehaviorCaptureSessionRegistry.invalidate(sessionToken)
        sessionToken = null
        clearCapturedData()
    }

    private fun interruptCapture() {
        invalidateCapture()
        if (::errorText.isInitialized) showInputError(localized(R.string.biometriccompat_behavior_error_capture_invalid))
    }

    private fun acceptCaptureTouch(event: MotionEvent, requireActive: Boolean = true): Boolean {
        if (!event.isTrustedBehaviorTouch()) {
            invalidateCapture()
            if (::errorText.isInitialized) showInputError(localized(R.string.biometriccompat_behavior_error_touch_obscured))
            return false
        }
        if (event.pointerCount != 1) {
            interruptCapture()
            return false
        }
        // Recovery and dismissal controls must remain usable after capture is invalidated.
        if (!requireActive) return true
        if (!runtimeCaptureAllowed()) return false
        if (captureInvalidated || disposed || prepared || sessionToken == null) return false
        if (SystemClock.elapsedRealtime() - sessionToken!!.startedAtMs > MAX_CAPTURE_DURATION_MS) {
            invalidateCapture()
            showInputError(localized(R.string.biometriccompat_behavior_help_timeout))
            return false
        }
        return true
    }

    private fun protectInput(view: View) {
        val originalFilter = view.filterTouchesWhenObscured
        val alreadyProtected = view.getTag(BiometricR.id.bio_tag_screen_protection_applied) == true
        if (!alreadyProtected) ScreenProtection.applyProtectionInView(view)
        inputProtection += InputProtection(view, originalFilter,
            !alreadyProtected && view.getTag(BiometricR.id.bio_tag_screen_protection_applied) == true)
        view.filterTouchesWhenObscured = true
    }

    private data class InputProtection(val view: View, val originalFilter: Boolean, val acquiredProtection: Boolean)
    private var submittedNonce: Long? = null

    private fun runtimeCaptureAllowed(): Boolean {
        if (!BiometricPromptCompat.hasDetectedRuntimeHooks()) return true
        invalidateCapture()
        showInputError(localized(R.string.biometriccompat_behavior_help_unavailable))
        return false
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private fun prepareExtras() {
        if (prepared || captureInvalidated || !runtimeCaptureAllowed()) return
        val mode = selectedMode()
        val phrase = phraseInput.text?.toString().orEmpty()
        val token = sessionToken ?: return
        val nowMs = SystemClock.elapsedRealtime()
        when (evaluateBehaviorCaptureSession(
            nowMs = nowMs,
            submitted = prepared,
            token = token,
            expectedNonce = token.nonce,
            maxDurationMs = MAX_CAPTURE_DURATION_MS
        )) {
            BehaviorCaptureSessionDecision.ACTIVE -> Unit
            BehaviorCaptureSessionDecision.EXPIRED -> {
                invalidateCapture()
                showInputError(localized(R.string.biometriccompat_behavior_help_timeout))
                return
            }
            BehaviorCaptureSessionDecision.ALREADY_SUBMITTED,
            BehaviorCaptureSessionDecision.INVALID_TOKEN -> return
        }
        if (phrase.length > MAX_CAPTURED_TYPING_CHARS) {
            showInputError(localized(R.string.biometriccompat_behavior_error_phrase_too_long))
            return
        }
        val hasTyping = phrase.trim().length >= MIN_TYPING_CHARS &&
            keyDowns.size >= MIN_TYPING_EVENTS &&
            keyDowns.size == keyUps.size
        val hasSignature = points.size >= BehaviorSample.POINT_STRIDE * MIN_SIGNATURE_POINTS
        if ((mode == BehaviorMode.TYPING || mode == BehaviorMode.COMBINED) && !hasTyping) {
            showInputError(localized(R.string.biometriccompat_behavior_error_need_typing))
            return
        }
        if ((mode == BehaviorMode.SIGNATURE || mode == BehaviorMode.COMBINED) && !hasSignature) {
            showInputError(localized(R.string.biometriccompat_behavior_error_need_signature))
            return
        }
        val sample = BehaviorSample(mode, phrase.trim(), keyDowns.toList(), keyUps.toList(), decodeCapturedPoints())
            .normalizedForMode()
        val inputDecision = evaluateBehaviorSampleIntegrity(
            sample, 0L, token.relativeUptime(SystemClock.uptimeMillis()), MAX_INTER_EVENT_GAP_MS)
        if (inputDecision != BehaviorInputIntegrityDecision.ACCEPT) {
            showInputError(localized(R.string.biometriccompat_behavior_error_capture_invalid))
            return
        }

        val extras = sealBehaviorExtras(builder.getExtras(), sample, enroll, token, nowMs)
        if (extras == null) {
            showInputError(localized(R.string.biometriccompat_behavior_error_capture_invalid))
            return
        }
        prepared = true
        submittedNonce = token.nonce
        builder.setExtras(extras)
        if (::actionButton.isInitialized) actionButton.isEnabled = false
        rootView.findViewById<TextView>(R.id.status)?.text =
            localized(R.string.biometriccompat_behavior_status_checking)
        hideOverlay(invalidateSession = false)
        onReady()
    }

    private fun decodeCapturedPoints(): List<BehaviorPoint> {
        return points.chunked(BehaviorSample.POINT_STRIDE).mapNotNull { values ->
            if (values.size != BehaviorSample.POINT_STRIDE) return@mapNotNull null
            BehaviorPoint(
                x = values[0],
                y = values[1],
                timestampMs = values[2].toLong(),
                pressure = values[3].takeIf { it >= 0f },
                size = values[4].takeIf { it >= 0f },
                strokeId = values[5].toInt()
            )
        }
    }

    private fun showInputError(message: CharSequence) {
        if (::errorText.isInitialized) {
            errorText.text = message
            errorText.visibility = View.VISIBLE
        }
        onInputError?.invoke(message)
        rootView.findViewById<TextView>(R.id.status)?.text = message
    }

    private fun clearInputError() {
        if (::errorText.isInitialized) {
            errorText.text = null
            errorText.visibility = View.GONE
        }
    }

    private fun selectedMode(): BehaviorMode {
        consumerMode?.let { return it }
        return when (modeGroup.checkedRadioButtonId) {
            MODE_TYPING_ID -> BehaviorMode.TYPING
            MODE_SIGNATURE_ID -> BehaviorMode.SIGNATURE
            else -> BehaviorMode.COMBINED
        }
    }

    private class SignaturePad(
        context: android.content.Context,
        lineColor: Int,
        private val acceptsTouch: (MotionEvent) -> Boolean,
        private val onPoint: (Float, Float, Long, Float, Float, Int) -> Unit,
        private val onInvalidated: () -> Unit
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
            filterTouchesWhenObscured = true
            ScreenProtection.applyProtectionInView(this)
            background = ContextCompat.getDrawable(
                context,
                R.drawable.biometric_capture_surface_bg
            )
        }

        fun clear() {
            parent?.requestDisallowInterceptTouchEvent(false)
            path.reset()
            strokeId = 0
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!acceptsTouch(event)) return true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    path.moveTo(event.x, event.y)
                }
                MotionEvent.ACTION_MOVE -> path.lineTo(event.x, event.y)
                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    strokeId++
                }
                MotionEvent.ACTION_CANCEL -> {
                    clear()
                    onInvalidated()
                    return true
                }
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

        override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean =
            acceptsTouch(event) && super.onFilterTouchEventForSecurity(event)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawPath(path, paint)
        }
    }

    private companion object {
        const val MODE_TYPING_ID = 0x510001
        const val MODE_SIGNATURE_ID = 0x510002
        const val MODE_COMBINED_ID = 0x510003
        const val MAX_CAPTURED_TYPING_CHARS = 256
        const val MAX_CAPTURED_TYPING_EVENTS = 512
        const val MAX_CAPTURED_SIGNATURE_POINTS = 2048
        const val MAX_CAPTURED_POINT_FLOATS =
            MAX_CAPTURED_SIGNATURE_POINTS * BehaviorSample.POINT_STRIDE
        const val TEXT_EVENT_DWELL_MS = 60L
        const val MAX_CAPTURE_DURATION_MS = 30_000L
        const val MAX_INTER_EVENT_GAP_MS = 2_000L
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

    private fun localized(id: Int, vararg formatArgs: Any?): String {
        return LocalizationHelper.getLocalizedString(rootView.context, id, *formatArgs)
    }
}

private fun MotionEvent.isTrustedBehaviorTouch(): Boolean {
    val obscured = flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
    val partiallyObscured = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0
    return !obscured && !partiallyObscured
}
