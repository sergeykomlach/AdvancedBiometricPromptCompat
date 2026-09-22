# Direct-binary licence audit — 2026-09-20

Scope: files tracked directly in this repository with extensions `.jar`, `.aar`, `.so`, `.tflite`,
`.onnx` or `.lite`, plus the module build rules that package them. This is an engineering audit,
not a legal opinion and not an SBOM for all transitive Maven dependencies.

This is a dated audit snapshot. Integration wording and the Soter attribution were corrected on
2026-09-22 against current build scripts and THIRD_PARTY_NOTICES.md; no new legal audit, binary
provenance check or public-artifact verification was performed for that documentation cleanup.

## Remediated in this change

`biometric-zkfinger` release packaging excludes ZKTeco JARs and native libraries. It uses a
direct typed adapter with `compileOnly` vendor JARs, kept as Git-ignored local development material.
The current Debug variant includes local vendor runtime files for evaluation, unlike the earlier
reflection-based build; the old Debug AAR inspection is not evidence for today's packaging.

## Attribution added

* TensorFlow Lite and LiteRT are Apache-2.0 runtime dependencies.
* `FaceAntiSpoofing.tflite` byte-matches the asset in the MIT-licensed
  `syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing` repository. Its notice is in
  `biometric-custom-face-tf/THIRD_PARTY_NOTICES.md`.
* The corrected Soter notice identifies BSD-3-Clause upstream terms and separate Apache-2.0
  components. See `biometric/THIRD_PARTY_NOTICES.md`; exact binary provenance remains a release gate.

## Release blockers still open

1. `biometric-custom-face-tf/src/main/assets/tf_bio/mobile_face_net.tflite` has no recorded
   origin or licence in this checkout and does not byte-match the similarly named MIT asset above.
   Do not claim it is Apache or MIT; record a source licence or replace it with an
   application-supplied model before public release.
2. `biometric-custom-face-tf/deepfake_detection_model.tflite` byte-matches a publicly uploaded
   model, but the identified repository's claimed MIT licence has no licence file available for
   verification. The file is outside the Android source set and is not used by the current module,
   but it is still distributed through Git. Remove it from public history or obtain a clear model
   redistribution grant before release.
3. `biometric/libs/localauthentication-1.0.1.jar` is a Huawei LocalAuthentication SDK binary
   currently embedded by `implementation fileTree(...)` in the public `:biometric` AAR. Huawei's
   current site distributes this SDK through its developer programme, but this checkout contains
   no redistribution agreement. It needs a source-only optional integration boundary,
   or written Huawei permission, before a public Maven release.

## Not a licence blocker from this inspection

The Tencent Soter and TensorFlow/LiteRT runtime licences are compatible with the project's Apache
licence when their required notices are retained. This statement does not cover model-training
data, patents, privacy obligations, or future versions of those dependencies.
