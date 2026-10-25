# Voice VAD And Denoise Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve `VoiceAuth` robustness by strengthening the current streaming VAD path with bounded loudness normalization, noise-aware preprocessing, and a pluggable path for stronger denoise / VAD backends without reintroducing custom UI.

**Architecture:** Split the work into two layers. First, harden the existing in-repo heuristic path with shared streaming conditioning and better speech gating. Second, add a narrow adapter seam so stronger optional engines such as WebRTC APM, RNNoise, or Silero VAD can be evaluated without entangling them with prompt orchestration or enrollment logic.

**Tech Stack:** Kotlin, Android audio capture pipeline, current `VoiceStreamingDetector`, current `VoiceAudioPreprocessor`, JUnit4 unit tests, optional future native / ONNX adapters

---

### Task 1: Lock The New Conditioning And VAD Contract With Tests

**Files:**
- Create: `biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditionerTest.kt`
- Modify: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt`
- Create: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendCapabilityPlanTest.kt`

- [ ] **Step 1: Write the failing conditioning tests**

```kotlin
@Test
fun `conditioning lifts quiet voiced chunk toward target rms without clipping`() {
    val conditioner = VoiceSignalConditioner(sampleRateHz = 16_000)
    val quiet = sineChunk(amplitude = 0.01f)

    val conditioned = conditioner.condition(quiet)

    assertTrue(rms(conditioned) > rms(quiet))
    assertTrue(conditioned.all { it in -1f..1f })
}
```

```kotlin
@Test
fun `conditioning does not amplify silence into pseudo speech`() {
    val conditioner = VoiceSignalConditioner(sampleRateHz = 16_000)
    val silence = FloatArray(320)

    val conditioned = conditioner.condition(silence)

    assertTrue(conditioned.all { kotlin.math.abs(it) < 0.0001f })
}
```

- [ ] **Step 2: Tighten streaming VAD tests around pause handling and noisy starts**

```kotlin
@Test
fun `detector ignores short noisy warmup before true speech start`() {
    val detector = VoiceStreamingDetector(sampleRateHz = 16_000)

    val result = detector.detect(
        listOf(
            noiseChunk(0.01f),
            noiseChunk(0.012f),
            voiceChunk(180.0, 0.09f),
            voiceChunk(220.0, 0.10f),
            voiceChunk(200.0, 0.08f),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk(),
            silenceChunk()
        )
    )

    assertTrue(result.detectedSpeech)
    assertTrue(result.isComplete)
}
```

- [ ] **Step 3: Add a planning guard test for optional backend integration assumptions**

```kotlin
@Test
fun `advanced backend plan keeps heuristic backend as default`() {
    val plan = VoiceBackendPlan.default()

    assertEquals(VoiceBackendType.HEURISTIC, plan.defaultBackend)
    assertTrue(plan.optionalBackends.contains(VoiceBackendType.SILERO_VAD))
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin :biometric:compileDebugUnitTestKotlin`

Expected: FAIL or compile failure because `VoiceSignalConditioner`, `VoiceBackendPlan`, and the new detector behavior do not exist yet.

- [ ] **Step 5: Commit**

```bash
git add biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditionerTest.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendCapabilityPlanTest.kt
git commit -m "test: lock vad conditioning and backend plan"
```

### Task 2: Add Shared Streaming Signal Conditioning

**Files:**
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditioner.kt`
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditionerTest.kt`

- [ ] **Step 1: Add a small shared conditioning primitive in core**

```kotlin
internal class VoiceSignalConditioner(
    private val sampleRateHz: Int,
    private val targetRms: Float = 0.05f,
    private val maxGain: Float = 6f,
    private val silenceFloor: Float = 0.003f
) {
    fun condition(chunk: FloatArray): FloatArray {
        // DC removal + bounded gain + clamp
    }
}
```

- [ ] **Step 2: Implement only bounded preprocessing, not aggressive denoise**

```kotlin
private fun boundedGain(currentRms: Float): Float {
    if (currentRms <= silenceFloor) return 1f
    return (targetRms / currentRms).coerceIn(1f, maxGain)
}
```

```kotlin
private fun clampToPcmRange(samples: FloatArray): FloatArray {
    return FloatArray(samples.size) { index -> samples[index].coerceIn(-1f, 1f) }
}
```

- [ ] **Step 3: Run the conditioner tests**

Run: `.\gradlew.bat :biometric:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditioner.kt biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditionerTest.kt
git commit -m "feat: add bounded streaming voice conditioner"
```

### Task 3: Upgrade The Heuristic Streaming VAD

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetector.kt`
- Modify: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt`

- [ ] **Step 1: Condition chunks before scoring them**

```kotlin
private val conditioner = VoiceSignalConditioner(sampleRateHz)
```

```kotlin
val conditionedChunks = chunks.map { conditioner.condition(it) }
val rmsValues = conditionedChunks.map { chunk -> rms(chunk) }
```

- [ ] **Step 2: Add stronger hysteresis and speech-start stability**

```kotlin
private fun firstSpeechStart(voiced: List<Boolean>): Int? {
    // require consecutive voiced frames
}
```

```kotlin
if (currentSilenceRun >= endSilenceFrames) {
    // complete only after sustained silence, not short pauses
}
```

- [ ] **Step 3: Keep normalization bounded and speech-first**

```kotlin
// never normalize silence-only chunks into speech
// never treat gain boost as evidence by itself
```

- [ ] **Step 4: Run targeted compile verification**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetector.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt
git commit -m "feat: improve heuristic voice streaming vad"
```

### Task 4: Strengthen Post-Capture Preprocessing For Matching

**Files:**
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessor.kt`
- Modify: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessorTest.kt`

- [ ] **Step 1: Reuse the same conditioning philosophy after utterance segmentation**

```kotlin
val cleaned = removeDcOffset(pcm)
val normalized = normalizeVoicedSegment(cleaned)
```

```kotlin
private fun normalizeVoicedSegment(pcm: FloatArray): FloatArray {
    // mild RMS normalization with clipping guard
}
```

- [ ] **Step 2: Keep the current quality gates authoritative**

```kotlin
if (clippedRatio > MAX_CLIPPED_RATIO) return SAMPLE_CLIPPED
if (trimmedRms < MIN_VOICE_RMS) return SAMPLE_TOO_QUIET
```

- [ ] **Step 3: Add regression coverage**

```kotlin
@Test
fun `preprocess normalizes quiet but valid voiced sample without tripping clipping gate`() {
    val pcm = quietVoiceLikeTone(durationSeconds = 1.5)

    val result = VoiceAudioPreprocessor.preprocess(pcm, SAMPLE_RATE)

    assertEquals(VoiceQualityIssue.NONE, result.qualityIssue)
}
```

- [ ] **Step 4: Run compile proof**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessor.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessorTest.kt
git commit -m "feat: normalize voiced samples before matching"
```

### Task 5: Add A Narrow Optional Backend Seam For Stronger Engines

**Files:**
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceEnhancementBackend.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendPlan.kt`
- Modify: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendCapabilityPlanTest.kt`

- [ ] **Step 1: Add an internal adapter boundary**

```kotlin
internal interface VoiceEnhancementBackend {
    fun enhance(pcm: FloatArray, sampleRateHz: Int): FloatArray
}
```

```kotlin
internal enum class VoiceBackendType {
    HEURISTIC,
    WEBRTC_APM,
    RNNOISE,
    SILERO_VAD
}
```

- [ ] **Step 2: Keep advanced backends disabled by default**

```kotlin
internal data class VoiceBackendPlan(
    val defaultBackend: VoiceBackendType,
    val optionalBackends: Set<VoiceBackendType>
) {
    companion object {
        fun default() = VoiceBackendPlan(
            defaultBackend = VoiceBackendType.HEURISTIC,
            optionalBackends = setOf(
                VoiceBackendType.WEBRTC_APM,
                VoiceBackendType.RNNOISE,
                VoiceBackendType.SILERO_VAD
            )
        )
    }
}
```

- [ ] **Step 3: Document backend fit explicitly in code comments**

```kotlin
// WebRTC APM: best fit for AGC + NS + AEC in real-time mic flow
// RNNoise: best fit for denoise-only front-end
// Silero VAD: best fit for stronger speech / non-speech detection
```

- [ ] **Step 4: Run compile proof**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceEnhancementBackend.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendPlan.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendCapabilityPlanTest.kt
git commit -m "refactor: add optional voice enhancement backend seam"
```

### Task 6: Verify The Full Improvement Slice And Record Remaining Gaps

**Files:**
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditionerTest.kt`
- Test: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt`
- Test: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessorTest.kt`
- Test: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendCapabilityPlanTest.kt`

- [ ] **Step 1: Run the compile-based proof gates**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin :biometric-custom-voice:compileDebugKotlin :biometric-custom-voice:compileDebugUnitTestKotlin :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Attempt the targeted unit tests despite the known local runner issue**

Run: `.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests "dev.skomlach.biometric.compat.engine.internal.voice.VoiceStreamingDetectionTest" --tests "dev.skomlach.biometric.compat.engine.internal.voice.VoiceAudioPreprocessorTest" --tests "dev.skomlach.biometric.compat.engine.internal.voice.VoiceBackendCapabilityPlanTest"`

Expected: PASS, or else compile proof succeeds and execution is blocked only by the known `Could not find or load main class VS` environment issue.

- [ ] **Step 3: Run whitespace verification**

Run: `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check`

Expected: no whitespace errors in the touched files

- [ ] **Step 4: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditioner.kt biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetector.kt biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSignalConditionerTest.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessor.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceEnhancementBackend.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendPlan.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceStreamingDetectionTest.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessorTest.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBackendCapabilityPlanTest.kt
git commit -m "feat: improve voice vad and denoise pipeline"
```
