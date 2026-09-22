# SoftwareBiometric hardening — first implementation checkpoint

## Delivered

- SEC-01: explicit PAD pending state, validated finite nonnegative scores, window invalidation on inference failure/reset/candidate switch, final-candidate measurement independent of frame stride, enrollment gate, required PAD by default.
- SEC-02: shared error/failed dispatch gate, retry-entry guard, guarded session timeout, stale success/cancel isolation from a newer timeout/session.
- SEC-03: Face assurance follows challenge configuration; Voice/Sherpa/Behavior expose passive compatibility profiles, not active challenge or trusted PCM provenance. Crypto rejection is retained.
- CORE-01: Legacy registration and prompt resolution consume one pinned runtime selection. Initial unavailable Sherpa falls back to VoiceAuth; later availability changes never swap only the prompt; permanent lockout does not authorize downgrade.

## Changed files / modules

`:biometric`:
- `src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt`
- `src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricSecurityProfile.kt`
- `src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt`
- `src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricCallbackGate.kt`
- `src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt`
- Matching `SoftwareBiometricPromptRegistryTest`, `SoftwareBiometricSecurityPolicyTest`, `SoftwareBiometricCallbackGateTest` under `src/test`.

`:biometric-custom-face-tf`:
- `src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceAntiSpoofingPolicy.kt`
- Adjacent `TensorFlowFaceConfig.kt`, `TensorFlowFacePreflight.kt`, `TensorFlowFaceUnlockManager.kt`.
- `src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceAntiSpoofingPolicyTest.kt`
- New adjacent `FaceAntiSpoofingWindowTest.kt`.

`:biometric-custom-voice`, `:biometric-sherpa-onnx`, `:biometric-custom-behavior`: respective `VoiceBiometricManager.kt`, `SherpaOnnxBiometricManager.kt`, `BehaviorBiometricManager.kt` security profiles only.

Documentation: this report, `docs/software-biometric-security.md`, `docs/superpowers/plans/2026-09-21-biometric-hardening-roadmap.md`.

No SDK binaries, templates, licenses, enrollment data, resources, dependencies or publication scripts were replaced. Existing unrelated untracked files are preserved. Nothing staged, committed or pushed.

## Verification commands and results

All commands run from the repository root, sequentially, with the Gradle daemon and existing caches.

1. Regression RED:
   `./gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests "*FaceAntiSpoofingPolicyTest" --console=plain`
   Result: 8 tests, 2 expected assertion failures (default unavailable PAD and invalid negative score).
2. New window tests before implementation:
   `./gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests "*FaceAntiSpoofing*" --console=plain`
   Result: expected compile-red for missing new window/decision API, not an executed assertion failure.
3. Face GREEN:
   `./gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests "*FaceAntiSpoofing*" --tests "*TensorFlowFacePreflightTest" --tests "*FaceAuthenticationAttemptPolicyTest" --console=plain`
   Result: 26 tests passed, including production Debug compilation.
4. New callback tests before implementation:
   `./gradlew.bat :biometric:testDebugUnitTest --tests "*SoftwareBiometricCallbackGateTest" --console=plain`
   Result: expected compile-red for missing dispatch API.
5. Callback/session/policy gate:
   `./gradlew.bat :biometric:testDebugUnitTest --tests "*SoftwareBiometricCallbackGateTest" --tests "*SoftwareBiometricSessionGuardTest" --tests "*SoftwareBiometricSecurityPolicyTest" --console=plain`
   First implementation run: nullable CancellationSignal compile error; fixed safe calls, inspected the error, reran the changed code. Result: 19 tests passed.
6. New passive-profile test before implementation:
   `./gradlew.bat :biometric:testDebugUnitTest --tests "*SoftwareBiometricSecurityPolicyTest" --console=plain`
   Result: compile-red before the passiveCompatibility factory existed.
7. Combined security verification:
   `./gradlew.bat :biometric:testDebugUnitTest --tests "*SoftwareBiometricCallbackGateTest" --tests "*SoftwareBiometricSessionGuardTest" --tests "*SoftwareBiometricSecurityPolicyTest" :biometric-custom-voice:compileDebugKotlin :biometric-sherpa-onnx:compileDebugKotlin :biometric-custom-behavior:compileDebugKotlin :biometric-custom-face-tf:testDebugUnitTest --tests "*FaceAntiSpoofing*" --tests "*TensorFlowFacePreflightTest" --tests "*FaceAuthenticationAttemptPolicyTest" --console=plain`
   Result: BUILD SUCCESSFUL; 46 focused tests passed, three additional provider modules compiled.
8. New selection tests before implementation:
   `./gradlew.bat :biometric:testDebugUnitTest --tests "*SoftwareBiometricPromptRegistryTest" --console=plain`
   Result: compile-red before the pinned selection type existed.
9. Selection and shared-security GREEN:
   `./gradlew.bat :biometric:testDebugUnitTest --tests "*SoftwareBiometricPromptRegistryTest" --tests "*SoftwareBiometricCallbackGateTest" --tests "*SoftwareBiometricSecurityPolicyTest" --tests "*SoftwareBiometricSessionGuardTest" --console=plain`
   Result: 29 tests passed, including production Debug compilation. Together with the unchanged Face tests: 55 focused tests, zero failures/errors in the inspected XML reports.
10. `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check -- biometric biometric-custom-face-tf biometric-custom-voice biometric-sherpa-onnx biometric-custom-behavior`
    Result: exit 0, no whitespace errors; Git warned about future LF-to-CRLF conversion.

Existing Gradle deprecation, AndroidX CancellationSignal, Kotlin annotation-target and parameter-name warnings remain. No claim of warning-free build or full-suite success.

## Not executed / remaining risks

- No full suite, clean, release/R8, APK assembly, connected tests or physical device QA. The sample's Debug variant is itself minified; it was not assembled under the repository's no-minified-iteration rule. Relevant modules were compiled instead.
- Pure state/policy tests and source wiring review do not certify camera/PAD accuracy or SDK concurrency. OEM sensor behavior, false acceptance/rejection, audio replay and generated speech still need physical evaluation.
- Actual APKs with absent runtime/AAR/SO and ABI combinations were not exercised; provider-selection tests use controlled manager availability.
- Registry selection stays pinned until unload/reset. A newly available Sherpa engine is not silently substituted into existing VoiceAuth registration or enrollment.
- Changing default required PAD can make TFFace unavailable where its model/backend does not work. Compatibility opt-out is documented; system authentication remains an independent route.
- UI/snackbar customization, Soter notice correction/packaging, and legal provenance work remain in the roadmap. No legal clearance or publication permission is asserted.
