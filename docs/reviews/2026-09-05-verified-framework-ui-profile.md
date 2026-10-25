# Explicit local framework UI profile

This is a persisted integration declaration, NOT automatic system-dialog detection. It makes the ordinary AUTO/FINGERPRINT entry use the already verified system-owned framework route on this installation, without changing AUTO/ANY routing or model/API whitelists. Unknown devices remain UNKNOWN until explicitly verified.

`FrameworkFingerprintUiProfile.setVerifiedOwner(SYSTEM)` records an explicit verification. UNKNOWN removes the record. Storage uses AtomicFile in noBackupFilesDir. Matching requires the exact firmware fingerprint, SDK, configured biometric UI package, package version and lastUpdateTime. Missing/unreadable/corrupt records or unavailable runtime identity return UNKNOWN. Firmware/provider changes invalidate the decision. Per-request explicit override wins; software/OEM backends and missing-UI exceptions do not inherit a framework SYSTEM record. No authentication/crypto acceptance changes.

Changed modules/files:
- biometric: new FrameworkFingerprintUiProfile.kt and FrameworkFingerprintUiProfileTest.kt; AuthenticationUiPolicy.kt and BiometricPromptGenericImpl.kt consume the record.
- app: FirstFragment.kt adds two debug profile controls. Existing standard buttons and API28 route remain intact.

Verification: `.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.FrameworkFingerprintUiProfileTest" --tests "dev.skomlach.biometric.compat.impl.AuthenticationUiPolicyTest" --tests "dev.skomlach.biometric.compat.impl.LegacyUiStagesTest" :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain` — PASS in 8s, 18 tests. Scoped source diff check PASS. No full suite/release/R8.

Installed APK SHA256 `438B85AF279BCE9F46F271A4E4EA88BFC7D76BC8F9A9D25A27BEC3C13444A928`, Pixel serial 4A221FDKD001JR, process 8312, `adb -s 192.168.0.10:34555 install -r app/build/outputs/apk/debug/app-debug.apk` Success.

Before profile registration, ordinary AUTO/FINGERPRINT logged UNKNOWN/doNotShowDialog=false at 20:09:06. The SYSTEM profile was explicitly saved via the debug control, based on the user's earlier system-only UI verification. Read-back confirmed the runtime-bound SYSTEM record. At 20:10:24 ordinary AUTO/FINGERPRINT logged SYSTEM/evidence=verified-local-runtime-profile and doNotShowDialog=true. Requested final user confirmation; pending. Accidental AUTO/FACE selection returned the previously accepted explicit UNSUPPORTED_AUTHENTICATION_TYPE and was canceled. No enrollment or permissions changed.

Limit: registration does not independently prove OEM behavior or every window mode. Changing the system UI contract without changing recorded build/provider metadata requires resetting the profile. This does not close BIO-REG-007 automatically for new devices.

Final confirmation: user reported only the system dialog. 20:10:46.214 onSucceeded(FINGERPRINT, cryptoSecurityLevel=NONE), 20:10:46.217 onUIClosed. At 20:12:04 standard AUTO/ANY still logged IBiometricPromptImpl=true and UI text `Триває розпізнавання обличчя`; canceled after route inspection. Thus the profile fixes the verified default fingerprint UI without forcing the FACE-capable ANY route to legacy. Profile remains enabled locally on Pixel.
