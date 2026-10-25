# Voice Auto-Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current custom `Voice` overlay with headless auto-capture that records `3` samples for enroll and `1` sample for auth through the existing biometric prompt and callbacks.

**Architecture:** Add a small headless voice auto-capture session in the compat dialog layer that collects microphone PCM before auth starts, reuse `VoicePromptData` to pass prepared samples, and extend the current voice preprocessing heuristics with streaming-friendly speech segmentation helpers. Keep `VoiceBiometricManager` responsible only for permission checks, quality validation, scoring, enrollment, and lockout.

**Tech Stack:** Kotlin, Android `AudioRecord`, existing biometric compat dialog flow, JUnit4 unit tests

---

### Task 1: Lock The New Voice Capture Contract With Tests

**Files:**
- Create: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt`
- Create: `biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSessionTest.kt`

- [ ] **Step 1: Write the failing streaming-detection tests**

```kotlin
@Test
fun `detects utterance with short pause between voiced chunks`() {
    val detector = VoiceStreamingDetector(sampleRateHz = 16_000)

    val frames = listOf(
        silenceFrame(),
        silenceFrame(),
        voiceFrame(0.08f),
        voiceFrame(0.07f),
        silenceFrame(scale = 0.002f),
        voiceFrame(0.09f),
        silenceFrame(),
        silenceFrame(),
        silenceFrame(),
    )

    val result = detector.consume(frames)

    assertTrue(result.completed)
    assertTrue(result.capturedPcm.isNotEmpty())
}
```

```kotlin
@Test
fun `rejects silence only capture`() {
    val detector = VoiceStreamingDetector(sampleRateHz = 16_000)

    val result = detector.consume(List(20) { silenceFrame() })

    assertFalse(result.completed)
    assertFalse(result.detectedSpeech)
}
```

- [ ] **Step 2: Write the failing orchestration tests**

```kotlin
@Test
fun `enroll session waits for three accepted samples before ready`() {
    val session = VoiceAutoCaptureSession.forTest(
        enroll = true,
        existingExtras = null,
        callback = RecordingCallback()
    )

    session.onSampleCaptured(validSample())
    session.onSampleCaptured(validSample(seed = 2))
    assertFalse(session.isReady())

    session.onSampleCaptured(validSample(seed = 3))

    assertTrue(session.isReady())
    assertEquals(3, session.preparedSamples().size)
}
```

```kotlin
@Test
fun `auth session becomes ready after one accepted sample`() {
    val session = VoiceAutoCaptureSession.forTest(
        enroll = false,
        existingExtras = null,
        callback = RecordingCallback()
    )

    session.onSampleCaptured(validSample())

    assertTrue(session.isReady())
    assertEquals(1, session.preparedSamples().size)
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests "dev.skomlach.biometric.compat.engine.internal.voice.VoiceStreamingDetectionTest" --tests "dev.skomlach.biometric.compat.impl.dialogs.VoiceAutoCaptureSessionTest"`

Expected: FAIL because `VoiceStreamingDetector` / headless session contract do not exist yet.

- [ ] **Step 4: Commit**

```bash
git add biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSessionTest.kt
git commit -m "test: lock voice auto-capture contract"
```

### Task 2: Implement Streaming Speech Detection Helpers

**Files:**
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessor.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetector.kt`

- [ ] **Step 1: Add a small streaming detector model**

```kotlin
internal data class VoiceStreamingDetection(
    val detectedSpeech: Boolean,
    val completed: Boolean,
    val capturedPcm: FloatArray
)
```

```kotlin
internal class VoiceStreamingDetector(
    private val sampleRateHz: Int
) {
    fun consume(frames: List<FloatArray>): VoiceStreamingDetection {
        // state-machine implementation
    }
}
```

- [ ] **Step 2: Reuse existing preprocessing thresholds instead of inventing a second quality model**

```kotlin
internal const val STREAM_FRAME_MS = 20
internal const val STREAM_SPEECH_START_FRAMES = 3
internal const val STREAM_END_SILENCE_FRAMES = 12
internal const val STREAM_SHORT_PAUSE_FRAMES = 4
```

```kotlin
internal fun frameRmsForStreaming(pcm: FloatArray, frameLength: Int): FloatArray {
    return frameRms(pcm, frameLength)
}
```

- [ ] **Step 3: Run the streaming detector tests**

Run: `.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests "dev.skomlach.biometric.compat.engine.internal.voice.VoiceStreamingDetectionTest"`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessor.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetector.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt
git commit -m "feat: add streaming voice detection helpers"
```

### Task 3: Add Headless Voice Auto-Capture Orchestration

**Files:**
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSession.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/VoicePromptData.kt`
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSessionTest.kt`

- [ ] **Step 1: Add the orchestration callback boundary**

```kotlin
internal interface VoiceAutoCaptureCallback {
    fun onHelp(message: CharSequence)
    fun onReady(extras: Bundle)
    fun onError(message: CharSequence)
    fun isPromptActive(): Boolean
}
```

- [ ] **Step 2: Implement the session without any `View` creation**

```kotlin
internal class VoiceAutoCaptureSession(
    private val builder: BiometricPromptCompat.Builder,
    private val callback: VoiceAutoCaptureCallback
) {
    fun shouldAutoCapture(): Boolean = !hasVoiceInput(builder.getExtras())
    fun start() { /* AudioRecord + detector + vibro + help messages */ }
    fun dispose() { /* stop recorder, clear buffers */ }
}
```

- [ ] **Step 3: Keep sample packaging centralized in `VoicePromptData`**

```kotlin
internal fun buildVoiceExtrasForSession(
    existing: Bundle?,
    phrase: CharSequence?,
    sampleRateHz: Int,
    samples: List<FloatArray>
): Bundle = buildVoiceExtras(existing, phrase, sampleRateHz, samples)
```

- [ ] **Step 4: Run orchestration tests**

Run: `.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.dialogs.VoiceAutoCaptureSessionTest" --tests "dev.skomlach.biometric.compat.VoicePromptDataTest"`

Expected: PASS or compile success with only the known local `VS` runner issue blocking execution.

- [ ] **Step 5: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSession.kt biometric/src/main/java/dev/skomlach/biometric/compat/VoicePromptData.kt biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSessionTest.kt
git commit -m "feat: add headless voice auto-capture session"
```

### Task 4: Wire The Session Into The Prompt And Remove The Custom Voice UI Path

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
- Delete: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceCaptureController.kt`

- [ ] **Step 1: Replace the overlay path in the dialog**

```kotlin
private var voiceAutoCaptureSession: VoiceAutoCaptureSession? = null
```

```kotlin
if (primaryBiometricType == BiometricType.BIOMETRIC_VOICE) {
    voiceAutoCaptureSession = VoiceAutoCaptureSession(
        compatBuilder,
        dialogCallback
    ).takeIf { it.shouldAutoCapture() }?.also { it.start() }
}
```

- [ ] **Step 2: Ensure `startAuth()` waits only for prepared auto-capture**

```kotlin
if (primaryBiometricType == BiometricType.BIOMETRIC_VOICE &&
    voiceAutoCaptureSession?.isReadyToStartAuth() == false
) {
    return
}
```

- [ ] **Step 3: Remove the old custom controller**

```kotlin
// delete VoiceCaptureController.kt once the dialog no longer references it
```

- [ ] **Step 4: Run targeted compile verification**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSession.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetector.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessor.kt biometric/src/main/java/dev/skomlach/biometric/compat/VoicePromptData.kt biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSessionTest.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt
git rm biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceCaptureController.kt
git commit -m "feat: switch voice auth to headless auto-capture"
```
