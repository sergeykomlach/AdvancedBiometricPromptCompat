# Behavior Balanced Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or **superpowers:executing-plans** to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strengthen the experimental `biometric-custom-behavior` provider against scripted, replayed, obscured, accessibility-driven, and malformed captures without a new ML model or public API change.

**Architecture:** Add pure Kotlin policies for session integrity, input integrity, and accessibility risk. The capture controller creates session-bound payloads, while `BehaviorBiometricManager` validates freshness/provenance before quality scoring and matching. Existing scorer, template format, public builder hooks, and lockout policy remain unchanged.

**Tech Stack:** Kotlin, Android Views, `SystemClock.elapsedRealtime()`, `SecureRandom`, existing `ScreenProtection`/`A11yDetection`, JUnit4, Gradle Android unit tests.

## Global Constraints

- Do not add a new ML model or ML/runtime dependency.
- Do not change the public `BiometricPromptCompat` API.
- Built-in capture uses balanced strict defaults; consumer-owned views remain an explicit compatibility path.
- Do not log raw phrases, coordinates, timestamps, or event payloads.
- Existing stored templates and legacy five-field signature points remain readable.
- A token is an in-process freshness/provenance signal, not cryptographic proof against a compromised process or rooted device.
- Do not commit changes without explicit user approval.

---

### Task 1: Add pure session-integrity policy

**Files:**
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureSessionPolicy.kt`
- Create: `biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureSessionPolicyTest.kt`

**Interfaces:**
- Produces `BehaviorCaptureSessionToken(nonce: Long, startedAtMs: Long)`.
- Exposes `evaluate(nowMs: Long, submitted: Boolean, token: BehaviorCaptureSessionToken, expectedNonce: Long, maxDurationMs: Long): BehaviorCaptureSessionDecision`.
- Decisions are `ACTIVE`, `EXPIRED`, `INVALID_TOKEN`, and `ALREADY_SUBMITTED`.

- [ ] **Step 1: Write failing tests for session decisions**

```kotlin
@Test
fun activeSessionAcceptsMatchingFreshToken() {
    val token = BehaviorCaptureSessionToken(nonce = 7L, startedAtMs = 1_000L)
    assertEquals(
        BehaviorCaptureSessionDecision.ACTIVE,
        evaluateBehaviorCaptureSession(1_500L, false, token, 7L, 5_000L)
    )
}

@Test
fun expiredSessionIsRejected() {
    val token = BehaviorCaptureSessionToken(nonce = 7L, startedAtMs = 1_000L)
    assertEquals(
        BehaviorCaptureSessionDecision.EXPIRED,
        evaluateBehaviorCaptureSession(6_000L, false, token, 7L, 5_000L)
    )
}

@Test
fun mismatchedTokenAndSecondSubmitAreRejected() {
    val token = BehaviorCaptureSessionToken(nonce = 7L, startedAtMs = 1_000L)
    assertEquals(
        BehaviorCaptureSessionDecision.INVALID_TOKEN,
        evaluateBehaviorCaptureSession(1_500L, false, token, 8L, 5_000L)
    )
    assertEquals(
        BehaviorCaptureSessionDecision.ALREADY_SUBMITTED,
        evaluateBehaviorCaptureSession(1_500L, true, token, 7L, 5_000L)
    )
}
```

- [ ] **Step 2: Run the focused test and confirm the intended missing-policy failure**

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :biometric-custom-behavior:testDebugUnitTest --tests '*BehaviorCaptureSessionPolicyTest'
```

Expected: test compilation fails because the policy types/functions do not exist.

- [ ] **Step 3: Implement the minimal pure policy**

```kotlin
internal data class BehaviorCaptureSessionToken(val nonce: Long, val startedAtMs: Long)

internal enum class BehaviorCaptureSessionDecision {
    ACTIVE, EXPIRED, INVALID_TOKEN, ALREADY_SUBMITTED
}

internal fun evaluateBehaviorCaptureSession(
    nowMs: Long,
    submitted: Boolean,
    token: BehaviorCaptureSessionToken,
    expectedNonce: Long,
    maxDurationMs: Long
): BehaviorCaptureSessionDecision {
    if (submitted) return BehaviorCaptureSessionDecision.ALREADY_SUBMITTED
    if (token.nonce != expectedNonce) return BehaviorCaptureSessionDecision.INVALID_TOKEN
    if (nowMs < token.startedAtMs || nowMs - token.startedAtMs >= maxDurationMs) {
        return BehaviorCaptureSessionDecision.EXPIRED
    }
    return BehaviorCaptureSessionDecision.ACTIVE
}
```

- [ ] **Step 4: Run the focused tests and keep them green**

Expected: all `BehaviorCaptureSessionPolicyTest` tests pass.

### Task 2: Add input-integrity policy

**Files:**
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorInputIntegrityPolicy.kt`
- Create: `biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorInputIntegrityPolicyTest.kt`
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorSample.kt`

**Interfaces:**
- `BehaviorInputIntegrityDecision`: `ACCEPT`, `NON_MONOTONIC_TIMESTAMPS`, `NEGATIVE_DWELL`, `INVALID_POINT`, `DUPLICATE_CAPTURE`, `CANCELLED_CAPTURE`.
- `evaluateTypingIntegrity(downs: List<Long>, ups: List<Long>, phraseLength: Int, startedAtMs: Long, nowMs: Long, maxInterEventGapMs: Long): BehaviorInputIntegrityDecision`.
- `evaluateSignatureIntegrity(points: List<BehaviorPoint>, startedAtMs: Long, nowMs: Long, cancelled: Boolean): BehaviorInputIntegrityDecision`.

- [ ] **Step 1: Write failing tests for malformed typing/touch sequences**

Cover matching monotonic events, missing/negative dwell, event gap timeout, non-finite/out-of-range points, and cancelled signature capture.

- [ ] **Step 2: Run the focused test and verify it fails because the policy is absent**

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :biometric-custom-behavior:testDebugUnitTest --tests '*BehaviorInputIntegrityPolicyTest'
```

- [ ] **Step 3: Implement bounded, pure validation**

Use `Float.isFinite()`, bounded coordinates, non-negative pressure/size, monotonic timestamps, equal DOWN/UP counts, and `nowMs - lastEventMs <= maxInterEventGapMs`. Do not use wall-clock time in the policy.

- [ ] **Step 4: Route `BehaviorSample` parsing through policy-compatible bounds**

Preserve existing Bundle limits and legacy stride support. Return `null` for malformed point data rather than normalizing it into a valid sample.

- [ ] **Step 5: Run Behavior scorer and input-integrity tests**

Expected: all existing scorer tests and new integrity tests pass.

### Task 3: Add payload fingerprint and replay policy

**Files:**
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorReplayPolicy.kt`
- Create: `biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorReplayPolicyTest.kt`

**Interfaces:**
- `fingerprintBehaviorSample(sample: BehaviorSample): Long`.
- `evaluateBehaviorReplay(previousFingerprint: Long?, currentFingerprint: Long, nowMs: Long, previousAtMs: Long, freshnessWindowMs: Long): BehaviorReplayDecision`.
- Decisions are `ACCEPT` and `REJECT_REPLAY`.

- [ ] **Step 1: Add failing tests for stable fingerprint and freshness-window replay rejection**

Test equal samples, changed timing/coordinates, duplicate samples within the window, and acceptance after the window.

- [ ] **Step 2: Verify the tests fail before the policy exists**

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :biometric-custom-behavior:testDebugUnitTest --tests '*BehaviorReplayPolicyTest'
```

- [ ] **Step 3: Implement a deterministic non-sensitive fingerprint**

Hash mode, phrase length (not phrase content), normalized event timing, and coarse signature data. Never log or persist the raw hash outside the active process.

- [ ] **Step 4: Run replay policy tests**

Expected: exact duplicate payloads are rejected only inside the configured freshness window.

### Task 4: Bind the built-in capture controller to a session

**Files:**
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureController.kt`
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptDelegate.kt`
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptData.kt`
- Modify: `biometric-custom-behavior/src/main/res/values/strings.xml`
- Modify: `biometric-custom-behavior/src/main/res/values-uk/strings.xml`

**Interfaces:**
- Built-in capture creates a `BehaviorCaptureSessionToken` using `SystemClock.elapsedRealtime()` and `SecureRandom`.
- `buildBehaviorExtras` receives the token and adds internal token metadata without changing existing public keys.
- Controller submits at most once and stops accepting events after timeout, cancellation, obscured input, or successful preparation.

- [ ] **Step 1: Add failing controller/data tests for token propagation and duplicate submit rejection**

Use `BehaviorPromptDataTest`-style Bundle assertions; verify token metadata is present only in internally prepared payloads and a second preparation is rejected.

- [ ] **Step 2: Add session lifecycle state to the controller**

Reset on `showOverlay`, invalidate on `dispose`/`hideOverlay`/cancel, use elapsed realtime for deadlines, and clear all buffers after terminal state.

- [ ] **Step 3: Validate input integrity before `onReady()`**

Reject stale, cancelled, obscured, or malformed capture before writing the Bundle or invoking the manager.

- [ ] **Step 4: Make timeout and retry messages localized**

Use existing Behavior timeout/error callback mapping; add only missing resource strings.

- [ ] **Step 5: Run controller/data tests and compile the module**

Expected: no public API changes and all controller/policy tests pass.

### Task 5: Add strict-vs-compatibility and accessibility policy

**Files:**
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorAccessibilityPolicy.kt`
- Create: `biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorAccessibilityPolicyTest.kt`
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureController.kt`

**Interfaces:**
- `BehaviorCaptureTrust`: `STRICT_BUILT_IN`, `COMPATIBILITY_EXTERNAL_VIEW`.
- `BehaviorAccessibilityDecision`: `ALLOW`, `ALLOW_COMPATIBILITY`, `REJECT_UNTRUSTED_SERVICE`.
- `evaluateBehaviorAccessibility(strict: Boolean, hasWhitelistedService: Boolean, hasUntrustedService: Boolean): BehaviorAccessibilityDecision`.

- [ ] **Step 1: Write failing policy tests**

Verify strict mode rejects an untrusted service, whitelist allows the explicitly supported path, and compatibility mode returns `ALLOW_COMPATIBILITY` rather than pretending to be strict.

- [ ] **Step 2: Implement the policy using existing `A11yDetection` signals**

Do not create a second service enumeration implementation.

- [ ] **Step 3: Apply `ScreenProtection` consistently to overlay, panel, action button, mode controls, typing view, keyboard buttons, and signature pad**

Preserve original view state on disposal and do not leave accessibility delegates installed after the prompt closes.

- [ ] **Step 4: Keep consumer-owned views in compatibility mode**

Do not silently elevate `TextWatcher`-generated timing to strict trust. Built-in keyboard/raw event capture remains the strict path.

- [ ] **Step 5: Run accessibility policy and existing prompt tests**

Expected: protected views reject accessibility actions while disposal restores prior state.

### Task 6: Integrate manager-side validation and add attack matrix

**Files:**
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorBiometricManager.kt`
- Create: `biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorAttackMatrixTest.kt`
- Modify: `biometric-custom-behavior/README.md`
- Modify: `docs/superpowers/reports/2026-07-15-software-biometric-anti-spoof-readiness.md`

**Interfaces:**
- Manager validates token/session/replay/integrity before `qualityIssue()` and `BehaviorScorer`.
- Caller-supplied bundles without an active internal token remain compatibility input only; strict built-in flow requires the matching active token.
- Existing score threshold and lockout behavior remain unchanged.

- [ ] **Step 1: Add failing manager/attack-matrix tests**

Cover stale token, wrong token, duplicate payload, paste-like typing, malformed touch points, obscured/cancelled capture, and valid compatibility input.

- [ ] **Step 2: Add manager session state and terminal cleanup**

Use `SystemClock.elapsedRealtime()`, clear state on every callback/cancel/error path, and ensure a failed validation cannot reach template matching.

- [ ] **Step 3: Preserve ordinary mismatch lockout semantics**

Only genuine score mismatch increments the existing failure budget; malformed/replayed input returns an unable-to-process style error without revealing matching details.

- [ ] **Step 4: Run the complete Behavior unit-test suite and attack matrix**

```powershell
$originalPath = $env:Path
$env:Path = (($originalPath -split ';') | Where-Object { $_ -notmatch '^"/M$' -and $_ -ne '"' -and $_ -notmatch 'Microsoft VS Code' }) -join ';'
.\gradlew.bat --no-daemon --max-workers=1 :biometric-custom-behavior:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` and all Behavior tests pass.

- [ ] **Step 5: Update README and readiness report with limitations**

State clearly that exact replay/injection hardening is not a neural human-presence detector and does not cover rooted devices, process compromise, or high-quality generated behavior.

### Task 7: Final verification and handoff

**Files:**
- Verify all files changed by Tasks 1–6.

- [ ] **Step 1: Compile production and unit-test Kotlin**

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :biometric-custom-behavior:compileDebugKotlin :biometric-custom-behavior:compileDebugUnitTestKotlin
```

- [ ] **Step 2: Run the complete Behavior tests again**

Expected: all tests pass with the cleaned Gradle PATH.

- [ ] **Step 3: Run repository diff validation**

```powershell
git diff --check
git status --short
```

- [ ] **Step 4: Review security-sensitive diff**

Check input validation, accessibility delegate restoration, cancellation cleanup, logging, Bundle handling, and absence of public API changes.

- [ ] **Step 5: Stop without committing**

Report changed files, test evidence, compatibility tradeoffs, and remaining device/OEM validation. Ask for explicit approval before staging or committing.

