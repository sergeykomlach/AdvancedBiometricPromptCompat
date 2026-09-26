# Publication preparation validation — 2026-09-26

Checkout: `main`, base commit `e7ed4a0b`; candidate version `2.4.rc-49`.
This report concerns the staged working-tree candidate, not a published or committed release.
Licensing/provenance work is outside this change.

## Changes

- `app/build.gradle`: isolate VoiceAuth from `sherpaOnly`, explicitly supply the Sherpa AAR, and share SDK roots.
- `build.gradle`: retain the existing rc-49 version update and load the SDK-input configuration.
- `biometric-sherpa-onnx/build.gradle`, `biometric-zkfinger/build.gradle`: defer SDK validation to compile/Dokka tasks.
- `scripts/vendor-sdks.gradle`, `scripts/vendor-sdk-checksums.properties`: configurable SDK roots and pinned input hashes.
- `scripts/tests/verify-optional-consumer.init.gradle`: use the same SDK roots for native-payload inspection.
- `scripts/publish-release.gradle`: describe the selected RC batch without claiming all included modules are stable.
- `scripts/tests/stage-maven-consumer.init.gradle`: unsigned local export and validation of the eight release publication inputs.
- `scripts/tests/maven-consumer/`: independent POM-based consumer with KTX-only, SDK-absent, Sherpa-only and SDK-present variants, plus explicit minified variants.
- `biometric-custom-voice/README.md`, `biometric-sherpa-onnx/README.md`, `biometric-zkfinger/README.md`, `docs/publishing/README.md`: integration status and reproducible validation instructions.
- `common/src/main/java/dev/skomlach/common/protection/A11yDetection.kt`, `AccessibilityServiceIdentity.kt`, and `common/src/test/java/dev/skomlach/common/protection/AccessibilityServiceIdentityTest.kt`: stage the existing accessibility change together with its previously untracked implementation and tests. Their logic was not modified in this publication-preparation task.

Affected production modules: `common`, `biometric-sherpa-onnx`, `biometric-zkfinger`; sample variant `app:sherpaOnly`.
The test consumer targets the eight selected Maven modules. Voice changes are documentation only.

## Commands executed

Run from `C:\Users\skoml\StudioProjects_5\AdvancedBiometricPromptCompat`.

```powershell
.\gradlew.bat :app:verifySherpaOnlyApk :common:testDebugUnitTest --tests dev.skomlach.common.protection.AccessibilityServiceIdentityTest --console=plain
```

Passed, exit 0, 1m24s. Sherpa-only APK verification confirmed the model, ARM64 Sherpa/ONNX runtime,
Sherpa service registration and absence of VoiceAuth registration. Both adapter SDK checksum tasks
executed successfully. AccessibilityServiceIdentityTest was UP-TO-DATE; its matching result XML reports
7 tests, 0 failures, 0 errors, 0 skipped. This was not a forced rerun of unchanged tests.

```powershell
.\gradlew.bat -I scripts/tests/stage-maven-consumer.init.gradle help --no-configure-on-demand -PsherpaOnnxSdkDir=build/missing-sherpa-sdk -PzkFingerSdkDir=build/missing-zk-sdk --offline --console=plain
```

Passed, exit 0: all projects can configure without the optional SDK files. The new staging init script
compiled/configured; no release artifact export was executed.

```powershell
.\gradlew.bat -p scripts/tests/maven-consumer '-PbiometricVersion=2.4.rc-49' '-PmavenConsumerRepository=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/build/maven-consumer/2.4.rc-49' help --offline --console=plain
```

Passed, exit 0: the independent consumer settings and build scripts configured successfully.
No consumer source compilation, dependency resolution or APK assembly is claimed by this check.
An initial invocation with the same property values left unquoted failed because PowerShell split
`-PbiometricVersion=2.4.rc-49` and Gradle interpreted `.4.rc-49` as a task; the command above fixes that invocation.

```powershell
.\gradlew.bat -I scripts/tests/stage-maven-consumer.init.gradle :biometric-sherpa-onnx:verifyVendorSdk '-PsherpaOnnxSdkDir=build/missing-sherpa-sdk' --no-configure-on-demand --offline --console=plain
```

Expected rejection, exit 1: configuration completed, then `verifyVendorSdk` failed with the explicit
`missing vendor SDK` message. Also configured the updated release-module contract assertion.

For the mismatch check, an intentionally invalid text file named `sherpa-onnx-1.13.8.aar` was created
under ignored `build/sdk-validation-mismatch/libs/`; the real SDK was not modified.

```powershell
.\gradlew.bat :biometric-sherpa-onnx:verifyVendorSdk '-PsherpaOnnxSdkDir=build/sdk-validation-mismatch' --offline --console=plain
```

Expected rejection, exit 1: `SHA-256 mismatch` before any compilation.

Final staged whitespace check:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --cached --check
```

## Remaining validation

- Full execution of `stageMavenConsumerRepository`: release AARs, all POMs, sources and Dokka JARs.
- Compilation/assembly and on-device use of the independent Maven consumer variants.
- R8, missing native ABI, SDK-absent runtime behavior, authentication and cancellation on devices.
- Signing, credential validity, Central coordinate availability and remote publication.
- Source-revision reproduction on another worker with the pinned SDK inputs.

These broader gates were intentionally not executed. No commit, push, remote upload or device operation was performed.
The local SHA-256 pins establish reproducible bytes, not independently verified SDK provenance.
