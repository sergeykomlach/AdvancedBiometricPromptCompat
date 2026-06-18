# Biometric Custom Voice

Experimental software voice biometric provider for `BIOMETRIC_VOICE`.

The module follows the same `SoftwareBiometricProvider` contract as the TF face,
ZK fingerprint, and behavior providers. It currently exposes a small Kotlin
engine boundary:

- `VoiceSample` accepts either caller-provided PCM float audio or a precomputed
  speaker embedding through the request `Bundle`.
- `CepstralVoiceEngine` is the default dependency-free PCM backend. It extracts
  MFCC-style cepstral features, applies per-utterance normalization, filters
  near-silent frames, and returns both frame-level features and a normalized
  speaker-like embedding.
- Enrollment trains a lightweight diagonal GMM speaker model when frame-level
  features are available. Authentication prefers GMM log-likelihood scoring and
  falls back to embedding cosine only for legacy templates or external engines
  that provide embeddings without frames.
- `BasicVoiceEngine` remains as a deterministic legacy/simple embedding helper
  for tests and experiments.
- `VoiceEngineProvider` can be registered through Java `ServiceLoader` by an
  optional backend module. The highest-priority available provider is used; if
  no provider is available, the module falls back to `CepstralVoiceEngine`.
- `VoiceTemplateStore` stores only normalized embeddings in protected
  preferences; raw audio is not persisted. Batch enrollment embeddings are
  normalized, filtered for outliers, augmented with a centroid template, and
  paired with a compact GMM model before storage when possible.
- `VoiceBiometricManager` handles microphone permission, lockout, enroll,
  authentication, and template removal.

Supported extras:

- `voice.sample_rate`: PCM sample rate in Hz.
- `voice.pcm_float`: mono PCM samples normalized to `[-1.0, 1.0]`.
- `voice.sample_count`: optional number of enrollment PCM samples in the bundle.
  Batch samples are read from `voice.pcm_float.0`, `voice.pcm_float.1`, and so on.
- `voice.embedding`: optional precomputed speaker embedding.
- `voice.phrase`: optional phrase label. When absent, authentication is treated
  as text-independent and is suitable for multilingual speaker-embedding
  backends. When present, phrase mismatch is treated as a different template
  family.

This is intentionally not a direct copy of Ye83/VAD-Speaker-Recognition-for-
Android or other demo apps. Those projects are useful as backend references for
a future VAD + ONNX Runtime speaker embedding implementation, but their demo app
layout, hardcoded paths, native assets, and model packaging are not suitable for
this library API as-is.

Optional multilingual engines should implement `VoiceEngineProvider` in a
separate module and register it at:

`META-INF/services/dev.skomlach.biometric.compat.engine.internal.voice.VoiceEngineProvider`
