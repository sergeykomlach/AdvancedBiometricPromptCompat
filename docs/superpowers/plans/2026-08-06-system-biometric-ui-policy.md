# System Biometric UI Policy Implementation Plan

Goal: trust a usable Android system biometric UI over a false-negative SystemUI APK heuristic, while retaining explicit LG and old OnePlus fallback behavior.

Architecture: modern SystemUI detection, initial fallback selection, and late fallback suppression are independent pure policies. Api28 orchestration uses them without changing legacy module startup.

Constraints:

- Library-only changes.
- Preserve LG G8-family and pre-Android-12 OnePlus UDFPS fallback.
- Do not change permissions, biometric crypto, callbacks, or app UI.
- Do not commit.

## Task 1: Modern SystemUI detection

Files:

- Modify `biometric/src/main/java/dev/skomlach/biometric/compat/utils/CheckBiometricUI.kt`.
- Add `biometric/src/test/java/dev/skomlach/biometric/compat/utils/CheckBiometricUiPolicyTest.kt`.

Steps:

- [ ] Add failing tests for a modern enabled provider, a missing provider, and a pre-S resource-inspection fallback.
- [ ] Verify RED with `:biometric:testDebugUnitTest --tests dev.skomlach.biometric.compat.utils.CheckBiometricUiPolicyTest --no-daemon`.
- [ ] Add `shouldTrustBiometricUiProvider(sdkInt, providerInstalledAndEnabled, legacyResourceLayoutFound)`. It returns package state on S+ and resource-inspection state pre-S.
- [ ] Make `hasExists` resolve the configured provider package and its enabled state, then use the policy.
- [ ] Verify GREEN with the focused test.

## Task 2: Compat dialog policy

Files:

- Modify `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricAuthState.kt`.
- Modify `biometric/src/test/java/dev/skomlach/biometric/compat/Api28StartAuthPlanTest.kt`.

Steps:

- [ ] Add failing tests that a selected system route overrides a missing-UI heuristic, an explicit device exception keeps fallback, and an active system prompt rejects late fallback even with a legacy route.
- [ ] Verify RED with `:biometric:testDebugUnitTest --tests dev.skomlach.biometric.compat.Api28StartAuthPlanTest --no-daemon`.
- [ ] Add `shouldShowInitialCompatDialog(explicitSystemUiBug, heuristicReportsMissingUi, hasSelectedSystemPromptRoute)` and `shouldShowPostSystemCompatDialog(systemPromptStarted, hasPendingLegacyRoute)`.
- [ ] Verify GREEN with the focused test.

## Task 3: Api28 wiring

Files:

- Modify `biometric/src/main/java/dev/skomlach/biometric/compat/utils/DevicesWithKnownBugs.kt`.
- Modify `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt`.

Steps:

- [ ] Separate the explicit LG/old-OnePlus condition from the aggregate `isMissedBiometricUI` heuristic.
- [ ] Use selected routes to suppress the initial compat dialog when an Android system prompt is available.
- [ ] Reset and set a session-scoped `systemPromptStarted` flag around system prompt launch, and suppress only the later compat dialog branch.
- [ ] Do not change `LegacyBiometric.authenticate` scheduling.

## Task 4: Verification

Steps:

- [ ] Run the two focused test classes.
- [ ] Run `:biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin --no-daemon`.
- [ ] Inspect the focused diff for permission bypasses, exported component changes, sensitive logging, or crypto changes.
- [ ] Leave the patch uncommitted.
