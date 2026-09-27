# Release artifact validation — 2026-09-26

Checkout: `main`, base commit `cf801622` (`Build refactoring`), plus the changes below.
Candidate coordinates: `dev.skomlach:<module>:2.4.rc-49`. The shared RC version is unchanged.
Licensing/provenance review is outside this validation, as requested.

## Changes and scope

- `biometric-custom-voice/README.md` and `docs/publishing/README.md`: remove Voice's experimental status.
- `biometric/build.gradle` and `gradle/libs.versions.toml`: explicitly export `kotlin-parcelize-runtime:2.3.10`.
- `scripts/tests/stage-maven-consumer.init.gradle`: require that exact Parcelize runtime in the core POM.
- This report records the release artifact checks and their limits.

The dependency change affects `biometric` and its consumers (`biometric-ktx` and all software providers).
Voice's production code is unchanged. Behavior remains excluded from the standard eight-module release.

## Release AARs

All eight release publications built and passed the staging contract. Each contains an AAR, POM,
sources JAR and Dokka/Javadoc JAR. The files are under `build/maven-consumer/2.4.rc-49/dev/skomlach/`.

| Artifact | AAR bytes | SHA-256 |
| --- | ---: | --- |
| common | 2031507 | `e4ffb8a37b828da9220252ed0eb7d8fd1bb122a8db829422b2e401258408920a` |
| biometric-api | 76565 | `0ea41408eabfa162a681511ad55aa4208528d710688f66b805ecbac8b8005d6d` |
| biometric | 1800485 | `fa302cba913eb371709eb21c7c554f9955615dd6c0bc6012496de1738a9798df` |
| biometric-ktx | 66283 | `3da097499d4fab6cdff8e1f1ab62fac2f6bcb0805122d0dfeeb1c0a3d4231168` |
| biometric-custom-face-tf | 8595480 | `16f642e9d8632a600f75cd721e14fb4f5a2f686033978e31ddcf2fde13ec3da3` |
| biometric-custom-voice | 307142 | `929ffac7cdcfd1fc151cce6221cecc6fa31ec2b7e6bd33db7a9df0b2ad8fd324` |
| biometric-sherpa-onnx | 309086 | `6b6dda2febc6bd45d96f281ba89241d2bdbeba0cbe85213c6c1d658ad03560c1` |
| biometric-zkfinger | 126527 | `433f68167a9bf4579a312a4fc9331b66e2ec2ad94b80514529aaf4a0c3728eb5` |

Verified POM coordinates, internal dependency sets, exact versions and absence of vendor SDK coordinates.
Both adapters contain no vendor classes, embedded SDK JARs, native libraries or models.
All AAR/JAR ZIP CRC checks passed, each AAR declares minSdk 23, and each optional provider's
ServiceLoader entry names a class present in its `classes.jar`. Voice, Sherpa, ZK and core consumer
rules are packaged; Face uses the core's shared provider-constructor rule. Face's two TFLite models
are included. `SHA256SUMS.txt` in the local Maven repository records all 32 publication files.

## Commands and outcomes

Commands ran sequentially from the checkout root. No `clean`, cache deletion or dependency refresh.

```powershell
.\gradlew.bat -I scripts/tests/stage-maven-consumer.init.gradle stageMavenConsumerRepository --no-configure-on-demand --console=plain
```

Initial run passed in 1m58s (316 tasks). After the dependency fix, the same release export passed
in 2m15s (316 tasks: 258 executed, 42 from cache, 16 up-to-date), including the new POM assertion.
Final log: `build/release-validation/stage-release-fixed.log`.

```powershell
.\gradlew.bat -p scripts/tests/maven-consumer '-PbiometricVersion=2.4.rc-49' '-PmavenConsumerRepository=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/build/maven-consumer/2.4.rc-49' :app:verifyKtxOnlyDebug :app:verifySdkAbsentMinified :app:verifySherpaOnlyMinified :app:verifySdkPresentMinified --console=plain
```

The first run built KTX-only successfully, then R8 rejected the SDK-absent consumer:
`Missing class kotlinx.parcelize.Parcelize (referenced from: BiometricAuthRequest)`.
The hand-generated POM omitted the Parcelize runtime. The fix adds the dependency to the library's
published contract; no consumer `dontwarn` or blanket keep rule was added.
Initial log: `build/release-validation/maven-consumer.log`.

The post-fix invocation passed in 4m06s, exit 0, 193 tasks executed.
Final log: `build/release-validation/maven-consumer-fixed.log`.

| Consumer | Result | Exact-version Maven modules | Providers in final APK |
| --- | --- | ---: | ---: |
| ktxOnlyDebug | PASS | 3 | 0 |
| sdkAbsentMinified | PASS, R8/resource shrinking | 7 | 4 |
| sherpaOnlyMinified | PASS, R8/resource shrinking | 4 | 1 |
| sdkPresentMinified | PASS, R8/resource shrinking | 7 | 4 |

All library dependencies resolve exclusively from the staged Maven repository, without project
substitution. The KTX-only source uses core API types through its transitive dependency.
`biometric-api` is an OEM compile-time stub artifact and correctly absent from runtime dependencies;
its AAR, sources, documentation and POM were checked separately by the release export.

```powershell
& 'C:\Users\skoml\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' build/release-validation/inspect-artifacts.py
```

Passed after correcting the inspection script's assumption that Face needed a separate rules file.
This was an inspection-script correction; Face's provider rule is supplied transitively by `biometric`.
Output: `build/release-validation/artifact-inventory.json` and the repository's `SHA256SUMS.txt`.
The one-off Python inspector and its JSON output are ignored build artifacts.

```powershell
& 'C:\Users\skoml\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' build/release-validation/inspect-consumers.py
& 'C:\Users\skoml\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' build/release-validation/package-release.py
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --cached --check
```

The APK inspector passed for all four variants: ZIP integrity, provider class definitions in DEX,
public no-argument constructors and correctly adapted ServiceLoader descriptors after R8.
SDK-absent has neither Sherpa/ZK classes nor native runtimes/model; its Face/LiteRT dependencies
still contribute native libraries. Sherpa-only has no VoiceAuth provider, and both SDK-present
variants contain the Sherpa model plus ARM64 Sherpa/ONNX libraries. The full SDK-present variant
also packages ZK classes and all four supplied ZK libraries for ARM64 and ARMv7.
Output: `build/release-validation/consumer-inventory.json`. These are static artifact checks,
not execution of the provider constructors or native libraries.

Packaging passed and rechecked all 32 hashes. The packaged Voice module README has its updated
status. The ZIP contains the eight Maven publications and `SHA256SUMS.txt`, with no consumer APKs,
vendor SDK inputs or credentials. Scoped/staged whitespace checks passed.

Downloadable local bundle: `build/release-validation/AdvancedBiometricPromptCompat-2.4.rc-49.zip`
(26,015,603 bytes).
ZIP SHA-256: `6635d97b4767eada97d5ae6c1c7de5b468e5d044d606c0b81e6fd0bc6a429e27`.

The six changed source/documentation files were staged. Build outputs remain ignored.
The user's unrelated untracked files were preserved.

## Limits

- No device/emulator installation or authentication run: runtime discovery, microphone/USB permissions,
  cancellation, missing-native-ABI behavior and recognition/spoof performance remain runtime-unverified here.
- No full unit suite or full lint was requested or run. Any lint-vital tasks belong to the selected consumer assemblies.
- Existing compiler deprecation and unresolved Dokka-link warnings remain; generated documentation JARs passed validation.
- No Maven artifact signing, Central credential/coordinate validation, upload, commit or push was performed.
- No second-worker reproduction. Local SDK hash checks pin input bytes, not upstream provenance.
