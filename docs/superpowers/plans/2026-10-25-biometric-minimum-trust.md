# Minimum Biometric Trust Implementation Plan

> Execute inline; read-only agents review selection and aggregation. Main agent alone runs Gradle.

**Goal:** Behavior is supplemental by default, explicit behavior authorization never bypasses the minimum trust floor, and ANY/ALL aggregate only eligible authentication providers.

**Architecture:** An immutable operation-local `BiometricTrustPolicy` admits actual provider capabilities before priority selection and again at software manager invocation. Existing availability, enrollment and crypto contracts remain separate. Supplemental results use an optional callback, not the authenticated result set.

**Spec:** Latest user-approved trust-policy requirements in this conversation.

**Constraints:** No commit/push/staging; preserve current dirty checkout. Only scoped tests and app debug assembly; one Gradle process. Existing public AuthenticationResult JVM constructor/copy signatures remain intact.

## Decisions

- Default minimum is TRUSTED_CAPTURE (platform/OEM hardware or software that requires controlled capture, rejects compatibility input and reports trusted capture); this does not assert Android Class 3 certification.
- COMPATIBILITY permits valid legacy software profiles; SYSTEM_BIOMETRIC excludes software. Crypto binding remains a separate existing requirement.
- `allowBehaviorOnlySuccess` defaults false. Enabling it does not lower minimum trust: current behavior needs an explicit COMPATIBILITY floor too.
- Supplemental behavior match, mismatch, timeout or unavailability cannot authorize or veto primary ANY/ALL. Explicit global Cancel remains terminal.
- No eligible authenticators fails closed before capture; ALL never succeeds from an empty set or a reduced required set.
- Operation policy freezes at beginAuthFlow; caller/provider extras cannot overwrite canonical policy or enrollment role.
- Retain behavior-first capture presentation; change its authorization role, not the already implemented UI design.

## Tasks

- [x] Add RED default supplemental regressions and minimum-capability admission tests.
- [x] Implement public trust policy, internal admission, supplemental callback and behavior coordinator changes.
- [x] Filter inadmissible module candidates before priority/fallback selection; enforce actual-manager policy from canonical context.
- [x] Cover canonical extras, below-floor fallback, empty/missing providers, ANY/ALL, opt-in, late results/cancel and enrollment preservation.
- [x] Run target core/behavior tests; independent source review; fix confirmed issues with regressions.
- [x] Build `:app:assembleDebug`, scoped diff/index check and save final evidence.
