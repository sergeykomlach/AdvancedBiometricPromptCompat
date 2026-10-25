# Voice State Machine Rearchitecture Design

Date: 2026-07-08
Repo: `C:\Users\skoml\StudioProjects_5\AdvancedBiometricPromptCompat`
Scope: `biometric`, `biometric-custom-voice`

## Goal

Replace the current event-driven `Voice` prompt flow with a state-driven architecture that cleanly separates:

- prompt UX state;
- microphone capture and speech segmentation;
- lockout and timeout policy;
- enrollment and authentication matching;
- shared Legacy or Software help delivery.

The result must solve three product problems together:

1. enroll guidance must be understandable for first-time users;
2. software and legacy help messages must reliably surface in combined enroll flows, including API 28;
3. auth must feel immediate, without delayed success after the user already stopped speaking.

## Non-goals

- no change to the public `Builder` API unless existing fields are insufficient;
- no rewrite of template storage format;
- no replacement of the current voice matching algorithm in this phase;
- no new visual overlay outside the existing compat prompt dialog;
- no modality-specific API 28 workaround that bypasses the shared software or legacy path.

## Current Problems

### 1. Prompt UX is event-driven, not state-driven

`VoiceAutoCaptureSession` currently emits short help snippets such as `Listening for voice`, `Voice detected`, `Voice sample 1 of 3 saved`, and `Checking voice`.

That works as a transport mechanism, but not as a user guidance system. The session does not know the difference between:

- initial instruction;
- retry instruction;
- capture confirmation;
- progress update;
- processing state.

This is why enroll text feels technical and fragmentary.

### 2. Voice mixes orchestration concerns in one flow

The current `Voice` pipeline blends:

- capture progress;
- speech detection;
- retry policy;
- local lockout policy;
- prompt help text;
- handoff to the real software-biometric auth flow.

Because these concerns are coupled, every tuning attempt on timeout, help, or lockout behavior tends to affect unrelated behavior.

### 3. Shared Legacy or Software help delivery is inconsistent

`VoicePromptDelegate` correctly emits `onHelp(...)`, and both `BiometricPromptGenericImpl` and `BiometricPromptApi28Impl` have code paths that can display help.

However, in combined enroll routing, especially on API 28, the active UI path does not reliably preserve software or legacy help semantics end to end. This is a platform-level prompt-routing issue, not a Voice-only issue.

### 4. Auth completion feels delayed

Even after a usable sample is captured and matched, `VoiceBiometricManager.finishWithSuccess()` currently delays success by `500 ms`.

That creates visible lag on top of any real matcher cost. The user can stop speaking, pause, and even begin another attempt before unlock fires.

## Design Summary

Introduce a dedicated Voice prompt state machine and move the current ad hoc event choreography into explicit, testable layers.

### New layers

1. `VoicePromptStateMachine`
   Owns prompt-visible state and user-facing guidance.

2. `VoiceCaptureOrchestrator`
   Owns microphone lifecycle, VAD-like streaming decisions, and sample acceptance or rejection.

3. `VoiceOutcomePolicy`
   Owns inactivity timeout, recoverable retry behavior, capture-level anti-jamming lockout, and match-level failed-attempt escalation boundaries.

4. `VoiceBiometricManager`
   Stays responsible for validation, embedding extraction, enrollment persistence, and matching.

5. Shared `SoftwarePromptHelpRouter`
   Owns reliable delivery of software or legacy help messages into the currently active compat dialog path, including API 28 combined enroll.

This rearchitecture deliberately keeps matching logic and storage logic separate from prompt orchestration.

## Target Architecture

### `VoicePromptState`

Introduce an explicit sealed state model for the prompt:

- `Idle`
- `Preparing`
- `EnrollInstruction(step, total, phrase, retryReason)`
- `AuthInstruction(phrase, retryReason)`
- `Listening(step, total)`
- `SpeechDetected(step, total)`
- `ProcessingCapture(step, total)`
- `ReadyForManager(step, total)`
- `Matching`
- `RecoverableError(message, resumeState)`
- `Timeout`
- `Lockout`
- `Success`
- `FatalError`

The prompt should never infer meaning from a raw callback string again. Instead, strings are rendered from state.

### `VoicePromptMessageResolver`

Create a dedicated resolver that maps `VoicePromptState` plus session context into localized strings.

It should generate separate categories of text:

- instruction text
- status text
- retry text
- completion text

Examples:

- first enroll step:
  `Щоб зареєструвати голос, скажіть вашу кодову фразу природним голосом протягом 2-4 секунд. Спроба 1 із 3.`
- repeated enroll step:
  `Повторіть ту саму кодову фразу ще раз. Спроба 2 із 3.`
- auth start:
  `Скажіть кодову фразу природним голосом.`
- processing:
  `Перевіряємо голос`
- recoverable short sample:
  `Фраза вийшла закороткою. Повторіть ту саму фразу ще раз.`

If a phrase exists in builder extras, the resolver must incorporate it into instruction text.

### `VoiceCaptureOrchestrator`

This replaces the current controller-centric flow as the owner of microphone-session progression.

Responsibilities:

- start and stop `AudioRecord`;
- stream chunks into the detector;
- translate detector output into capture outcomes;
- report capture outcomes to `VoicePromptStateMachine`;
- prepare voice PCM extras once enough accepted samples exist.

It must not render strings directly. It returns structured outcomes only.

### `VoiceCaptureOutcome`

Introduce a structured outcome contract:

- `Accepted(sample, hadSpeechActivity, metrics)`
- `Rejected(reason, qualityIssue, hadSpeechActivity, metrics)`
- `Timeout`
- `Fatal(message)`

This becomes the boundary between streaming audio logic and prompt-state logic.

### `VoiceOutcomePolicy`

Move retry, timeout, and lockout decisions out of the controller and into a dedicated policy layer.

Rules:

- inactivity timeout is based on absence of meaningful session progress for `30 s`;
- `NO_SPEECH` and incomplete partials do not count as anti-jamming failures;
- anti-jamming capture lockout counts only repeated suspicious capture outcomes;
- match-level failed attempts stay owned by `VoiceBiometricManager`;
- capture-level and match-level lockout counters must remain separate.

This removes the current ambiguity where silence, low-quality voice, and suspicious replay-like input can all be handled through overlapping paths.

## Shared Legacy or Software Help Routing

### Problem statement

Software prompt delegates already emit help messages, but combined enroll flows can still fail to present them consistently through the active dialog implementation.

### Proposed fix

Add a shared routing layer for software-prompt help delivery instead of leaving each prompt implementation to infer whether the message belongs to the current active flow.

The routing layer must:

- preserve software help through `combined` enroll routing;
- work for `BiometricPromptGenericImpl`;
- work for `BiometricPromptApi28Impl`;
- remain modality-agnostic;
- avoid Voice-specific branching in the compat dialog code.

The active dialog must subscribe to structured prompt-state updates from the selected software delegate and always display the latest help or status state while the software delegate owns the prompt.

## Enroll UX Flow

### First sample

1. prompt enters `EnrollInstruction(1, 3, phrase, null)`
2. status becomes instructional, not technical
3. when capture begins, prompt enters `Listening(1, 3)`
4. if speech is seen, prompt enters `SpeechDetected(1, 3)`
5. once sample is closed, prompt enters `ProcessingCapture(1, 3)`
6. if accepted, prompt emits a confirmation and advances to sample `2`

### Repeat samples

Samples `2` and `3` reuse the same state sequence, but the instruction explicitly says to repeat the same phrase.

### Recoverable errors

Recoverable enroll outcomes must return the prompt to the correct instruction state, not just post a generic low-level sample failure.

## Auth UX Flow

### Auth states

1. `AuthInstruction`
2. `Listening`
3. `SpeechDetected`
4. `ProcessingCapture`
5. `Matching`
6. `Success` or failure state

### Auth latency rules

- remove the fixed `500 ms` success delay;
- move from `Listening` to `ProcessingCapture` immediately after capture is closed;
- move to `Matching` immediately after extras are ready;
- do not keep the prompt visually in a listening state while matching already runs.

This change is intentionally user-visible and should make unlock happen closer to the moment the user expects it.

## Implementation Plan

### Phase 1. Prompt state model

- add `VoicePromptState`
- add `VoicePromptMessageResolver`
- add session context model for step count, phrase, and retry reason
- keep existing flow wired, but render help through resolver

### Phase 2. Capture orchestration split

- extract microphone and detector flow into `VoiceCaptureOrchestrator`
- convert controller callbacks into structured `VoiceCaptureOutcome`
- remove direct string rendering from capture logic

### Phase 3. Shared help routing

- add shared software-prompt help delivery path in compat dialog or prompt implementations
- verify API 28 and generic dialog both consume the same software state stream
- ensure combined enroll does not lose software help ownership

### Phase 4. Timeout and lockout rework

- introduce `VoiceOutcomePolicy`
- migrate current capture-level anti-jamming decisions into policy
- explicitly separate inactivity timeout from suspicious repeated capture rejection

### Phase 5. Auth latency cleanup

- remove fixed success delay
- move state transitions earlier
- verify prompt no longer appears stuck in listening mode after the user stops speaking

## Candidate Files

### Voice-specific

- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt`
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureController.kt`
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt`
- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt`
- `biometric-custom-voice/src/main/res/values*/strings.xml`

### Shared software prompt path

- `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptHost.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`

## Testing Strategy

### Unit tests

Add focused unit coverage for:

- prompt-state to message rendering;
- enroll step progression;
- recoverable error to resume-state mapping;
- inactivity timeout versus capture-level lockout policy;
- suspicious quality outcomes that should count toward anti-jamming;
- success path without fixed artificial delay.

### Compile verification

Primary proof commands:

- `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin`
- `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin`
- `.\gradlew.bat :biometric:compileDebugKotlin`
- `.\gradlew.bat :biometric:compileDebugUnitTestKotlin`

`testDebugUnitTest` remains a secondary signal on this machine because of the known local `VS` runner issue.

## Risks

- prompt-state refactor may expose hidden assumptions in current dialog timing;
- shared API 28 help routing may reveal other software modalities with the same bug;
- removing the fixed success delay may surface real matcher cost that was previously masked;
- a large state-machine refactor can accidentally change lockout semantics if policy boundaries are not kept explicit.

## Self-review

This spec intentionally keeps scope limited to prompt orchestration, shared help routing, and latency or policy cleanup.

It does not widen into:

- speech-to-text;
- new public APIs;
- matcher replacement;
- overlay redesign.

The architecture, phases, and testing strategy are aligned around one implementation objective: make Voice prompt behavior explicit, predictable, and reusable across both generic and API 28 legacy-software flows.
