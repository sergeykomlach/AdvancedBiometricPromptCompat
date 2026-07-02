# Software Biometric Production Hardening Design

## Context

`AdvancedBiometricPromptCompat` already contains four software-biometric modules with different maturity levels:

- `biometric-custom-voice`
- `biometric-custom-behavior`
- `biometric-custom-face-tf`
- `biometric-zkfinger`

Recent work already improved module separation for `voice` and `behavior`, and the current tree now has a generic software prompt-flow contract in `:biometric`. However, the overall software-biometric surface is still not uniformly production-ready. Maturity currently varies across:

- user-facing localization coverage
- prompt/help/error callback semantics
- preflight failure delivery
- lifecycle/cancellation behavior
- lockout and retry behavior
- custom-module boundaries
- test coverage for non-happy paths

The user wants the entire software-biometric scope hardened to the maximum practical “production ready” level, not just one module at a time.

## Goal

Bring all software-biometric modules (`Voice`, `Behavior`, `FaceTF`, `ZKFinger`) to one consistent production-hardening standard, with a final readiness summary per module and for the shared software-biometric platform.

## Non-Goals

This scope does not automatically include large algorithmic rewrites when they are not required for production hardening. In particular:

- no full DSP/ML redesign for voice denoise or VAD unless a concrete correctness issue requires it
- no model retraining or model asset replacement for `FaceTF`
- no vendor SDK replacement for `ZKFinger`
- no unrelated UI redesign beyond production-hardening fixes
- no broad refactoring outside the software-biometric path

Algorithmic issues are still in scope for analysis and may receive targeted fixes if they materially affect correctness, reliability, or user-facing behavior.

## Production-Ready Definition

A software-biometric module is considered production-ready in this repo only if all of the following are true:

1. Every user-facing string comes from module-owned Android resources unless it is clearly a non-user internal constant.
2. Preflight, capture, validation, lockout, cancellation, and success states are delivered through correct upstream callbacks with no silent failure paths.
3. Module-specific implementation details stay inside the module boundary and do not leak into generic `:biometric` orchestration.
4. Hardware/software availability checks and permission checks fail predictably and produce stable upstream-facing messages.
5. Lockout and retry behavior is internally consistent and does not accidentally misclassify recoverable states as fatal states or vice versa.
6. There is targeted test coverage for state-heavy and failure-heavy flows, not just happy-path helpers.
7. The touched modules compile together with the app and their direct unit-test compile slices remain green.

## Recommended Approach

Use one cross-cutting hardening program instead of isolated per-module cleanup.

Why this approach:

- the modules already share one generic orchestration layer in `:biometric`
- the biggest remaining issues are consistency issues across modules, not only local bugs
- module-by-module patching without a shared bar would likely reintroduce drift

The implementation should therefore proceed in two coordinated layers:

1. define and enforce one shared software-biometric hardening baseline
2. apply targeted module-specific fixes to close the remaining gaps

## Architecture

### 1. Shared Baseline In `:biometric`

The generic software-biometric surface in `:biometric` must remain the only orchestration layer and should own only generic responsibilities:

- software prompt host/delegate/factory contracts
- prompt lifecycle coordination
- upstream callback wiring
- route selection and generic software-biometric result mapping

It must not regain concrete `voice`, `behavior`, `FaceTF`, or `ZKFinger` implementation logic.

The shared hardening pass should verify:

- prompt delegate contract is sufficient for all software modules
- pre-auth failures are surfaced consistently
- generic route selection does not accidentally depend on module-specific assumptions
- shared failure translation does not introduce incorrect reasons such as false `LOCKED_OUT`

### 2. Module-Owned Production Surfaces

Each software-biometric module must fully own:

- its user-facing strings
- its module-specific help/error/success text
- its capture pipeline and custom validation logic
- its hardware or backend preflight logic
- its targeted tests

This is especially important for:

- `Voice` auto-capture prompts and VAD-driven help text
- `Behavior` explicit capture UI and sample packaging
- `FaceTF` camera and anti-spoofing state machine
- `ZKFinger` USB permission and device lifecycle flow

## Workstreams

### Workstream A: Shared Software-Biometric Audit And Baseline

Audit all software-biometric paths against one checklist:

- hardcoded user-facing strings
- missing module resources
- silent or dropped callback paths
- inconsistent help vs error usage
- incorrect cancellation semantics
- inconsistent lockout delivery
- generic/shared code that still knows too much about a concrete module

Expected outputs:

- one normalized defect inventory
- a minimal shared baseline adjustment set in `:biometric`

### Workstream B: Voice Hardening

`Voice` should be hardened around the new auto-capture architecture.

Required focus:

- replace remaining hardcoded progress/help strings with resources
- verify all auto-capture stages use standard callbacks only
- ensure enroll and auth states are distinct and correctly messaged
- verify preflight errors, sample validation, embedding validation, and lockout semantics
- verify no prompt path silently proceeds without usable audio payload
- confirm voice-specific VAD/preprocessing remains fully inside `biometric-custom-voice`

Target outcome:

- `Voice` is no longer merely structurally improved, but operationally polished

### Workstream C: Behavior Hardening

`Behavior` now has module separation, but it still needs productization-level consistency.

Required focus:

- validate that explicit capture UX uses only module-owned resources
- verify no ugly or inconsistent fallback surfaces remain
- verify capture preparation, sample packaging, and validation messages are stable
- verify lockout, cancel, and missing-sample semantics
- add focused tests where the current behavior flow is state-sensitive but under-tested

Target outcome:

- `Behavior` feels like a first-class software biometric instead of a special-case addon

### Workstream D: FaceTF Hardening

`FaceTF` is already the strongest software module, but it has the highest architectural complexity and therefore the highest regression risk.

Required focus:

- fix any preflight error paths that can drop callbacks before `authCallback` is attached
- verify camera blocked / camera busy / no model / no enroll / lockout flows
- verify anti-spoofing and recognition-state transitions remain coherent
- verify config defaults are sane and consistently enforced
- add targeted tests for callback-state and state-machine behavior, not just helper utilities

Target outcome:

- `FaceTF` remains the strongest module and loses known integration fragility

### Workstream E: ZKFinger Hardening

`ZKFinger` is already structurally strong, but it must be normalized against the same production bar.

Required focus:

- verify USB permission request and polling flow
- verify detach / permission denied / sensor unavailable / lockout messaging
- verify callback semantics match the shared standard
- verify module strings and user-facing copy are complete and consistent
- add focused tests for hardware detection / permission / lifecycle helpers where feasible

Target outcome:

- `ZKFinger` stays reference-grade and does not become the outlier after the rest are hardened

## Readiness Summary Format

The final deliverable must include a readiness summary using the same categories for each module:

- `Architecture`
- `User-Facing UX / Localization`
- `Failure Semantics`
- `Lifecycle / Cancellation`
- `Lockout / Retry`
- `Test Coverage`
- `Compile / Verification`
- `Residual Risks`

Each module must receive one status:

- `Production Ready`
- `Near Production Ready`
- `Needs More Hardening`

This summary is not optional. It is part of the scope.

## Testing And Verification Strategy

Verification must be compile-first and targeted, since some local Gradle test runtime tasks in this repo can still be noisy.

Baseline verification should include:

- `:biometric:compileDebugKotlin`
- `:biometric:compileDebugUnitTestKotlin`
- `:biometric-custom-voice:compileDebugKotlin`
- `:biometric-custom-voice:compileDebugUnitTestKotlin`
- `:biometric-custom-behavior:compileDebugKotlin`
- `:biometric-custom-behavior:compileDebugUnitTestKotlin`
- `:biometric-custom-face-tf:compileDebugKotlin`
- `:biometric-custom-face-tf:compileDebugUnitTestKotlin` when targeted tests are added
- `:biometric-zkfinger:compileDebugKotlin`
- `:biometric-zkfinger:compileDebugUnitTestKotlin` when targeted tests are added
- `:app:compileDebugKotlin`
- `git diff --check`

Where direct unit tests are introduced, they should target:

- callback-state correctness
- parsing or packaging helpers
- lockout or state support helpers
- module-specific validation logic

They should not attempt to simulate impossible full-device flows when a smaller seam can prove the behavior.

## Risks

### Risk 1: Drift Between Modules

If hardening is done as isolated patches, the modules will remain inconsistent.

Mitigation:

- establish shared production criteria first
- keep one final readiness summary across all modules

### Risk 2: Over-Refactoring

It would be easy to turn this into a broad rewrite.

Mitigation:

- keep fixes tightly attached to production criteria
- do not redesign algorithms or public APIs unless the current design blocks readiness

### Risk 3: False Confidence From Happy-Path Compile Success

Compile success alone is insufficient for stateful biometric modules.

Mitigation:

- add targeted tests for callback-heavy and failure-heavy paths
- explicitly summarize residual risks per module

## Recommended Execution Order

1. shared audit and defect inventory
2. shared `:biometric` baseline fixes
3. `Voice` hardening
4. `Behavior` hardening
5. `FaceTF` hardening
6. `ZKFinger` hardening
7. full verification
8. final readiness summary

This order keeps the generic platform stable first, closes the weakest modules next, then hardens the strongest but most stateful modules last.

## Success Criteria

This design is successful only if, at the end:

- all software-biometric modules meet the shared production baseline
- no user-facing hardcoded strings remain in software-biometric production flows
- callback and failure semantics are consistent across modules
- compile verification is green for all touched modules and app integration
- the repo has a concrete readiness summary instead of an implicit “should be better now”
