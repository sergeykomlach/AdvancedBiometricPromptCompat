# Voice/Sherpa code-only hardening — 2026-09-21

## Scope

Approved follow-up to the production-readiness review. Implemented in
`biometric-custom-voice` and `biometric-sherpa-onnx`, Debug libraries.
Behavior remains deferred as MVP. Licenses, device/emulator QA, APK assembly,
release/R8 and publication are explicitly excluded. Existing unrelated dirty
changes, capture/recording ownership and cancellation-safe commits were preserved.

Review feedback was verified against the current implementation. TDD first
reproduced the outlier/feature-alignment failures; verification-before-completion
was applied using the project's narrow gates, not a full build or suite.

## Changes

### Enrollment consensus and GMM alignment

- Removed the average-similarity rejection followed by unconditional restoration
  of every rejected sample.
- A batch now needs an isolated, pairwise-consistent strict majority using the
  existing 0.70 training similarity threshold. Connected groups containing a
  conflicting pair fail closed. Ties, ambiguous bridging recordings and batches
  with invalid embeddings are rejected.
- Single-sample enrollment remains supported for compatibility. This change does
  not add liveness, speaker identity guarantees, or calibrated FAR/FRR evidence.
- Accepted original recording indices select both embeddings and GMM frame
  batches. Managers no longer discard empty batches and accidentally shift indices.
- Optional frame features may all be absent. Otherwise accepted batches must be
  complete, finite, dimension-consistent and sufficient to train the GMM.
- A rejected batch does not reach the manager's save/reset/success branch.
  Public `VoiceTemplateStore.saveAll()` now throws `IllegalArgumentException`
  instead of returning a tag for an enrollment it did not save.
- Authentication thresholds, provider priorities and lockout policy were not changed.

### Explicit migration / re-enrollment

- Manager `getEnrolls()` and enrollment snapshots enumerate complete stored
  membership, independent of model preparation or compatibility.
- `hasEnrolledBiometric()` means stored membership, not readiness. This keeps a
  migration-only route discoverable so it can return repair guidance.
- New `getEnrollmentStatus()` distinguishes `NOT_ENROLLED`, `ENGINE_NOT_READY`,
  `READY` and `REENROLLMENT_REQUIRED`. READY requires at least one compatible
  tag; other older tags may coexist. Storage failures are not represented as an
  empty enrollment list. The public status query can propagate storage errors;
  the authentication/prompt boundary maps them to unavailable.
- Stored identities now include `voice-consensus-v2:` before the engine identity.
  Earlier profiles, including ones carrying the previous engine-only identity,
  require re-enrollment because old training could have retained outliers.
- No automatic deletion or conversion of existing templates. Re-enrollment under
  the same tag uses the existing identity-aware replacement path; it does not
  merge an old-identity profile into the new one.
- Prompt delegates return localized terminal guidance and a failure description
  before automatic microphone capture for incompatible profiles. Direct manager
  authentication performs the same preflight before embedding extraction.
- Added `biometriccompat_voice_help_reenrollment_required` in all 20 resource
  configurations of each module. Corrupt/unreadable template payloads with
  existing membership also receive re-enrollment guidance after load fails.

### Sherpa preparation

- `PreparingVoiceEngine` is an optional interface; the existing `VoiceEngine`
  JVM interface was not changed. Custom identity getters must be cheap/cached.
- Both managers use the serial worker for `prepareForAuthentication()` and
  deliver its callback on the main handler. Direct authentication also prepares
  the engine on that worker.
- Sherpa uses `PreparedModelIdentity`: explicit SHA-256 preparation, volatile
  cached reads, one digest after successful preparation, no partial publication
  on I/O failure. Concurrent getters do not take the digest monitor.
- Hashing is outside the extractor monitor. The getter no longer opens assets.
- Missing optional runtime / failed preparation fails closed. The existing
  skippable HW_UNAVAILABLE route and Sherpa-above-VoiceAuth priority are retained;
  no new in-flight provider swapping was introduced.
- Sherpa remains standalone: no dependency on the VoiceAuth module, no reflection.
  Native API access remains typed with the existing compile-only runtime boundary.

## Files changed in this iteration

Within `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/`:

- `VoiceBiometricManager.kt`
- `VoicePromptDelegate.kt`
- `VoiceEngine.kt`
- `VoiceTemplateStore.kt`
- `VoiceEnrollmentStatus.kt` (new)

Within `biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/`:

- `SherpaOnnxBiometricManager.kt`
- `SherpaOnnxPromptDelegate.kt`
- `VoiceEngine.kt`
- `VoiceTemplateStore.kt`
- `VoiceEnrollmentStatus.kt` (new)
- `SherpaOnnxVoiceEngine.kt`
- `PreparedModelIdentity.kt` (new)

Tests under the matching `src/test/java/.../internal/voice/` and
`src/test/java/.../internal/sherpaonnx/` packages:

- VoiceAuth `VoiceTemplateTrainingTest.kt` (extended)
- VoiceAuth `VoiceEnrollmentStatusTest.kt` (new)
- Sherpa `VoiceTemplateTrainingTest.kt` (new)
- Sherpa `VoiceEnrollmentStatusTest.kt` (new)
- Sherpa `PreparedModelIdentityTest.kt` (new)

Resources in both modules: `src/main/res/<configuration>/strings.xml` for
`values`, `values-en`, `values-ar`, `values-b+es+419`, `values-de`,
`values-es`, `values-fr`, `values-hi`, `values-id`, `values-it`,
`values-ja`, `values-ko`, `values-nl`, `values-pt-rBR`, `values-ru`,
`values-tr`, `values-uk`, `values-vi`, `values-zh-rCN`, `values-zh-rTW`.
One new message per file; newline-at-EOF normalization only otherwise.

Documentation: this report and
`docs/superpowers/plans/2026-09-21-biometric-hardening-roadmap.md`.
No core, Face, ZK, Behavior, sample-app or build-script edits in this iteration.

## Verification

Behavior-red gate (23 tests, 18 expected assertion failures: nine per module):

```powershell
.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplateTrainingTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplateTrainingTest' --continue --console=plain
```

Final focused gate (BUILD SUCCESSFUL; 67 tests, zero failures/errors/skips,
35 VoiceAuth + 32 Sherpa; both Debug Kotlin compile tasks passed):

```powershell
.\gradlew.bat :biometric-custom-voice:testDebugUnitTest --tests '*VoiceTemplate*Test' --tests '*VoiceEnrollmentStatusTest' --tests '*VoiceBiometricManagerFlowTest' --tests '*GmmVoiceModelTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*VoiceTemplate*Test' --tests '*VoiceEnrollmentStatusTest' --tests '*PreparedModelIdentityTest' --tests '*SherpaOnnxVoiceEngineTest' --console=plain
```

The previously reported VoiceTemplateTrainingTest outlier failure is resolved,
not excluded. Test totals were read from fresh JUnit XML results.

Scoped whitespace gate (passed):

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric-custom-voice/src biometric-sherpa-onnx/src
```

Static checks: parsed all 40 resource XML files and required exactly one nonempty
migration key in each; inspected scoped diffs; compared the two independent
training/status/engine-contract implementations after package/storage-name
normalization. No VoiceAuth dependency or reflection was added to Sherpa.

## Evidence limits / remaining integration risks

- JVM tests cover production training, matching, metadata policies, model cache,
  preparation failure policy and callback-dispatch primitives, not complete
  Android managers or persistent SharedPreferences migration transactions.
- No actual microphone, device, native+legacy overlay, accessibility or timing QA.
  Sending a terminal status is not proof of OEM overlay visibility.
- No absent-runtime consumer APK, packaging/ABI, minification or release proof.
- Native extractor creation can still occur through `isAvailable()`; this
  iteration removes synchronous *hashing*, not every possible cold native-start
  latency. No performance profile was measured.
- Conservative consensus can require users to repeat enrollment. Threshold
  calibration and speaker-spoofing evaluation remain outside this code-only gate.
- Initializing Sherpa without a runtime is covered at the JVM linkage/factory
  boundary, not by a real consumer install. Runtime fallback behavior remains
  device-unverified.
- Host integrations must handle re-enrollment guidance, distinguish saved
  membership from usability, and handle invalid-input exceptions from direct
  public store writes.
- Full suites, clean, app assemble/APK, release/R8, emulator/device tests, license
  audit, commit and push were intentionally not run.
