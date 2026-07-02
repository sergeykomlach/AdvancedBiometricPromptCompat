# Software Biometric Module Separation Design

**Date:** 2026-07-02

**Goal**

Restore a strict module boundary where `:biometric` remains the generic prompt/orchestration layer and software-biometric-specific flow logic lives inside the corresponding custom module (`:biometric-custom-voice`, `:biometric-custom-behavior`).

**Problem**

The current tree places concrete software-biometric flow classes inside `:biometric`, including:

- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureController.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSession.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BehaviorCaptureController.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/VoicePromptData.kt`

This breaks the intended dependency direction:

- `:biometric` should expose shared contracts and prompt orchestration
- `:biometric-custom-*` modules should depend on `:biometric`
- `:biometric` should not know concrete implementation details of software voice or behavior capture

## Current Constraints

- `:biometric-custom-voice` already depends on `:biometric`
- `:biometric-custom-behavior` already depends on `:biometric`
- introducing a reverse dependency from `:biometric` back to custom modules would create an invalid cycle
- `BiometricPromptCompatDialogImpl` currently instantiates `BehaviorCaptureController` and `VoiceAutoCaptureController` directly, so the orchestration layer is the main coupling point that must be cut

## Target Architecture

### `:biometric`

Responsibilities:

- generic dialog lifecycle
- route selection and prompt orchestration
- common callback bridge between software-biometric flow and auth pipeline
- shared software-biometric contracts

Must not contain:

- voice-specific enrollment/auth capture implementations
- behavior-specific capture implementations
- concrete prompt-data carriers meaningful only to one custom module

### `:biometric-custom-voice`

Responsibilities:

- voice-specific prompt setup data
- voice auto-capture session state
- voice auto-capture controller
- voice-specific help/error mapping
- interaction with voice VAD, preprocessing, and sample packaging

### `:biometric-custom-behavior`

Responsibilities:

- behavior-specific capture controller/session state
- behavior-specific help/error mapping
- any custom view/input logic needed for behavior flow

## Contract Model

Use a provider/delegate contract owned by `:biometric`.

### Core Contracts In `:biometric`

`SoftwareBiometricPromptDelegate`

- created by a custom module for a specific biometric type
- owns setup/start/cancel/dispose lifecycle for one prompt session

Required shape:

- `fun shouldInstall(): Boolean`
- `fun install()`
- `fun start()`
- `fun cancel()`
- `fun dispose()`

`SoftwareBiometricPromptCallbacks`

- callback sink implemented by `:biometric`
- lets custom modules report generic events without the generic layer knowing module internals

Required events:

- `onHelp(message: CharSequence)`
- `onReady(extras: Bundle?)`
- `onFailure(result: AuthenticationResult)`
- `isPromptActive(): Boolean`

`SoftwareBiometricPromptFactory`

- generic entrypoint visible to `:biometric`
- custom modules expose one factory per software biometric flavor

Required shape:

- `val biometricType: BiometricType`
- `fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate?`

`SoftwareBiometricPromptHost`

- generic context object passed from `:biometric` to the custom module factory
- prevents leaking large implementation surfaces into custom modules

Required data:

- `Context`
- `BiometricPromptCompat.Builder`
- prompt root view reference for modules that require view installation
- `SoftwareBiometricPromptCallbacks`

## Registration Strategy

`BiometricPromptCompatDialogImpl` must stop referencing concrete classes directly.

Instead:

1. detect `primaryBiometricType`
2. ask a small registry in `:biometric` for a matching `SoftwareBiometricPromptFactory`
3. create the delegate through that factory
4. drive the returned delegate only through generic lifecycle methods

The registry may start as a simple in-process list resolved from classes available on the classpath. It does not need a broad plugin system.

## Dependency Direction

Allowed:

- `:biometric-custom-voice` -> `:biometric`
- `:biometric-custom-behavior` -> `:biometric`

Forbidden:

- `:biometric` -> `:biometric-custom-voice`
- `:biometric` -> `:biometric-custom-behavior`

This means the custom modules must provide registration through a mechanism that does not require `:biometric` to import their concrete classes. The first implementation should use a narrow classpath-discovery or explicit registrar pattern already acceptable in this repo. Reflection is acceptable only if it stays tiny, deterministic, and local to factory discovery. If a non-reflective registration point already exists in module bootstrap, prefer that.

## Concrete Migration Plan

### Phase 1: Introduce Generic Contracts

In `:biometric`:

- add generic software-biometric prompt contracts
- add a small registry/factory resolution layer
- update `BiometricPromptCompatDialogImpl` to use the contracts instead of concrete voice/behavior classes

At the end of this phase:

- no behavior change intended
- direct imports of `VoiceAutoCaptureController` and `BehaviorCaptureController` are removed from `BiometricPromptCompatDialogImpl`
- temporary adapters may still wrap old classes while files are being moved

### Phase 2: Move Voice Implementation

Move out of `:biometric`:

- `VoiceAutoCaptureController`
- `VoiceAutoCaptureSession`
- `VoicePromptData`

Move into `:biometric-custom-voice`:

- concrete voice delegate/factory
- voice-specific prompt data and capture flow

At the end of this phase:

- `:biometric` contains no concrete voice flow implementation
- voice route still works through generic callbacks and extras handoff

### Phase 3: Move Behavior Implementation

Move out of `:biometric`:

- `BehaviorCaptureController`

Move into `:biometric-custom-behavior`:

- behavior delegate/factory
- behavior-specific controller/session logic

At the end of this phase:

- `:biometric` contains no concrete behavior flow implementation

## File-Level Intent

### New files expected in `:biometric`

- software-biometric delegate contract
- software-biometric callback/host contract
- software-biometric factory/registry helper

These files should stay small and generic.

### New files expected in `:biometric-custom-voice`

- voice prompt delegate
- voice prompt factory
- moved `VoiceAutoCaptureController`
- moved `VoiceAutoCaptureSession`
- moved `VoicePromptData`

### New files expected in `:biometric-custom-behavior`

- behavior prompt delegate
- behavior prompt factory
- moved `BehaviorCaptureController`

## Behavioral Requirements

- no custom-module-specific class should be referenced directly from `BiometricPromptCompatDialogImpl`
- existing voice auto-capture UX must continue to use standard callbacks/UI, not a new custom dialog
- behavior explicit-mode capture must still be able to block auth start until setup is complete
- generic layer may forward `Bundle` extras, help messages, and auth failures, but should not interpret voice- or behavior-only state

## Non-Goals

- no redesign of biometric route-selection priority in this change
- no redesign of VAD/preprocessing logic in this change
- no new public external API for app consumers unless strictly required by the current internal contract split
- no broad plugin framework

## Risks

### Registration risk

If factory discovery is too implicit, setup may fail silently.

Mitigation:

- keep factory resolution narrow and explicit
- fail loudly in logs when a route is selected but no module factory is available

### Bundle/extras drift

Voice currently depends on extras handoff.

Mitigation:

- keep `Bundle` transfer generic at the contract boundary
- cover `onReady(extras)` in tests

### Lifecycle regression

Moving setup code out of `:biometric` can break dismiss/cancel/dispose ordering.

Mitigation:

- keep lifecycle methods explicit in the delegate contract
- add tests around cancel/dismiss/ready/start sequencing

## Testing Strategy

### `:biometric`

Add or update tests for:

- factory lookup by `BiometricType`
- dialog orchestration using a fake software-biometric delegate
- cancel/dismiss/dispose calling delegate lifecycle correctly
- voice/behavior route startup no longer depends on concrete imports

### `:biometric-custom-voice`

Add or update tests for:

- voice delegate producing `onReady(extras)` correctly
- voice delegate forwarding help/failure callbacks
- voice auto-capture cleanup on cancel/dispose

### `:biometric-custom-behavior`

Add or update tests for:

- behavior delegate install/start flow
- explicit-mode behavior setup delaying auth start until ready
- cleanup on dismiss/cancel

## Success Criteria

- `:biometric` contains only generic software-biometric contracts/orchestration
- concrete voice and behavior prompt-flow classes live in their own custom modules
- `BiometricPromptCompatDialogImpl` has no direct import/reference to voice or behavior concrete implementations
- module dependency direction remains one-way from custom modules to `:biometric`
- compile/test coverage proves voice and behavior flows still bootstrap through the generic contract
