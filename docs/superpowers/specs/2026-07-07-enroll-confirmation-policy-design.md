# Enroll Confirmation Policy Design

Date: 2026-07-07
Repo: `C:\Users\skoml\StudioProjects_5\AdvancedBiometricPromptCompat`
Scope: biometric enroll flow in `biometric`

## Goal

Align enroll behavior with QA feedback:

- do not report enroll success automatically just because some software biometrics were filtered out by runtime permission denial or unavailability;
- treat enroll as a user confirmation flow that must end with at least one real confirmation action;
- preserve `BiometricConfirmation.ANY` and `BiometricConfirmation.ALL` semantics instead of overriding them with ad hoc success/failure rules;
- rollback previously enrolled software biometric data only when the chosen confirmation contract requires failure.

## Non-goals

- no redesign of route selection outside enroll-related decisions;
- no changes to ordinary auth semantics outside what is needed to keep enroll fallback consistent;
- no OEM-specific point fixes unless they fall out of the shared enroll policy.

## Current Problem

The current generalized enroll resolver already separates:

- `enrollScopeTypes`
- `pendingEnrollTypes`
- `preSatisfiedEnrollResults`

But `setupBiometric()` can still finish successfully after runtime permission or software-module filtering leaves no pending software work, even when the current run did not obtain an explicit confirmation from the user.

This creates a gap between:

- "the library found no more pending software modules"
- "the user has actually confirmed identity in this enroll session"

QA expects the second condition, not the first one, to drive `onSuccess`.

## Confirmation Contract

### What counts as a valid confirmation action

The current enroll run may finish successfully only after at least one of these happens:

1. A software biometric enroll succeeds.
2. A system hardware enroll succeeds.
3. A hardware auth fallback succeeds because software enroll is not possible for the remaining scope.

### What does not count as confirmation

The following must not produce success by themselves:

- runtime permission denial;
- software module removal from scope because of missing permission;
- software module becoming unavailable;
- an empty pending software scope that was produced only by filtering/failure;
- credential fallback that bypasses biometric confirmation.

## `BiometricConfirmation` Semantics

### `BiometricConfirmation.ANY`

- Keep every successful confirmation result collected during the run.
- Ignore later `cancel` or `error` from other software biometrics.
- Do not rollback already successful software enrolls.
- If at least one valid confirmation action succeeded, return `onSuccess`.

Examples:

- voice enrolled, behavior canceled -> success, keep voice;
- system hardware enroll succeeded, software enroll later canceled -> success;
- software was impossible due to permission denial, hardware auth fallback succeeded -> success.

### `BiometricConfirmation.ALL`

- Every type in the final enroll scope must be confirmed successfully.
- If one software biometric succeeds and a later required biometric cancels or fails, rollback all previously successful software enrolls from this run.
- If hardware confirmation is required for the remaining scope and it fails/cancels, the full enroll session fails.

Examples:

- voice enrolled, behavior failed -> rollback voice, fail;
- software modules unavailable, system hardware enroll succeeded for the required scope -> success;
- software unavailable, hardware auth fallback failed -> fail.

## Effective Scope Rules

### Full enroll scope

`enrollScopeTypes` should remain the full target scope of the session.

### Pending scope

`pendingEnrollTypes` should remain "what still needs an active runtime enroll/auth step".

This can shrink because of:

- software success;
- system hardware enroll success;
- route-aware scope filtering.

### Confirmation-aware terminal rule

A terminal success requires both:

1. the confirmation contract (`ANY` or `ALL`) is satisfied for the final scope; and
2. at least one valid confirmation action was observed in this run.

The second rule is what blocks today's false-positive success after permission-based filtering.

## Hardware Behavior

### System hardware enroll

Successful system hardware enroll is itself a valid identity confirmation.

Do not force an extra hardware auth after successful system enroll.

### Hardware auth fallback

Use hardware auth fallback only when software enroll cannot be performed for the remaining scope.

Typical cases:

- runtime permission denied for software face/voice;
- no software module left after route-aware filtering;
- software route unsupported but already enrolled hardware biometric exists.

### No confirmation path available

If neither software enroll nor system hardware enroll nor hardware auth fallback can produce confirmation, fail instead of auto-succeeding.

## Override Audit

The implementation must review and, if needed, narrow current aggressive overrides in enroll flow:

### `disableBiometricForPermissionFailure(...)`

- It may continue to remove a failed software module from active pending execution.
- It must not indirectly turn the whole session into success without a real confirmation event.

### `setForceDeviceCredentials(...)` and device-credential fallback

- Keep auth lockout recovery behavior for ordinary authentication.
- Do not let device-credential fallback become a hidden enroll success path.
- Enroll flow must continue to require biometric confirmation, not only credential confirmation.

### Existing rollback hooks

`LegacyBiometric.rollbackLastEnrollInSoftwareModules()` is already used on cancel/failure in some paths.

The new policy should make rollback conditional:

- `ANY` -> no rollback for already successful software enrolls;
- `ALL` -> rollback earlier successful software enrolls when a later required biometric cancels/fails.

## Recommended Implementation

Use a centralized session outcome policy rather than scattered path-specific conditions.

### Add a shared enroll confirmation state model

Extend the shared enroll resolver layer in `BiometricAuthState.kt` with a session-aware outcome model that tracks:

- scope types;
- successful confirmations;
- failed/canceled confirmations;
- whether the current run observed any real confirmation event;
- whether rollback is required.

### Keep terminal integration points thin

Use the shared policy from:

- `BiometricPromptCompat.setupBiometric()`
- `BiometricPromptGenericImpl`
- `BiometricPromptSilentImpl`

These integration points should only feed events into the resolver and execute:

- `onSucceeded`
- `onFailed` / `onCanceled`
- conditional software rollback

### Treat system enroll as a success event

`InitiateSystemBiometricEnrollFragment` completion must feed a real confirmation success into the session state, not only remove pending work.

## Candidate Code Areas

- `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricAuthState.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptSilentImpl.kt`
- possibly `BiometricPromptApi28Impl.kt` only if the same confirmation override risk exists there for enroll-like paths

## Testing Strategy

Add focused unit coverage for the shared resolver first.

Minimum cases:

1. `ANY`: one software enroll succeeds, later required software enroll cancels -> success, no rollback requested.
2. `ALL`: one software enroll succeeds, later required software enroll fails -> fail, rollback requested.
3. permission denial removes software pending scope, no other confirmation event happened -> not success yet.
4. permission denial removes software pending scope, hardware auth fallback succeeds -> success.
5. system hardware enroll succeeds with no further pending work -> success without extra auth.
6. `ANY`: system hardware enroll succeeds, later software cancel/error happens -> success.
7. `ALL`: system hardware enroll succeeds, later required software failure happens -> fail and rollback software enrolls from this run.

Verification should prefer:

- `:biometric:compileDebugKotlin`
- `:biometric:compileDebugUnitTestKotlin`

`testDebugUnitTest` is still expected to be noisy on this machine because of the local `VS` runner issue.

## Risks

- accidentally changing normal auth fallback semantics while fixing enroll;
- treating filtered scope changes as confirmation events;
- rolling back software enroll data too aggressively for `ANY`;
- failing to propagate the same policy across generic and silent prompt implementations.

## Recommendation

Implement the centralized session-aware enroll confirmation policy.

This keeps the previously introduced `scope + pending + preSatisfied` shape, preserves `BiometricConfirmation` semantics, prevents false-positive success after permission filtering, and limits behavior changes to one shared policy layer instead of multiple callback patches.
