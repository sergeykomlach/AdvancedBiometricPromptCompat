# Software provider startup ANR — 2026-09-22

## Evidence and scope

Crashlytics issue `253f0cf21f66f265b382c1351da40ead`, RoboForm 9.9.8.18 (9090818),
2026-09-22 11:38:19 +0300: main is runnable in StringBuilder construction beneath
LegacyBiometric.loadSoftwareModules -> BiometricManagerCompat.loadNonHardwareBiometrics ->
BiometricPromptCompat.startBiometricInit.onBiometricReady, dispatched by the main Handler.

The current source explicitly queued synchronous software discovery/registration on main.
That path includes ServiceLoader iteration, provider/manager construction, availability checks,
module registration and cache invalidation. These may initialize SDKs, query services or access
storage. The sampled StringBuilder frame alone does not identify the slow provider, elapsed time,
or a deadlock. No claim is made that Firebase's separately blocked workers caused this main-thread ANR.

Smallest affected module: `:biometric`, Debug verification. Its software modules and consuming apps
depend on this core module, but no public signature, dependency, resource, manifest or SDK change is made.

## Change

- Run the entire synchronous software loading step on the existing background executor.
- Only after loading and cache invalidation return, post readiness flags and the pending-callback
  drain to main. No early readiness or unconditional finally-completion on unexpected failure.
- Preserve synchronous public load APIs; mark them WorkerThread and document provider/factory
  construction on a worker. Existing bundled constructors use explicit Loopers or defer UI creation.
- Keep provider ordering, Sherpa fallback, storage checks, enrollment identity and authentication
  policy unchanged. The previously reported non-deferred preparation/cancel P2 is separate and unchanged.

Files (relative to repository root):

- biometric/src/main/java/dev/skomlach/biometric/compat/BiometricInitializationDispatch.kt (new)
- biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt
- biometric/src/main/java/dev/skomlach/biometric/compat/BiometricManagerCompat.kt
- biometric/src/main/java/dev/skomlach/biometric/compat/engine/LegacyBiometric.kt
- biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricProvider.kt
- biometric/src/test/java/dev/skomlach/biometric/compat/BiometricInitializationDispatchTest.kt (new)
- this report

## Verification

Tests were written first. Initial test compilation failed because the new dispatcher did not exist.
The old main-queue behavior was then extracted unchanged into the dispatcher and wired into startup.
The following test failed on the assertion that no loader/readiness task should be on main before
worker execution (a reproduced scheduling defect, not a reproduced device ANR):

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*BiometricInitializationDispatchTest' --offline --console=plain
.\gradlew.bat :biometric:testDebugUnitTest --tests '*BiometricInitializationDispatchTest.discoveryRunsOnWorkerBeforeReadinessIsPostedToMain' --offline --console=plain
```

After changing dispatcher ordering to worker-load -> main-ready, this focused gate passed:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*BiometricInitializationDispatchTest' --tests '*SoftwareBiometricPromptRegistryTest' --tests '*SoftwareBiometricServicesTest' --tests '*LegacyModuleSelectionTest' --offline --console=plain
```

BUILD SUCCESSFUL; 33 tests, zero failures/errors/skips (4 dispatch, 23 registry, 3 service discovery,
3 module selection). Production Debug compilation also passed. Existing Kotlin/API/Gradle warnings
remain. The blocking-loader regression uses a real worker and latches; main-queue input can execute
while loading is held, and readiness arrives only after release. Other cases cover empty providers
and unexpected loader failure without falsely publishing readiness.

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric docs/reviews/2026-09-22-software-initialization-anr.md
```

Scoped diff check passed; new files also checked for whitespace/conflict markers. Independent
read-only review of the unstaged patch found no P1/P2 findings in this startup change.

## Limits and handoff

No full suite, APK/app build, R8/release build, device profiling, RoboForm integration, Maven upload
or publication. The consuming app must use the patched library before the incident can be retested.
No universal no-ANR claim: explicit synchronous load/unload calls from main can still wait on
provider work/locks. A hung provider can delay readiness on the worker; this patch does not add
timeouts or force native interruption. External providers must support worker construction.

The 183-file staged baseline from the previous task is preserved. This task's patch/report remain
unstaged; no staging, commit or push was performed.
