# Authentication trust policy

`BiometricPromptCompat.Builder.setBiometricTrustPolicy(...)` controls which selected providers may authorize an authentication operation. Physical availability remains separate. Enrollment and its rollback rules are unchanged.

```kotlin
builder.setBiometricTrustPolicy(BiometricTrustPolicy())
```

The default minimum is `TRUSTED_CAPTURE`, and `allowBehaviorOnlySuccess` is false.

| Minimum | Eligible authentication providers |
| --- | --- |
| `COMPATIBILITY` | Hardware routes and software with a valid matching profile; intrinsic provider capture/crypto checks still apply. |
| `TRUSTED_CAPTURE` | Hardware routes, or software that requires trusted capture, rejects compatibility capture, reports trusted capture, and declares PASSIVE_MATCH, ACTIVE_CHALLENGE or VENDOR_BACKED. |
| `SYSTEM_BIOMETRIC` | System/OEM hardware routes; software providers are excluded even when their metadata says VENDOR_BACKED. |

These are library capability requirements, not Android biometric certification classes, measured FAR/PAD values or a ranking by enum ordinal. `SYSTEM_BIOMETRIC` does not by itself mean Class 3. Cryptographic binding remains controlled by the existing cryptography-purpose/CryptoObject contract; a software match cannot substitute for required hardware-bound crypto.

## Behavior is supplemental by default

The current behavior provider accepts compatibility input and cannot meet the default authentication floor. It may still collect an advisory signal. Neither its match nor mismatch/timeout/unavailability authorizes or vetoes the primary operation.

```kotlin
val callback = object : BiometricPromptCompat.AuthenticationCallback() {
    override fun onSupplementalResult(result: SupplementalBiometricResult) {
        // MATCHED / NOT_MATCHED / UNAVAILABLE; advisory only, without raw input or score.
    }

    override fun onSucceeded(confirmed: Set<AuthenticationResult>) {
        // Only eligible authentication factors, never default supplemental behavior.
    }
}
```

The existing behavior-first UI presentation is retained. After supplemental behavior, ANY succeeds on any eligible primary route; ALL requires all initially admitted primary factors. An empty eligible set fails with `INSUFFICIENT_TRUST` before capture; an ALL requirement cannot disappear because a provider subsequently becomes unavailable or falls below the floor. Explicit Cancel terminates the operation. A provider-local supplemental timeout is advisory and continues to primary authentication.

ANY/ALL operate on selected routes for the requested modalities, not on every SDK registered in the process. Priority selects among admissible candidates; priority never establishes trust. Software metadata is declared by the provider and is not independent attestation.

## Explicit behavior authorization

For a deliberately lower-assurance operation, both the role and floor must permit behavior:

```kotlin
builder.setBiometricTrustPolicy(
    BiometricTrustPolicy(
        minimumTrust = BiometricTrustLevel.COMPATIBILITY,
        allowBehaviorOnlySuccess = true
    )
)
```

Setting only `allowBehaviorOnlySuccess=true` does not lower `TRUSTED_CAPTURE`. With both options, behavior is an authentication factor: ANY may finish on its success; ALL also requires it. Lowering the floor alone does not promote supplemental behavior to an authenticator.

## Lifetime and enforcement

- Policy freezes at operation start; setters during an active operation configure the next one.
- Software candidates below the floor are excluded before priority and preparation fallback selection.
- The actual software manager is checked before invoking its SDK and again before delivering success.
- Canonical policy, enrollment and supplemental-role fields overwrite caller and prepared-provider extras. A private request snapshot of trust fields survives retries and cannot be modified through the extras passed to the SDK.
- The role comes from the immutable admitted-factor set; a formerly admitted behavior factor cannot downgrade its capture and relabel itself supplemental to bypass the floor.
- The existing low-level `LegacyBiometric` entry point without a Builder operation context keeps its previous contract. This Builder policy is not a global process-wide policy.

No existing `AuthenticationResult` constructor/copy fields were changed. The supplemental callback is optional. Applications that relied on raw compatibility software or behavior-only authentication must opt into the corresponding policy explicitly.
