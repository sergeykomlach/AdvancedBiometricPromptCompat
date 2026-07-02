# Thin Prompt Delegates For FaceTF And ZKFinger

## Goal

Add lightweight `SoftwareBiometricPromptFactory` support for `FaceTF` and `ZKFinger` so all software biometrics participate in one prompt-side lifecycle contract, without moving camera, USB, or engine state machines out of their existing managers.

## Problem

`Voice` and `Behavior` already integrate with the generic software prompt pipeline through `SoftwareBiometricPromptFactory` and `SoftwareBiometricPromptDelegate`. That gives them prompt-owned lifecycle hooks such as `start`, `cancel`, `dispose`, readiness gating, and standardized `onHelp` delivery through `BiometricPromptCompatDialogImpl`.

`FaceTF` and `ZKFinger` currently skip that layer by returning `null` from `getPromptFactory()`. Their managers still work, but prompt-side UX is less uniform because the dialog cannot attach module-specific pre-auth orchestration before `startAuth()`.

The goal is not to redesign `FaceTF` or `ZKFinger` around custom capture UI. The goal is to make them participate in the same prompt lifecycle with the smallest safe delta.

## Constraints

- Do not move camera acquisition out of `TensorFlowFaceUnlockManager`.
- Do not move USB permission, device attach, or sensor lifecycle out of `ZkFingerUnlockManager`.
- Do not create a second state machine that competes with the managers.
- Preserve the current software prompt architecture where `BiometricPromptCompatDialogImpl` resolves a delegate from `SoftwareBiometricPromptRegistry`.
- Keep changes narrow and internal to the existing prompt abstraction.

## Recommended Approach

Introduce a reusable thin delegate in `:biometric` for manager-backed software biometrics.

This delegate does not capture data or install a heavy custom view. Instead, it:

- emits module-owned localized help text when the prompt starts;
- immediately signals `onReady(null)` so the existing manager auth flow starts;
- forwards `cancel` and `dispose` as lightweight prompt lifecycle cleanup;
- optionally supports module-specific `shouldInstall()` semantics later, but defaults to no custom install UI.

`FaceTF` and `ZKFinger` then each provide a thin factory that returns this generic delegate configured with their biometric type and localized start/help messages.

## Alternatives Considered

### 1. Keep FaceTF and ZKFinger without prompt factories

Pros:
- zero code churn;
- no new abstraction.

Cons:
- leaves software biometrics split across two prompt models;
- makes shared UX evolution harder because `Voice` and `Behavior` can evolve through prompt delegates while `FaceTF` and `ZKFinger` cannot.

Not recommended because it preserves the inconsistency we are trying to remove.

### 2. Full prompt-owned acquisition for all software biometrics

Pros:
- strongest long-term conceptual uniformity;
- prompt layer would orchestrate every software biometric the same way.

Cons:
- would duplicate or relocate camera and USB lifecycle logic;
- significantly higher regression risk in `FaceTF` and `ZKFinger`;
- much larger diff than the user asked for.

Not recommended for this step because it breaks the “do not over-disrupt existing logic” constraint.

### 3. Thin manager-backed prompt delegates

Pros:
- achieves one prompt contract for all software biometrics;
- preserves manager ownership of camera/USB/engine behavior;
- smallest safe change set;
- creates a clean path for future UX hardening.

Cons:
- `FaceTF` and `ZKFinger` still have less prompt-side intelligence than `Voice` and `Behavior`;
- some runtime UX remains manager-driven rather than fully prompt-driven.

Recommended.

## Target Architecture

### Shared Layer

Add a small reusable delegate in `:biometric`, tentatively `EngineBackedSoftwarePromptDelegate`.

Responsibilities:

- accept `SoftwareBiometricPromptHost`;
- accept a `PromptMessages` value object or equivalent lambdas for start/help copy;
- on `start()`, post the module-owned start/help text if present and then call `host.callbacks.onReady(null)`;
- on `cancel()` / `dispose()`, become inert;
- report `isReadyToStartAuth() = true` because readiness is immediate for manager-backed modules;
- not install a custom view by default.

This class is intentionally thin. It is not a second engine and should not call managers directly.

### FaceTF Module

`TensorFlowProvider.getPromptFactory()` should return a new `TensorFlowFacePromptFactory`.

That factory should create the shared thin delegate with `BiometricType.BIOMETRIC_FACE` and localized prompt-start copy owned by the `biometric-custom-face-tf` module.

The delegate should not gate readiness on camera state. Camera blocked/in-use and related preflight outcomes already belong to `TensorFlowFaceUnlockManager`.

### ZKFinger Module

`ZkFingerProvider.getPromptFactory()` should return a new `ZkFingerPromptFactory`.

That factory should create the shared thin delegate with `BiometricType.BIOMETRIC_FINGERPRINT` and localized prompt-start copy owned by the `biometric-zkfinger` module.

The delegate should not take over USB permission flow. Permission and attach/detach handling stay in `ZkFingerUnlockManager`.

## UX Contract

After this change:

- `Voice` and `Behavior` remain rich prompt delegates with prompt-owned preparation.
- `FaceTF` and `ZKFinger` become thin prompt delegates with prompt-owned start/help messaging and shared lifecycle hooks.
- `BiometricPromptCompatDialogImpl` sees all software biometrics through the same registry/delegate contract.

This gives us one prompt integration model without forcing one acquisition model.

## Localization

Any new user-facing copy for `FaceTF` and `ZKFinger` must be added to their module `strings.xml` files and resolved through `LocalizationHelper`.

No shared generic English literals should be embedded in the reusable delegate.

## Failure And Lifecycle Semantics

- Managers remain the source of truth for preflight failures, runtime errors, lockout, cancellation side effects, and engine-specific acquisition state.
- Thin delegates only own prompt-start help text and prompt lifecycle inertness after `cancel()` / `dispose()`.
- `BiometricPromptCompatDialogImpl` remains the single place that attaches the delegate and transitions into `startAuth()`.

## Testing Strategy

Add focused unit coverage for:

- the shared thin delegate start behavior;
- inert behavior after `cancel()` / `dispose()`;
- provider contract tests proving `TensorFlowProvider` and `ZkFingerProvider` now expose prompt factories instead of `null`;
- factory creation tests or registry-resolution tests confirming the correct biometric type mapping.

Compile proof remains the required verification baseline for this repo because direct `testDebugUnitTest` execution is still environment-sensitive.

## Scope Boundaries

In scope:

- shared thin prompt delegate abstraction;
- `FaceTF` and `ZKFinger` prompt factories;
- resource-backed module-owned prompt start/help copy;
- tests for delegate/factory wiring.

Out of scope:

- moving camera flow into prompt delegates;
- moving USB permission/device flow into prompt delegates;
- redesigning `Voice` or `Behavior`;
- changing manager engine logic beyond what is required for prompt-factory wiring.

## Success Criteria

This design is successful if:

1. all four software biometrics resolve through the same prompt delegate registry model;
2. `FaceTF` and `ZKFinger` gain prompt-side start/help lifecycle hooks;
3. existing manager ownership of camera and USB logic is preserved;
4. compile/test coverage proves the new delegate/factory wiring without introducing a broad refactor.
