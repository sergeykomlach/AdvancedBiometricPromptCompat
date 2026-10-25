# Biometric Production Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AdvancedBiometricPromptCompat safer and more predictable for RoboForm-style production biometric unlock flows, especially Samsung Face ID upgrade and camera-permission denial cases.

**Architecture:** Keep the current compatibility architecture intact, but isolate behavior fixes behind small helpers and tests. Prioritize stable public contract, provider-aware routing, permission UX, and reduced hidden side effects before larger module splitting.

**Tech Stack:** Android Gradle library modules, Kotlin, AndroidX Biometric, legacy/vendor biometric modules, JVM unit tests where possible, Android compile verification.

---

### Task 1: Stabilize Core Auth State And Provider Cache

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricManagerCompat.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/BiometricAuthRequestCacheKeyTest.kt`

- [ ] Add a small cache-key helper that includes `api`, `type`, and `provider`.
- [ ] Replace `authFlowInProgress.get()` plus `set(true)` with `compareAndSet(false, true)`.
- [ ] Verify `:biometric:compileDebugKotlin` and the new JVM tests.

### Task 2: Normalize Missing Permission Errors

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/AuthenticationResultDescriptionTest.kt`

- [ ] Ensure every `MISSING_PERMISSIONS_ERROR` result has a user-facing description before leaving `BiometricPromptCompat`.
- [ ] Prefer the existing camera-specific string when selected modules require `android.permission.CAMERA`.
- [ ] Verify RoboForm cannot receive a blank/raw `(MISSING)_PERMISSIONS_ERROR` style message from this library path.

### Task 3: Samsung Face ID Upgrade Routing

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt`

- [ ] Keep route-aware lockout behavior.
- [ ] Ensure a face-only Samsung route does not surface fingerprint UI when fingerprint is not enrolled.
- [ ] Treat stale persisted RF-side biometric config as a caller state problem, but expose enough result detail to prompt re-enable biometrics.

### Task 4: Camera-Denied Setup Flow

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt`

- [ ] If camera permission is denied for the only viable software/legacy face module, stop setup with `MISSING_PERMISSIONS_ERROR`.
- [ ] Do not continue into software face preparation after explicit permission denial unless another permission-satisfied module remains.
- [ ] Preserve system BiometricPrompt hardware fallback only when it does not require the denied permission.

### Task 5: Production Hardening Backlog

Current regression review (2026-09-05): **changes requested**. Track the three static P1 findings and seven Pixel 9 Pro Fold / AOS17 observations in the [biometric regression backlog](/C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/docs/reviews/biometric-regression-backlog.md), BIO-REG-001 through BIO-REG-010. Their status, evidence, reproduction steps, and acceptance criteria are maintained there; the hardening tasks below remain separate and open.

**Files:**
- Modify later: manifests/build files after behavior fixes are green.

- [ ] Reduce manifest footprint by moving optional vendor/software features into optional artifacts.
- [ ] Revisit exported boot receiver and quickboot actions.
- [ ] Replace unbounded `GlobalScope`/`observeForever` patterns with app-scoped lifecycle registration.
- [ ] Refresh OWASP dependency scan and remove unused local artifacts from release inputs.
