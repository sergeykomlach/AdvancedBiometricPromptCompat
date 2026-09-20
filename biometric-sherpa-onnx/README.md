# sherpa-onnx voice provider

This module is a functional `SoftwareBiometric` voice provider when the consuming application
supplies both of the following locally:

1. `sherpa-onnx-1.13.8.aar` in `vendor-sdk/libs/` for a local Debug sample build, or the same
   official runtime as an application dependency for a consumer build.
2. A licensed speaker-embedding model as the application asset
   `sherpa-onnx/speaker-embedding.onnx`.

The module reuses `biometric-custom-voice` for microphone capture, enrollment prompting,
protected embedding storage, replay checks, lockout, cancellation, and matching. It substitutes
only sherpa-onnx's `SpeakerEmbeddingExtractor` for the default cepstral embedding engine.

`vendor-sdk/` is Git-ignored. The wrapper compiles against sherpa-onnx's public typed API using a
`compileOnly` dependency and has no tracked sherpa binary or model. The sample app adds the local
AAR only to its Debug configuration; release and published AARs do not bundle it. If a consumer
does not package the runtime, the provider reports itself unavailable and is not offered for
authentication; it does not crash the process. A consumer intending to distribute the runtime must
obtain and comply with the licenses for the sherpa runtime and its selected model.
