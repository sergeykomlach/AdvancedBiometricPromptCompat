# System Biometric UI Policy Design

## Goal

Prevent a compatibility biometric dialog from appearing before or over a usable
Android system biometric prompt, including after runtime permission denial.

## Scope

- Library-only changes in `AdvancedBiometricPromptCompat`.
- Applies to enrollment and authentication flows that select
  `BiometricPromptApi28Impl`.
- Preserves the existing compatibility fallback for known missing or broken
  system UI on LG G8-family devices and pre-Android-12 OnePlus UDFPS devices.
- Does not change RoboForm application UI or callback formatting.

## Policy

`CheckBiometricUI` is a compatibility signal, not authority over a confirmed
system biometric route.

On Android 12 and newer, the biometric SystemUI provider is considered present
when the package selected by `config_biometric_prompt_ui_package` is installed
and enabled. The old ZIP-entry search is retained for older Android versions,
where it remains the compatibility fallback heuristic.

`BiometricPromptApi28Impl` must choose the compatibility dialog only when:

1. the device belongs to an explicit known-missing-system-UI exception; or
2. no selected route can open Android's system biometric prompt and the
   compatibility signal reports that UI as unavailable.

Once a system prompt has started in a session, no later legacy result may open
the compatibility dialog. Legacy biometric modules continue to start and report
their results; only the duplicate UI is suppressed.

## Test Contract

- A modern, enabled SystemUI package is treated as usable without inspecting
  APK layout entry names.
- A missing or disabled modern provider is not trusted.
- Pre-Android-12 keeps the legacy resource-inspection result.
- Explicit LG/old-OnePlus exceptions can still request the compatibility UI.
- A selected system route suppresses heuristic fallback.
- An active system prompt suppresses a later compatibility dialog while legacy
  route execution remains permitted.

## Security and Lifecycle Boundaries

- No permissions, exported components, biometric cryptography, or callback
  semantics are broadened.
- The change never treats a missing runtime permission as granted.
- The guard is session-scoped, reset before each authentication attempt, and
  prevents only duplicate visual UI.
