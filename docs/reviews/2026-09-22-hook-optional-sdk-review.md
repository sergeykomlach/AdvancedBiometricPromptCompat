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
