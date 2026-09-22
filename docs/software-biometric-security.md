# Software biometric security boundaries

SoftwareBiometric providers return application-level matching results. They do not unlock Android Keystore authentication-per-use keys. Provider priority is routing preference, not biometric strength or certification.

## TFFace

Anti-spoofing is required by default, including enrollment. Missing/disabled inference fails closed. Valid scores must fill the liveness window before enrollment or authentication can succeed; pending samples are not failures and not passes. The final candidate always receives a current check even when frame-stride sampling would skip it.

`TensorFlowFaceConfig(requireAntiSpoofingForAuthentication = false)` is an explicit compatibility opt-out: unavailable PAD may be accepted. To disable PAD entirely this opt-out must accompany `antiSpoofingEnabled = false` or the relevant flow/mode setting. This reduces assurance and should not be used for sensitive operations.

Head-turn challenges remain opt-in (`faceChallengeEnabled`). The manager reports `ACTIVE_CHALLENGE` only when enabled, otherwise `PASSIVE_MATCH`. Neither label certifies resistance to presentation attacks; physical spoof evaluation remains necessary.

## VoiceAuth and Sherpa ONNX

Both implement passive speaker matching. Their public PCM inputs may be caller-supplied, so their baseline profile permits compatibility capture and does not claim trusted-capture provenance. The phrase is metadata, not an ASR verification of what was spoken. Short-window exact-PCM duplicate detection is not protection against acoustic replay or generated speech. Stronger guarantees require a separate validated challenge/PAD implementation, not a different enum value.

Sherpa remains independent of VoiceAuth and has higher provider priority. Each has its own enrollment/storage namespace. A fallback requires an enrollment usable by the selected provider; embeddings are not interchangeable.

## Behavior and ZKFinger

Behavior exposes a passive compatibility profile because callers can submit samples without an interactive capture nonce. Interactive capture checks do not make the compatibility path an active challenge.

ZKFinger's vendor-backed profile describes SDK matching, not Android hardware-backed biometric cryptography or a certified liveness result.

## Deployment gates

Do not use software success as evidence that a hardware-auth-bound operation was authorized. Validate enrollment changes, lockout, cancellation, unavailable storage, absent optional runtimes and physical spoof/replay behavior in the consuming application. Refer to the [hardening roadmap](superpowers/plans/2026-09-21-biometric-hardening-roadmap.md) for open provider-selection, licensing and UI work. This document is not a claim that those gates have passed.
