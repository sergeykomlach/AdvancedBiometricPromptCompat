# ScreenProtection Hardening Implementation Plan

**Goal:** Keep `FLAG_SECURE` as the authoritative protection while isolating optional Android screenshot/overlay controls from failures.

**Architecture:** Refactor `ScreenProtection` so the mandatory window flag is applied independently, while recents and overlay controls remain best-effort. Add focused unit-level regression coverage for the decision boundaries that can be tested without a real Android window.

**Tech Stack:** Kotlin, AndroidX, Gradle, JUnit.

## Global Constraints

- Do not change the public `ScreenProtection` method signatures.
- Do not change the meaning of `disableWindow` or `includeHostActivity`.
- Do not add a QA/debug bypass.
- Do not commit changes.

### Task 1: Refactor window protection failure boundaries

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/utils/ScreenProtection.kt`

- [ ] Keep `window.addFlags(FLAG_SECURE)` and `window.clearFlags(FLAG_SECURE)` outside the broad optional-feature failure boundary.
- [ ] Wrap only `setRecentsScreenshotEnabled` and `setHideOverlayWindows` in independent best-effort guards.
- [ ] Preserve existing view protection and rollback behavior.

### Task 2: Add regression coverage

**Files:**
- Create or modify: `biometric/src/test/java/dev/skomlach/biometric/compat/utils/ScreenProtectionTest.kt`

- [ ] Add tests for the protection mode contract and optional-control isolation using the smallest testable seams available in the current module.
- [ ] Run the focused biometric unit-test task and inspect failures before claiming completion.

### Task 3: Verify consumer compatibility

**Files:**
- Inspect only: `C:/Users/skoml/StudioProjects_5/rf-dev/roboform/android/java/app/src/main/java/com/siber/roboform/util/ScreenProtection.kt`

- [ ] Confirm the unchanged public signatures remain compatible with RoboForm.
- [ ] Run the narrow library verification and report any environment/build limitation separately from code results.
