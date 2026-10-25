# Behavior Fragment and Behavior-first Authentication Implementation Plan

> **For agentic workers:** Execute inline; only the main agent edits and runs Gradle. Read-only agents review integration and lifecycle.

**Goal:** Replace the behavior activation overlay with an automatic themed child fragment, preserve consumer-owned input, disable completion during capture, and authenticate behavior before remaining modalities.

**Architecture:** A flow-owned coordinator executes behavior-only then the existing backend for the remaining execution scope. One outer completion remains authoritative. A child Fragment renders the existing capture mechanics in the compat content slot; temporary input policy preserves caller configuration.

**Tech Stack:** Kotlin, AndroidX Fragment/AppCompat, JUnit, Robolectric.

**Spec:** User-approved design in this conversation, refined by the latest explicit ANY/ALL requirements.

## Global Constraints

- No commit, push or staging. Preserve existing dirty work on main.
- ANY behavior success finishes immediately; behavior failure starts remaining biometrics.
- ALL executes remaining biometrics after either behavior outcome; both stages must succeed.
- Explicit user cancellation cancels the whole operation. Late and duplicate callbacks are ignored.
- Enrollment, crypto requirements, accessibility policy and protected storage remain fail-closed.
- Silent authentication does not acquire new interactive UI.
- No clean or root-wide Gradle tasks; one Gradle process, main agent only.

## Review Focus

- Animated dialog teardown must finish before opening the next backend.
- ALL behavior failure cannot disappear when remaining availability changes.
- Consumer password/numeric/multiline fields and custom listeners must survive restoration.
- Autofill suppression must be independent of accessibility whitelist.
- Fragment destruction and cancellation must retire capture tokens and listeners.

## Task 1: Capture UI and input policy

- [x] Add failing controller regressions for automatic content, visible host and suppressed completion; run affected class.
- [x] Add BehaviorCaptureFragment and optional fragment-host capability without changing existing public host constructor.
- [x] Replace B/overlay with themed content, preserve touch keyboard features and signature capture; restore caller input policy on disposal.
- [x] Add focused input-policy and Fragment lifecycle regressions; run behavior tests.

## Task 2: Behavior-first execution

- [x] Add failing coordinator tests covering ANY/ALL outcomes, cancellation, deferred teardown, duplicate/stale callbacks and missing remaining routes.
- [x] Add internal execution scope independent of availability, a flow-owned coordinator, and a cached backend wrapper.
- [x] Scope Generic/API28/Silent execution and compat delegate selection; preserve enrollment paths.
- [x] Ensure mismatch becomes a completed first-stage result and intermediate UI close is absorbed.
- [x] Run focused core regressions and review both routing paths.

## Task 3: Integration and verification

- [x] Update behavior README/QA for automatic capture and consumer completion suppression.
- [x] Run target core tests and behavior regression suite sequentially.
- [x] Run `.\gradlew.bat :app:assembleDebug --console=plain`.
- [x] Review scoped changes; verify index unchanged and record exact commands/results/APK hash.

## Execution ledger

- Ruling: Continue in the user's dirty main checkout; existing uncommitted changes are necessary context. Snapshot saved under `.tmp/behavior-fragment-*`; no worktree reset or Git mutations.
- Ruling: The existing full-operation permission/model preflight is retained; behavior-first orders authentication and UI stages. It does not introduce a second flow or weaken authorization preflight.
- Ruling: Keep current capture feature semantics and stored profiles; do not replace the touch keyboard with a different IME timing model as part of UI work.
