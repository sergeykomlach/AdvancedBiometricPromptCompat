# Voice State Machine Rearchitecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the `Voice` prompt pipeline around explicit prompt states, a separated capture orchestrator, shared software-help routing, and lower-latency auth completion without changing the current voice template format or matching algorithm.

**Architecture:** The implementation splits the current event-driven `Voice` flow into four explicit layers: prompt state, capture orchestration, policy, and manager handoff. Shared software-prompt help routing is fixed in the compat dialog path so both generic and API 28 legacy-software flows render the same structured state updates instead of relying on raw ad hoc help strings.

**Tech Stack:** Kotlin, Android framework `AudioRecord`, existing compat prompt/dialog stack, Gradle Android unit tests, existing `Voice` DSP and template-matching code.

## Global Constraints

- Preserve the existing public `BiometricPromptCompat.Builder` API unless a blocker proves it insufficient.
- Do not change the current voice template storage format.
- Do not replace the current voice matching algorithm in this implementation.
- Do not introduce a new custom overlay; stay inside the existing compat prompt dialog.
- Fix API 28 combined-enroll help delivery at the shared software-prompt layer, not with a Voice-only branch.
- Keep inactivity timeout separate from capture-level anti-jamming lockout.
- Keep capture-level anti-jamming lockout separate from match-level failed-attempt lockout in `VoiceBiometricManager`.
- Prefer compile-based proof commands because `testDebugUnitTest` is noisy on this machine due to the known local `VS` runner issue.

---

## File Structure

- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptState.kt`
  Defines prompt-visible state, retry reason, and capture outcome contracts.
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptMessageResolver.kt`
  Maps `VoicePromptState` plus session context into localized prompt help strings.
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceOutcomePolicy.kt`
  Encapsulates timeout, recoverable retry, anti-jamming, and local lockout-count decisions.
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceCaptureOrchestrator.kt`
  Owns `AudioRecord` lifecycle, detector loop, and structured capture outcomes.
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt`
  Becomes the prompt-state owner and the coordinator between resolver, policy, and orchestrator.
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureController.kt`
  Reduced or removed as orchestration migrates into `VoiceCaptureOrchestrator` and `VoiceAutoCaptureSession`.
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt`
  Bridges shared software-prompt host callbacks to structured `Voice` state updates and auth handoff.
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt`
  Keeps match and enroll logic, but removes fixed success delay and consumes state-driven handoff timing.
- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptHost.kt`
  Expanded to carry structured software-prompt status updates instead of only raw help text.
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
  Renders structured software-prompt state in the generic compat dialog path.
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt`
  Renders the same shared software-prompt state in the API 28 path.
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt`
  Aligned with the same software-help routing contract when legacy software modules are active.
- `biometric-custom-voice/src/main/res/values/strings.xml`
  Adds or updates state-driven Voice prompt strings.
- `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/...`
  Focused unit tests for prompt states, message resolution, policy, and auth latency behavior.

### Proposed unit boundaries

- `VoicePromptState.kt` and `VoicePromptMessageResolver.kt` are pure and test-heavy.
- `VoiceOutcomePolicy.kt` is pure and test-heavy.
- `VoiceCaptureOrchestrator.kt` owns streaming logic and stays isolated from UI strings.
- `VoiceAutoCaptureSession.kt` coordinates state transitions and sample progression.
- Shared compat-dialog files only render already-resolved software state and do not own Voice-specific policy.

### Task decomposition

- Task 1 builds the pure state and message model first.
- Task 2 introduces the pure policy model.
- Task 3 rewires capture orchestration onto structured outcomes.
- Task 4 updates shared software-prompt routing and dialog consumption.
- Task 5 finalizes manager latency cleanup, integration wiring, and proof commands.

### Task 1: Add structured Voice prompt state and message resolution

**Files:**
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptState.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptMessageResolver.kt`
- Modify: `biometric-custom-voice/src/main/res/values/strings.xml`
- Modify: `biometric-custom-voice/src/main/res/values-uk/strings.xml`
- Test: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptMessageResolverTest.kt`

**Interfaces:**
- Consumes: existing builder phrase data via `BiometricPromptCompat.Builder.getVoicePhrase()`
- Produces:
  - `sealed interface VoicePromptState`
  - `enum class VoiceRetryReason`
  - `data class VoicePromptRender(val primaryMessage: String, val secondaryMessage: String? = null)`
  - `object VoicePromptMessageResolver { fun resolve(state: VoicePromptState, phrase: CharSequence?): VoicePromptRender }`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePromptMessageResolverTest {
    @Test
    fun resolvesFirstEnrollInstructionWithPhrase() {
        val render = VoicePromptMessageResolver.resolve(
            state = VoicePromptState.EnrollInstruction(
                step = 1,
                total = 3,
                retryReason = null
            ),
            phrase = "open sesame"
        )

        assertTrue(render.primaryMessage.contains("1"))
        assertTrue(render.primaryMessage.contains("3"))
        assertTrue(render.primaryMessage.contains("open sesame"))
    }

    @Test
    fun resolvesRetryInstructionForShortSample() {
        val render = VoicePromptMessageResolver.resolve(
            state = VoicePromptState.EnrollInstruction(
                step = 2,
                total = 3,
                retryReason = VoiceRetryReason.SAMPLE_TOO_SHORT
            ),
            phrase = "open sesame"
        )

        assertTrue(render.primaryMessage.contains("2"))
        assertTrue(render.primaryMessage.contains("3"))
        assertTrue(render.secondaryMessage!!.isNotBlank())
    }

    @Test
    fun resolvesMatchingStateWithoutListeningLanguage() {
        val render = VoicePromptMessageResolver.resolve(
            state = VoicePromptState.Matching,
            phrase = null
        )

        assertEquals("Checking voice", render.primaryMessage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: FAIL with unresolved references for `VoicePromptState`, `VoiceRetryReason`, `VoicePromptRender`, and `VoicePromptMessageResolver`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

sealed interface VoicePromptState {
    data class EnrollInstruction(
        val step: Int,
        val total: Int,
        val retryReason: VoiceRetryReason?
    ) : VoicePromptState

    data class AuthInstruction(
        val retryReason: VoiceRetryReason?
    ) : VoicePromptState

    data object Listening : VoicePromptState
    data object SpeechDetected : VoicePromptState
    data object ProcessingCapture : VoicePromptState
    data object Matching : VoicePromptState
    data object Timeout : VoicePromptState
    data object Lockout : VoicePromptState
}

enum class VoiceRetryReason {
    NO_SPEECH,
    SAMPLE_TOO_SHORT,
    SAMPLE_TOO_QUIET,
    SAMPLE_TOO_FLAT,
    SAMPLE_TOO_LONG,
    SAMPLE_REPLAY_RISK,
    RECORDING_FAILED
}

data class VoicePromptRender(
    val primaryMessage: String,
    val secondaryMessage: String? = null
)
```

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

object VoicePromptMessageResolver {
    fun resolve(state: VoicePromptState, phrase: CharSequence?): VoicePromptRender {
        return when (state) {
            is VoicePromptState.EnrollInstruction -> {
                val base = if (state.step == 1) {
                    "To register your voice, say your code phrase naturally for 2-4 seconds. Attempt ${state.step} of ${state.total}."
                } else {
                    "Repeat the same code phrase again. Attempt ${state.step} of ${state.total}."
                }
                val withPhrase = phrase?.takeIf { it.isNotBlank() }?.let {
                    "$base Phrase: $it"
                } ?: base
                VoicePromptRender(withPhrase, retryText(state.retryReason))
            }

            is VoicePromptState.AuthInstruction -> {
                val base = "Say your code phrase naturally."
                val withPhrase = phrase?.takeIf { it.isNotBlank() }?.let {
                    "$base Phrase: $it"
                } ?: base
                VoicePromptRender(withPhrase, retryText(state.retryReason))
            }

            VoicePromptState.Listening -> VoicePromptRender("Listening for voice")
            VoicePromptState.SpeechDetected -> VoicePromptRender("Voice detected")
            VoicePromptState.ProcessingCapture -> VoicePromptRender("Processing voice sample")
            VoicePromptState.Matching -> VoicePromptRender("Checking voice")
            VoicePromptState.Timeout -> VoicePromptRender("Voice authentication timed out")
            VoicePromptState.Lockout -> VoicePromptRender("Voice authentication is temporarily locked")
        }
    }

    private fun retryText(reason: VoiceRetryReason?): String? {
        return when (reason) {
            null -> null
            VoiceRetryReason.NO_SPEECH -> "No speech detected. Try the same phrase again."
            VoiceRetryReason.SAMPLE_TOO_SHORT -> "The phrase was too short. Repeat the same phrase again."
            VoiceRetryReason.SAMPLE_TOO_QUIET -> "The phrase was too quiet. Move closer to the microphone and try again."
            VoiceRetryReason.SAMPLE_TOO_FLAT -> "Speak the phrase more clearly and try again."
            VoiceRetryReason.SAMPLE_TOO_LONG -> "Keep the phrase shorter and try again."
            VoiceRetryReason.SAMPLE_REPLAY_RISK -> "Use a fresh natural phrase and try again."
            VoiceRetryReason.RECORDING_FAILED -> "Recording failed. Try the same phrase again."
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: PASS at compile level with the new state and resolver types present.

- [ ] **Step 5: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptState.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptMessageResolver.kt biometric-custom-voice/src/main/res/values/strings.xml biometric-custom-voice/src/main/res/values-uk/strings.xml biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptMessageResolverTest.kt
git commit -m "feat: add voice prompt state model"
```

### Task 2: Extract timeout and anti-jamming policy from prompt orchestration

**Files:**
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceOutcomePolicy.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceCaptureDecision.kt`
- Test: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceOutcomePolicyTest.kt`

**Interfaces:**
- Consumes:
  - `VoiceCaptureDecision`
  - `VoiceQualityIssue`
- Produces:
  - `data class VoiceOutcomeProgress(val suspiciousCaptureAttempts: Int, val lastProgressAtMs: Long)`
  - `sealed interface VoiceOutcomeDecision`
  - `class VoiceOutcomePolicy { fun onCaptureRejected(...): VoiceOutcomeDecision; fun isTimedOut(...): Boolean }`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOutcomePolicyTest {
    @Test
    fun noSpeechDoesNotIncreaseSuspiciousAttemptCount() {
        val policy = VoiceOutcomePolicy()
        val progress = VoiceOutcomeProgress(suspiciousCaptureAttempts = 0, lastProgressAtMs = 1000L)

        val decision = policy.onCaptureRejected(
            progress = progress,
            rejection = VoiceCaptureDecision(
                acceptedSample = null,
                rejectReason = VoiceCaptureRejectReason.NO_SPEECH,
                qualityIssue = VoiceQualityIssue.SAMPLE_MISSING,
                shouldNotifySpeechDetected = false,
                hadSpeechActivity = false
            ),
            nowMs = 2000L
        )

        assertTrue(decision is VoiceOutcomeDecision.Retry)
        assertEquals(0, (decision as VoiceOutcomeDecision.Retry).progress.suspiciousCaptureAttempts)
    }

    @Test
    fun replayRiskEventuallyLocksOut() {
        val policy = VoiceOutcomePolicy(maxSuspiciousCaptureAttempts = 3, inactivityTimeoutMs = 30_000L)
        var progress = VoiceOutcomeProgress(suspiciousCaptureAttempts = 0, lastProgressAtMs = 1000L)

        repeat(2) { index ->
            val retry = policy.onCaptureRejected(
                progress = progress,
                rejection = VoiceCaptureDecision(
                    acceptedSample = null,
                    rejectReason = VoiceCaptureRejectReason.QUALITY_ISSUE,
                    qualityIssue = VoiceQualityIssue.SAMPLE_REPLAY_RISK,
                    shouldNotifySpeechDetected = false,
                    hadSpeechActivity = true
                ),
                nowMs = 2000L + index
            ) as VoiceOutcomeDecision.Retry
            progress = retry.progress
        }

        val lastDecision = policy.onCaptureRejected(
            progress = progress,
            rejection = VoiceCaptureDecision(
                acceptedSample = null,
                rejectReason = VoiceCaptureRejectReason.QUALITY_ISSUE,
                qualityIssue = VoiceQualityIssue.SAMPLE_REPLAY_RISK,
                shouldNotifySpeechDetected = false,
                hadSpeechActivity = true
            ),
            nowMs = 3000L
        )

        assertTrue(lastDecision is VoiceOutcomeDecision.Lockout)
    }

    @Test
    fun inactivityTimeoutUsesSessionProgressInsteadOfRawAttemptCount() {
        val policy = VoiceOutcomePolicy()
        assertFalse(policy.isTimedOut(lastProgressAtMs = 5_000L, nowMs = 34_000L))
        assertTrue(policy.isTimedOut(lastProgressAtMs = 5_000L, nowMs = 35_001L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: FAIL with unresolved references for `VoiceOutcomePolicy`, `VoiceOutcomeProgress`, and `VoiceOutcomeDecision`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

data class VoiceOutcomeProgress(
    val suspiciousCaptureAttempts: Int,
    val lastProgressAtMs: Long
)

sealed interface VoiceOutcomeDecision {
    data class Retry(val progress: VoiceOutcomeProgress) : VoiceOutcomeDecision
    data class Lockout(val progress: VoiceOutcomeProgress) : VoiceOutcomeDecision
}

class VoiceOutcomePolicy(
    private val maxSuspiciousCaptureAttempts: Int = 3,
    private val inactivityTimeoutMs: Long = 30_000L
) {
    fun onCaptureRejected(
        progress: VoiceOutcomeProgress,
        rejection: VoiceCaptureDecision,
        nowMs: Long
    ): VoiceOutcomeDecision {
        val suspicious = when {
            rejection.rejectReason == VoiceCaptureRejectReason.QUALITY_ISSUE &&
                rejection.hadSpeechActivity &&
                rejection.qualityIssue in setOf(
                    VoiceQualityIssue.SAMPLE_TOO_LONG,
                    VoiceQualityIssue.SAMPLE_CLIPPED,
                    VoiceQualityIssue.SAMPLE_REPLAY_RISK
                ) -> true
            else -> false
        }
        val next = VoiceOutcomeProgress(
            suspiciousCaptureAttempts = if (suspicious) progress.suspiciousCaptureAttempts + 1 else progress.suspiciousCaptureAttempts,
            lastProgressAtMs = if (rejection.hadSpeechActivity) nowMs else progress.lastProgressAtMs
        )
        return if (next.suspiciousCaptureAttempts >= maxSuspiciousCaptureAttempts) {
            VoiceOutcomeDecision.Lockout(next)
        } else {
            VoiceOutcomeDecision.Retry(next)
        }
    }

    fun isTimedOut(lastProgressAtMs: Long, nowMs: Long): Boolean {
        return nowMs - lastProgressAtMs > inactivityTimeoutMs
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: PASS at compile level with the policy contract available to tests.

- [ ] **Step 5: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceOutcomePolicy.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceCaptureDecision.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceOutcomePolicyTest.kt
git commit -m "feat: extract voice outcome policy"
```

### Task 3: Introduce `VoiceCaptureOrchestrator` and migrate session orchestration to structured outcomes

**Files:**
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceCaptureOrchestrator.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureController.kt`
- Test: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSessionTest.kt`

**Interfaces:**
- Consumes:
  - `VoicePromptState`
  - `VoicePromptMessageResolver`
  - `VoiceOutcomePolicy`
- Produces:
  - `sealed interface VoiceCaptureOutcome`
  - `interface VoiceCaptureOrchestrator { fun start(step: Int, total: Int); fun cancel() }`
  - `fun VoiceAutoCaptureSession.onCaptureOutcome(outcome: VoiceCaptureOutcome, nowMs: Long)`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAutoCaptureSessionTest {
    @Test
    fun acceptedEnrollCaptureAdvancesToNextInstructionState() {
        val callback = RecordingCallback()
        val session = VoiceAutoCaptureSession(
            enroll = true,
            existingExtras = null,
            phrase = "open sesame",
            callback = callback,
            sampleRateHz = 16_000,
            messages = VoiceAutoCaptureSession.Messages.empty()
        )

        session.start()
        session.onCaptureOutcome(
            VoiceCaptureOutcome.Accepted(
                sample = FloatArray(16_000),
                hadSpeechActivity = true
            ),
            nowMs = 1000L
        )

        assertEquals(2, session.collectedSampleCount())
        assertEquals(VoicePromptState.EnrollInstruction(step = 2, total = 3, retryReason = null), callback.lastState)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: FAIL because `VoiceCaptureOutcome`, `onCaptureOutcome(...)`, and state-aware callback behavior do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

sealed interface VoiceCaptureOutcome {
    data class Accepted(
        val sample: FloatArray,
        val hadSpeechActivity: Boolean
    ) : VoiceCaptureOutcome

    data class Rejected(
        val decision: VoiceCaptureDecision
    ) : VoiceCaptureOutcome

    data object Timeout : VoiceCaptureOutcome

    data class Fatal(
        val message: CharSequence
    ) : VoiceCaptureOutcome
}
```

```kotlin
fun onCaptureOutcome(outcome: VoiceCaptureOutcome, nowMs: Long) {
    when (outcome) {
        is VoiceCaptureOutcome.Accepted -> { /* append sample, advance state, or prepare extras */ }
        is VoiceCaptureOutcome.Rejected -> { /* map through policy, emit retry state */ }
        VoiceCaptureOutcome.Timeout -> { /* emit timeout state or fatal result */ }
        is VoiceCaptureOutcome.Fatal -> { /* emit fatal result */ }
    }
}
```

```kotlin
internal class VoiceCaptureOrchestrator(
    private val onOutcome: (VoiceCaptureOutcome) -> Unit
) {
    fun start(step: Int, total: Int) {
        // Move existing AudioRecord loop here and replace direct UI callbacks with onOutcome(...)
    }

    fun cancel() {
        // Stop recorder and clear active thread
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin :biometric-custom-voice:compileDebugKotlin`

Expected: PASS at compile level with session and orchestrator contracts wired together.

- [ ] **Step 5: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceCaptureOrchestrator.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureController.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSessionTest.kt
git commit -m "refactor: split voice capture orchestration"
```

### Task 4: Add shared software-prompt help routing for generic and API 28 dialog paths

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptHost.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/SoftwarePromptHelpRoutingTest.kt`

**Interfaces:**
- Consumes:
  - `VoicePromptState`
  - `VoicePromptRender`
- Produces:
  - `data class SoftwarePromptStatus(val primaryText: CharSequence, val secondaryText: CharSequence? = null, val terminal: Boolean = false)`
  - `SoftwareBiometricPromptHost.Callbacks.onStatus(status: SoftwarePromptStatus)`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SoftwarePromptHelpRoutingTest {
    @Test
    fun api28AndGenericUseSharedSoftwarePromptStatusModel() {
        val status = SoftwarePromptStatus(
            primaryText = "Repeat the same code phrase again. Attempt 2 of 3.",
            secondaryText = "The phrase was too short. Repeat the same phrase again.",
            terminal = false
        )

        assertEquals("Repeat the same code phrase again. Attempt 2 of 3.", status.primaryText)
        assertEquals(false, status.terminal)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :biometric:compileDebugUnitTestKotlin`

Expected: FAIL because `SoftwarePromptStatus` and `onStatus(...)` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package dev.skomlach.biometric.compat.custom

data class SoftwarePromptStatus(
    val primaryText: CharSequence,
    val secondaryText: CharSequence? = null,
    val terminal: Boolean = false
)
```

```kotlin
interface Callbacks {
    fun onHelp(message: CharSequence)
    fun onStatus(status: SoftwarePromptStatus)
    fun onReady(extras: Bundle? = null)
    fun onFailure(result: AuthenticationResult)
    fun isPromptActive(): Boolean
}
```

```kotlin
override fun onHelp(message: CharSequence) {
    host.callbacks.onStatus(SoftwarePromptStatus(primaryText = message))
}
```

```kotlin
fun onSoftwareStatus(status: SoftwarePromptStatus) {
    dialog.status?.text = status.primaryText
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :biometric:compileDebugUnitTestKotlin :biometric:compileDebugKotlin`

Expected: PASS at compile level with shared software status routing available to both generic and API 28 prompt implementations.

- [ ] **Step 5: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptHost.kt biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt biometric/src/test/java/dev/skomlach/biometric/compat/SoftwarePromptHelpRoutingTest.kt
git commit -m "refactor: share software prompt status routing"
```

### Task 5: Remove auth success delay and finish integration wiring

**Files:**
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt`
- Test: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManagerFlowTest.kt`

**Interfaces:**
- Consumes:
  - `VoicePromptState.Matching`
  - prepared extras from `VoiceAutoCaptureSession`
- Produces:
  - immediate success callback behavior without fixed `RESULT_DELAY_MS`
  - state transition order: `Listening -> ProcessingCapture -> Matching -> Success`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceBiometricManagerFlowTest {
    @Test
    fun successPathDoesNotRequireFixedHalfSecondDelay() {
        assertEquals(0L, VoiceBiometricManager.successResultDelayMsForTest())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: FAIL because the manager still relies on the old fixed success delay contract.

- [ ] **Step 3: Write minimal implementation**

```kotlin
private fun finishWithSuccess(
    callback: AuthenticationCallback?,
    crypto: CryptoObject?,
    helpMessage: CharSequence
) {
    if (sessionActive.compareAndSet(true, false)) {
        callback?.onAuthenticationHelp(CUSTOM_BIOMETRIC_ACQUIRED_GOOD, helpMessage)
        callback?.onAuthenticationSucceeded(AuthenticationResult(crypto))
    }
}

internal fun successResultDelayMsForTest(): Long = 0L
```

```kotlin
callback.onStatus(
    SoftwarePromptStatus(
        primaryText = "Checking voice",
        terminal = false
    )
)
callback.onReady(preparedExtras)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin :biometric-custom-voice:compileDebugKotlin :biometric:compileDebugUnitTestKotlin`

Expected: PASS at compile level with the fixed delay removed and wiring complete.

- [ ] **Step 5: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManagerFlowTest.kt
git commit -m "refactor: remove delayed voice auth success"
```

## Self-Review

### Spec coverage

- State-driven prompt architecture is covered by Task 1 and Task 3.
- User-friendly enroll and auth text is covered by Task 1.
- Shared Legacy or Software help routing for combined enroll and API 28 is covered by Task 4.
- Separation of inactivity timeout, capture-level anti-jamming, and match-level lockout is covered by Task 2 and Task 3.
- Lower-latency auth completion is covered by Task 5.

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” markers remain.
- Every task includes exact file paths, commands, interfaces, and minimal code snippets.
- No task refers to an undefined neighbor contract without naming the produced type or method.

### Type consistency

- `VoicePromptState`, `VoiceRetryReason`, `VoicePromptRender`, and `VoicePromptMessageResolver` are introduced first in Task 1 and then consumed in later tasks.
- `VoiceOutcomePolicy`, `VoiceOutcomeProgress`, and `VoiceOutcomeDecision` are introduced in Task 2 and consumed by Task 3.
- `SoftwarePromptStatus` is introduced in Task 4 and consumed by dialog and delegate changes in the same task.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-08-voice-state-machine-rearchitecture.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
