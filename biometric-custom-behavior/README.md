# Biometric Custom Behavior

Experimental software biometric provider for `BiometricType.BIOMETRIC_BEHAVIOR`.

Supported modes:

- `TYPING`: compares phrase typing dwell and digraph timings.
- `SIGNATURE`: compares touch/stylus stroke path with DTW and shape features.
- `COMBINED`: requires both typing and signature data and fuses both scores.

The module is registered through `ServiceLoader` and uses the existing
`SoftwareBiometricModule` route. It does not provide Android Keystore-backed
crypto authentication.

For production integration, keep behavior supplemental and follow the
[security contract](../docs/security/behavior-production-security.md) covering
instrumentation, raw compatibility input, backups, identity boundaries and release evidence.

Pass samples through `BiometricPromptCompat.Builder.setExtras(...)` using keys
from `BehaviorSample`:

```kotlin
// Compatibility input example only: collect fresh real events for each attempt.
// Use the built-in EXPLICIT UI for strict session-bound capture.
val extras = Bundle().apply {
    putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, BehaviorMode.TYPING.name)
    putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, "sample")
    putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS, longArrayOf(0, 120, 275, 430, 610, 810))
    putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_UPS, longArrayOf(60, 190, 350, 515, 700, 915))
}
```

For enrollment, call the normal enrollment flow and provide the same extras.
For authentication, provide a fresh probe sample in the same format. Repeating
enrollment with the same enrollment tag stores up to five recent samples within a 262,144-character serialized limit. Older
samples are pruned before writing to keep the profile readable. This
improves tolerance for natural behavior variance. Authentication evaluates the
recent templates with a small top-k aggregation, similar to lightweight KNN
matching, instead of trusting a single best sample.

Built-in and binding capture are session-bound and time-limited. Only an opaque
handle crosses Builder/provider extras; the registry holds a defensive sample
snapshot and the enrollment/auth role. It accepts one submission, rejects exact recent payload replay,
and invalidates the capture after obscured or cancelled input. Strict built-in
capture also applies the existing accessibility/screen protection policy to
the complete overlay. These controls improve capture integrity but are not a
neural human-presence detector and do not protect against a compromised or
rooted process.

The 30-second capture deadline uses elapsed realtime, including device sleep. Input
timestamps use uptime relative to the start of capture, so previous deep sleep and
long device uptime do not corrupt event validation or signature float precision.
Typing-only capture must be submitted within two seconds of its last event.
Combined capture uses the last validated typing or signature event, allowing the
user to finish a signature after typing while rejecting stale or future events.

Replay admission is shared across manager instances for 30 seconds and normalizes
timestamp origins. A bounded history rejects recent authentication duplicates even
when other samples intervene; a full history rejects new admissions until entries
expire. Repeated enrollment remains allowed, but its payload cannot immediately be
reused for authentication. Fingerprints remain in process memory and are not logged.

Exact-duplicate rejection is not liveness or fuzzy replay protection. Raw
compatibility input cannot establish freshness or origin. The store also limits
enrollment to 32 profiles and 2,097,152 serialized characters in total. See the
security contract for corruption/overflow recovery and legacy-data behavior.

Enrollment and authentication reject low-quality samples before matching:
typing phrases must be long enough and have useful timing variation, while
signature samples must contain enough points, path length, and visible shape
area. Typing samples also fail closed when the text appears paste/autofill-like
or unrealistically fast. Signature samples fail closed for repeated duplicate
points and very simple straight-line shapes. Signature points support an
optional `strokeId` field so separate pen strokes are not treated as one
continuous path. Legacy five-value points are still accepted and are treated as
a single stroke. Diagnostic logs include sanitized counts, durations, and
coarse geometry buckets; phrases and raw coordinates are not logged.

Alternatively, let the library collect samples from consumer-owned views:

```kotlin
BiometricPromptCompat.Builder(request, activity)
    .setBehaviorAuthMode(BehaviorAuthMode.EXPLICIT)
    .setBehaviorTypingView(passphraseEditText)
    .setBehaviorSignatureContainer(signatureContainer)
```

`BehaviorAuthMode.EXPLICIT` automatically mounts `BehaviorCaptureFragment` as
child content of the compat dialog. There is no activation button or second
overlay window. The parent retains the consumer title, status and Cancel.
When consumer-owned views are provided, temporary listeners reuse those views;
the library does not reparent them. Otherwise the fragment creates its own inputs.
The capture body scrolls while Restart and Enroll/Verify stay visible. Restart
clears captured data and creates a fresh 30-second session; use it after an
interruption or expiry. Closing and reopening also starts a new capture. The
controller restores the host card and caller-owned view protection on disposal.
Signature ignores unused typing data; Typing ignores unused signature data in
matching, persisted templates, and replay identity.
The built-in typing fragment uses a compact touch keyboard so the module can
capture real key down/up timings instead of relying on soft-keyboard text change
events. Consumer-owned typing views remain a compatibility path: their
`TextWatcher`-derived timing is not treated as equivalent to strict built-in
raw-event capture.

For an existing application screen with its own submit/restart buttons, collect
without opening a library window:

```kotlin
val builder = BiometricPromptCompat.Builder(request, activity)
    .setBehaviorTypingView(passphraseEditText)
    .setBehaviorSignatureContainer(signatureContainer)
val capture = BehaviorCaptureBinding(
    builder, BehaviorMode.COMBINED,
    onReady = { builder.build().authenticate(callback) },
    onError = { message -> showInputError(message) }
)
capture.start()
submitButton.setOnClickListener { capture.submit() }
restartButton.setOnClickListener { capture.restart() }
// Close after the auth result/cancellation, or when the caller screen is destroyed.
// Keep it open until the manager consumes its single-use session token.
```

Both integration paths suppress text suggestions, autocorrect, autocomplete and
Android Autofill while monitoring. `AutoCompleteTextView` adapters are detached
temporarily. Input class/variation, password transformation, text, selection and
IME actions are preserved; owned changes are restored on close. IME privacy flags
are best effort and do not replace sample quality/integrity checks.

Authentication runs behavior capture first. By default it is supplemental:
either outcome proceeds to eligible authentication providers. `Confirmation.ANY`
accepts any eligible provider; `Confirmation.ALL` requires all admitted primary
factors. Supplemental behavior neither authorizes nor vetoes their result.
Behavior-only authentication requires both `allowBehaviorOnlySuccess=true` and
`minimumTrust=BiometricTrustLevel.COMPATIBILITY` on `BiometricTrustPolicy`.
The role option alone never lowers the trust floor. Supplemental evidence is
available through the optional `AuthenticationCallback.onSupplementalResult`;
it is not part of the authenticated result set. Explicit cancellation cancels
the whole operation.
The first dialog is removed before starting the next backend. Enrollment keeps
its existing confirmation and rollback rules; Silent does not open capture UI.
The operation's existing permission/model preflight still runs before authentication.

See [the trust policy](../docs/security/biometric-trust-policy.md) for capability
levels, policy snapshots, provider fallback and explicit lower-assurance opt-in.

The current implementation intentionally avoids bundling TensorFlow Lite or
Python/SciPy. Public Android references for keystroke and online-signature
authentication commonly require trained user/model data or heavy runtime stacks;
this module keeps the default provider deterministic and dependency-light. A
future optional ML backend can be added once a model format and calibration
dataset are available.

`BehaviorAuthMode.PASSIVE` does not show behavior UI and does not delay prompt
startup. Use it when the app already collected a behavior sample and passed it
through `setExtras(...)`, or when the provider should fail fast because no
sample is available.

See [QA.md](QA.md) for the sample-app setup, manual scenarios, and evidence limits.
