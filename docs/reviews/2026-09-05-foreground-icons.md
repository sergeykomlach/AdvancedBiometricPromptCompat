# Restore foreground biometric status icons

Final Pixel visual confirmation: user reported icons visible, only the system dialog present, and no fallback dialog. The AUTO/ANY visibility regression is device-verified on this APK; the pending statement below records the earlier verification step.

In module biometric, WindowForegroundBlurring.kt suppressed the entire biometricTypesList when systemPromptOwnsUi was true. Removed that condition: UI ownership controls the compat dialog, not the foreground status indicators. RenderEffect still targets contentView; the overlay is added as its sibling. Existing attachment, icons-enabled, permission and selected-type availability checks remain.

Verification: `.\gradlew.bat :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain` PASS in 8s; scoped `git diff --check` PASS. No new tests/full suite/release/R8. No authentication routing, profile, app source or resources changed.

Installed on Pixel 9 Pro Fold via `adb -s 192.168.0.10:34555 install -r app/build/outputs/apk/debug/app-debug.apk`: Success. APK SHA256 `2E275444C53F7269FA22AACAFC4FC6717A746F75FAB50246C9BF6595C31BB4E8`. Standard AUTO/ANY replay logs show foreground setup and icon layout update at 20:26:51; outer success ANY at 20:27:03.386 and UIClosed at .391. These logs do not prove icon visibility; user visual confirmation requested and pending. Other OEM/window modes not replayed.
