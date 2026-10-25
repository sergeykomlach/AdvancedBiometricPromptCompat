# Advertise supported authentication routes

`BiometricPromptCompat.getAvailableAuthRequests()` now filters candidates by route capability instead of advertising every hardware-detected sensor/API pair. Strict system-prompt FACE/FINGERPRINT requests are excluded when they cannot establish sensor identity. AUTO retains sensor-specific legacy hardware when present and respects existing software-priority/native-face preferences. ANY remains available so system FACE capability is not removed globally.

This is capability filtering, not a guarantee that authentication succeeds: enrollment, permission, lockout and other runtime states still apply. Raw manager hardware snapshots are unchanged, avoiding loss of hardware information used by ANY and setup. Directly constructed unsupported requests retain their explicit error; callers cannot bypass checks by avoiding the list.

Changed biometric files: BiometricPromptCompat.kt, new AuthRequestSupportPolicy.kt, new AuthRequestSupportPolicyTest.kt. App source unchanged in this turn; its existing list consumer receives the filtered requests.

Command: `.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.AuthRequestSupportPolicyTest" :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain`. PASS in 15s, 5 targeted tests; scoped source diff check PASS. No full suite, release/R8 or new sensor enrollment.

APK SHA256: `CAE279F223FC11F530B6BC7EAA2978D843BACCA4E73426C05EAE3457FE4D9AE9`. `adb -s 192.168.0.10:34555 install -r app/build/outputs/apk/debug/app-debug.apk`: Success. Pixel UI tree confirms standard entries AUTO/ANY, AUTO/FINGERPRINT, AUTO/VOICE, LEGACY/FACE, LEGACY/FINGERPRINT, LEGACY/VOICE. AUTO/FACE and both strict BIOMETRIC_API FACE/FINGERPRINT entries are absent. This validates list presentation, not a fresh authentication replay of every retained entry. Diagnostic QA controls remain separate.
