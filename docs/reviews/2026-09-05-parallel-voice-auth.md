# AUTO/ANY: parallel system prompt and software authentication

User reported voice unavailable in AuthFlow AUTO/ANY. The system prompt started while the voice route waited for dialog-provided extras; ANY then completed on system success. Restoring foreground icons did not restore voice execution.

The proposed alternate-method button was fully removed at the user's request. Auth now creates opted-in background software delegates alongside the system prompt. Voice captures through its existing controller and onReady(extras), then the Legacy batch authenticates the prepared sample. Standard application-context Toast feedback supplies instructions without a second dialog. Cancellation/disposal invalidates capture callbacks and releases capture/Toast resources. Setup enrollment retains its hardware-confirmation-first flow.

Changed modules/files:
- biometric: BiometricPromptApi28Impl.kt; SoftwareBiometricPromptFactory.kt (separate BackgroundSoftwareBiometricPromptFactory marker, preserving the existing interface contract); ParallelSoftwareCapture.kt; ParallelSoftwareCaptureTest.kt.
- biometric-custom-voice: VoicePromptFactory.kt opts into background preparation.
- This report and the regression backlog. No app/QA-button or resource changes.

Verification commands:
```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.ParallelSoftwareCaptureTest" --tests "dev.skomlach.biometric.compat.impl.Api28EnrollmentCompletionTest" :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain
.\gradlew.bat :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptFactory.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptFactory.kt
adb -s 192.168.0.10:34555 install -r app/build/outputs/apk/debug/app-debug.apk
```

First command PASS (8s, 6 tests, zero failures). The second PASS (9s) verifies the subsequent binary-compatibility marker change; unchanged policy tests were not repeated. Scoped diff check PASS.

Pixel 9 Pro Fold / Android 17 / SDK 37, process 15771, APK SHA256 D7E47745AAD3603560B1CA2A000F003F1192A3D7DCC8B8DAB470324AF31B19C3:
- 20:39:44.272 system stage with primary fingerprint/face and secondary voice; .277 startParallelCapture voice.
- 20:39:59.044 Legacy authentication starts module 329184405 (VOICE); .153 success; .241 outer result BIOMETRIC_VOICE, crypto disabled.
- User confirms Toast instructions visible and voice authorization successful.
- Second attempt 20:40:44.603 starts capture; 20:40:45.333 Back yields ERROR_USER_CANCELED, followed by outer canceled/closed callbacks. RECORD_AUDIO appops changed from running to a completed 723ms duration.

Limits: background preparation is explicit opt-in, currently voice. Providers requiring interactive Views retain their existing staged path. A mixed Legacy batch waits for background extras because LegacyBiometric accepts only one active batch. No full suite, release/R8, other OEM, encrypted parallel auth, ALL, or fresh enrollment runtime replay. This is not universal closure of BIO-REG-007.

Final APK after marker-interface compatibility change: SHA256 2CD20D28A281A3F6F1DF03D5215C47A345684DA3D9E6A7843F8FCFF10F6363D0, installed successfully. Process 16810: repeated AUTO/ANY starts voice capture with RECORD_AUDIO running; Back stops the operation. Successful spoken authorization and Toast visibility were verified on the preceding APK above; the final marker-only change received a startup/cancellation smoke check.
