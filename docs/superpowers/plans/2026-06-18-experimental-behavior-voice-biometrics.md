# Experimental Behavior and Voice Biometrics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add experimental software biometric providers for typing/signature behavior and voice speaker verification without changing the public `BiometricPromptCompat` API.

**Architecture:** Keep all new modalities behind `AbstractSoftwareBiometricManager` and `SoftwareBiometricProvider`, matching the existing TF/ZK plugin pattern. Behavior authentication is local Kotlin scoring over captured touch/key timing samples; voice authentication is a separate optional module with microphone permission, VAD, and speaker embedding assets gated before registration.

**Tech Stack:** Kotlin, Android library modules, `ServiceLoader`, existing `SoftwareBiometricModule`, JUnit tests, TensorFlow Lite/LiteRT or ONNX-equivalent voice backend only after asset/license validation.

---

## File Structure

- Create `biometric-custom-behavior/`: optional software provider module for `BIOMETRIC_BEHAVIOR`.
- Create `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorBiometricManager.kt`: manager registered through `ServiceLoader`.
- Create `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorMode.kt`: `TYPING`, `SIGNATURE`, `COMBINED`.
- Create `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorSample.kt`: normalized input sample model.
- Create `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorTemplateStore.kt`: encrypted/hash-separated template storage wrapper using app-private storage.
- Create `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorScorer.kt`: deterministic scoring for typing/signature/combined samples.
- Create `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorProvider.kt`: `SoftwareBiometricProvider`.
- Create `biometric-custom-behavior/src/main/resources/META-INF/services/dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider`: service registration.
- Create `biometric-custom-voice/`: optional software provider module for `BIOMETRIC_VOICE`.
- Create `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt`: manager registered only when model assets are present.
- Create `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceCapture.kt`: microphone capture and VAD windowing.
- Create `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceEmbeddingEngine.kt`: interface for VAD + speaker embedding backend.
- Create `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/LiteRtVoiceEmbeddingEngine.kt`: adapter for validated model assets.
- Modify `settings.gradle`: include both optional modules.
- Modify `app/build.gradle`: add demo dependencies on both modules only for the sample app.
- Modify `gradle/libs.versions.toml`: add voice runtime dependency only after confirming artifact coordinates.

## Research Decisions Already Made

- Palmprint SDK integration is deferred because the most relevant Android repo, `kby-ai/Palmprint-Recognition-Android`, appears to require a vendor license.
- Typing behavior should use the `Biomey-KeystrokeDynamics` approach as a reference, but avoid Chaquopy/Python in this library.
- Signature auth should be implemented as behavior biometrics over touch/stylus strokes, Samsung-like in UI/flow, not as a Samsung OEM module until a real Samsung service/API reference is found.
- Voice should target the VAD/Speaker Recognition approach from `Ye83/VAD-Speaker-Recognition-for-Android`; older MFCC/GMM repos are references only.

---

### Task 1: Behavior Scoring Core

**Files:**
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorMode.kt`
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorSample.kt`
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorScorer.kt`
- Test: `biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorScorerTest.kt`

- [ ] **Step 1: Create deterministic sample contracts**

```kotlin
enum class BehaviorMode {
    TYPING,
    SIGNATURE,
    COMBINED
}

data class BehaviorPoint(
    val x: Float,
    val y: Float,
    val timestampMs: Long,
    val pressure: Float? = null,
    val size: Float? = null
)

data class BehaviorSample(
    val mode: BehaviorMode,
    val phrase: String?,
    val keyDownTimesMs: List<Long>,
    val keyUpTimesMs: List<Long>,
    val strokePoints: List<BehaviorPoint>
)
```

- [ ] **Step 2: Write failing scorer tests**

```kotlin
@Test
fun typingScoreAcceptsSimilarTiming() {
    val enrolled = BehaviorSample(
        mode = BehaviorMode.TYPING,
        phrase = "open sesame",
        keyDownTimesMs = listOf(0, 120, 260),
        keyUpTimesMs = listOf(80, 190, 330),
        strokePoints = emptyList()
    )
    val probe = enrolled.copy(
        keyDownTimesMs = listOf(0, 125, 255),
        keyUpTimesMs = listOf(78, 195, 328)
    )

    assertTrue(BehaviorScorer.score(enrolled, probe) >= 0.82f)
}

@Test
fun signatureScoreRejectsDifferentPath() {
    val enrolled = BehaviorSample(
        mode = BehaviorMode.SIGNATURE,
        phrase = null,
        keyDownTimesMs = emptyList(),
        keyUpTimesMs = emptyList(),
        strokePoints = listOf(
            BehaviorPoint(0f, 0f, 0),
            BehaviorPoint(10f, 10f, 10),
            BehaviorPoint(20f, 20f, 20)
        )
    )
    val probe = enrolled.copy(
        strokePoints = listOf(
            BehaviorPoint(0f, 20f, 0),
            BehaviorPoint(10f, 10f, 10),
            BehaviorPoint(20f, 0f, 20)
        )
    )

    assertTrue(BehaviorScorer.score(enrolled, probe) < 0.70f)
}
```

- [ ] **Step 3: Implement minimal scorer**

Implement typing as normalized dwell/flight distance and signature as resampled path distance. Combined mode averages typing and signature scores and fails closed when either side is missing.

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :biometric-custom-behavior:testDebugUnitTest`
Expected: behavior scorer tests pass.

---

### Task 2: Behavior Template Storage and Manager

**Files:**
- Create: `biometric-custom-behavior/build.gradle`
- Create: `biometric-custom-behavior/src/main/AndroidManifest.xml`
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorTemplateStore.kt`
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorBiometricManager.kt`
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorProvider.kt`
- Create: `biometric-custom-behavior/src/main/resources/META-INF/services/dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider`
- Modify: `settings.gradle`
- Modify: `app/build.gradle`

- [ ] **Step 1: Add module and service provider**

Register a library module matching `biometric-zkfinger` and `biometric-custom-face-tf`, then add service file content:

```text
dev.skomlach.biometric.compat.engine.internal.behavior.BehaviorProvider
```

- [ ] **Step 2: Implement manager contract**

`BehaviorBiometricManager` must return:

```kotlin
override val biometricType = BiometricType.BIOMETRIC_BEHAVIOR
override val priority = PRIORITY_BELOW_SYSTEM_HARDWARE
override fun getPermissions(): List<String> = emptyList()
override fun isHardwareDetected(): Boolean = true
override fun hasEnrolledBiometric(): Boolean = store.hasTemplate()
override fun getManagers(): Set<Any> = setOf(this)
override fun getEnrolls(): Collection<String> = store.templateNames()
```

Authentication must fail with `CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC` when no template exists, `CUSTOM_BIOMETRIC_ERROR_LOCKOUT` when locked, and `CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS` when the caller did not provide a behavior capture bundle.

- [ ] **Step 3: Define enrollment/auth bundle keys**

Use explicit keys to avoid public API churn:

```kotlin
const val EXTRA_BEHAVIOR_MODE = "behavior.mode"
const val EXTRA_BEHAVIOR_PHRASE = "behavior.phrase"
const val EXTRA_BEHAVIOR_KEY_DOWNS = "behavior.key_downs"
const val EXTRA_BEHAVIOR_KEY_UPS = "behavior.key_ups"
const val EXTRA_BEHAVIOR_POINTS = "behavior.points"
```

- [ ] **Step 4: Run compile**

Run: `.\gradlew.bat :biometric-custom-behavior:compileDebugKotlin :app:compileDebugKotlin`
Expected: build succeeds and `LegacyBiometric.loadSoftwareModules()` discovers the provider through `ServiceLoader`.

---

### Task 3: Behavior Capture UI Hook

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialog.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/utils/activityView/WindowForegroundBlurring.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/BehaviorBundleParsingTest.kt`

- [ ] **Step 1: Keep behavior UI local to dialog flow**

Do not add a new public API method. Use the existing software enrollment/auth flow and pass `Bundle` extras to the selected software manager.

- [ ] **Step 2: Offer mode selection**

For `BIOMETRIC_BEHAVIOR`, show mode options as `Typing`, `Signature`, `Combined`. Default to `Combined` only during enrollment if both typing and signature samples are present; otherwise default to the single available capture type.

- [ ] **Step 3: Verify no AUTO/ANY cascade regression**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :app:compileDebugKotlin`
Expected: no changes to hardware/OEM module discovery and no new permissions requested for behavior.

---

### Task 4: Voice Module Asset Gate

**Files:**
- Create: `biometric-custom-voice/build.gradle`
- Create: `biometric-custom-voice/src/main/AndroidManifest.xml`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceProvider.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceEmbeddingEngine.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt`
- Create: `biometric-custom-voice/src/main/resources/META-INF/services/dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider`
- Modify: `settings.gradle`
- Modify: `app/build.gradle`

- [ ] **Step 1: Add permission and fail-closed detection**

Declare `android.permission.RECORD_AUDIO`. `isHardwareDetected()` returns true only when microphone feature exists and required model assets are present. Missing models must make the provider unavailable, not registered as a broken software biometric.

- [ ] **Step 2: Add engine interface**

```kotlin
interface VoiceEmbeddingEngine {
    fun isReady(): Boolean
    fun extractEmbedding(pcm16: ShortArray, sampleRate: Int): FloatArray
    fun cosineSimilarity(left: FloatArray, right: FloatArray): Float
}
```

- [ ] **Step 3: Implement manager shell**

`VoiceBiometricManager` must expose `BIOMETRIC_VOICE`, return `RECORD_AUDIO` from `getPermissions()`, and use the shared software lockout policy. Authentication must cancel capture immediately when `CancellationSignal` fires.

- [ ] **Step 4: Run compile**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :app:compileDebugKotlin`
Expected: voice module compiles even when model assets are absent; runtime discovery treats absent assets as unavailable.

---

### Task 5: Voice VAD/Speaker Backend

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `biometric-custom-voice/build.gradle`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceCapture.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/LiteRtVoiceEmbeddingEngine.kt`
- Test: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceEmbeddingMathTest.kt`

- [ ] **Step 1: Validate model redistribution**

Before adding assets from `Ye83/VAD-Speaker-Recognition-for-Android`, confirm the model/license allows redistribution in this project. If not, add documented Gradle placeholders and require the app integrator to provide assets.

- [ ] **Step 2: Implement VAD/capture boundaries**

Capture max 5 seconds, stop after enough voiced frames, and return timeout via `CUSTOM_BIOMETRIC_ERROR_TIMEOUT`. Never write raw audio to logs or external storage.

- [ ] **Step 3: Implement embedding comparison**

Enrollment stores normalized embeddings only. Authentication compares probe embedding to enrolled embedding by cosine similarity. Raw audio is discarded immediately after embedding extraction.

- [ ] **Step 4: Run verification**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :app:compileDebugKotlin`
Expected: build succeeds; missing or invalid model assets produce controlled unavailable/no-permission/no-biometric errors.

---

### Task 6: Integration Regression Pass

**Files:**
- Modify only files touched by previous tasks if regressions are found.

- [ ] **Step 1: Verify software module discovery**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric-custom-behavior:compileDebugKotlin :biometric-custom-voice:compileDebugKotlin :app:compileDebugKotlin`
Expected: all modules compile.

- [ ] **Step 2: Verify diff hygiene**

Run: `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check`
Expected: no whitespace errors or merge markers.

- [ ] **Step 3: Manual runtime checks**

Check these flows on device:

```text
AUTO/ANY with hardware fingerprint: hardware route remains preferred.
AUTO/FACE without hardware face: TF route remains preferred.
SOFTWARE/BEHAVIOR enrollment: Typing, Signature, Combined modes are selectable.
SOFTWARE/VOICE enrollment without RECORD_AUDIO: returns NO_PERMISSIONS.
SOFTWARE/VOICE without models: provider is unavailable, not broken.
```

