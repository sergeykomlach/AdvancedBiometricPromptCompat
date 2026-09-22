# Control review: face enrollment cache and provider revocation

Date: 2026-09-21
Checkout: `C:\Users\skoml\StudioProjects_5\AdvancedBiometricPromptCompat`
Base HEAD: `b29c841f8923827b1d7a15d36155c592ea2e1794` (existing dirty tree preserved).

## Changes

1. TFFace no longer trusts a model-instance enrollment map forever. `FaceTemplateCache`
   reads the protected payload on every snapshot and decodes only when that payload changes.
   A storage read failure propagates instead of returning the old accepted profiles.
   Model register/delete operations serialize their JSON read-modify-write across instances.
   Manager removal revokes the process-wide face operation before editing storage, including
   an operation owned by another manager and a success still waiting on the result queue.
2. `SoftwareBiometricWorkerCallback.onAuthenticationCancelled()` immediately revokes work
   and sends one terminal cancellation on the destination queue. A delivered success wins;
   a success only queued when revocation occurs is dropped. VoiceAuth, Sherpa and TFFace
   use this path for provider removal/replacement and external cancellation. Terminal cleanup
   releases retained callback references only when they belong to that same operation.

The storage namespace/JSON schema and public provider API are unchanged. These guarantees
cover manager operations in one process; this is not a new multi-process storage contract.

## Changed files in this batch

- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricWorkerCallback.kt`
- `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricWorkerCallbackTest.kt`
- `biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricSessionCallbackTest.kt`
- `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceTemplateCache.kt` (new)
- `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceTemplateCacheTest.kt` (new)
- `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceSessionOwner.kt`
- `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceSessionOwnerTest.kt`
- `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TFLiteObjectDetectionAPIModel.kt`
- `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceUnlockManager.kt`
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt`
- `biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/SherpaOnnxBiometricManager.kt`
- This report.

## Verification

Callback behavioral RED:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricWorkerCallbackTest' --console=plain
```

Four tests ran; the new revocation case failed because a commit remained possible after
`onAuthenticationCancelled()`. The original callback implementation inherited a no-op.

Cache behavioral RED after mechanically extracting the existing lazy cache from the model
into the production `FaceTemplateCache` component (before changing its behavior):

```powershell
.\gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceTemplateCacheTest' --console=plain
```

Four tests ran; three failed: externally changed profiles, same-tag replacement, and unreadable
storage continued returning the old cached data. This was assertion failure, not compile-red.
The tests exercise the production cache with an in-memory payload boundary, not native TFLite.

Post-fix focused gate:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricWorkerCallbackTest' --tests '*SoftwareBiometricWorkSessionTest' --tests '*SoftwareBiometricSessionCallbackTest' --tests '*SoftwareBiometricCallbackGateTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceTemplateCacheTest' --tests '*FaceSessionOwnerTest' --tests '*FaceCaptureSessionTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceBiometricManagerFlowTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*SherpaOnnxVoiceEngineTest' --console=plain
```

BUILD SUCCESSFUL. JUnit XML: **39 tests, zero failures/errors/skips**: core 22, TFFace 12,
VoiceAuth 2, Sherpa 3. Production Debug Kotlin compilation passed in all four modules.
Existing Gradle deprecation and TFFace cast/parameter-name warnings remain.

The added callback-chain test connects the real worker callback to the real module session
adapter/gate. The face-owner test verifies that external removal drops queued success and
does not release a replacement when the old cancellation is eventually delivered.
Voice manager flow tests cover the shared callback component, not Android manager execution.

Scoped whitespace gate:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric biometric-custom-face-tf biometric-custom-voice biometric-sherpa-onnx
```

Passed. New/untracked files are inspected separately because `git diff` omits them.

## Limits

No full suite, app/APK assembly, release/minified/R8 build, emulator/device session, or
license audit. No claim of complete Android manager/UI, protected-storage platform or
JNI/OEM timing verification. ZKFinger and Behavior production sources were not changed.
No staging, commit, push or publication. The two pre-existing staged notices remain intact.

TDD and verification-before-completion guided the behavioral regressions and exact evidence
boundaries; requesting-code-review was used for an independent read-only wiring review.
That review reported no confirmed material findings in this fix batch. It checked cache
refresh, global face revocation, terminal arbitration, identity-safe replacement cleanup,
and lock ordering. It did not run tests or add runtime/device evidence.
