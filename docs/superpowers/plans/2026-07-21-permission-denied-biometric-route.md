# Permission-denied biometric route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make enrollment permission denial recompute active biometric routes so only valid system or legacy prompts are shown.

**Architecture:** Keep permission handling module-agnostic. Add a pure route-policy helper that distinguishes remaining valid legacy routes from system-only routes, use it after modules are disabled, and keep Api28 fallback-dialog behavior driven by actual pending legacy types.

**Tech Stack:** Kotlin, JUnit, Android biometric compatibility library, Gradle.

## Global Constraints

- Library-only changes in `AdvancedBiometricPromptCompat`.
- Apply equally to camera, microphone, and all other runtime permissions.
- Preserve valid legacy routes for `BiometricConfirmation.ALL`.
- Do not change runtime authentication (`enroll = false`).
- Do not commit changes.

---

### Task 1: Add the failing route-policy regression tests

**Files:**
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/BiometricPermissionPolicyTest.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/BiometricPermissionPolicyTest.kt`

**Interfaces:**
- Consumes the existing `shouldStopAfterPermissionDenied` policy and the new pure route classification contract.
- Produces failing tests for system-only, independent legacy, no-route, and non-enrollment cases.

- [ ] **Step 1: Write tests for permission-agnostic route classification**

Add cases asserting that a route set with only a system route has no pending legacy route, a route set with an independent legacy route does have one, and an empty route set is terminal during enrollment. Use generic permission names such as `Manifest.permission.CAMERA` and `Manifest.permission.RECORD_AUDIO` only as inputs; the policy must not branch on their values.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.BiometricPermissionPolicyTest' --no-daemon
```

Expected: compilation/test failure because the route-policy helper or expected behavior does not yet exist.

### Task 2: Implement the minimal route policy and wire permission-denied recomputation

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPermissionPolicy.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt:1158-1203`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt:430-480,772-777`

**Interfaces:**
- Produces a pure helper accepting selected routes and returning whether a valid legacy route remains.
- The helper must distinguish `usesBiometricPromptHardware` from legacy routes and must not inspect permission names.

- [ ] **Step 1: Add the smallest pure route helper**

Implement a helper over `SelectedBiometricRoute?` values that returns true only when at least one non-null route is legacy (`usesBiometricPromptHardware == false`). Keep `shouldStopAfterPermissionDenied` responsible only for enrollment/denied-permission terminal policy.

- [ ] **Step 2: Recompute routes after disabled modules**

After `disablePermissionDeniedModules(permissionsMap)`, derive the active types/routes from the builder’s current selected routes. Pass the resulting `hasUsableRouteAfterDeniedModules` into `shouldStopAfterPermissionDenied`; do not use the stale `getAllAvailableTypes().isNotEmpty()` check.

- [ ] **Step 3: Keep Api28 fallback suppression route-aware**

Ensure `BiometricPromptApi28Impl` suppresses the compat dialog only when the system prompt is active and no actual legacy stage remains. Do not broaden the guard to all `BiometricConfirmation.ALL` flows.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same focused Gradle test. Expected: PASS.

### Task 3: Verify adjacent contracts and security boundaries

**Files:**
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/BiometricPermissionPolicyTest.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/Api28StartAuthPlanTest.kt`

- [ ] **Step 1: Run permission and Api28 regression tests**

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.BiometricPermissionPolicyTest' --tests 'dev.skomlach.biometric.compat.Api28StartAuthPlanTest' --no-daemon
```

- [ ] **Step 2: Run compile verification**

```powershell
.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin --no-daemon
```

- [ ] **Step 3: Inspect the diff and confirm no permission-specific branching**

```powershell
git diff --check
git diff -- biometric/src/main/java biometric/src/test/java
```

Expected: only route-policy, permission-denied recomputation, Api28 prompt gating, and focused tests are changed; no secrets, logging of sensitive data, exported-component changes, or permission-name special cases are introduced.
