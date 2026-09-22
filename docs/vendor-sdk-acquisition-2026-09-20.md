# Vendor SDK acquisition status — 2026-09-20

Acquisition results below are the dated local snapshot, not a new download verification.
Integration descriptions were updated on 2026-09-22 to match the current direct adapters.

All downloaded files are under Git-ignored `vendor-sdk/` directories. They are for local
evaluation only and are not a statement that a release or Maven distribution is permitted.

| Integration | Result | Local location / reason |
| --- | --- | --- |
| ZKFinger | Available locally | Three previously supplied JARs and eight ABI-specific SO files are in `biometric-zkfinger/vendor-sdk/`. JARs are `compileOnly` for all variants; runtime JAR/SO packaging is Debug-only. |
| sherpa-onnx | Downloaded | Official `sherpa-onnx-1.13.8.aar` is in `biometric-sherpa-onnx/vendor-sdk/libs/`; SHA-256 is `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`. It contains Android JAR code and native libraries for arm64-v8a, armeabi-v7a, x86 and x86_64. |

## Integration boundary

The Sherpa module has an independent `SoftwareBiometric` provider with its own capture,
enrollment, protected template, replay, lockout and cancellation paths. It does not depend
on VoiceAuth. Both use the shared SoftwareBiometric lifecycle contract. Sherpa becomes
available only after the consuming app supplies a compatible runtime and a separately licensed
speaker-embedding model.

The unimplemented vendor contracts and their evaluation artifacts were removed rather than
published as unavailable modules. A future integration must begin with a licensed SDK, a dedicated
runtime bridge, isolated tests and supported-device verification.
