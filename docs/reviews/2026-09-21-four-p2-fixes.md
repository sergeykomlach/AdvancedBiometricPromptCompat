# Four P2 follow-up fixes — 2026-09-21

## Scope

Implemented the four findings from the latest read-only review. Earlier dirty/staged work was
preserved. Production edits are in biometric, biometric-custom-voice, biometric-sherpa-onnx and
biometric-custom-face-tf; biometric-zkfinger is a direct consumer of the shared callback fix.
No dependency, resource, manifest, vendor binary, license, publishing or Git-state changes.

## Changes

1. The module authentication callback now inherits a session-aware cancellation adapter.
   Provider-initiated cancellation claims CANCELLED once, removes that attempt's timeout,
   cancels the Core operation, then notifies the listener. Replaced/externally canceled attempts
   cannot send a second cancellation. ZK remove/replacement uses this existing callback entry.
2. Both voice template stores use a namespace-wide companion monitor for payload/identity
   reads, enrollment-status queries, read-modify-write saves and removals. Public store instances
   share the monitor; expensive training stays outside it. Storage format and identities are
   unchanged. This is same-process coordination, not a new multi-process storage guarantee.
3. Ordinary ANY authentication with an actual selected native prompt route can start the native
   prompt before software preparation. Initial readiness and manager preparation form one
   deferred batch with a five-second flow-local deadline. Native success does not wait;
   native failure keeps the optional branch pending only until the batch finishes/expires.
   Timeout does not set the provider to FAILED and cannot authorize an initialization downgrade.
   Actual Sherpa initial failure still uses the existing guarded VoiceAuth fallback.
   Native types are captured before preparation; late software cannot take those types over.
   New software routes recheck permissions, camera privacy, enrollment and lockout before admission.
   Non-admitted software types are disabled in the flow's Builder before fallback-dialog selection,
   so the dialog cannot revive a rejected delegate and suppress another valid software route.
   Late callbacks after timeout/cancel/replacement cannot join the operation or start another stage.
   ALL, enrollment, silent, forced credentials, known missing-system-UI and non-native routes keep
   the strict preparation path. Eligibility is rechecked after permission/sensor preflight; if
   native disappears, strict initial preparation and preflight run again.
4. TFFace crop/alignment cleanup releases only an independently owned intermediate, not a
   borrowed camera frame or the returned scaled bitmap. A scaled result aliasing the original
   camera frame is copied before being returned to a consumer that will recycle it.

## Changed files

Paths relative to this checkout. Pre-existing changes in these files are not attributed to this batch.

### biometric

- src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt
- src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt
- src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricCallbackGate.kt
- src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricSessionCallback.kt (new)
- src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt
- src/main/java/dev/skomlach/biometric/compat/impl/AuthenticationCompletionPolicy.kt
- src/main/java/dev/skomlach/biometric/compat/impl/DeferredSoftwarePreparation.kt (new)
- src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt
- src/test/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricSessionCallbackTest.kt (new)
- src/test/java/dev/skomlach/biometric/compat/impl/DeferredSoftwarePreparationTest.kt (new)

### VoiceAuth and Sherpa (each independent module)

- biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceTemplateStore.kt
- biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceTemplateStoreIdentityTest.kt
- biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/VoiceTemplateStore.kt
- biometric-sherpa-onnx/src/test/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/VoiceTemplateStoreIdentityTest.kt

### TFFace

- biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceCropOwnership.kt (new)
- biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceUnlockManager.kt
- biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceCropOwnershipTest.kt (new)

Also added this report. The two pre-existing staged THIRD_PARTY_NOTICES.md files remain staged;
this task did not stage, commit, push, or publish anything.

## Verification

First tests-first gate:

```powershell
.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' :biometric:testDebugUnitTest --tests '*SoftwareBiometricSessionCallbackTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCropOwnershipTest' --continue --console=plain
```

Both voice modules executed four tests, with the new concurrent re-enrollment case failing:
the old payload was accepted with the new identity. This is behavioral RED evidence.
The new session callback and crop ownership APIs did not exist yet: those failures were
compile-red only, not a full Android/JNI reproduction.

Second tests-first gate:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricSessionCallbackTest' --tests '*DeferredSoftwarePreparationTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCropOwnershipTest' --continue --console=plain
```

VoiceAuth, Sherpa and crop tests passed after the first implementation batch. Core test
compilation failed because the new deferred-preparation APIs had not been implemented yet.
This native-first test-first gate is compile-red only.

Post-implementation gate:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricSessionCallbackTest' --tests '*SoftwareBiometricCallbackGateTest' --tests '*DeferredSoftwarePreparationTest' --tests '*Api28StartAuthPlanTest' --tests '*AuthenticationCompletionPolicyTest' --tests '*Api28EnrollmentCompletionTest' --tests '*AuthFlowGateTest' --tests '*SoftwareBiometricPromptRegistryTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCropOwnershipTest' --continue --console=plain
```

BUILD SUCCESSFUL. JUnit XML: 88 tests, zero failures/errors/skips (core 76, VoiceAuth 4,
Sherpa 4, face 4). Production Debug Kotlin compilation passed for the four changed modules.
Existing deprecation warnings remain; this is not a warning-free or whole-suite result.

Direct ZK consumer gate:

```powershell
.\gradlew.bat :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerCaptureSessionTest' --tests '*ZkFingerOperationOwnerTest' --console=plain
```

BUILD SUCCESSFUL; ZK Debug Kotlin compilation and its 11 selected tests passed, zero
failures/errors/skips. The ZK production sources did not need changes for the callback fix.

Independent review found the fallback dialog could still choose a non-admitted software route.
Added two admission-policy cases, then ran:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*DeferredSoftwarePreparationTest' --console=plain
```

This failed test compilation because the new admission-policy API did not exist (compile-red).
Implemented the policy and wired it to flow-local Builder exclusions before any dialog selection.
The reviewer rechecked this exact edge and reported no remaining material finding in that scope.

Final core-only gate after this correction:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricSessionCallbackTest' --tests '*SoftwareBiometricCallbackGateTest' --tests '*DeferredSoftwarePreparationTest' --tests '*Api28StartAuthPlanTest' --tests '*AuthenticationCompletionPolicyTest' --tests '*Api28EnrollmentCompletionTest' --tests '*AuthFlowGateTest' --tests '*SoftwareBiometricPromptRegistryTest' --console=plain
```

BUILD SUCCESSFUL; core now has 78 selected passing tests. Together with the latest unaffected
provider gates: **101 distinct tests, zero failures/errors/skips** (78 + 4 + 4 + 4 + 11).
The final admission correction only changed native orchestration/policy and its tests; provider
storage/crop/callback inputs did not change, so their passing gates were not repeated.

Scoped whitespace gate:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric biometric-zkfinger biometric-custom-face-tf biometric-custom-voice biometric-sherpa-onnx
```

Passed. New files are checked separately because git diff omits untracked files.

## Evidence limits

The receiving-code-review, test-driven-development and verification-before-completion skills
guided source validation, regression tests and evidence reporting. Requesting-code-review
provided an independent native-first design/wiring review.

Tests exercise production stores, session callback/gate, preparation coordinator, completion
policy and ownership helper. Android persistence is replaced with a test boundary for voice;
bitmap alias lifetimes use stand-in resources. Complete Android manager/dialog, real bitmap
pixel operations, platform Handler/permission UI, USB SDK, JNI and OEM timing are not proven.

No full suite, APK assembly, release/minified/R8 build, emulator/device QA or license audit.
The sample Debug app is minified, so no app assembly was added to these focused local checks.
These fixes are not a complete production/publication clearance.
