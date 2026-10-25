# Shared Software Biometric Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enforce explicit software biometric assurance and fail-closed lifecycle, routing, cryptographic, registry, and input security invariants across the library.

**Architecture:** Add shared immutable provider security metadata, a request gate, an atomic terminal session guard, bounded input primitives, and deterministic provider discovery in `biometric`. Adapt the four provider modules only where their existing lifecycle or vendor callbacks need the shared invariants. Preserve public request-builder compatibility.

**Tech Stack:** Kotlin, Android library modules, JVM unit tests, Gradle Kotlin DSL, existing callback/provider APIs.

## Global Constraints

- No new ML model or device-only dependency.
- No public request-builder breaking change.
- Software biometric must never satisfy a hardware-backed cryptographic request by default.
- Unknown or malformed security metadata fails closed.
- No raw biometric data, embeddings, nonces, or full Bundle contents in logs.
- Do not commit changes without explicit user approval.

---

### Task 1: Shared security profile and request gate — completed

**Files:**
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricSecurityProfile.kt`
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricSecurityPolicy.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/AbstractSoftwareBiometricManager.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricSecurityPolicyTest.kt`

**Steps:**

- [ ] Write pure JVM tests proving the default profile rejects cryptographic requests, accepts ordinary non-cryptographic compatibility requests, rejects unknown profiles, and rejects a mismatched modality.
- [ ] Run the focused test and confirm it fails because the profile and policy do not exist.
- [ ] Add immutable profile fields for assurance class, trusted capture requirement, compatibility allowance, crypto capability, and bounded capture duration.
- [ ] Add a pure policy evaluator returning explicit allow/reject reasons.
- [ ] Expose the conservative default profile from `AbstractSoftwareBiometricManager` without changing existing callers.
- [ ] Run the focused tests and compile `:biometric`.

### Task 2: Cryptographic route and `ANY`/`AUTO` filtering — partially completed

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricAuthState.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptSilentImpl.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/engine/SoftwareBiometricRouteSecurityTest.kt`

**Steps:**

- [ ] Add failing route tests for software fallback with a `BiometricCryptoObject`, `ANY` with only an unsafe software candidate, and `ALL` with an untrusted candidate.
- [ ] Run the tests and confirm the expected failures.
- [ ] Apply the shared policy before software module selection and before software success is counted.
- [ ] Ensure rejected software crypto routes produce the existing unable-to-process/security outcome and never a success result or hardware crypto level.
- [ ] Preserve ordinary non-cryptographic `ANY` behavior when the provider profile allows it.
- [ ] Run focused route tests and the existing module-selection tests.

### Task 3: Exactly-once authentication session guard — completed

**Files:**
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricSessionGuard.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/AbstractSoftwareBiometricManager.kt`
- Modify: provider managers in `biometric-custom-face-tf`, `biometric-custom-voice`, `biometric-custom-behavior`, and `biometric-zkfinger` where terminal callbacks are emitted.
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricSessionGuardTest.kt`

**Steps:**

- [ ] Write failing tests for duplicate success, success after cancel, success after timeout, stale session id, and error after terminal success.
- [ ] Run the tests and verify they fail for the missing guard.
- [ ] Implement an atomic opaque session guard with start, cancel, timeout, and one terminal transition.
- [ ] Integrate only terminal callback delivery; do not move provider-specific capture evidence into the shared guard.
- [ ] Clear guard state on every terminal path and verify listener/timer cleanup at provider boundaries.
- [ ] Run guard tests plus targeted provider flow tests.

### Task 4: Shared bounded input validation — completed for shared primitives and voice PCM boundary

**Files:**
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricInputPolicy.kt`
- Modify: voice and face input boundary classes under `biometric-custom-voice` and `biometric-custom-face-tf`.
- Modify: `biometric-custom-behavior` to reuse shared scalar/size checks where semantics match.
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricInputPolicyTest.kt`

**Steps:**

- [ ] Write failing tests for negative/overflow durations, oversized byte arrays, oversized collections, non-finite numbers, and clock rollback.
- [ ] Run the tests and confirm the expected failures.
- [ ] Implement allocation-safe bounded checks with explicit rejection decisions.
- [ ] Apply checks before scoring and before derived arrays/copies are created.
- [ ] Keep modality-specific phrase, PCM, typing, and signature validation in their provider modules.
- [ ] Run policy tests and provider scorer tests.

### Task 5: Deterministic provider registry hardening — completed

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricProvider.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistryTest.kt`

**Steps:**

- [ ] Add failing tests for duplicate modality providers, provider metadata exception, factory exception, and deterministic priority selection.
- [ ] Run the tests and confirm current first-provider behavior fails the new assertions.
- [ ] Add explicit provider priority metadata with a conservative default.
- [ ] Sort providers deterministically and reject duplicate effective registrations rather than relying on classpath order.
- [ ] Isolate provider failures and ensure no partially initialized manager enters the module map.
- [ ] Run registry and software module loading tests.

### Task 6: ZKFinger callback hardening — completed at common terminal callback boundary

**Files:**
- Modify: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerUnlockManager.kt`
- Modify: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProvider.kt`
- Test: `biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerSessionStateTest.kt`

**Steps:**

- [ ] Write failing tests for success after cancel, success after lockout, duplicate vendor success, and vendor callback after destroy.
- [ ] Run the tests and verify they fail against the current callback paths.
- [ ] Route vendor callbacks through the shared terminal guard and retain existing public error mappings.
- [ ] Make vendor exceptions terminal and prevent later success delivery.
- [ ] Ensure no vendor payload or fingerprint data is logged.
- [ ] Run ZKFinger focused tests and compile the module.

### Task 7: Attack matrix, docs, and security regression coverage — completed for shared policies

**Files:**
- Create: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricSecurityAttackMatrixTest.kt`
- Modify: `biometric-custom-face-tf/README.md`
- Modify: `biometric-custom-voice/README.md`
- Modify: `biometric-custom-behavior/README.md`
- Modify: `biometric-zkfinger/README.md` if present
- Modify: `docs/superpowers/reports/2026-07-15-software-biometric-anti-spoof-readiness.md`

**Steps:**

- [ ] Add one matrix test per invariant covering crypto rejection, route filtering, stale/duplicate callbacks, bounded input, duplicate providers, and vendor terminal state.
- [ ] Run the matrix and confirm all new protections pass.
- [ ] Document the assurance classes, compatibility behavior, crypto limitation, residual root/instrumentation risk, and vendor dependency.
- [ ] Update the readiness report with exact verification limitations.
- [ ] Run `git diff --check`.

### Task 8: Verification and handoff

**Files:**
- No production file changes expected.

**Steps:**

- [ ] Run `:biometric:compileDebugKotlin`, all four provider compile tasks, and their unit-test compilation tasks.
- [ ] Run focused shared policy, routing, registry, session, attack matrix, and provider tests.
- [ ] Run the broadest feasible unit-test suite and record any Android Bundle/Robolectric environment failures separately.
- [ ] Inspect the final diff for sensitive logging, public API drift, unsafe fallback, and unrelated file changes.
- [ ] Report verified results and remaining device/ML limitations; do not commit without user approval.
