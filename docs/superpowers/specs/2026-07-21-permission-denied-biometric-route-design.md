# Permission-denied biometric route design

## Goal

Prevent enrollment from showing a compat biometric dialog when a software biometric route was disabled because any required runtime permission was denied, while preserving real legacy routes that remain valid.

## Scope

Library-only. The rule is permission-agnostic and applies equally to camera, microphone, and any other runtime permission declared by a selected biometric module. Runtime authentication (`enroll = false`) keeps its current behavior.

## Design

After permission denial, the builder disables the affected modules and recomputes selected routes. Api28 orchestration must use the recomputed route set, not the original type list, to decide whether a legacy stage is pending. A system prompt may coexist with a legacy prompt only when a valid legacy route remains after disabled modules are excluded.

The flow is terminal with `MISSING_PERMISSIONS_ERROR` when no selected route remains. If a system hardware route remains, the flow continues with the system prompt only. If an independent legacy route remains, it is preserved for `BiometricConfirmation.ALL`.

## Regression contract

- Any denied runtime permission removes only the module that requires it.
- A remaining system hardware route does not cause a compat dialog.
- A remaining independent legacy route is not suppressed.
- No-route enrollment terminates with missing-permission failure.
- Non-enrollment authentication behavior is unchanged.
