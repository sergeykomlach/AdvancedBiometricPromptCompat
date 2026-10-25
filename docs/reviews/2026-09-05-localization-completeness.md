# Localization completeness — 2026-09-05

Added 153 translated entries (9 keys × 17 locales) and synchronized 9 English entries into values-en: 162 entries in 72 strings.xml files.

Affected modules: common, biometric, biometric-custom-behavior, biometric-custom-face-tf. Existing Ukrainian translations retained. Audited all 133 explicit locale strings.xml files across seven modules, including app, voice and zkfinger; no missing translatable base/EN keys remain. Configuration-only directories (night, dimensions) are not separate languages. Identical behavior key symbols B and < intentionally retained.

Validation: Python XML parsing, union of values/values-en string/plural/array keys compared with each locale, and positional format-argument comparison: PASS, 0 missing keys, 0 placeholder mismatches. This checks resource completeness, not native-speaker review of every language or device text clipping.

Commands:
```powershell
$localePaths = Get-Content C:/Users/skoml/AppData/Local/Temp/biometric-locale-changed.txt
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- $localePaths
.\gradlew.bat :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain
```
Both PASS; build successful in 9s. Existing debug init disables minification/shrinkResources. No full suite, release/R8 or device QA; new APK not installed. No runtime code or dialog-sizing changes, no commit/push.

## Changed files

- `biometric/src/main/res/values-ar/strings.xml`
- `biometric-custom-behavior/src/main/res/values-ar/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-ar/strings.xml`
- `common/src/main/res/values-ar/strings.xml`
- `biometric/src/main/res/values-de/strings.xml`
- `biometric-custom-behavior/src/main/res/values-de/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-de/strings.xml`
- `common/src/main/res/values-de/strings.xml`
- `biometric/src/main/res/values-es/strings.xml`
- `biometric-custom-behavior/src/main/res/values-es/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-es/strings.xml`
- `common/src/main/res/values-es/strings.xml`
- `biometric/src/main/res/values-fr/strings.xml`
- `biometric-custom-behavior/src/main/res/values-fr/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-fr/strings.xml`
- `common/src/main/res/values-fr/strings.xml`
- `biometric/src/main/res/values-hi/strings.xml`
- `biometric-custom-behavior/src/main/res/values-hi/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-hi/strings.xml`
- `common/src/main/res/values-hi/strings.xml`
- `biometric/src/main/res/values-id/strings.xml`
- `biometric-custom-behavior/src/main/res/values-id/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-id/strings.xml`
- `common/src/main/res/values-id/strings.xml`
- `biometric/src/main/res/values-it/strings.xml`
- `biometric-custom-behavior/src/main/res/values-it/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-it/strings.xml`
- `common/src/main/res/values-it/strings.xml`
- `biometric/src/main/res/values-ja/strings.xml`
- `biometric-custom-behavior/src/main/res/values-ja/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-ja/strings.xml`
- `common/src/main/res/values-ja/strings.xml`
- `biometric/src/main/res/values-ko/strings.xml`
- `biometric-custom-behavior/src/main/res/values-ko/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-ko/strings.xml`
- `common/src/main/res/values-ko/strings.xml`
- `biometric/src/main/res/values-nl/strings.xml`
- `biometric-custom-behavior/src/main/res/values-nl/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-nl/strings.xml`
- `common/src/main/res/values-nl/strings.xml`
- `biometric/src/main/res/values-pt-rBR/strings.xml`
- `biometric-custom-behavior/src/main/res/values-pt-rBR/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-pt-rBR/strings.xml`
- `common/src/main/res/values-pt-rBR/strings.xml`
- `biometric/src/main/res/values-ru/strings.xml`
- `biometric-custom-behavior/src/main/res/values-ru/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-ru/strings.xml`
- `common/src/main/res/values-ru/strings.xml`
- `biometric/src/main/res/values-tr/strings.xml`
- `biometric-custom-behavior/src/main/res/values-tr/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-tr/strings.xml`
- `common/src/main/res/values-tr/strings.xml`
- `biometric/src/main/res/values-vi/strings.xml`
- `biometric-custom-behavior/src/main/res/values-vi/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-vi/strings.xml`
- `common/src/main/res/values-vi/strings.xml`
- `biometric/src/main/res/values-zh-rCN/strings.xml`
- `biometric-custom-behavior/src/main/res/values-zh-rCN/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-zh-rCN/strings.xml`
- `common/src/main/res/values-zh-rCN/strings.xml`
- `biometric/src/main/res/values-zh-rTW/strings.xml`
- `biometric-custom-behavior/src/main/res/values-zh-rTW/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-zh-rTW/strings.xml`
- `common/src/main/res/values-zh-rTW/strings.xml`
- `biometric/src/main/res/values-b+es+419/strings.xml`
- `biometric-custom-behavior/src/main/res/values-b+es+419/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-b+es+419/strings.xml`
- `common/src/main/res/values-b+es+419/strings.xml`
- `biometric-custom-behavior/src/main/res/values-en/strings.xml`
- `biometric-custom-face-tf/src/main/res/values-en/strings.xml`
- `common/src/main/res/values-en/strings.xml`
- `biometric/src/main/res/values-en/strings.xml`
