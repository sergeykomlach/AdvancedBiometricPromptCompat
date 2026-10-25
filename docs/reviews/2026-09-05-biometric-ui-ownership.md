# BIO-REG-007: explicit backend UI ownership

## Demo correction: preserve the standard FACE-capable routes

The prior temporary fixture replaced AUTO/ANY and AUTO/FACE buttons in the installed APK. Restoring the source after assembly did not restore those buttons on the device. This was a QA usability regression, not evidence of a library AUTO-to-legacy route change.

Corrected demo source: FirstFragment retains all original buttons and appends four debug-only diagnostics: AUTO ANY with ANY/ALL and default UI, plus LEGACY ANY with ANY/ALL and explicit SYSTEM override. BiometricExtension accepts an optional presentation override defaulting to UNKNOWN. Standard callers do not force SYSTEM. No library routing change in this correction.

`:app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain` passed in 6s; scoped app diff check passed. Installed successfully on Pixel serial 4A221FDKD001JR via `adb -s 192.168.0.10:34555 install -r app/build/outputs/apk/debug/app-debug.apk`. SHA256 `548722527E930FD16E5EFAEAA9EF9E805190D01C27E1B0CD3BFF158C45A37F11`. This APK now corresponds to the demo source in the checkout.

19:58:53 AUTO/ANY/ANY replay: log `BiometricPromptCompat.IBiometricPromptImpl - true` and UI accessibility description `Триває розпізнавання обличчя` confirm the API28 implementation and face recognition are available again. Canceled the request after inspection; this is route/UI-start proof, not a claim of FACE-specific success. Other new buttons were compiled but not separately replayed in this correction. No full suite, release/R8 or enrollment changes.

## Latest runtime verification: mixed stages on Pixel

Pixel 9 Pro Fold / Android 17, serial 4A221FDKD001JR, ADB 192.168.0.10:34555, process 3064. Debug APK SHA256: `C3CE8ABBA0C2405FBF8FB24618A10D5E35BD70084700A2BF83B585C27E85DA07`.

Temporary demo fixture added explicit SYSTEM override and clearly labeled LEGACY ANY/ANY and LEGACY ANY/ALL buttons. `BiometricExtension.kt` and `FirstFragment.kt` were restored byte-for-byte after assembly. Library source was not modified for QA. Installed APK retains that fixture; restored demo source does not. Build command: `.\gradlew.bat :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain` — PASS in 6s. `adb -s 192.168.0.10:34555 install -r app/build/outputs/apk/debug/app-debug.apk` — Success.

Runtime results (all non-crypto authentication, not enrollment):

- ALL: 19:48:47.865 engine started only `{100=BIOMETRIC_FINGERPRINT}` using GenericImpl. Fingerprint succeeded at 19:49:09.895; completion remained pending VOICE. Ownership switched to COMPAT at 19:49:09.912. After voice capture, engine started only `{329184405=BIOMETRIC_VOICE}` at 19:49:23.903. At 19:49:24.026 outer onSucceeded contained VOICE and FINGERPRINT, followed by onUIClosed at .032. User explicitly confirmed custom dialog appeared after system dialog disappeared, as expected.
- ANY: 19:50:17.629 only fingerprint started. At 19:50:22.692 outer onSucceeded contained FINGERPRINT, then onUIClosed at .695. No voice backend start in this request; user confirmed no voice dialog after finger success.
- ALL cancel: 19:51:23.607 only fingerprint started. System Cancel produced onCanceled at 19:51:36.544 and onUIClosed at .548, with no next voice stage in the observed trace. Returned to scenario list.

No FATAL EXCEPTION observed in filtered process logs. Used UI-tree-derived taps and `logcat --pid=3064 -d -v time`; no permission/enrollment changes, data clearing or induced lockout. No full suite or release/R8. These results supersede the earlier mixed-success runtime gap for this explicit SYSTEM Pixel fixture. UNKNOWN default, fatal-error handoff, lockout, crypto, enrollment, Samsung, Bubble and split-screen remain unverified. BIO-REG-007 stays OPEN as a universal default/OEM issue; the tested staged override route passes.

## Latest correction: mixed legacy stages

The mixed-request finding is addressed in source: a SYSTEM-owned fingerprint is executed alone before remaining compat routes. ANY finishes on fingerprint success and can advance on fatal fingerprint failure; ALL advances on success and fails on fatal failure. User cancel terminates the whole request. Enrollment still uses its existing outcome resolver; only CONTINUE permits handoff. UNKNOWN default is unchanged and still cannot guarantee no native UI.

At handoff, callback stage generation changes before backend cancellation. Pending start is session/generation guarded, finished types are excluded from the next dialog selection, and ownership is updated for the next stage. If eligibility disappears during handoff, the flow fails rather than waiting with no backend. Starting the next stage on the main queue is not proof that every OEM native dismiss animation has finished.

Changed files this correction: `BiometricPromptGenericImpl.kt`, Builder KDoc in `BiometricPromptCompat.kt`, new `LegacyUiStages.kt`, new `LegacyUiStagesTest.kt`, this report and backlog. Module: biometric.

Verification command: `.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.LegacyUiStagesTest" --tests "dev.skomlach.biometric.compat.impl.AuthenticationUiPolicyTest" --tests "dev.skomlach.biometric.compat.impl.LegacyAuthStartDelayTest" --console=plain`. PASS twice (6s initial, 7s after empty-stage terminal guard); 16 tests, no failures. Scoped source/test diff check PASS. No app assemble/install, full suite, release/R8 or new device QA.

Repeated static review traced preparation, backend registration, ANY/ALL and enrollment completion, cancellation, deferred start and dialog primary selection. No further concrete blocker identified within this correction. Tests cover completion and stage eligibility decisions, not real Fragment/SystemUI transitions. Mixed ANY/ALL, enrollment, late native callback and animation overlap remain device/integration gates; previous single-fingerprint Pixel proof does not cover this code change. Earlier statements below describe the prior implementation.

## Subsequent Pixel QA (SYSTEM override fixture)

Targeted `:app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain` passed in 6s. Temporarily inserted the explicit SYSTEM setter into demo BiometricExtension, then restored that source byte-for-byte in finally. APK therefore contains a QA override not present in the restored demo source. Library default remains UNKNOWN.

APK SHA256 `F4AFB7DBBC508C65908ECD2002A7CD3F17BA4AE09212774EFC7DE5C794DFE815`; `adb -s 192.168.0.10:34555 install -r app/build/outputs/apk/debug/app-debug.apk`: Success. Pixel 9 Pro Fold serial `4A221FDKD001JR`, process 29647.

- 19:30:31 AUTO/FINGERPRINT auth: backend=100, placement=SIDE, owner=SYSTEM, evidence=integration-framework-override, doNotShowDialog=true. UI tree shows system fingerprint prompt. System Cancel produced one observed onCanceled at 19:30:48.715 and onUIClosed at 19:30:48.738, returning to scenario list.
- 19:31:05 AUTO/VOICE auth: backend=329184405, owner=COMPAT despite SYSTEM override, doNotShowDialog=false, onUIOpened. Back produced onCanceled at 19:31:08.116 and onUIClosed at 19:31:08.120. This proves routing/open/close, not voice authentication success or full visual layout.
- 19:31:30 AUTO/FINGERPRINT reopened with SYSTEM owner. User confirmed only the system dialog is visible. Log: 19:31:50.106 onSucceeded with BIOMETRIC_FINGERPRINT, reason=null, cryptoSecurityLevel=NONE; 19:31:50.111 onUIClosed. This is non-crypto fingerprint success on the explicit SYSTEM fixture.

No fatal exception observed in the filtered process logs. UNKNOWN baseline, lockout, mixed/enrollment transitions, Samsung/Bubble/Split-Screen remain unverified on this APK. Earlier no-device-QA statements below describe the implementation gate before this follow-up.

Implemented in `biometric`; status remains OPEN / DEVICE VERIFICATION REQUIRED.

`AuthenticationUiOwner` separates SYSTEM / COMPAT / UNKNOWN from fingerprint placement. UNKNOWN renders compat UI. Side-mounted device metadata does not prove that FingerprintService redirects to BiometricPrompt. Existing UDFPS compatibility remains explicitly labeled historical evidence. Missing-system-UI exceptions win over SYSTEM overrides. Mixed requests retain compat rather than suppress software UI based on the highest-priority hardware module.

`Builder.setFrameworkFingerprintUiOwner(AuthenticationUiOwner.SYSTEM)` is an integration assertion for a verified single API23FingerprintModule request. It does not affect OEM/Soter backends, software requests, silent authentication or API28 prompt routing. COMPAT explicitly retains the framework fallback; UNKNOWN restores automatic compatibility policy. No model/API37 whitelist or focus/timer detection was added. Authentication modality and cryptographic acceptance rules are unchanged.

The decision is snapshotted before host UI observers for each flow. A system-owned legacy session excludes alternative module tags so that selection changes cannot silently start a backend requiring compat controls/preview. If the selected backend is no longer eligible, normal failure handling applies. Layout callbacks do not refresh ownership or restart authentication.

## Runtime evidence limits

AOSP FingerprintService has a legacy-to-prompt path for UDFPS/side sensors. This is evidence for that implementation, not proof for every OEM. The platform sensor-properties accessor is hidden and guarded by USE_BIOMETRIC_INTERNAL; reflection does not provide a portable unprivileged ownership query. No privileged probes, permission bypass, global learned UI cache or fingerprint success inference were introduced.

- [AOSP FingerprintService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/biometrics/sensors/fingerprint/FingerprintService.java)
- [AOSP FingerprintManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/fingerprint/FingerprintManager.java)

The ordinary default side-fingerprint route therefore remains UNKNOWN. This patch supplies the explicit design and integration escape hatch; it does not claim an automatic universal fix or current Pixel device proof. New APK was not assembled or installed. Next device gate: explicitly configured SYSTEM framework route, cancel/success/lockout, then UNKNOWN baseline and software/mixed flow retention. Samsung/Bubble/Split-Screen and enrollment transitions remain runtime-unverified.

## Changed files

- biometric/src/main/java/dev/skomlach/biometric/compat/AuthenticationUiOwner.kt
- biometric/src/main/java/dev/skomlach/biometric/compat/impl/AuthenticationUiPolicy.kt
- biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt
- biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt
- biometric/src/test/java/dev/skomlach/biometric/compat/impl/AuthenticationUiPolicyTest.kt

## Verification

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.AuthenticationUiPolicyTest" --tests "dev.skomlach.biometric.compat.impl.LegacyAuthStartDelayTest" --console=plain
```

PASS, 8s; 9 ownership policy tests and 2 existing start-delay tests. Scoped `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- <changed source/test paths>` passed. No full suite, app assemble, release/R8, device QA, commit or push. Tests validate decisions; they do not simulate complete backend/lifecycle transitions.
