# Sherpa cold-start preparation — 2026-09-21

## Scope and result

Implemented the approved next-step item 1 only: defer native Sherpa initialization
to explicit worker preparation, keep Sherpa preferred while NEW/PREPARING, and
allow VoiceAuth fallback on confirmed initial failure.

Affected production modules: `:biometric-sherpa-onnx` and its `:biometric` routing
contract, Debug. `:biometric-custom-voice` was compiled as the fallback consumer;
its source was not changed. The sample app is a direct integration consumer, but
APK/device/R8 verification remains explicitly excluded. No unrelated dirty changes,
vendor artifacts, licenses, resources, enrollment identities or match thresholds
were changed. No commit or push.

Receiving-code-review verified the actual synchronous call chain and the registry's
previous pin-at-registration behavior before implementation. TDD reproduced the
cold-start and deferred-selection failures, then the lockout boundary cases.
Verification-before-completion used focused project gates only.

## Implementation

- `SherpaOnnxVoiceEngine` stores a lazy runtime factory. Construction and
  `isAvailable()` / identity queries do not initialize the SDK or access model
  assets. Availability is a cached ready/healthy result.
- Explicit `prepare()` performs factory creation, model hashing and native
  extractor creation. The existing manager's serial worker invokes it both from
  `prepareForAuthentication()` and direct authentication.
- Concurrent preparation callers join one initialization under a dedicated lock.
  UI getters do not acquire that lock. Successful and failed initial outcomes are
  cached; inference before preparation fails closed without initializing anything.
- The direct SDK backend calls `ensureExtractor()` only from preparation, not
  availability checks or inference. Typed SDK calls / compileOnly linkage isolation
  are preserved. The context captured by the lazy factory is the application context.
- Optional `SoftwareBiometricDeferredInitialization` reports NEW, PREPARING, READY
  or FAILED. Initial READY/FAILED are terminal. A later inference/runtime error
  can make an engine unhealthy but cannot relabel it as an initial failure.
- Sherpa manager treats NEW/PREPARING as an eligible preparation candidate when
  the microphone feature exists. This is not a ready-to-authenticate claim:
  preparation and the existing authentication guards remain mandatory.
- The registry retains lower-priority standbys for deferred initial selection.
  It uses the existing shared priority comparator. Standby runtimes are registered
  but excluded from Legacy route selection, capability/enrollment queries and
  permission queries until selected.
- Initial failure switches the entire manager/prompt pair to a fallback.
  Legacy prepares that selected fallback before continuing; it does not merely
  skip the failed provider and start an unprepared replacement.
- Once preparation succeeds, selection remains pinned for that registry generation.
  Later runtime failure, temporary/permanent lockout, or unreadable protected
  lockout storage cannot cause silent fallback.
- Lockout protection also applies when failure was already known at discovery or
  when an intermediate standby in a fallback chain failed. NEW/PREPARING alone
  never authorizes fallback.
- Existing explicit all-software maintenance/removal operations still cover all
  registered managers. Native hardware routes are not standby-filtered.

## Integration notes

Direct consumers of `SherpaOnnxVoiceEngine` must call `prepare()` off the UI thread
before expecting `isAvailable()` or extraction to succeed. Manager consumers use
`prepareForAuthentication()`; its callback is delivered on the main handler.
The standard prompt flow already does this.

The optional readiness interface does not add abstract methods to existing manager
or VoiceEngine APIs. Providers that do not opt in retain eager/pinned selection.
Sherpa remains independent of the VoiceAuth module.

## Files changed in this iteration

Production:

- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricDeferredInitialization.kt` — new optional readiness contract.
- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt` — initial selection/fallback and post-ready pinning.
- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricProvider.kt` — runtime selection eligibility.
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt` — standby filtering and replacement preparation.
- `biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/SherpaOnnxVoiceEngine.kt` — lazy factory, cached queries, one-time preparation.
- Adjacent `SherpaOnnxBiometricManager.kt` — readiness-aware hardware candidacy.

Tests:

- `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistryTest.kt`.
- `biometric-sherpa-onnx/src/test/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/SherpaOnnxColdStartTest.kt` — new.
- `biometric-sherpa-onnx/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/sherpaonnx/SherpaOnnxVoiceEngineTest.kt` — explicit preparation in the success fixture.

Documentation: this report and the hardening roadmap ledger.

## Verification commands and outcomes

Initial behavior-red gate: 17 tests, six expected assertion failures
(four cold-start, two deferred-registry cases):

```powershell
.\gradlew.bat :biometric-sherpa-onnx:testDebugUnitTest --tests '*SherpaOnnxColdStartTest' :biometric:testDebugUnitTest --tests '*SoftwareBiometricPromptRegistryTest' --continue --console=plain
```

First green gate: BUILD SUCCESSFUL, 46 selected tests passed, including 26 Sherpa
tests. Sherpa/core Debug Kotlin compilation passed.

```powershell
.\gradlew.bat :biometric-sherpa-onnx:testDebugUnitTest --tests '*SherpaOnnxColdStartTest' --tests '*SherpaOnnxVoiceEngineTest' --tests '*PreparedModelIdentityTest' --tests '*VoiceEnrollmentStatusTest' :biometric:testDebugUnitTest --tests '*SoftwareBiometricPromptRegistryTest' --tests '*LegacyModuleSelectionTest' --tests '*SoftwareModuleStateReaderTest' --tests '*SoftwareModulePreparationPolicyTest' --console=plain
```

The final filter above matched no test class and is not counted as coverage.
The actual preparation-policy test classes were identified and run in the final gate.

Additional behavior-red gate: both newly added early-failure/standby lockout
boundary tests failed as expected before the guard was extended:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricPromptRegistryTest.failureKnownBeforeDiscoveryCannotBypassStoredLockout' --tests '*SoftwareBiometricPromptRegistryTest.fallbackChainCannotSkipAStoredLockoutOnAFailedStandby' --console=plain
```

Final core/fallback gate: BUILD SUCCESSFUL, 29 core tests passed; core and VoiceAuth
Debug compilation passed. Unchanged Sherpa cold-start test inputs were not rerun.

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricPromptRegistryTest' --tests '*LegacyModuleSelectionTest' --tests '*SoftwareModuleStateReaderTest' --tests '*PreparationErrorHandlingTest' --tests '*BackgroundSoftwarePreparationTest' :biometric-custom-voice:compileDebugKotlin --console=plain
```

Combined current evidence: **55 tests passed (29 core + 26 Sherpa)**, zero
failures/errors/skips in their latest JUnit XML results. This is two focused gates,
not a full suite or one combined final invocation. Existing Gradle deprecation and
Legacy callback-parameter/deprecation warnings remain.

Scoped diff check passed:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricProvider.kt biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistryTest.kt biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/SherpaOnnxVoiceEngine.kt biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/SherpaOnnxBiometricManager.kt biometric-sherpa-onnx/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/sherpaonnx/SherpaOnnxVoiceEngineTest.kt
```

New files were also checked for trailing whitespace. Static review traced native
extractor creation to preparation and checked both Legacy selection and prompt
resolution against the same registry choice.

## Not verified / remaining limits

- No APK, device/emulator session, release/R8, full suite, clean, or license audit.
- JVM tests use controlled backend fixtures in the production engine and real
  registry policy. They do not execute the actual native SDK, Android managers,
  microphone capture, complete Legacy callback orchestration, or absent-ABI APK.
- No cold-start timing measurements or OEM UI responsiveness claims.
- No native-call watchdog or forced interruption was added. A hung JNI constructor
  can still keep preparation pending, although availability queries no longer wait
  on it. Cancellation does not forcibly terminate an in-progress native call.
- Full manager/storage/routing integration tests from the separate proposed item 2
  remain future work, as do sample/host migration instructions from item 3.
