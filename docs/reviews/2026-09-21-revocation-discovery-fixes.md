# Cross-instance revocation and optional provider discovery

## Scope

User-authorized fixes for the two findings from the latest read-only review, followed by
targeted regression checks and an independent read-only review. No license reassessment,
APK/device session, release/R8 build, publication, staging or commit.

Production changes are limited to `biometric`, `biometric-custom-voice`, and
`biometric-sherpa-onnx`. TFFace and ZKFinger are direct consumers of the unchanged public
no-argument work-session API; their session tests were checked separately.

## Changes

- Each voice provider owns a separate process-local work scope shared by its managers and
  public template stores. The scope monitor serializes admission, short commits, terminal
  claims and removal. Expensive model/training work stays outside it.
- Removal revokes pending work across instances, even when persistence subsequently fails.
  Cancellation delivery runs after releasing the scope monitor; late callback binding is
  also handled. Completed/cancelled sessions leave the scope registry.
- Public `save`/`saveAll` participate in the same scope. A write revoked before its commit
  throws `CancellationException`, rather than recreating a removed enrollment.
- Removing a single tag conservatively revokes all pending operations in that provider's
  namespace. The other voice provider is unaffected. This is not cross-process coordination.
- Terminal claims and removal are ordered; a callback that already claimed completion may
  physically finish after concurrent removal. Application callbacks are not run under a
  storage lock to promise stronger ordering.
- Optional service iteration isolates errors in both `hasNext()` and `next()`, as well as
  provider construction/availability. A bounded traversal failure throws instead of returning
  a partial success. Neither the registry runtime cache nor its selection cache retains that
  incomplete discovery. Voice engine selection retains the Cepstral fallback.

## Changed files in this batch

Paths below are relative to the repository root. Existing unrelated changes were preserved.

- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricWorkSession.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricWorkScope.kt` (new)
- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricWorkerCallback.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricServices.kt` (new)
- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt`
- `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricWorkScopeTest.kt` (new)
- `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricServicesTest.kt` (new)
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt`
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceTemplateStore.kt`
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceEngineSelector.kt`
- `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceTemplateStoreIdentityTest.kt`
- `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceEngineSelectorTest.kt`
- `biometric-custom-voice/README.md`
- `biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/SherpaOnnxBiometricManager.kt`
- `biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/VoiceTemplateStore.kt`
- `biometric-sherpa-onnx/src/test/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/VoiceTemplateStoreIdentityTest.kt`
- `biometric-sherpa-onnx/README.md`
- This report.

## Verification

First behavioral RED, after extracting existing no-argument session creation into the store
factory used by each manager (without adding revocation):

```powershell
.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --tests '*VoiceEngineSelectorTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --continue --console=plain
```

17 tests ran: six new failures, covering queued success/late commit, late callback binding,
provider constructor failure and availability linkage failure. Existing cases passed.

Second behavioral RED, after extracting the registry's existing whole-iteration catch into
the shared service collector, before changing its behavior:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricServicesTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest.publicEnrollmentStartedBeforeRemovalCannotRestoreProfile' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest.publicEnrollmentStartedBeforeRemovalCannotRestoreProfile' --continue --console=plain
```

Five new tests failed as expected: real ServiceLoader constructor and descriptor errors,
partial discovery from a stuck iterator, and public enrollment resurrection in both stores.

Post-fix focused gate:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricServicesTest' --tests '*SoftwareBiometricWorkScopeTest' --tests '*SoftwareBiometricWorkSessionTest' --tests '*SoftwareBiometricWorkerCallbackTest' --tests '*SoftwareBiometricSessionCallbackTest' --tests '*SoftwareBiometricPromptRegistryTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --tests '*VoiceEngineSelectorTest' --tests '*VoiceBiometricManagerFlowTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --tests '*SherpaOnnxColdStartTest' --continue --console=plain
```

BUILD SUCCESSFUL: 72 tests, zero failures/errors/skips (core 42, VoiceAuth 14, Sherpa 16).
Production Debug Kotlin compilation passed in all three affected modules.

Direct-dependent session gate:

```powershell
.\gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCaptureSessionTest' --tests '*FaceSessionOwnerTest' :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerCaptureSessionTest' --tests '*ZkFingerOperationOwnerTest' --console=plain
```

BUILD SUCCESSFUL: 19 tests, zero failures/errors/skips (TFFace 8, ZKFinger 11).
Their Debug Kotlin compilation also passed. Existing Gradle deprecation warnings remain.

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric biometric-custom-voice biometric-sherpa-onnx
```

Passed. New/untracked source files were also read directly; Git diff does not include them.

## Control review outcome

After the behavioral RED and implementation/GREEN cycle, an independent read-only reviewer
checked the actual scope/session/callback code, both stores and manager admission points,
service traversal, registry cache handling and voice engine selection. No material blockers
were confirmed in this changed boundary. The main-agent follow-up checked the current source,
lock order, no-argument session consumers, XML test results and scoped diff separately.

Both reported findings are closed at the code level. Current successful targeted evidence:
**91 tests across five modules, zero failures/errors/skips.** This is not an all-library
production certification. The review did not revisit unrelated OEM UI behavior, licensing,
spoof resistance, Behavior MVP, consumer R8 or physical device behavior.

The receiving-code-review, TDD, verification-before-completion and requesting-code-review
skills guided reproductions, isolated checks and independent review. Repository instructions
limited verification to affected tests rather than a root/full-suite build.

## Evidence limits

Tests exercise real store serialization/identity/training, real scope/session/callback logic,
and a real JVM ServiceLoader with temporary descriptors. The persistence boundary is in-memory,
and callback queues are controlled; these are not full Android manager/Handler/device tests.
No full test suite, APK assembly, consumer R8 matrix, JNI/ABI, USB/microphone/camera QA,
cross-process storage test or anti-spoof certification was performed.

The pre-existing staged THIRD_PARTY_NOTICES files remain staged, untouched by this batch.
