# Cleanup and pre-staging review — 2026-09-22

## Scope

Reviewed the accumulated SoftwareBiometric changes against HEAD, including new source/test files,
foreground feedback, provider lifetimes, optional SDK boundaries and publishing. Independent
read-only review covered core/UI, VoiceAuth/Sherpa and TFFace/ZKFinger/Behavior. This is not a
fresh full security/licensing audit or device validation.

## Cleanup

- Removed the write-only native-error timestamp and redundant clock reads in BiometricPromptApi28Impl.
- Removed an unused TFFace import, grouped imports, and normalized local getter/listener whitespace.
- Removed redundant Sherpa same-package imports; clarified the required voice identity contract and
  expanded both independent stores' revocation/storage blocks without changing their lock scope.
- Removed obsolete commented POM code and closed the publishing properties input stream with withInputStream.
- Ignored nested build outputs, local Gradle/Kotlin/Python caches and local private signing-key files.
- Corrected stale documentation about ZK reflection, Debug-only dependencies, Sherpa/VoiceAuth coupling
  and Soter attribution. Acquisition/licensing documents remain dated evidence, not new legal clearance.

Runtime cleanup is behavior-preserving; it does not change authentication routing or public APIs.

## Review finding still open

P2: BiometricPromptCompat.checkModulePreparation's non-deferred path calls the public
LegacyBiometric.prepareSoftwareModulesForAuthentication overload, which supplies isActive = { true }.
After cancellation, a late asynchronous preparation result can therefore advance to another provider.
ZKFinger preparation clears pending permission state and can request USB permission; the stale chain
can affect a replacement flow or display permission UI after cancellation. Final authentication
callbacks are guarded, but that does not prevent these intermediate preparation side effects.

Recommended separate behavioral fix: use the existing internal overload with
isActive = { isCurrentAuthFlow(authFlowId) }, then add late-success/error regressions proving the next
provider is not prepared. This was not silently bundled into a behavior-preserving cleanup request.

No additional confirmed P1/P2 findings in the reviewed diff. This does not constitute production
or publishing approval while the finding and previously documented release gates remain open.

## Verification

Executed successfully after the relevant cleanup:

```powershell
.\gradlew.bat -I scripts/tests/verify-publishing.init.gradle verifyPublishingContract --no-configure-on-demand --offline --console=plain
.\gradlew.bat :biometric:compileDebugKotlin :biometric-custom-face-tf:compileDebugKotlin --offline --console=plain
.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :biometric-sherpa-onnx:compileDebugKotlin --offline --console=plain
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --cached --check
```

The publishing check covers eight real publication models, documentation artifacts, signing inputs
and the transfer dependency barrier. It does not execute signing or uploads. Core and TFFace Debug
Kotlin compilation succeeded, followed by successful VoiceAuth/Sherpa Debug compiles; existing API
deprecation/parameter-name and Gradle warnings remain.
ZK formatting-only changes did not require another compile. Earlier runtime regression/fixture test
results are historical evidence, not newly executed tests in this cleanup turn.

Whitespace/conflict and staged-content checks passed. The ignored-path check
confirmed sample private-key names, nested build outputs and local caches are excluded.

Not run: full suites, detekt/lint, app/APK assembly, release/R8, signing/upload, device QA or web/legal
research. Physical sensor timing, native ABI/optional-runtime packaging and OEM foreground geometry
remain outside this verification.

## Staging scope

Stage accumulated modified library sources/resources, their new source/test helpers, consumer rules,
module READMEs, the two third-party notices, publishing scripts/tests/docs, this review and the related
September 20–21 hardening/audit reports and roadmap. Preserve unrelated local files outside the index.
Do not add vendor SDK files, APK/AAR/ZIP archives, signing material, caches, device logs, decompiled
sources, app service configuration or unrelated older reports/tooling settings. No commit or push.

The final candidate contains 183 text files. The index checks reject vendor/build artifacts and
local credentials, and scan the selected staged content for known private-key/API-token markers.
No such matches were found; this targeted scan is not an exhaustive historical secret audit.
