# Publishing

The supported complete release entry point is `./gradlew :publishRelease` (`.\gradlew.bat :publishRelease` on Windows).
Root `:publish` is an alias. These are real network operations; run only with release authorization.

The coordinator uploads eight selected library modules, then runs exactly one
`:common:postRelease` request. The transfer depends on all uploads succeeding, including when
Gradle runs with `--continue`. There are no deployment finalizers. A failed upload leaves remote
staging state for operator inspection; there is no automatic cleanup or rollback.

- `:uploadReleaseArtifacts` uploads the batch without transferring it.
- `:module:publish` uploads only that module; it does not transfer the namespace.
- `:common:postRelease` also depends on the complete upload batch; it is not a transfer-only shortcut.
- Generic `publish` excludes experimental `biometric-custom-behavior` and `app`. Explicit low-level
  publishing tasks are not a supported release workflow and can bypass the coordinator.
- `biometric-custom-voice` is part of the standard release module set.
- Do not run concurrent releases for the same Sonatype namespace. The staging API operates on
  remote namespace state, not a transaction isolated by this local task graph.
- A successful transfer request is not proof that Central validation/publication has completed.
  Confirm the deployment status and resulting artifacts in Central separately.

Every library publication attaches sources and a `javadoc`-classified documentation JAR before
signing. The latter contains generated Dokka HTML API reference, documentation/source pointers,
module README and existing third-party notices where available.

## Reproducible build inputs

Use a reviewed source revision containing the version and all new source/test files together.
In particular, a working-tree change that references an untracked helper is not a complete release revision.
Do not include local APKs/AARs, signing material, device dumps or `vendor-sdk/` in that source revision.

Adapter compilation needs these separately supplied files:

| SDK root | Required compile input |
| --- | --- |
| `biometric-sherpa-onnx/vendor-sdk` | `libs/sherpa-onnx-1.13.8.aar` |
| `biometric-zkfinger/vendor-sdk` | `libs/zkandroidcore.jar`, `libs/zkandroidfingerservice.jar`, `libs/zkandroidfpreader.jar` |

Provision the SDKs on the build worker before compiling adapters. Override their locations with
`-PsherpaOnnxSdkDir=<directory>` and `-PzkFingerSdkDir=<directory>` if they are outside the checkout.
The sample and consumer checks use the same roots for optional `assets/` and `jniLibs/` inputs.
They remain compile-only dependencies of the library modules.

`scripts/vendor-sdk-checksums.properties` pins the SDK bytes supplied for this checkout. These are
reproducibility pins, not an independent upstream authenticity verification. Updating an SDK requires
reviewing its identity and changing the corresponding pin together. Missing or changed inputs fail
before adapter compilation and Dokka; configuring unrelated projects does not require these files.

Check provisioned files without compiling:

```powershell
.\gradlew.bat :biometric-sherpa-onnx:verifyVendorSdk :biometric-zkfinger:verifyVendorSdk --console=plain
```

## Local regression checks (no upload)

From the repository root:

```powershell
.\gradlew.bat -I scripts/tests/verify-optional-consumer.init.gradle verifyOptionalConsumerContract --no-configure-on-demand --offline --console=plain
.\gradlew.bat -I scripts/tests/verify-optional-consumer.init.gradle verifyKtxDocumentationContract --no-configure-on-demand --console=plain
```

These focused gates independently verify the non-minified SDK-absent sample/POM and generated
KTX API pages. Adapter compilation still requires official local vendor SDKs. The documentation
gate may download large Dokka dependencies on its first run and compile release classpath
dependencies; it does not assemble a release APK or sign/upload Maven artifacts.

For the complete publishing contract (broader than normal local verification):

```powershell
.\gradlew.bat -I scripts/tests/verify-publishing.init.gradle verifyPublishingContract --no-configure-on-demand --offline --console=plain
pwsh -NoProfile -File scripts/tests/verify-publishing-coordinator.ps1
```

The first checks the actual eight publication models, documentation JAR contents, signing inputs
and dependency graph. It rejects remote upload/transfer tasks before execution. It does not execute
signing, assemble AARs, or verify credentials.

The second applies the production coordinator to an isolated Gradle fixture with fake uploads and
transfer. It verifies complete/generic releases, failed upload with `--continue`, module-only upload,
and exclusion of Behavior. It makes no network calls and uses no repository credentials.
All fixture scenarios enable configuration-on-demand and parallel execution, with the transfer
task registered after the coordinator to exercise production's registration order.

## Maven artifact consumer (explicit release validation)

The existing SDK-absent sample uses project dependencies. The separate project at
`scripts/tests/maven-consumer` consumes Maven coordinates only, using the same dependency versions
as this checkout. An exclusive local repository for `dev.skomlach` prevents fallback to an older
Central or Maven Local publication. It does not include library projects or use dependency substitution.

After release-build validation is authorized, stage the actual release AARs, generated POMs,
sources and Dokka JARs without signing or uploading:

```powershell
.\gradlew.bat -I scripts/tests/stage-maven-consumer.init.gradle stageMavenConsumerRepository --no-configure-on-demand --console=plain
```

This verifies the eight POM dependency sets, matching versions, documentation/source JAR contents,
the core Parcelize runtime dependency, and absence of vendor classes/native payloads in the two adapter AARs. It writes only to
`build/maven-consumer/<version>`. Signing, remote publication and Maven Local publication tasks are
rejected before execution. This gate does build release libraries and generate their documentation;
it is not part of ordinary local iteration and does not validate signatures.

Set `ANDROID_HOME` for the independent consumer, or give it its own ignored `local.properties`
containing only `sdk.dir`. Use the exact staged version and absolute repository path, for example:

```powershell
.\gradlew.bat -p scripts/tests/maven-consumer '-PbiometricVersion=2.4.rc-49' "-PmavenConsumerRepository=$PWD/build/maven-consumer/2.4.rc-49" :app:verifyKtxOnlyDebug :app:verifySdkAbsentDebug --console=plain
```

The KTX-only variant must compile core API usage and a real KTX extension through its transitive POM.
The SDK-absent variant adds all four selected software providers without their vendor runtimes.
Both checks assert the exact set of resolved Maven modules and versions.

Additional explicit validation variants are `verifySherpaOnlyDebug` and `verifySdkPresentDebug`.
They need the separately supplied SDK/model/native inputs described above. Corresponding
`verifyKtxOnlyMinified`, `verifySdkAbsentMinified`, `verifySherpaOnlyMinified` and
`verifySdkPresentMinified` tasks exercise published consumer rules with R8 and no blanket library keep rules.
Run those only as an intentional minified-integration gate.

Install each selected consumer APK on a target device to check that its expected ServiceLoader
providers survive packaging/minification; the activity fails explicitly if the provider count is wrong.
Its button exercises the public KTX prompt entry point. Assembly alone does not prove runtime discovery,
SDK-absent behavior, native ABI compatibility, authentication quality or cancellation. Check those device
scenarios separately, including a supplied runtime with a missing device ABI and denied permissions.

Recorded local release and Maven/R8 artifact validation: [2026-09-26 report](2026-09-26-release-validation.md).
