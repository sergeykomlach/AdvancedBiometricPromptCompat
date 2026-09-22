# Three P1 review fixes — 2026-09-21

## Scope

The user's request to close three issues was scoped to the three P1 findings from the latest
review. The three P2 findings (voice payload/identity read consistency, native startup waiting
for software preparation, and the TFFace immutable full-frame bitmap alias) remain deferred.

Changed production modules: `:biometric` and `:biometric-zkfinger`, Debug verification.
`:app` is the direct integration consumer; other software providers depend on the core.
No dependencies, manifests, resources, vendor artifacts, public manager signatures, licenses
or R8 rules were changed in this iteration. No staging, commit, push or publication.

## Fixes

1. Initial preparation and FAILED-provider fallback now both require readable enrollment and
   lockout state. An `Unavailable` snapshot or a thrown read failure cannot authorize a software
   namespace change. An empty `Available` snapshot remains valid; legacy `Unsupported` keeps
   its existing compatibility behavior. READY pinning is unchanged.
2. A whole-setup `EnrollmentRollbackSession` owns every started software provider. API28,
   generic and silent launch paths register ownership before dispatching to LegacyBiometric.
   Cleanup cancels engines first, then rolls back all recorded provisional tags if the outer
   setup did not succeed, regardless of whether the success callback reached confirmation
   bookkeeping. Repeated stages reuse their scope; a finished transaction cannot accept stages.
3. ZKFinger has one process-wide operation owner, established before native work is queued.
   Remove/replacement/cancel invalidates commits and pending callback delivery, including
   sessions started by another manager. Removal orders persistence and queued native cleanup
   before a later operation can start. Native cleanup stops capture without discarding its
   legitimate pending terminal result; destination-thread delivery claims that result once.
   Independent review caught a redundant CancellationSignal check after that terminal claim:
   cancellation in between could suppress both terminal outcomes. Removed the second check;
   the delivery session is now the single success-versus-cancellation arbiter.

## Changed files

Paths relative to this checkout; pre-existing dirty changes in these files were preserved.

### biometric

- `src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- `src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt`
- `src/main/java/dev/skomlach/biometric/compat/engine/internal/EnrollmentRollbackSession.kt` (new)
- `src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt`
- `src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt`
- `src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptSilentImpl.kt`
- `src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistryTest.kt`
- `src/test/java/dev/skomlach/biometric/compat/engine/internal/EnrollmentRollbackSessionTest.kt` (new)

### biometric-zkfinger

- `src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerCaptureSession.kt`
- `src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerUnlockManager.kt`
- `src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerOperationOwner.kt` (new)
- `src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerOperationOwnerTest.kt` (new)

Also added this report. The two previously staged THIRD_PARTY_NOTICES.md files are unchanged.

## Verification commands and results

Test-first registry command, executed twice:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricPromptRegistryTest' --console=plain
```

- First attempt: test fixture compilation failed because its property getter collided with
  `getEnrollmentSnapshot()`. Renamed the fixture property; no production changes at this point.
- Second attempt: 23 tests executed, exactly the three new enrollment-storage regressions
  failed assertions. This reproduces the existing preparation/fallback bug before the fix.

Test-first coordinator API gate:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*EnrollmentRollbackSessionTest' :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerOperationOwnerTest' --continue --console=plain
```

- Failed test compilation because the new transaction/operation ownership APIs did not yet
  exist. This is compile-red evidence only, not a behavioral reproduction of the old UI/JNI path.

Post-implementation gate:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwareBiometricPromptRegistryTest' --tests '*EnrollmentRollbackScopeTest' --tests '*EnrollmentRollbackSessionTest' --tests '*EnrollOutcomeResolverTest' --tests '*Api28EnrollmentCompletionTest' --tests '*AuthFlowCompletionTest' :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerCaptureSessionTest' --tests '*ZkFingerOperationOwnerTest' --continue --console=plain
```

- **BUILD SUCCESSFUL**, including both changed modules' production Debug Kotlin compilation.
- JUnit XML: **63 tests, zero failures/errors/skips**: core 52, ZKFinger 11.
- Existing Gradle/Android deprecation and Kotlin warnings remain; not a warning-free build.
- Tests execute production policy/ownership components, real callback queues and an in-flight
  work interleaving with latches. They do not instantiate the complete Android Builder/dialog
  or call vendor JNI. Caller registration in all three prompt implementations was source-reviewed.

Final ZK-only gate after the review follow-up above:

```powershell
.\gradlew.bat :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerCaptureSessionTest' --tests '*ZkFingerOperationOwnerTest' --console=plain
```

- **BUILD SUCCESSFUL**; the 11 ZK tests passed again, with zero failures/errors/skips.
- Core inputs were unchanged after their passing gate, so its 52 tests were not rerun.
- There are 63 distinct selected tests, not 74. The final manager callback correction was
  source-reviewed and compiled; these tests do not directly instantiate the Android manager.
- The receiving-code-review, test-driven-development, verification-before-completion and
  requesting-code-review skills guided evidence checking, tests-first work and independent
  review. The latter caught the additional callback-loss race before handoff.

Scoped tracked diff check:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric biometric-zkfinger
```

- Passed. New source/test files were inspected separately since `git diff` omits untracked files.

## Not executed / remaining release risks

- No full suite, APK assembly, release/minified/R8 consumer build, emulator or device QA.
- No USB scanner/native timing, protected Android storage failure injection, camera/microphone
  capture, or full staged/generic/silent Android UI run.
- The three deferred P2 findings, optional-SDK/R8 runtime matrix, artifact notice packaging and
  existing licensing/model-provenance gates remain open. Closing these three P1 code findings
  is not a declaration that the whole library is ready for stable publication.
