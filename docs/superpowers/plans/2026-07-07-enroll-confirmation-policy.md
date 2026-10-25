# Enroll Confirmation Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make enroll success depend on real confirmation events, preserve `BiometricConfirmation.ANY/ALL`, and rollback software enrolls only when the chosen confirmation contract requires failure.

**Architecture:** Keep the existing `enrollScopeTypes + pendingEnrollTypes + preSatisfiedEnrollResults` model, but extend it with one centralized session-aware enroll resolver in `BiometricAuthState.kt`. Terminal paths in `BiometricPromptCompat`, `BiometricPromptGenericImpl`, and `BiometricPromptSilentImpl` should feed events into that resolver instead of deciding success from filtered pending scope alone.

**Tech Stack:** Kotlin, AndroidX fragments, Gradle, JUnit4 unit tests in `:biometric`

## Global Constraints

- Preserve `BiometricConfirmation.ANY` and `BiometricConfirmation.ALL` semantics; do not add ad hoc enroll-only success overrides.
- Successful system hardware enroll counts as biometric confirmation; do not force an extra hardware auth afterward.
- Runtime permission denial, module filtering, and device-credential fallback must not become hidden enroll success paths.
- `ANY` keeps previously successful software enrolls and ignores later cancel/error from other biometrics.
- `ALL` rolls back earlier successful software enrolls when a later required biometric cancels/fails.
- Keep the change localized to enroll-related code paths unless the same aggressive override is clearly shared.

---

### Task 1: Lock The Policy In Tests

**Files:**
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/EnrollOutcomeResolverTest.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/EnrollOutcomeResolverTest.kt`

**Interfaces:**
- Consumes: `resolveEnrollTerminalOutcome(...)`, new session-aware enroll resolver helpers in `BiometricAuthState.kt`
- Produces: failing contract tests for confirmation events, fallback confirmation, and rollback requirements

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `permission filtering alone does not satisfy enroll confirmation`() {
    val outcome = resolveEnrollSessionOutcome(
        confirmation = BiometricConfirmation.ANY,
        scopeTypes = listOf(BiometricType.BIOMETRIC_FACE),
        successResults = emptySet(),
        failureResults = emptySet(),
        canceledResults = emptySet(),
        confirmedThisRun = false,
        terminal = true
    )

    assertEquals(EnrollTerminalStatus.FAILED, outcome.status)
}

@Test
fun `hardware fallback confirmation satisfies any after software becomes unavailable`() {
    val outcome = resolveEnrollSessionOutcome(
        confirmation = BiometricConfirmation.ANY,
        scopeTypes = listOf(BiometricType.BIOMETRIC_FACE),
        successResults = setOf(AuthenticationResult(BiometricType.BIOMETRIC_FACE)),
        failureResults = emptySet(),
        canceledResults = emptySet(),
        confirmedThisRun = true,
        terminal = true
    )

    assertEquals(EnrollTerminalStatus.SUCCEEDED, outcome.status)
}

@Test
fun `all requires rollback when one prior software enroll succeeded and later one fails`() {
    val outcome = resolveEnrollSessionOutcome(
        confirmation = BiometricConfirmation.ALL,
        scopeTypes = listOf(BiometricType.BIOMETRIC_VOICE, BiometricType.BIOMETRIC_BEHAVIOR),
        successResults = setOf(AuthenticationResult(BiometricType.BIOMETRIC_VOICE)),
        failureResults = setOf(
            AuthenticationResult(
                BiometricType.BIOMETRIC_BEHAVIOR,
                reason = AuthenticationFailureReason.CANCELED_BY_USER
            )
        ),
        canceledResults = emptySet(),
        confirmedThisRun = true,
        terminal = true
    )

    assertEquals(true, outcome.rollbackSuccessfulEnrolls)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\\gradlew.bat :biometric:compileDebugUnitTestKotlin`
Expected: FAIL in `EnrollOutcomeResolverTest` because `resolveEnrollSessionOutcome(...)` and `rollbackSuccessfulEnrolls` do not exist yet.

- [ ] **Step 3: Add the minimal shared test scaffolding**

```kotlin
private fun success(type: BiometricType) = AuthenticationResult(type)
```

- [ ] **Step 4: Run test to verify it still fails for missing production API**

Run: `.\\gradlew.bat :biometric:compileDebugUnitTestKotlin`
Expected: FAIL with unresolved references to the new session-aware resolver API, not with syntax errors.

- [ ] **Step 5: Commit**

```bash
git add biometric/src/test/java/dev/skomlach/biometric/compat/EnrollOutcomeResolverTest.kt
git commit -m "test: define enroll confirmation policy contract"
```

### Task 2: Add Shared Session-Aware Enroll Outcome Resolution

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricAuthState.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/EnrollOutcomeResolverTest.kt`

**Interfaces:**
- Consumes: existing `resolvePreSatisfiedEnrollResults(...)`, `resolveEnrollTerminalOutcome(...)`, `BiometricConfirmation`
- Produces: `resolveEnrollSessionOutcome(...)`, `EnrollSessionOutcome`, `rollbackSuccessfulEnrolls`, `confirmedThisRun`, `requiresFallbackAuth`

- [ ] **Step 1: Implement the failing shared resolver API**

```kotlin
internal data class EnrollSessionOutcome(
    val status: EnrollTerminalStatus,
    val results: Set<AuthenticationResult>,
    val confirmedThisRun: Boolean,
    val rollbackSuccessfulEnrolls: Boolean,
    val requiresFallbackAuth: Boolean
)
```

- [ ] **Step 2: Implement minimal resolution logic**

```kotlin
internal fun resolveEnrollSessionOutcome(
    confirmation: BiometricConfirmation,
    scopeTypes: Collection<BiometricType>,
    successResults: Collection<AuthenticationResult>,
    failureResults: Collection<AuthenticationResult> = emptySet(),
    canceledResults: Collection<AuthenticationResult> = emptySet(),
    confirmedThisRun: Boolean,
    terminal: Boolean
): EnrollSessionOutcome {
    val base = resolveEnrollTerminalOutcome(
        confirmation = confirmation,
        scopeTypes = scopeTypes,
        successResults = successResults,
        failureResults = failureResults,
        canceledResults = canceledResults,
        terminal = terminal
    )
    val shouldRollback = confirmation == BiometricConfirmation.ALL &&
        failureResults.isNotEmpty() &&
        successResults.isNotEmpty()

    val adjustedStatus = when {
        base.status == EnrollTerminalStatus.SUCCEEDED && !confirmedThisRun -> EnrollTerminalStatus.FAILED
        else -> base.status
    }

    return EnrollSessionOutcome(
        status = adjustedStatus,
        results = base.results,
        confirmedThisRun = confirmedThisRun,
        rollbackSuccessfulEnrolls = shouldRollback,
        requiresFallbackAuth = !confirmedThisRun && successResults.isEmpty()
    )
}
```

- [ ] **Step 3: Thread the shared helper into `setupBiometric()` decision points**

```kotlin
private fun dispatchEnrollSessionOutcome(
    callback: AuthenticationCallback,
    confirmedThisRun: Boolean,
    failureResults: Set<AuthenticationResult> = emptySet(),
    canceledResults: Set<AuthenticationResult> = emptySet()
) { /* call resolveEnrollSessionOutcome(...) */ }
```

- [ ] **Step 4: Run compile verification**

Run: `.\\gradlew.bat :biometric:compileDebugUnitTestKotlin :biometric:compileDebugKotlin`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/BiometricAuthState.kt biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt biometric/src/test/java/dev/skomlach/biometric/compat/EnrollOutcomeResolverTest.kt
git commit -m "feat: add shared enroll confirmation resolver"
```

### Task 3: Integrate Prompt Terminal Paths And Narrow Overrides

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptSilentImpl.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/EnrollOutcomeResolverTest.kt`

**Interfaces:**
- Consumes: `resolveEnrollSessionOutcome(...)`, `LegacyBiometric.rollbackLastEnrollInSoftwareModules()`
- Produces: terminal enroll behavior that respects `ANY/ALL`, treats system enroll as confirmation, and only rolls back on `ALL`

- [ ] **Step 1: Track real confirmation events in terminal paths**

```kotlin
private fun buildEnrollSessionOutcome(
    terminal: Boolean
): EnrollSessionOutcome = resolveEnrollSessionOutcome(
    confirmation = builder.getBiometricAuthRequest().confirmation,
    scopeTypes = completionTypes(),
    successResults = successfulResults(),
    failureResults = fatalErrorResults(),
    canceledResults = canceled,
    confirmedThisRun = successfulResults().isNotEmpty(),
    terminal = terminal
)
```

- [ ] **Step 2: Only rollback on `ALL` when the shared outcome requests it**

```kotlin
if (builder.enroll && outcome.rollbackSuccessfulEnrolls) {
    ExecutorHelper.startOnBackground {
        LegacyBiometric.rollbackLastEnrollInSoftwareModules()
    }
}
```

- [ ] **Step 3: Prevent permission filtering from becoming hidden success**

```kotlin
if (builder.enroll && executionTypes().isEmpty() && successfulResults().isEmpty()) {
    callback?.onFailed(emptyEffectiveBiometricCancellationResults(completionTypes()))
    cancelAuthentication()
    return
}
```

- [ ] **Step 4: Run end-to-end compile verification**

Run: `.\\gradlew.bat :biometric:compileDebugUnitTestKotlin :biometric:compileDebugKotlin :biometric-custom-voice:compileDebugKotlin :biometric-custom-behavior:compileDebugKotlin`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptSilentImpl.kt
git commit -m "feat: enforce enroll confirmation policy"
```
