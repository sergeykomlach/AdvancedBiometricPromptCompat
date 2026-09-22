# Production review fixes — 2026-09-21

## Scope and outcome

The user approved fixing the production review findings, explicitly leaving Behavior as an MVP.
This batch addresses the nine other findings in :biometric, :biometric-zkfinger,
:biometric-custom-face-tf, :biometric-custom-voice and :biometric-sherpa-onnx.
Base checkout: main, HEAD b29c841f8923827b1d7a15d36155c592ea2e1794, with pre-existing dirty work.
No Behavior, license, SDK binary, dependency, manifest, resource, publishing or Git-state changes.

Implemented source fixes; **not a production/release clearance**.
Final focused verification: **76 tests, 0 failures, 0 errors**; Debug compilation of the five
libraries succeeded as prerequisites. Independent read-only reviewers rechecked voice/Sherpa,
face/ZK and native/UI wiring. Material issues discovered during that review were corrected.

## Fixed boundaries

1. ZKFinger vendor template/error/exception and USB permission callbacks bind to the originating
   capture session at registration. A late vendor event cannot look up the replacement session.
   Vendor buffers remain copied before enqueue.
2. TFFace uses one serial face worker for start/inference/capture cleanup. Each frame has a
   session and continuity ticket; replacement/cancellation prevents old-frame persistence.
   Global operation ownership changes before enqueue, including between different managers.
   Ownership lasts until terminal delivery, not just camera close; canceled-before-start
   attempts release it without retaining the manager/callback. Terminal errors close capture.
3. RealCameraProvider reports empty detections and acquisition/detection/conversion failures
   as capture discontinuities. They invalidate in-flight inference immediately and reset PAD,
   candidate counters and the challenge on the worker. ImageReader lifetime ownership is retained.
4. VoiceAuth/Sherpa PCM feature extraction, model inference, GMM training and matching run on
   serial background workers. Bounded input arrays are copied before authenticate returns.
   Callbacks use the supplied Handler or the main Handler and check the original operation
   again at delivery.
5. Both voice scorers reject unequal embedding dimensions; training cannot form a truncated
   centroid from incompatible vectors; GMM selection also filters incompatible embeddings.
   Stored templates have engine/model identity metadata.
6. Cancellation and final enrollment/lockout writes share a commit monitor. Heavy inference
   and training stay outside that monitor. remove() cancels/waits for any admitted commit
   before removing templates, so rollback cannot be followed by a late enrollment write.
7. Native UNABLE_TO_PROCESS/NO_SPACE errors terminate the native branch instead of leaving
   a dead prompt pending behind an unexecuted restart predicate. Terminal errors are not
   timestamp-debounced.
8. Native vendor/unknown errors use the same completion policy: independent legacy ANY may
   continue, ALL and enrollment confirmation retain their rules. Explicit cancel stays
   session-wide. Only actual native LOCKOUT creates native lockout; the old synthetic
   retry-exhaustion conversion is limited to legacy.
9. Foreground card text is rebound independently from message id, timeout and animation.
   A retained message is restored after resize/geometry hide clears the TextView.

## Compatibility / migration

- Existing authenticate signatures, VoiceEngine and IFrameProvider contracts remain intact.
  Added optional VoiceTemplateIdentityProvider and CaptureContinuityProvider interfaces.
- Built-in Cepstral and Basic voice engines have different versioned identities.
- Sherpa identity includes the SHA-256 of the actual ONNX asset and a waveform pipeline version.
  It is not merely a filename or embedding dimension.
- **Voice templates without matching identity are not usable for authentication or reported as
  current enrollments. Re-enrollment is required.** They are not automatically deleted.
  Explicit re-enrollment under the same tag replaces incompatible data; explicit removal works.
- Custom voice engines must implement their module's VoiceTemplateIdentityProvider, returning
  a stable identity that changes with model/preprocessing/embedding-space changes. An absent
  identity fails closed. This is a deliberate runtime migration requirement.
- Sherpa remains independent of VoiceAuth and its existing higher provider priority is preserved.
- Applications using direct managers must consume asynchronous callbacks. Framework module
  wiring already uses asynchronous callback contracts.

## Changed file inventory

Paths below are relative to the repository. Pre-existing dirty files not listed here were not
edited by this batch.

### biometric

Under src/main/java/dev/skomlach/biometric/compat/:

- custom/SoftwareBiometricWorkSession.kt (new)
- custom/SoftwareBiometricWorkerCallback.kt (new)
- impl/NativePromptError.kt (new)
- impl/BiometricPromptApi28Impl.kt
- utils/activityView/ForegroundFeedbackText.kt (new)
- utils/activityView/ForegroundFeedbackView.kt (pre-existing untracked file, modified)

Under src/test/java/dev/skomlach/biometric/compat/:

- custom/SoftwareBiometricWorkSessionTest.kt (new)
- custom/SoftwareBiometricWorkerCallbackTest.kt (new)
- impl/NativePromptErrorTest.kt (new)
- utils/activityView/ForegroundFeedbackTextTest.kt (new)

### biometric-custom-face-tf

Under src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/:

- FaceCaptureSession.kt, FaceSessionOwner.kt (new)
- TensorFlowFaceUnlockManager.kt
- TFLiteObjectDetectionAPIModel.kt
- provider/CaptureContinuityProvider.kt (new)
- provider/RealCameraProvider.kt

Under the matching src/test/java package:

- FaceCaptureSessionTest.kt, FaceSessionOwnerTest.kt (new)

### biometric-custom-voice

Under src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/:

- BasicVoiceEngine.kt, CepstralVoiceEngine.kt
- VoiceBiometricManager.kt, VoiceEngine.kt, VoiceScorer.kt
- VoiceTemplateMatcher.kt, VoiceTemplateStore.kt

Under the matching src/test/java package:

- VoiceTemplateCompatibilityTest.kt (new)
- VoiceBiometricManagerFlowTest.kt (now exercises the production callback dispatcher)

### biometric-sherpa-onnx

Under src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/:

- SherpaOnnxBiometricManager.kt, SherpaOnnxVoiceEngine.kt
- VoiceEngine.kt, VoiceScorer.kt, VoiceTemplateMatcher.kt, VoiceTemplateStore.kt

Under the matching src/test/java package:

- VoiceTemplateCompatibilityTest.kt (new)

### biometric-zkfinger

Under src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/:

- ZkFingerCaptureSession.kt, ZkFingerUnlockManager.kt

Under the matching src/test/java package:

- ZkFingerCaptureSessionTest.kt

Documentation: this report and an appended ledger entry in
docs/superpowers/plans/2026-09-21-biometric-hardening-roadmap.md.

## Final verification

One Gradle process, selected Debug unit-test classes only:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*NativePromptErrorTest' --tests '*Api28EnrollmentCompletionTest' --tests '*ForegroundFeedback*Test' --tests '*SoftwareBiometricWorkSessionTest' --tests '*SoftwareBiometricWorkerCallbackTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCaptureSessionTest' --tests '*FaceSessionOwnerTest' --tests '*FaceAntiSpoofingWindowTest' --tests '*FrameResourceLifetimeTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateCompatibilityTest' --tests '*VoiceTemplateMatcherTest' --tests '*VoiceTemplateMergeTest' --tests '*VoiceBiometricManagerFlowTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateCompatibilityTest' --tests '*SherpaOnnxVoiceEngineTest' --tests '*SherpaOnnxProviderPriorityTest' :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerCaptureSessionTest' --console=plain
```

Result: BUILD SUCCESSFUL in 12s; 118 actionable tasks (19 executed, 99 up-to-date).
JUnit XML results: biometric 37, face 17, VoiceAuth 11, Sherpa 7, ZKFinger 4.
A subsequent edit only removed an extra blank EOF line from the Sherpa manager; no behavioral
inputs changed and no redundant build was run for formatting.

Scoped whitespace gate:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric biometric-zkfinger biometric-custom-face-tf biometric-custom-voice biometric-sherpa-onnx
```

Passed. An earlier check caught the extra EOF blank line, which was removed.

## Earlier focused checks and red/green evidence

Compile-red runs below establish missing testable APIs, not a reproduced runtime exploit.
The unequal-dimension voice cases did fail as behavioral assertions before the fixes.
Passing gates were repeated only after relevant implementation or dependency inputs changed.

ZK callback binding: new API compile-red, then 4 tests passed after implementation:

```powershell
.\gradlew.bat :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerCaptureSessionTest' --console=plain
```

WorkSession API compile-red:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricWorkSessionTest' --console=plain
```

WorkSession tests and both voice manager Debug compiles passed:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricWorkSessionTest' :biometric-custom-voice:compileDebugKotlin :biometric-sherpa-onnx:compileDebugKotlin --console=plain
```

Both voice modules: 4 expected assertion failures reproduced dimension/training defects:

```powershell
.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateCompatibilityTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateCompatibilityTest' --continue --console=plain
```

New compatibility tests passed; existing VoiceTemplateTrainingTest outlier assertion failed. VoiceAuthenticationCompletionTest selector matched no class; not counted as coverage:

```powershell
.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplate*Test' --tests '*VoiceAuthenticationCompletionTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateCompatibilityTest' --tests '*SherpaOnnxVoiceEngineTest' --console=plain
```

FaceCaptureSession API compile-red:

```powershell
.\gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCaptureSessionTest' --console=plain
```

Face capture, PAD window and frame lifetime tests passed after implementation:

```powershell
.\gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCaptureSessionTest' --tests '*FaceAntiSpoofingWindowTest' --tests '*FrameResourceLifetimeTest' --console=plain
```

Native/text new API compile-red; also corrected test constructor argument primaryText:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*NativePromptErrorTest' --tests '*ForegroundFeedbackTextTest' --console=plain
```

Native/foreground and face focused tests passed after wiring fixes:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*NativePromptErrorTest' --tests '*Api28EnrollmentCompletionTest' --tests '*ForegroundFeedback*Test' --tests '*SoftwareBiometricWorkSessionTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCaptureSessionTest' --tests '*FaceAntiSpoofingWindowTest' --tests '*FrameResourceLifetimeTest' --console=plain
```

Callback enqueue constructor compile-red:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricWorkerCallbackTest' --console=plain
```

Worker/callback, face, voice and Sherpa selected tests passed:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricWorkSessionTest' --tests '*SoftwareBiometricWorkerCallbackTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceCaptureSessionTest' --tests '*FaceAntiSpoofingWindowTest' --tests '*FrameResourceLifetimeTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateCompatibilityTest' --tests '*VoiceTemplateMatcherTest' --tests '*VoiceTemplateMergeTest' --tests '*VoiceBiometricManagerFlowTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateCompatibilityTest' --tests '*SherpaOnnxVoiceEngineTest' --console=plain
```

Owner and synthetic-lockout APIs compile-red:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*NativePromptErrorTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceSessionOwnerTest' --continue --console=plain
```

Owner/delivery/native regression tests passed:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*NativePromptErrorTest' --tests '*SoftwareBiometricWorkerCallbackTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceSessionOwnerTest' --tests '*FaceCaptureSessionTest' --console=plain
```

## Known existing failure and remaining evidence

The broader selected VoiceTemplate*Test run reported
VoiceTemplateTrainingTest.trainVoiceTemplatesFiltersOutlierAndAddsCentroid:
expected 3 templates, actual 4. Static comparison with HEAD confirms the same pre-existing
average-similarity cutoff and fallback: all three sample averages are below 0.70, so ifEmpty
restores the outlier and then adds a centroid. New dimension guards do not affect these
same-size vectors. This training-policy discrepancy was not silently changed or counted
as passing; it remains separate work requiring an explicit enrollment consensus policy.

Not run: full suite, clean build, app/APK assembly, release/R8, device/emulator, physical USB
scanner, camera/PAD attack evaluation, absent-runtime consumer APK, native+legacy animation/
geometry/TalkBack matrix, performance profiling or license audit. No commit or push.
The sample app's Debug build is minified, so assembling it would exceed the approved local gate.

JVM tests cover the production session/owner/dispatcher/policy components; they do not instantiate
the entire Android camera manager/USB SDK or prove OEM behavior. First-use Sherpa identity hashing
can also occur through synchronous enrollment queries; its UI-thread latency is not profiled.
Behavior remains an explicitly deferred MVP. Actual production readiness still requires runtime
QA and resolution of the existing voice training-policy test discrepancy.
