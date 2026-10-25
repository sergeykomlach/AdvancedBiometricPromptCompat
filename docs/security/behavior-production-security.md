# Behavior: production security contract

Use behavior as a supplemental local risk signal. It is not an independently attested
identity assertion, an Android Class 3 authenticator or biometric-bound crypto.
Thresholds remain uncalibrated against representative FAR/FRR/PAD datasets. Keep the
default `BiometricTrustPolicy()` for normal use; ANY/ALL evaluate admitted primary
providers. Explicit compatibility/behavior-only opt-in is a lower-assurance choice.
Strict UI capture and opaque handles do not raise the provider's security profile.

## Instrumented processes

The existing hook/debugger detector is best effort. Positive detection is retained
for the process lifetime and rejects success regardless of callback delay. Behavior
stops collecting input and rejects pending results when that signal is positive.
A negative result means only that nothing was detected. Legitimate instrumentation
or a false positive requires investigating the detector and restarting the process.

An attacker controlling the process through Frida can read input/decryption plaintext
and replace the matcher, detector, provider assertions or callbacks. Local checks,
nonces and obfuscation cannot establish an independent trust boundary. Protect the
actual sensitive operation with an auth-per-use Android Keystore key and the
existing cryptographic contract, or a server-verified fresh challenge and suitably
verified evidence. A success callback or app-flow-only crypto object is insufficient.
This module supplies no server attestation service and makes no Frida-resistance
claim from JVM tests. High-value enrollment requires independent primary reauth.

Sources: [Android Keystore](https://developer.android.com/privacy-and-security/keystore),
[biometric crypto](https://developer.android.com/identity/sign-in/biometric-auth),
[OWASP runtime integrity](https://mas.owasp.org/MASTG/knowledge/android/MASVS-RESILIENCE/MASTG-KNOW-0032/).

## Capture and lifetime

Prefer `BehaviorCaptureFragment` or `BehaviorCaptureBinding`. They preserve caller
UI integration and suppress autocomplete, suggestions, Autofill, IME learning hints
and text view-state saving while monitoring. Settings are restored on close. IME
flags cannot constrain a malicious keyboard. Use a dedicated non-secret phrase;
do not collect passwords or OTPs for behavior analysis.
One capture owns a Builder/input view at a time. An overlapping binding is rejected
before changing input settings; close the first binding before starting another.

Submission places only an opaque single-use handle in Builder/provider extras.
The registry owns a defensive sample snapshot, enrollment/auth role and a 30-second
elapsed-realtime deadline. Reseal, reuse, expiry, invalidation and role changes fail.
Raw payload added to a valid handle cannot replace its snapshot; invalid handles
never fall back to raw parsing. Dispose/restart invalidates the handle; consumption
removes its sample. Expired abandoned entries are purged on the next capture.
Close bindings on screen destruction/auth completion. Recreation cancels ownerless
capture; controller, nonce and raw input are not serialized as Fragment arguments.

The handle is a local bearer capability, not an account/flow-bound or server proof.
Do not export it through IPC, URLs, analytics or logs. JVM strings/copies cannot be
reliably zeroized; these changes minimize transport and retention.

Direct raw `BehaviorSample` extras remain a compatibility API. They cannot prove
event provenance/freshness. Recent replay history is an exact-duplicate throttle,
not liveness: small trace changes can retain a high similarity score. Default
supplemental admission is essential for this threat model.

## Enrollment and storage

Profiles use the existing protected preferences and checked durable commits.
Keep `failClosedWhenKeyStoreUnavailable=true`; no plaintext fallback is introduced.
Public writes validate a defensive snapshot; decode rejects malformed data instead
of truncating/dropping it into a valid sample. Corruption is unavailable, not an
invitation to bootstrap a new identity. Name-only enumeration avoids decrypting
all samples and permits deletion when a value is corrupt.

Limits: 32 profiles; five samples per tag; 262,144 serialized characters per tag;
2,097,152 across profiles; 256 phrase characters; 512 typing events; 2,048 points.
New enrollment is rejected without evicting existing profiles. Legacy overflow is
unavailable; explicitly remove selected/all profiles to recover. No silent
destructive migration occurs. Tags identify enrollment records, not accounts:
authentication currently compares all local tags. Multi-account consumers must
isolate identity and authorize enrollment/removal at their own authenticated boundary.

Protected preferences now use [v3 key/file-bound AEAD and one-way legacy migration](protected-preferences-v3.md).
The Keystore fence prevents downgrade to legacy after migration. Replay of valid
v3 data within the same file/key, record deletion and key use in a compromised
process remain outside that guarantee. Keep behavior supplemental for those threats.

## Consumer backup contract

The sample app disables backup. A library cannot replace a consumer's backup policy.
If backup is enabled, merge these exclusions into pre-Android-12 full-backup rules
and both Android-12+ cloud-backup and device-transfer sections; preserve existing
application exclusions. Ciphertext restore without its Keystore key is unsupported.

```xml
<exclude domain="sharedpref" path="behavior_templates.xml" />
<exclude domain="sharedpref" path="behavior_lockout.xml" />
<exclude domain="device_sharedpref" path="behavior_templates.xml" />
<exclude domain="device_sharedpref" path="behavior_lockout.xml" />
<exclude domain="root" path="bio_key" />
<exclude domain="root" path="bio_hash" />
<exclude domain="root" path="bio_key_v2" />
<exclude domain="root" path="bio_hash_v2" />
```

See [Android backup rules](https://developer.android.com/identity/data/autobackup).

## Release evidence

Unit/Robolectric tests cover parser/store rejection, limits, immutable samples,
encrypted-store removal, opaque transport, real manager callbacks, hook-positive
rejection and UI lifecycle. They do not prove physical Keystore behavior, absence
of Frida bypasses, OEM touch delivery or matcher accuracy. Before production
sign-off: run the OnePlus scenarios in [QA.md](../../biometric-custom-behavior/QA.md)
with a release-equivalent artifact, verify consumer backup/identity policies,
perform independent device/Frida assessment and measure dataset-based FAR/FRR/PAD
before considering primary use.
