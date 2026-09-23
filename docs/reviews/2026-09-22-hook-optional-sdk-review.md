# Hook result and optional SDK consumer follow-up

## Scope

User approved fixes for finding 3 and finding 5, and static severity assessment of the other
supplied claims. They explicitly clarified that compiling the adapters may require local official
vendor SDKs; only the consumer must build without the runtimes. No stubs, SDK redistribution,
reflection bridge, credential changes, staging or publication are authorized by this change.

## Changes

- `biometric`: append `AuthenticationFailureReason.HOOK_DETECTED`. The existing detected-hook /
  fast-success heuristic now rejects through `onFailed(AuthenticationResult)` before enrollment
  confirmation, crypto use or outer success dispatch. The terminal completion path performs cleanup
  and does not enter credential fallback. Rejected results contain no crypto object/security level.
  The callback base method no longer throws. Its historical `@Throws` signature remains for Java
  source compatibility. Detection semantics are not bypassed for debuggers; this is graceful rejection,
  not authorization to authenticate a hooked process. Timing now uses monotonic uptime.
- `biometric-zkfinger`: vendor JARs are compile-only; SOs are excluded from every library variant. Only sample
  Debug explicitly packages the runtimes. Direct adapter construction resolves required class
  literals inside the LinkageError boundary: lazy JVM linking previously let an absent SDK appear
  available until its first method call. No reflective lookup or invocation is added.
- `biometric-ktx`: core dependency becomes `api`; shared POM generation exports API dependencies
  once, preserving existing compile scope. Three source files move from a dotted directory into
  the conventional package tree without changing their Kotlin package/API.
- Publication script: Dokka 2.2.0 generates real HTML API pages in the javadoc-classified JAR.
  Module README/notices remain supplementary. The contract check now requires `index.html`.
- Sample app: non-minified `sdkAbsent` consumer variant plus a no-upload verification init script
  checking assembly, absence of vendor runtime dependencies, KTX core POM entry and generated docs.

Changed files: `biometric/{AuthenticationFailureReason,BiometricPromptCompat,HookDetectionResult}`
and `HookDetectionResultTest` under their existing source/test packages; KTX build file and three
moved auth sources; ZK build file/README/`ZkFingerSdkBridge`/its test; root and app build files;
`scripts/publish-mavencentral.gradle`, `scripts/tests/verify-publishing.init.gradle`, new
`scripts/tests/verify-optional-consumer.init.gradle`, both publishing README files, and this report.

## Verification

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*HookDetectionResultTest' --tests '*AuthFlowCompletionTest' :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerSdkBridgeTest' :biometric-sherpa-onnx:testDebugUnitTest --tests '*SherpaOnnxVoiceEngineTest' --console=plain
```

Initial result: core 8 and Sherpa 3 tests passed. ZK's new real-factory test failed because the
adapter was non-null without vendor classes; its existing simulated linkage-error test passed.
After the class-linking fix:

```powershell
.\gradlew.bat :biometric-zkfinger:testDebugUnitTest --tests '*ZkFingerSdkBridgeTest' --offline --console=plain
```

Passed 2/2. Total final focused results: 13 tests, zero failures/errors. Core/Sherpa were not rerun
because the subsequent change only affected ZK. Hook tests cover positive controls, rejection at
the timing boundary, empty results and stripping an actual Cipher wrapper. No debugger/device
reproduction was run. Caller wiring and terminal-before-success placement were statically reviewed.

Consumer/artifact gate:

```powershell
.\gradlew.bat -I scripts/tests/verify-optional-consumer.init.gradle verifyOptionalConsumerContract --no-configure-on-demand --offline --console=plain
```

Passed: SDK-absent APK assembled, vendor runtime artifacts absent from its selected Android class
artifact view, and KTX core dependency present exactly once with compile scope. Manual ZIP inspection
also found no ZK/Sherpa vendor native libraries in the APK. The gate initially failed on ambiguous
AGP artifact types in its own inspection code; selecting `android-classes-jar` fixed the gate.

Documentation is **not verified**. Before separating the consumer and documentation tasks, these
combined commands were attempted:

```powershell
.\gradlew.bat -I scripts/tests/verify-optional-consumer.init.gradle verifyOptionalConsumerContract --no-configure-on-demand --console=plain
.\gradlew.bat -I scripts/tests/verify-optional-consumer.init.gradle verifyOptionalConsumerContract --no-configure-on-demand --console=plain '-Dorg.gradle.internal.http.socketTimeout=30000' '-Dorg.gradle.internal.http.connectionTimeout=30000'
```

Both were canceled during Dokka dependency downloads after APK assembly/POM generation. The latter
was retried with elevated network access; a read-only thread dump still showed SSL reads inside
Gradle's DownloadAction before generation. An intermediate invocation with unquoted `-D` arguments
failed at task selection because PowerShell split those arguments; no verification tasks ran in it.
No complete Dokka JAR/API-page check passed. Documentation now has a separate repeatable gate:
`verifyKtxDocumentationContract` using the same init script, without `--offline` on its first run.

Dokka task dependencies compiled release classpaths for core/common/API/KTX. No release APK,
R8, Maven signing or upload task ran; the SDK-absent sample uses Debug signing. Full publication
validation remains outstanding until documentation generation succeeds.

Scoped `git diff --check` passed. The three KTX moves retain identical source content/packages.

## Severity of the other supplied claims

- **1: secrets** — P0/compromise is not demonstrated. Named private-key patterns and local.properties
  are absent from tracked paths and the inspected local Git history. Values were not inspected.
  Public GPG keys are not secrets. Ignore rules do not cover arbitrary ASC filenames or every nested
  private-key location: P2 hardening; actual exposure of active signing/publishing credentials would
  require urgent containment/rotation (P0/P1 depending on reach and impact).
- **2: networking** — translation text is transmitted to Google endpoints and logged, so privacy
  review is P1 if sensitive caller content reaches that API. Unofficial endpoint stability is a
  separate risk. User-Agent customization alone does not prove a ToS violation; runtime JSON is
  data, not proof of prohibited executable-code loading or an automatic Play ban. No legal clearance
  or complete data-flow privacy audit is claimed.
- **4: manifest** — CAMERA and POST_NOTIFICATIONS declarations propagate, but declarations are not
  runtime grants. Permission minimization is P2. The exported receiver checks actions/reboot evidence
  for OEM boot broadcasts; no attacker-triggered lockout bypass was proven. Exported broadcast paths
  still merit a dedicated API/OEM check before calling the surface safe.
- **6: models** — MobileFaceNet's recorded provenance remains unresolved: P1 release gate, not a
  runtime P0 exploit. FaceAntiSpoofing already has an MIT notice and a previous documented matching
  artifact check; the claim that neither model has documentation is inaccurate. No new model hash
  verification or legal opinion was performed.

References consulted: https://support.google.com/googleplay/android-developer/answer/16559646
and https://policies.google.com/terms/embedded?hl=en-GB . Dokka integration follows
https://kotlinlang.org/docs/dokka-gradle.html . These do not establish blanket compliance.

Full suites, release/R8 APKs, actual Android debugger attach, absent-ABI JNI, USB/camera/microphone
device tests, credential validity, remote history and publication are not verified by this task.

## Staging and strict follow-up review

The follow-up request authorized staging the task files and fixing review findings, but not
committing or publishing. The initial index was empty. Exactly 20 task paths were staged;
unrelated APKs, vendor artifacts, keys, IDE files and pre-existing reports were excluded.
During review, external commits `30b931b3` and `3663e661` consumed the staged changes and the
subsequent fixes. The assistant did not create those commits or change their history.

Reviewed the task diff and direct dependencies: success/failure callback routing, enrollment
completion and cleanup, optional SDK factories and consumer rules, KTX API/source moves,
POM dependency export, sample variant isolation, documentation packaging and verification scripts.

Fixed findings:

- **P2, inconsistent terminal result:** the permission-description wrapper rechecked the mutable
  hook flag after the flow owner had accepted success and cleanup could have committed enrollment.
  It could report failure after committed enrollment. The owner now decides exactly once before
  completion; the outer wrapper only forwards. The separate system-enrollment terminal path
  snapshots its decision before releasing the flow. Detection policy and crypto rejection remain.
- **Verification gap:** the SDK-absent gate inspected dependency artifacts but could miss vendor
  SOs copied through `jniLibs`. It now inspects the actual APK using SO names from the official local
  SDKs. Artifact checks no longer depend on one exact Sherpa version. No actual SO leak was found.
- **Verification safety:** the no-signing promise is now enforced by rejecting Gradle `Sign` tasks
  as well as upload/transfer tasks. This does not prohibit Debug signing of the sample APK.

Verification commands executed after the logical edit batch:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*HookDetectionResultTest' --tests '*AuthFlowCompletionTest' -I scripts/tests/verify-optional-consumer.init.gradle verifyOptionalConsumerContract --no-configure-on-demand --offline --console=plain
.\gradlew.bat -I scripts/tests/verify-optional-consumer.init.gradle :biometric-ktx:signReleasePublication --dry-run --offline --console=plain
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check
```

The first passed: 10 focused tests (5 hook-decision/completion-composition tests and 5 completion
tests), rebuilt SDK-absent APK, vendor dependency/native absence and KTX POM checks. The composition
tests exercise decision snapshots and cleanup ordering, not Android callback wiring on a device.
The second intentionally exited 1 with `Maven signing and remote publication forbidden by consumer
verification` before any task execution: successful negative-control evidence, not a signing run.
The diff check passed. No new blocking code defect remained in the reviewed scope after fixes.

Dokka, full suites, R8/minification, actual debugger attach and hardware/JNI device QA were not
rerun. Existing Dokka verification and MobileFaceNet provenance publication gates remain open.
