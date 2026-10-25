# Behavior Balanced Hardening Design

## Goal

Strengthen the experimental `biometric-custom-behavior` provider against scripted, replayed, obscured, accessibility-driven, and malformed typing/touch captures without adding a new ML model, changing the public `BiometricPromptCompat` API, or making Behavior claim hardware-backed assurance.

The design targets a practical baseline: stronger capture integrity and deterministic fail-closed behavior while preserving an explicit compatibility path for consumer-owned views and accessibility-sensitive integrations.

## Scope

In scope:

- capture-session lifetime, freshness, nonce, and single-submit rules;
- typing event provenance and sequence validation;
- touch/stylus event integrity validation;
- duplicate/replay detection for behavior payloads;
- consistent overlay, obscuring, and accessibility protection;
- deterministic policy tests and an offline attack matrix;
- localized, non-sensitive error mapping and readiness documentation.

Out of scope:

- neural behavioral anti-spoofing;
- root detection or device attestation;
- claiming that accessibility detection proves malicious intent;
- removal of the experimental provider or its public builder hooks;
- new public API methods;
- device/OEM certification in this code-only slice.

## Security posture

The provider will distinguish capture quality from capture trust. A sample can be structurally valid and still be rejected because it is stale, duplicated, synthetic-looking, obscured, or not tied to the active session.

The default path will be stricter for samples collected by the built-in prompt. Consumer-owned views remain supported, but their limitations are explicit: a `TextWatcher` cannot prove that characters came from physical human key events. The compatibility path therefore receives a lower-trust classification and cannot silently bypass strict policy when strict mode is selected.

No raw phrases, coordinates, or event payloads will be logged. Diagnostics remain limited to coarse counts, durations, issue codes, and buckets.

## Architecture

### `BehaviorCaptureSessionPolicy`

A pure Kotlin policy owns the session contract:

- random per-session nonce/token;
- monotonic start and last-event timestamps;
- capture deadline and maximum inter-event gap;
- maximum submit count, with one successful payload submission;
- freshness and duplicate fingerprint checks;
- explicit `ACTIVE`, `EXPIRED`, `REPLAY`, `ALREADY_SUBMITTED`, and `INVALID` decisions.

The policy will be injectable through simple values/functions in tests, while production code uses `SystemClock.elapsedRealtime()` and `SecureRandom`.

### `BehaviorInputIntegrityPolicy`

A pure policy validates typing and touch data before scoring:

- typing DOWN/UP counts match;
- events are monotonic and within the active session window;
- no impossible negative dwell/flight times;
- no unexplained event-count/text-length mismatch;
- touch pointer/action sequence is valid;
- timestamps are monotonic;
- coordinates, pressure, size, and stroke identifiers are finite and bounded;
- cancellation or obscured input invalidates the current capture rather than merely dropping one event;
- unsupported or synthetic-looking source/tool combinations are rejected in strict mode.

The policy will not attempt to prove human intent from timing alone. It only rejects signals that violate the capture contract or are clearly inconsistent.

### `BehaviorAccessibilityPolicy`

Accessibility handling will be explicit and configurable:

- the entire Behavior capture overlay and all interactive descendants receive the existing `ScreenProtection` behavior;
- protected views reject accessibility actions and are marked non-exportable to accessibility services where platform support allows;
- a detected accessibility service is classified as a risk signal, not automatically as malware;
- strict mode can reject the session when an untrusted service is active;
- compatibility mode preserves legitimate accessibility use but records the lower-trust path and does not claim equivalent assurance.

The implementation will reuse existing `A11yDetection` and whitelist semantics instead of inventing a second global service detector.

### Payload binding

Built-in capture will attach an internal session token and capture metadata to the existing extras. The manager will validate the token against the active in-process session before scoring. Caller-supplied bundles without a valid active token remain supported only through the explicit compatibility path; they are not treated as strict high-assurance captures.

The token is an in-process freshness/provenance signal, not a cryptographic proof against a compromised process or rooted device.

## Data flow

1. Behavior prompt starts a capture session and creates a nonce/token.
2. Capture controller resets all buffers and records only events belonging to that session.
3. Obscured, cancelled, malformed, expired, or accessibility-risk events invalidate the current sample according to policy.
4. Submit performs a single freshness, token, duplicate, and quality check.
5. Only a trusted, valid, fresh sample reaches `BehaviorScorer`.
6. Authentication applies the existing score threshold and lockout policy.
7. Session state is cleared on success, failure, cancellation, timeout, and disposal.

## Compatibility behavior

- Existing public builder methods remain unchanged.
- Built-in prompt capture uses balanced strict defaults.
- Consumer-owned typing views continue to work in compatibility mode, but synthetic `TextWatcher` timing is not accepted as strict evidence.
- Existing stored templates remain readable; no destructive migration is introduced.
- Existing five-field signature points remain supported, with the same conservative validation.

## Error handling

All security-relevant failures fail closed and map to existing Behavior callback errors:

- timeout or expired session -> timeout error;
- replay, duplicate, invalid token, or already submitted -> unable-to-process/vendor error;
- obscured or accessibility-risk input -> unable-to-process with localized retry guidance;
- ordinary score mismatch -> existing vendor mismatch plus lockout accounting.

Error messages will not reveal whether a specific template, phrase, or fingerprint matched.

## Testing strategy

Test-first pure policy coverage will include:

- active, expired, and already-submitted sessions;
- changed nonce and replayed token;
- duplicate payload and freshness-window behavior;
- typing missing UP, negative dwell, non-monotonic timestamps, paste/autofill-like counts;
- touch obscured, cancelled, invalid coordinates, invalid pressure/size, and invalid pointer transitions;
- accessibility strict-vs-compatibility decisions;
- malformed and oversized Bundles;
- end-to-end manager rejection before scorer invocation where feasible;
- one offline attack matrix aggregating the above vectors.

Verification will use module compilation, Behavior unit tests, targeted attack-matrix tests, and `git diff --check`. Device/OEM testing remains a separate follow-up.

## Acceptance criteria

- No public API change.
- Built-in Behavior captures are session-bound, time-bounded, single-submit, and reject exact duplicate replay.
- Synthetic or incomplete typing/touch sequences cannot reach scoring in strict mode.
- Obscured and cancelled captures invalidate the session/sample consistently.
- Accessibility behavior is explicit, configurable, and covered by tests.
- All new policy tests pass, existing Behavior tests pass, and no sensitive input is added to logs.
- No commit is created without explicit user approval.
