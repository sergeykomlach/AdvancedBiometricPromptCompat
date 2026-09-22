# Source-only vendor SDK integrations

Every module in this list is Apache-licensed project code only. It does not package vendor JARs,
native libraries, models, keys, licence files, samples, or vendor documentation. A consuming app
must obtain every proprietary dependency and production licence itself.

| Module | Modality | Current iteration | Required before a usable provider can ship |
| --- | --- | --- | --- |
| `biometric-zkfinger` | Fingerprint | Direct typed adapter compiled with `compileOnly`; optional runtime boundary | ZKTeco SDK JARs/SO and explicit distribution rights for the app |
| `biometric-sherpa-onnx` | Voice | VoiceAuth-parity provider when the app supplies the local runtime and a licensed speaker model | Compatible sherpa-onnx runtime and a redistributable speaker model |

When both Sherpa and the built-in VoiceAuth module are installed, Sherpa has the higher explicit
voice-module and prompt-factory priority. VoiceAuth remains the fallback when Sherpa's runtime is
not available after initial preparation fails. A successfully prepared provider stays selected;
missing enrollment, lockout or unavailable protected storage does not authorize fallback.

Sherpa has its own capture, enrollment and storage implementation and does not depend on VoiceAuth.
Vendor artifacts are required locally to compile these direct adapters, but are not published
dependencies. ZKFinger's local Debug variant additionally packages its ignored runtime for evaluation;
the source-only guarantee above applies to Git sources and release Maven artifacts, not that Debug AAR.
