# Voice Localization And Crypto Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the software biometric crypto fallback message into resources and correct the affected biometric voice and shared biometric locale translations.

**Architecture:** Keep the change narrow: one shared localized helper for the crypto fallback in `biometric`, one regression test in `biometric/src/test`, and direct resource updates in `biometric` plus `biometric-custom-voice`. Reuse the existing `LocalizationHelper` and current `values-*` folder layout instead of introducing new localization infrastructure.

**Tech Stack:** Android library resources, Kotlin, JUnit4, Gradle

## Global Constraints

- Keep the fix scoped to user-facing biometric copy relevant to the reported voice auth flow.
- Preserve existing `%` placeholders and any HTML markup such as `<b>...</b>`.
- Do not introduce one-off wrapper classes for a single call site.
- Add a failing test before production-code changes.
- Leave unrelated dirty-worktree files untouched.

---

### Task 1: Resource-Backed Crypto Fallback

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricText.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt`
- Modify: `biometric/src/main/res/values/strings.xml`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/SoftwareBiometricMessageTextTest.kt`

**Interfaces:**
- Consumes: `LocalizationHelper.getLocalizedString(...)`, `AndroidContext.appContext`
- Produces: `softwareBiometricHardwareBackedCryptoUnsupportedDescription(name: String): String`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun softwareBiometricHardwareBackedCryptoUnsupportedDescriptionInterpolatesModuleName() {
    assertEquals(
        "Software biometric module Voice cannot satisfy a hardware-backed CryptoObject",
        softwareBiometricHardwareBackedCryptoUnsupportedDescription("Voice")
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :biometric:testDebugUnitTest --tests dev.skomlach.biometric.compat.SoftwareBiometricMessageTextTest`
Expected: FAIL because `softwareBiometricHardwareBackedCryptoUnsupportedDescription` does not exist yet

- [ ] **Step 3: Write minimal implementation**

```kotlin
internal fun softwareBiometricHardwareBackedCryptoUnsupportedDescription(name: String): String =
    biometricText(R.string.biometriccompat_software_hardware_backed_crypto_unsupported, name)
```

```kotlin
listener?.onFailure(
    tag(),
    AuthenticationFailureReason.CRYPTO_ERROR,
    softwareBiometricHardwareBackedCryptoUnsupportedDescription(name)
)
```

```xml
<string name="biometriccompat_software_hardware_backed_crypto_unsupported">Software biometric module %1$s cannot satisfy a hardware-backed CryptoObject</string>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :biometric:testDebugUnitTest --tests dev.skomlach.biometric.compat.SoftwareBiometricMessageTextTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/BiometricText.kt biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt biometric/src/main/res/values/strings.xml biometric/src/test/java/dev/skomlach/biometric/compat/SoftwareBiometricMessageTextTest.kt
git commit -m "test: localize software biometric crypto fallback"
```

### Task 2: Shared Biometric Locale Corrections

**Files:**
- Modify: `biometric/src/main/res/values-*/strings.xml`

**Interfaces:**
- Consumes: existing `biometriccompat_*` string keys used by prompt, privacy, and fallback flows
- Produces: translated locale entries for the shared biometric strings used in the reported voice flow

- [ ] **Step 1: Update shared locale strings**

Update these keys across every existing `biometric/src/main/res/values-*` file:

```text
biometriccompat_software_hardware_backed_crypto_unsupported
biometriccompat_sensor_privacy_start_use_mic_notification_content_title
biometriccompat_sensor_privacy_start_use_dialog_turn_on_button
biometriccompat_biometric_dialog_default_title
biometriccompat_biometric_dialog_default_subtitle
```

- [ ] **Step 2: Verify placeholders and markup stay intact**

Check that `%1$s` is preserved for the new crypto string and that no locale drops formatting markers.

- [ ] **Step 3: Smoke-check the files**

Run: `rg -n "biometriccompat_software_hardware_backed_crypto_unsupported|biometriccompat_sensor_privacy_start_use_mic_notification_content_title|biometriccompat_biometric_dialog_default_subtitle" biometric/src/main/res`
Expected: every locale file contains translated entries for all listed keys

- [ ] **Step 4: Commit**

```bash
git add biometric/src/main/res
git commit -m "feat: localize shared biometric voice-facing strings"
```

### Task 3: Voice Module Locale Corrections

**Files:**
- Modify: `biometric-custom-voice/src/main/res/values-*/strings.xml`

**Interfaces:**
- Consumes: existing `biometriccompat_voice_*` keys
- Produces: translated `voice` strings in every shipped locale folder

- [ ] **Step 1: Update all voice locale files**

Translate every user-facing `biometriccompat_voice_*` string in all `biometric-custom-voice/src/main/res/values-*` folders while preserving `%1$d`, `%2$d`, and `%1$s`.

- [ ] **Step 2: Verify locale completeness**

Run: `rg -n "biometriccompat_voice_" biometric-custom-voice/src/main/res`
Expected: each locale file has the same key set as base `values/strings.xml`

- [ ] **Step 3: Commit**

```bash
git add biometric-custom-voice/src/main/res
git commit -m "feat: translate voice biometric locale packs"
```

### Task 4: Verification

**Files:**
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/SoftwareBiometricMessageTextTest.kt`

**Interfaces:**
- Consumes: updated test and resource trees
- Produces: fresh verification evidence for the narrow scope

- [ ] **Step 1: Run targeted biometric unit tests**

Run: `.\gradlew.bat :biometric:testDebugUnitTest --tests dev.skomlach.biometric.compat.SoftwareBiometricMessageTextTest --tests dev.skomlach.biometric.compat.AuthenticationResultDescriptionTest --tests dev.skomlach.biometric.compat.PreparationErrorHandlingTest`
Expected: PASS

- [ ] **Step 2: Run targeted resource merge/compile checks**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric-custom-voice:mergeDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Review diff for scope**

Run: `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff -- biometric biometric-custom-voice docs/superpowers/plans/2026-07-07-voice-localization-and-crypto-fallback.md`
Expected: only the planned code, test, resource, and plan changes appear
