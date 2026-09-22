# Preparation cancellation follow-up — 2026-09-22

## Scope and result

Reviewed the unstaged startup ANR patch, non-deferred preparation, the API28 deferred caller,
flow ownership and the directly related provider callback paths. Fixed the known P2:
`BiometricPromptCompat.checkModulePreparation` now passes its flow-owned `isActive` predicate
to the internal LegacyBiometric overload. The public overload and its binary signature remain
unchanged. Delayed completion from an inactive/replaced flow cannot advance preparation to
another module through this chain (including the USB-permission preparation path).

Extracted the existing sequence into an internal, Android-independent helper to exercise
actual transition logic. Preserved provider order, fallback position, skipped-tag selection
and missing-manager behavior. Added once-only completion per attempt and an activity recheck
after `onModuleSkipped`, before fallback lookup. These are defensive hardening: a duplicate
callback from the bundled providers was not demonstrated in production.

Changed files in this follow-up, all within `:biometric` except this report:

- biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt
- biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt
- biometric/src/main/java/dev/skomlach/biometric/compat/engine/SoftwarePreparationSequence.kt (new)
- biometric/src/test/java/dev/skomlach/biometric/compat/engine/SoftwarePreparationSequenceTest.kt (new)
- docs/reviews/2026-09-22-preparation-cancellation-followup.md (new)

The earlier ANR patch/report remain present; their statement that P2 was outstanding describes
the earlier checkpoint and is superseded by this follow-up.

## Verification

Test-first baseline: moved the original transition logic to the helper without its new guards.

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwarePreparationSequenceTest' --offline --console=plain
```

RED: 9 tests ran, 3 assertion failures: cancellation in the skip callback still performed fallback
lookup; duplicate completion advanced twice; success after terminal completion advanced again.
The pre-existing helper activity checks already passed delayed-cancellation cases; the known
P2 was specifically the missing caller predicate, verified by source review and compilation.

After the guard and caller fixes, including a replacement-generation regression:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*SoftwarePreparationSequenceTest' --tests '*AuthFlowGateTest' --tests '*LegacyBiometricCancellationTest' --tests '*DeferredSoftwarePreparationTest' --tests '*PreparationErrorHandlingTest' --offline --console=plain
```

GREEN: BUILD SUCCESSFUL; 34 tests, zero failures/errors/skips (10 sequence, 8 flow gate,
4 Legacy cancellation, 8 deferred preparation, 4 error classification). Debug production and
test compilation passed as dependencies of this gate. Existing deprecation warnings remain.

Final independent read-only review found no additional confirmed P1/P2 or regression in the
startup/preparation changes. Both preparation callers were checked for the correct owner guard.
Scoped whitespace/conflict checks passed for the tracked changes and new helper/test/report.

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt
```

## Limits

No full-suite, APK, release/R8, device or RoboForm integration testing. Helper tests do not
instantiate the Android prompt or prove end-to-end USB permission UI behavior. Already-running
provider work is not forcibly interrupted; this fix rejects subsequent transitions and stale
callbacks. Startup scheduling still needs device profiling with the updated consuming app.
No public API, dependency, resource or manifest changes. No staging, commit or push; the
pre-existing 183-file staged set was not modified by this follow-up.
