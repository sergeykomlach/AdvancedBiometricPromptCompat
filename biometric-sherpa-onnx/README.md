# sherpa-onnx voice provider

This module is a functional `SoftwareBiometric` voice provider when the consuming application
supplies both of the following locally:

1. `sherpa-onnx-1.13.8.aar` in `vendor-sdk/libs/` for a local Debug sample build, or the same
   official runtime as an application dependency for a consumer build.
2. A licensed speaker-embedding model as the application asset
   `sherpa-onnx/speaker-embedding.onnx`.

The module is atomic: it contains its own `SoftwareBiometricProvider`, manager, microphone capture,
enrollment/authentication prompt, protected template storage, replay checks, lockout, cancellation,
and matching flow. It does not depend on or instantiate `biometric-custom-voice`/`VoiceAuth`.
The only external biometric runtime used by this module is sherpa-onnx's typed
`SpeakerEmbeddingExtractor` API.

`vendor-sdk/` is Git-ignored. The wrapper compiles against sherpa-onnx's public typed API using a
`compileOnly` dependency and has no tracked sherpa binary or model. The sample app adds the local
AAR only to its Debug configuration; release and published AARs do not bundle it. If a consumer
does not package the runtime, the provider reports itself unavailable and is not offered for
authentication; it does not crash the process. A consumer intending to distribute the runtime must
obtain and comply with the licenses for the sherpa runtime and its selected model.

## Initialization and direct enrollment writes

The standard prompt prepares a NEW/PREPARING engine before applying the final enrollment gate.
A confirmed initial failure permits a lower-priority voice provider to be selected. Once READY,
runtime failures or a missing enrollment do not switch to another provider's identity namespace.

Direct engine users must call `prepare()` off the UI thread before availability checks or
extraction. For direct template writes, use this module's `VoiceTemplateStore(engine)` with the
same prepared engine that produced the embeddings. Custom engines must implement this module's
`VoiceTemplateIdentityProvider`. Stored identities include the consensus version; older or
different-model profiles require re-enrollment and are not automatically deleted.
The no-argument store still supports reads/removal, but public `save`/`saveAll` calls on it now
throw `IllegalStateException` before any storage access rather than create an unusable profile.

Removal through any manager or store revokes pending operations across this provider's
process-local namespace, including queued results and in-flight public enrollment writes.
This conservatively cancels all pending operations in that namespace even for a single-tag
removal; VoiceAuth's separate namespace is unaffected. A revoked `save`/`saveAll` throws
`CancellationException`. Operations admitted after removal can proceed normally.
Terminal results that already claimed completion before removal are not retroactively revoked.
This is not a cross-process synchronization guarantee.

Consumer R8 rules suppress only missing optional sherpa classes and retain the direct adapter's
linkage boundary against inlining/merging. This is not a substitute for testing a minified host
with the runtime absent, with a supported runtime, and with a missing native ABI.
