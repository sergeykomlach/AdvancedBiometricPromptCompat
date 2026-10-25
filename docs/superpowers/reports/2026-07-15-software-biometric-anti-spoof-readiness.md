# Software biometric anti-spoof readiness

## Implemented in this slice

- FaceTF now classifies invalid, non-finite, missing, and unavailable PAD results as `UNAVAILABLE` and fails authentication closed when anti-spoofing is required.
- FaceTF exposes opt-in strict authentication controls: `faceChallengeEnabled` and
  `requireAntiSpoofingForAuthentication` are disabled by default for compatibility
  with ordinary enrolled-face authentication. A real `SPOOF` decision is still
  rejected; only an unavailable PAD result can continue when strict requirement is
  disabled.
- Voice authentication rejects embedding-only input and requires PCM capture data.
- Voice authentication rejects any precomputed embedding supplied through the input bundle, including bundles that also contain PCM data.
- The public `setVoiceEmbedding` builder method is deprecated to make this security boundary explicit; PCM capture or automatic capture is required.
- Voice templates with an enrolled phrase now require a matching presented phrase; missing or different phrases cannot bypass the challenge. Legacy templates without a phrase remain compatible.
- Mixed voice template sets are fail-closed for phrase validation: once any enrolled template uses a phrase, legacy templates cannot be selected to bypass it.
- Face authentication can use a session-scoped randomized head-pose challenge, backed
  by a per-session nonce, existing ML Kit head angles, and localized help instructions.
  It is opt-in; enrollment remains unchanged.
- Each face challenge step now has a monotonic-clock timeout and a bounded number of rejected attempts; timeout and exhaustion are mapped to terminal authentication errors and consume the normal authentication failure budget.
- Face authentication requires a trusted real-camera provider by default; injected/custom providers can still be selected explicitly for controlled integrations and tests.
- Voice authentication fingerprints captured PCM with sample-rate and content data, rejects duplicate samples in one probe, and blocks exact recent PCM replay within the freshness window.
- Behavior capture now has an in-process nonce, a 30-second session deadline, one-submit semantics, exact recent payload replay detection, and fail-closed invalidation for obscured/cancelled input.
- Behavior compatibility typing timestamps use the same monotonic `elapsedRealtime` clock as session validation, including external typing/combined capture paths.
- Behavior validates typing/touch timestamps and point integrity before scoring, applies screen/accessibility protection across the built-in overlay, and keeps consumer-owned views explicitly in a compatibility trust path.
- Shared `SoftwareBiometricAssurance` keeps `PASS`, `SPOOF`, `MISMATCH`, `UNAVAILABLE`, and `CAPTURE_UNTRUSTED` distinguishable. Only `PASS` is accepted as authentication success.
- Shared software security profiles now classify assurance, trusted capture, compatibility capture, cryptographic capability, and capture bounds; legacy providers receive conservative non-cryptographic defaults.
- The common software module rejects software routes for cryptographic requests unless a provider explicitly declares capability, blocks mismatched modality/profile metadata, and rejects provider crypto returned on a non-cryptographic profile.
- Common software callback delivery now uses an opaque exactly-once session guard, preventing stale, duplicate, post-cancel, and post-timeout success callbacks from reaching the application.
- Shared bounded-input primitives cover finite values, collection/payload sizes, clock rollback, and capture expiry; voice bundle PCM parsing reuses the size gate.
- Software prompt registry uses explicit priority and rejects ambiguous top-priority factories. Legacy module loading removes ambiguous modalities instead of selecting the first `ServiceLoader` result.

## Attack classes covered

- Precomputed or injected voice embeddings supplied through the input `Bundle`.
- Voice replay/probe input that omits or changes the enrolled phrase.
- Face PAD model failure, invalid output, or disabled/unavailable anti-spoofing on an
  authentication path when strict anti-spoofing is explicitly required.
- Fail-open exception paths in the face PAD decision.
- Repeated face spoof attempts consuming no lockout budget.
- Pre-generated face video/screen replay that cannot predict the next randomized pose instruction.
- Face challenge hangs and unbounded rejected-step retries.
- Exact PCM replay and duplicate voice captures within one probe.
- Untrusted/injected face capture providers on the default authentication path.
- Replayed or stale Behavior bundles, malformed typing/touch sequences, obscured/cancelled captures, and strict-mode untrusted accessibility automation.

## Explicit residual limits

- Voice does not yet contain a dedicated neural TTS/voice-conversion detector; PCM quality and replay-risk heuristics are not equivalent to a trained anti-spoof model.
- Face challenge is an active signal, but it is still not equivalent to a trained neural deepfake detector; real-time deepfake systems can react to visible prompts.
- The voice fingerprint protects against exact PCM replay and duplicate capture batches; it is not a detector for replay with noise, time-stretching, or voice conversion.
- Behavior nonce/fingerprint protection is an in-process provenance and freshness check; it is not cryptographic protection against a rooted or compromised process.
- Shared session tokens protect callback lifecycle within the library process; they do not authenticate a callback emitted by a fully instrumented process.
- Virtual camera injection beyond the provider boundary, rooted-device capture manipulation, and high-quality real-time face/voice morphing require app/device integrity signals and, for high-risk actions, server-side step-up controls.

## Verification

- Kotlin and unit-test compilation passed for `biometric`, `biometric-custom-face-tf`, and `biometric-custom-voice`.
- Offline attack-matrix tests pass for invalid PAD, spoof scores, wrong/expired face challenges, injected voice embeddings, phrase bypass attempts, exact voice replay, and Behavior session/input/accessibility vectors.
- `git diff --check` passed; only normal LF/CRLF warnings were emitted.
- Targeted policy/scorer runtime execution passes with a clean process PATH. Two existing Behavior tests that exercise Android `Bundle` fail in the local plain-JUnit runner because `android.os.Bundle` methods are not mocked; this is test-harness/environment noise and is separate from the new pure policies.
- Shared security profile, session guard, input policy, and provider-registry tests pass with a single-worker Gradle runner. Production compilation passes for `biometric` and all four software provider modules.
