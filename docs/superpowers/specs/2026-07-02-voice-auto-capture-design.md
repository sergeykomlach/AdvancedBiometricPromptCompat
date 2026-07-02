# Voice Auto-Capture Design

## Goal

Replace the current custom `Voice` prompt overlay with a headless, fully automated capture flow that uses the existing prompt UI and standard biometric callbacks.

The new flow must:

- remove all custom `Voice` buttons, panels, and overlays;
- automatically capture `3` voiced samples for `enroll`;
- automatically capture `1` voiced sample for `auth`;
- use `onAuthenticationHelp(...)` for progress and guidance;
- keep `VoiceBiometricManager` focused on validation, enrollment, scoring, and lockout logic.

## Background

The current `Voice` implementation works only because `VoiceCaptureController` injects a dedicated overlay into the compat dialog. That solves the missing-sample problem, but it overloads the prompt UX with a second interaction model.

At the same time, the repository already has useful pieces that should be reused instead of replaced:

- `VoicePromptData.kt` packages PCM and phrase data into request extras;
- `VoiceBiometricManager.kt` handles permission checks, template management, scoring, lockout, and localized errors;
- `VoiceAudioPreprocessor.kt` already performs useful quality checks such as silence trimming, clipping detection, and voiced-duration checks.

The missing piece is a headless capture orchestrator that can collect audio before `VoiceBiometricManager.authenticate(...)` starts.

## Scope

### In scope

- remove `Voice` custom prompt UI from the compat dialog flow;
- add headless microphone capture for voice auth and enroll;
- automatically segment speech from silence and background noise;
- automatically collect `3` enrollment samples and `1` authentication sample;
- post status and retry guidance through standard help callbacks;
- preserve the existing `extras` contract consumed by `VoiceBiometricManager`;
- add focused tests for segmentation and orchestration behavior.

### Out of scope

- new speech-to-text dependencies;
- phrase recognition or semantic verification of spoken content;
- waveform, meter, overlay, or any other custom visual UI;
- changing template format or the core matching approach in `VoiceBiometricManager`;
- adding a new public API unless current builder APIs are insufficient.

## User Experience

### Enroll

When `BIOMETRIC_VOICE` is selected for enrollment and the caller did not provide voice input explicitly:

1. The standard biometric prompt opens as usual.
2. A short vibration is triggered.
3. The prompt status is updated through `onAuthenticationHelp(...)` with a message like `Voice sample 1 of 3: recording started`.
4. The system listens to the microphone without showing any custom controls.
5. Once real speech is detected and a complete utterance is captured, the sample is stored in memory.
6. The prompt posts `Voice sample 1 of 3 saved`.
7. The flow automatically repeats for samples `2` and `3`.
8. After the third valid sample is collected, the controller packages the batch into `extras` and starts the normal software-biometric enrollment.

If a sample is too short, too quiet, or no speech is detected before timeout, the prompt posts a help/error-style message and retries the current sample instead of advancing.

### Authentication

When `BIOMETRIC_VOICE` is selected for authentication and the caller did not provide voice input explicitly:

1. The standard biometric prompt opens as usual.
2. The prompt posts a help message like `Listening for voice`.
3. The system listens to the microphone without showing custom controls.
4. Once a complete voiced utterance is captured, the sample is packed into `extras`.
5. Normal `VoiceBiometricManager.authenticate(...)` starts automatically.
6. Success and failure are reported through the standard existing callbacks.

## Architecture

### Design summary

Introduce a headless capture orchestrator in the `biometric` module. It owns microphone capture and speech segmentation, but it does not own voice scoring or template storage.

The orchestrator prepares voice input before software authentication starts. Once samples are ready, control returns to the existing `VoiceBiometricManager` path.

### Responsibilities

#### `BiometricPromptCompatDialogImpl`

- decides whether the selected route is `BIOMETRIC_VOICE`;
- decides whether voice input is already present in builder `extras`;
- starts the headless auto-capture session when needed;
- delays `startAuth()` until auto-capture has prepared a sample or sample batch;
- disposes the session on dialog dismissal or cancel.

#### New headless `VoiceAutoCaptureSession`

- owns `AudioRecord` lifecycle;
- streams microphone PCM into a small state machine;
- segments spoken utterances from silence and noise;
- emits progress/help messages through the existing prompt callback path;
- packages valid samples with `buildVoiceExtras(...)`;
- signals the dialog when auth/enroll can proceed.

This class must not create any `View`, overlay, button, or extra dialog.

#### `VoiceAudioPreprocessor`

- remains the source of truth for post-capture quality validation;
- gains reusable streaming-oriented helpers or constants needed by the new voice-activity state machine.

It should not become responsible for prompt orchestration or UI.

#### `VoiceBiometricManager`

- remains unchanged in responsibility;
- receives already captured PCM through `extras`;
- continues to handle permissions, quality failure messages, enrollment, scoring, and lockout.

## Data Flow

### Enroll

1. Prompt is shown.
2. `BiometricPromptCompatDialogImpl` detects `BIOMETRIC_VOICE`.
3. If `extras` already contain valid voice input, normal auth starts immediately.
4. Otherwise, the dialog starts `VoiceAutoCaptureSession`.
5. The session collects one voiced utterance at a time until `3` samples are accepted.
6. `VoicePromptData.buildVoiceExtras(...)` stores the batch into builder extras.
7. The dialog calls normal `startAuth()`.
8. `VoiceBiometricManager.authenticate(...)` receives the prepared batch and performs enrollment.

### Authentication

1. Prompt is shown.
2. `BiometricPromptCompatDialogImpl` detects `BIOMETRIC_VOICE`.
3. If `extras` already contain valid voice input, normal auth starts immediately.
4. Otherwise, the dialog starts `VoiceAutoCaptureSession`.
5. The session captures one voiced utterance.
6. `VoicePromptData.buildVoiceExtras(...)` stores the sample into builder extras.
7. The dialog calls normal `startAuth()`.
8. `VoiceBiometricManager.authenticate(...)` scores the sample and returns the result.

## Streaming voice detection

### Required behavior

The system must detect real speech, not blindly accept a fixed-duration recording.

That means the session has to:

- tolerate brief pauses between words;
- avoid ending the sample on short silence;
- stop once the speaker has actually finished speaking;
- reject silence-only or noise-only captures.

### State machine

Recommended states:

- `CALIBRATING_NOISE`
- `WAITING_FOR_SPEECH`
- `CAPTURING_SPEECH`
- `ALLOWING_SHORT_PAUSE`
- `UTTERANCE_COMPLETE`
- `FAILED_TIMEOUT`

### Detection model

Keep the first version simple and deterministic:

- read mono PCM16 from `AudioRecord` at `16 kHz`;
- convert to normalized float PCM;
- process in short fixed frames;
- estimate a rolling noise floor from low-energy frames;
- mark frames as voiced when RMS crosses a threshold relative to noise floor;
- treat brief silence as an internal pause;
- finalize only when silence exceeds a longer end-of-utterance threshold.

The design intentionally avoids ML VAD for now. The repo already has enough DSP-style heuristics to build a useful first automated version.

## Callback behavior

The prompt should communicate progress only through standard biometric help messages.

Recommended messages:

- `Voice sample 1 of 3: recording started`
- `Speak now`
- `Voice detected`
- `Voice sample 1 of 3 saved`
- `No speech detected, try again`
- `Sample too short, try again`
- `Processing voice sample`
- `Listening for voice`
- `Verifying voice`

Failures that prevent capture from completing must surface through standard failure/error callbacks, not through hidden internal retries forever.

## Error handling

### Microphone / permission

- if microphone hardware is missing, keep using normal hardware-unavailable errors;
- if `RECORD_AUDIO` is missing, keep using the existing localized permission error;
- if `AudioRecord` cannot be initialized, surface a normal processing error.

### Capture failure

- silence timeout retries the same sample slot;
- sample too short retries the same sample slot;
- user cancellation immediately stops recording and clears partial in-memory samples;
- capture failure must not be translated to `LOCKED_OUT`;
- lockout remains owned by `VoiceBiometricManager` scoring failures, not by pre-capture orchestration.

## Security and privacy

- raw PCM must stay in memory only for the active session;
- raw audio must never be logged;
- only the already defined normalized extras contract may be passed forward;
- partial samples must be cleared on cancel, dismiss, or failure;
- no files should be written to disk;
- microphone capture must stop immediately when the prompt closes.

## Testing

### Unit tests

- speech segmentation accepts a voiced utterance with short pauses;
- speech segmentation rejects silence-only input;
- speech segmentation rejects very short utterances;
- enrollment orchestration waits for `3` accepted samples before proceeding;
- auth orchestration proceeds after `1` accepted sample;
- pre-supplied voice extras bypass auto-capture;
- cancel/dismiss clears buffered samples and stops capture;
- capture-side failure does not get remapped to `LOCKED_OUT`.

### Build verification

Use the same repo-safe proof gates already known to work here:

- `:biometric-custom-voice:compileDebugKotlin`
- `:biometric:compileDebugUnitTestKotlin`
- `:app:compileDebugKotlin`

If local unit test execution still hits the known `Could not find or load main class VS` runner issue, report that explicitly and separate it from compile proof.

## File impact

Expected primary files:

- modify `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
- remove or stop using `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceCaptureController.kt`
- add a new headless capture/orchestration class under `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/`
- extend `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAudioPreprocessor.kt`
- keep `biometric/src/main/java/dev/skomlach/biometric/compat/VoicePromptData.kt` as the extras packaging helper
- add tests in `biometric` and `biometric-custom-voice`

## Callback wiring decision

The headless capture orchestration must not update dialog widgets directly.

Instead, it must depend on a small callback interface implemented by the dialog flow. That interface should expose only the minimum operations needed for orchestration, such as:

- post a help/status message;
- signal that capture completed and auth may start;
- signal a fatal capture-side error;
- query whether the prompt is still active.

This keeps the recorder/orchestrator testable and avoids hard-coupling it to dialog widget internals.

## Non-goals check

This design does not reintroduce any hidden visual voice UI. It also does not attempt to solve semantic phrase recognition, multilingual ASR, or heavy ML VAD in the same change.

That keeps the change focused on the actual user goal: a smarter `VoiceAuth` flow with minimal user effort using the existing biometric UI and callbacks.
