# Publishing

The supported complete release entry point is `./gradlew :publishRelease` (`.\gradlew.bat :publishRelease` on Windows).
Root `:publish` is an alias. These are real network operations; run only with release authorization.

The coordinator uploads the eight non-experimental library modules, then runs exactly one
`:common:postRelease` request. The transfer depends on all uploads succeeding, including when
Gradle runs with `--continue`. There are no deployment finalizers. A failed upload leaves remote
staging state for operator inspection; there is no automatic cleanup or rollback.

- `:uploadReleaseArtifacts` uploads the batch without transferring it.
- `:module:publish` uploads only that module; it does not transfer the namespace.
- `:common:postRelease` also depends on the complete upload batch; it is not a transfer-only shortcut.
- Generic `publish` excludes experimental `biometric-custom-behavior` and `app`. Explicit low-level
  publishing tasks are not a supported release workflow and can bypass the coordinator.
- Do not run concurrent releases for the same Sonatype namespace. The staging API operates on
  remote namespace state, not a transaction isolated by this local task graph.
- A successful transfer request is not proof that Central validation/publication has completed.
  Confirm the deployment status and resulting artifacts in Central separately.

Every library publication attaches sources and a `javadoc`-classified documentation JAR before
signing. The latter contains generated Dokka HTML API reference, documentation/source pointers,
module README and existing third-party notices where available.

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
