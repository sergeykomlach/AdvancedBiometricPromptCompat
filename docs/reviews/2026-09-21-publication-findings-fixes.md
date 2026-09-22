# Publication review fixes — 2026-09-21

## Scope

Implemented the four approved code findings, optional-SDK consumer R8 rules, and staging of
the two THIRD_PARTY_NOTICES.md files. No commit, push, publication, APK assembly or device QA.
Unrelated dirty files, SDK artifacts and private keys were preserved and not staged.

Production modules: :biometric, :biometric-custom-voice, :biometric-sherpa-onnx and
:biometric-zkfinger (Debug verification). :app is the direct integration consumer; its minified
build was not run. :biometric-custom-face-tf has only its existing notice staged, no code change.

## Changes

- Every software enrollment now receives an explicit fresh provisional tag, including staged
  ALL and retries. Rollback no longer passes a null/remove-all tag to voice managers.
- Initial NEW/PREPARING software readiness is resolved before the final enrollment/availability
  gate, independently of the preferred provider's stored enrollment. Only confirmed initial
  FAILED can change runtime selection. READY without enrollment does not borrow fallback data;
  lockout or unreadable storage cannot authorize initialization/fallback.
- The prompt refreshes its available-type and route caches after initial preparation. Type caches
  are flow-scoped instead of permanent lazy snapshots. Hardware-only requests skip this phase;
  stale/canceled flow callbacks cannot resume it. Deferred initialization explicitly permits only
  engine loading/checks before permission routing, never capture or permission/UI requests.
- ZK enrollment persistence, success lockout reset and failed-attempt/expired-lockout updates
  use the shared session commit monitor. Cancel can invalidate an in-flight native result;
  expensive native inference remains outside the commit monitor.
- Both independent voice stores add VoiceTemplateStore(engine). Public save/saveAll require a
  prepared, identified engine and persist its consensus-versioned identity. The existing no-arg
  constructor remains for reads/removal/internal managers; unbound public writes now throw
  IllegalStateException before opening preferences. Consumer migration is documented in both READMEs.
- Consumer rules suppress missing classes only in the optional vendor namespaces and retain
  direct SDK adapter classes against inlining/merging. No blanket dontwarn, vendor redistribution,
  reflection bridge or dependency changes were added.
- Corrected the core notice's Soter attribution to BSD-3-Clause using Tencent's upstream LICENSE:
  https://github.com/Tencent/soter/blob/master/LICENSE . Exact binary provenance and full notice
  delivery in published artifacts remain separate release gates; this is not legal clearance.

## Files changed in this iteration

Paths are relative to this checkout. Earlier uncommitted changes in these files were preserved.

### biometric

- src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt
- src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt
- src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricDeferredInitialization.kt
- src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt
- src/main/java/dev/skomlach/biometric/compat/engine/internal/EnrollmentRollbackScope.kt
- src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistryTest.kt
- src/test/java/dev/skomlach/biometric/compat/engine/internal/EnrollmentRollbackScopeTest.kt
- THIRD_PARTY_NOTICES.md (corrected and staged)

### biometric-zkfinger

- src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerCaptureSession.kt
- src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerUnlockManager.kt
- src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerCaptureSessionTest.kt
- proguard-project-biometric-zkfinger.pro

### biometric-custom-voice and biometric-sherpa-onnx

Each module's independent internal voice / sherpaonnx package:

- src/main/java/.../VoiceTemplateStore.kt
- src/test/java/.../VoiceTemplateStoreIdentityTest.kt (new)
- src/test/java/.../VoiceTemplateTrainingTest.kt (updated engine-bound fixture)
- src/test/java/android/util/Base64.java (new, JVM codec test boundary only)
- README.md

Sherpa also: proguard-project-biometric-sherpa-onnx.pro.

Other: biometric-custom-face-tf/THIRD_PARTY_NOTICES.md staged unchanged; this report added.

## Verification

TDD and verification-before-completion were applied within the narrower repository workflow.
No full suite or minified app build was run. One Gradle process at a time, no clean/cache deletion.

1. Test-first compile-red: new tests could not compile because prepareInitial, provisionalEnrollment,
   ZK commit and the injectable engine-bound store constructors did not yet exist. This is API
   compile-red evidence, not a reproduced runtime assertion failure:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricPromptRegistryTest' --tests '*EnrollmentRollbackScopeTest' :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerCaptureSessionTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --continue --console=plain
```

2. Implementation gate: core 42 and ZK 7 tests passed; all four production Debug compiles passed.
   The command failed in the voice suites: two new fixtures per module incorrectly used 2-dimensional
   vectors (the contract requires at least 8), and each existing publicSaveDoesNotReportATagWhenTrainingRejectedTheRecordings
   fixture used the now-unbound no-arg store and hit IllegalStateException before its expected
   training IllegalArgumentException. Corrected fixtures to valid 8-dimensional vectors / an
   identified engine; production thresholds and training assertions were not relaxed:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricPromptRegistryTest' --tests '*EnrollmentRollbackScopeTest' --tests '*AuthFlowGateTest' --tests '*LegacyModuleSelectionTest' --tests '*PreparationErrorHandlingTest' :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerCaptureSessionTest' --tests '*ZkFingerSdkBridgeTest' :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --tests '*VoiceEnrollmentStatusTest' --tests '*VoiceTemplateTrainingTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --tests '*VoiceEnrollmentStatusTest' --tests '*VoiceTemplateTrainingTest' --tests '*SherpaOnnxColdStartTest' --tests '*SherpaOnnxVoiceEngineTest' --continue --console=plain
```

3. Final voice gate: BUILD SUCCESSFUL; VoiceAuth 25 and Sherpa 36 tests passed. Unchanged core/ZK
   passing inputs were not rerun:

```powershell
.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --tests '*VoiceEnrollmentStatusTest' --tests '*VoiceTemplateTrainingTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateStoreIdentityTest' --tests '*VoiceEnrollmentStatusTest' --tests '*VoiceTemplateTrainingTest' --tests '*SherpaOnnxColdStartTest' --tests '*SherpaOnnxVoiceEngineTest' --continue --console=plain
```

Latest selected JUnit XML results: **110 tests, zero failures/errors/skips** across these gates
(42 core + 7 ZK + 25 VoiceAuth + 36 Sherpa), not a full-suite result. Existing Gradle deprecations,
Android API warnings and one test unnecessary-non-null-assertion warning remain.

Scoped diff whitespace check passed (exit 0):

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/EnrollmentRollbackScope.kt biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistryTest.kt biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/EnrollmentRollbackScopeTest.kt biometric-zkfinger biometric-custom-voice biometric-sherpa-onnx
```

Staging required escalation after the sandbox denied .git/index.lock, then succeeded:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat add -- biometric/THIRD_PARTY_NOTICES.md biometric-custom-face-tf/THIRD_PARTY_NOTICES.md
```

## Remaining verification / release gates

- No actual R8/minified consumer, absent-AAR APK, unsupported ABI, USB scanner, microphone,
  camera, native+legacy UI, emulator/device QA, publication or full license audit was run.
- Store tests execute real training/serialization/identity filtering over an in-memory Android
  preferences boundary and JVM Base64 shim, not Android Keystore-backed persistence.
- Registry/rollback/commit tests exercise production policy components. Full Android Builder,
  staged ALL UI orchestration and vendor JNI timing remain integration checks.
- Native preparation still has no watchdog/forced interruption; a hung JNI constructor can
  keep preparation pending. This iteration does not claim to resolve that known limitation.
- Only the two notices are staged. Code/test additions remain unstaged/untracked for review.
- Notices are now in the index, not automatically included in AAR/source-JAR packaging. The
  user requested Git addition; artifact notice delivery and unresolved model/vendor grants remain open.
