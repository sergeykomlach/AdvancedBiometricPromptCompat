# Shared Software Biometric Security Hardening Design

**Date:** 2026-07-15

## Goal

Raise the code-only production readiness of the library by making software biometric trust explicit and enforcing the same security invariants across provider loading, route selection, cryptographic requests, authentication sessions, callbacks, and bounded input handling.

## Scope

The implementation covers the shared `biometric` module and the four currently implemented software providers: FaceTF, Voice, Behavior, and ZKFinger. It preserves the existing public request builders and legacy provider contract where possible, while making unsafe combinations fail closed.

This work does not claim hardware-backed security for software providers, add a new ML model, certify vendor sensors, or redesign the public authentication API. Device-specific validation and server-side step-up controls remain outside the code-only scope.

## Security model

Software biometric results are evidence of a software capture and matching pipeline, not proof of Android Keystore authorization. Every software provider must expose a security profile with:

- an explicit assurance class;
- whether its capture source is trusted by default;
- whether compatibility/external capture is allowed;
- whether it can satisfy a cryptographic request;
- bounded capture and input limits.

The safe defaults are fail-closed: software providers cannot satisfy a hardware-backed cryptographic request, unknown profiles are rejected for sensitive routes, and a provider failure never becomes a successful fallback.

## Architecture

### 1. Shared provider security profile

Add an internal/shared profile model and resolver. The profile is attached to the provider manager rather than inferred from the biometric type. This avoids treating voice, behavior, or software face as equivalent merely because they share a prompt API.

The profile must be immutable for an authentication session. It is used both before capture and before terminal success delivery.

### 2. Authentication request gate

Add a policy gate at the common software route boundary. It rejects:

- software providers when a cryptographic primitive is requested and the provider is not explicitly cryptographic-capable;
- unknown or malformed provider profiles;
- compatibility capture for strict high-assurance requests;
- sessions whose requested modality and provider modality do not match.

The existing legacy request builders remain source-compatible. Existing non-cryptographic software flows continue to work under the safe default profile.

### 3. Route-aware `ANY`/`AUTO` handling

Before selecting a software module, filter candidates using the request security requirements. A software provider may be used for `ANY` only when the request does not require hardware-backed cryptography and the provider profile allows the selected capture mode. `ALL` must not count an unavailable or untrusted software route as confirmed.

### 4. Exactly-once session terminal guard

Add a small shared session guard with an opaque session id and atomic terminal transition. Providers and common callbacks use it to reject stale, duplicate, post-cancel, and post-timeout terminal events. It must not expose biometric data or rely on wall-clock time for uniqueness.

Provider-specific capture sessions remain responsible for their modality evidence; the shared guard only protects lifecycle and result delivery.

### 5. Bounded input policy

Add shared validation primitives for collection sizes, byte payloads, finite numeric values, timestamps, and duration limits. Provider-specific validators continue to enforce modality semantics. Validation occurs before scoring and before large derived allocations where the input path permits it.

### 6. Registry and vendor hardening

Make software provider discovery deterministic. Duplicate providers for one modality must not be silently selected based on classpath order. Provider construction and metadata failures are isolated and cannot partially register a module. ZKFinger additionally validates vendor callback state transitions and prevents success after terminal cancellation, timeout, error, or lockout.

## Error handling

Security-policy rejection maps to the existing unable-to-process or capture-untrusted outcome, preserving the public callback contract. It must not be mapped to hardware-unavailable when the provider was present but rejected for security. All terminal paths clear session state and detach provider listeners where supported.

Logs contain provider class/type and policy outcome only; they must not contain raw face frames, PCM, embeddings, typing sequences, signature coordinates, nonces, or full Bundle contents.

## Testing strategy

Use pure JVM tests for all shared policy decisions and provider state transitions. Add attack-matrix coverage for:

- cryptographic request routed to software;
- unknown/duplicate provider profiles;
- `ANY`/`AUTO` software fallback;
- duplicate, stale, cancelled, timed-out, and post-terminal callbacks;
- malformed and oversized input;
- clock rollback and duration overflow;
- ZKFinger success after cancellation/error/lockout;
- provider construction and factory exceptions.

Run targeted module tests and compilation first, then the broadest feasible repository checks. Existing plain-JUnit Android `Bundle` limitations must be reported separately rather than hidden as production failures.

## Non-goals and residual risk

This design does not detect novel AI-generated face or voice content without a trained model. It cannot protect a rooted or fully instrumented process, establish sensor provenance for vendor SDKs, or replace server-side risk controls. It also does not make software biometric results suitable for Android Keystore auth-per-use keys.

## Compatibility policy

The public API remains backward compatible. Legacy software providers receive the conservative default profile and are allowed only for non-cryptographic compatibility flows. New providers must opt into explicit metadata and pass the shared policy gate.
