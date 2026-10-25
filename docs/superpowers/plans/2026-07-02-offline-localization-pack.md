# Offline Localization Pack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add committed offline translations for every existing module `strings.xml`, create explicit `values-en` copies, extract remaining user-facing hardcoded production strings, and verify locale parity plus Android compile/resource safety.

**Architecture:** Keep each module as the owner of its own localized `strings.xml` files. Add one small localization verification tool under `scripts/localization`, align the app locale surface with the approved offline pack, then land resource extraction plus locale files in two batches: core modules first, software-biometric modules second.

**Tech Stack:** Android resource qualifiers, Kotlin, XML string resources, PowerShell verification tooling, Gradle compile/resource gates, `rg`.

---

### Task 1: Freeze The Locale Matrix And Add Verification Tooling

**Files:**
- Create: `scripts/localization/verify_offline_locales.ps1`
- Create: `scripts/localization/copy_default_strings_to_en.ps1`
- Modify: `app/src/main/res/xml/locales_config.xml`

- [ ] **Step 1: Add the locale verification script**

```powershell
param(
    [string]$RepoRoot = (Get-Location).Path
)

$modules = @(
    "app",
    "common",
    "biometric",
    "biometric-custom-behavior",
    "biometric-custom-face-tf",
    "biometric-custom-voice",
    "biometric-zkfinger"
)

$expectedLocaleDirs = @(
    "values-en",
    "values-de",
    "values-fr",
    "values-es",
    "values-b+es+419",
    "values-it",
    "values-nl",
    "values-ru",
    "values-uk",
    "values-zh-rCN",
    "values-zh-rTW",
    "values-ja",
    "values-ko",
    "values-id",
    "values-vi",
    "values-pt-rBR",
    "values-hi",
    "values-ar",
    "values-tr"
)

function Get-StringNodes([string]$path) {
    [xml]$xml = Get-Content -LiteralPath $path
    return @($xml.resources.string)
}

function Get-PlaceholderSet([string]$value) {
    if ([string]::IsNullOrEmpty($value)) {
        return @()
    }
    return [regex]::Matches($value, "%\d+\$[sd]|%[sd]") | ForEach-Object { $_.Value }
}

$errors = New-Object System.Collections.Generic.List[string]

foreach ($module in $modules) {
    $baseFile = Join-Path $RepoRoot "$module\src\main\res\values\strings.xml"
    if (-not (Test-Path -LiteralPath $baseFile)) {
        $errors.Add("Missing base strings file: $baseFile")
        continue
    }

    $baseNodes = Get-StringNodes $baseFile
    $baseKeys = @($baseNodes | ForEach-Object { $_.name })

    foreach ($localeDir in $expectedLocaleDirs) {
        $localeFile = Join-Path $RepoRoot "$module\src\main\res\$localeDir\strings.xml"
        if (-not (Test-Path -LiteralPath $localeFile)) {
            $errors.Add("Missing locale file: $localeFile")
            continue
        }

        $localeNodes = Get-StringNodes $localeFile
        $localeKeys = @($localeNodes | ForEach-Object { $_.name })

        $missing = $baseKeys | Where-Object { $_ -notin $localeKeys }
        $extra = $localeKeys | Where-Object { $_ -notin $baseKeys }

        if ($missing.Count -gt 0) {
            $errors.Add("$localeFile missing keys: $($missing -join ', ')")
        }
        if ($extra.Count -gt 0) {
            $errors.Add("$localeFile has extra keys: $($extra -join ', ')")
        }

        foreach ($baseNode in $baseNodes) {
            $localeNode = $localeNodes | Where-Object { $_.name -eq $baseNode.name } | Select-Object -First 1
            if ($null -eq $localeNode) {
                continue
            }
            $basePlaceholders = @(Get-PlaceholderSet $baseNode.'#text')
            $localePlaceholders = @(Get-PlaceholderSet $localeNode.'#text')
            if (($basePlaceholders -join '|') -ne ($localePlaceholders -join '|')) {
                $errors.Add("Placeholder mismatch for key '$($baseNode.name)' in $localeFile")
            }
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Offline locale verification passed."
```

- [ ] **Step 2: Add the default-to-English copy script**

```powershell
param(
    [string]$RepoRoot = (Get-Location).Path
)

$modules = @(
    "app",
    "common",
    "biometric",
    "biometric-custom-behavior",
    "biometric-custom-face-tf",
    "biometric-custom-voice",
    "biometric-zkfinger"
)

foreach ($module in $modules) {
    $source = Join-Path $RepoRoot "$module\src\main\res\values\strings.xml"
    $targetDir = Join-Path $RepoRoot "$module\src\main\res\values-en"
    $target = Join-Path $targetDir "strings.xml"

    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing source strings file: $source"
    }

    if (-not (Test-Path -LiteralPath $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir | Out-Null
    }

    Copy-Item -LiteralPath $source -Destination $target -Force
}

Write-Host "Copied default strings into values-en for all modules."
```

- [ ] **Step 3: Replace the broad locale config with the approved offline set**

Update `app/src/main/res/xml/locales_config.xml` to:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="ar" />
    <locale android:name="de" />
    <locale android:name="en-GB" />
    <locale android:name="en-US" />
    <locale android:name="es" />
    <locale android:name="es-419" />
    <locale android:name="fr" />
    <locale android:name="hi" />
    <locale android:name="id" />
    <locale android:name="it" />
    <locale android:name="ja" />
    <locale android:name="ko" />
    <locale android:name="nl" />
    <locale android:name="pt-BR" />
    <locale android:name="ru" />
    <locale android:name="tr" />
    <locale android:name="uk" />
    <locale android:name="vi" />
    <locale android:name="zh-CN" />
    <locale android:name="zh-TW" />
</locale-config>
```

- [ ] **Step 4: Run the locale verification script before any locale files exist**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\localization\verify_offline_locales.ps1`
Expected: FAIL with missing locale files such as `values-en\strings.xml`

- [ ] **Step 5: Commit the tooling and locale-config baseline**

```bash
git add scripts/localization/verify_offline_locales.ps1 scripts/localization/copy_default_strings_to_en.ps1 app/src/main/res/xml/locales_config.xml
git commit -m "chore: add offline localization tooling"
```

### Task 2: Extract Remaining App-Side Hardcoded User Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/dev/skomlach/biometric/app/FirstFragment.kt`
- Modify: `app/src/main/java/dev/skomlach/biometric/app/MainActivity.kt`
- Modify: `app/src/main/java/dev/skomlach/biometric/app/SecondFragment.kt`
- Modify: `app/src/main/java/dev/skomlach/biometric/app/utils/MailTo.kt`
- Modify: `app/src/main/java/dev/skomlach/biometric/app/utils/BiometricExtension.kt`

- [ ] **Step 1: Add new app string keys for the currently hardcoded user-facing copy**

Append keys like these to `app/src/main/res/values/strings.xml`:

```xml
<string name="initialization_in_progress">Initialization in progress...</string>
<string name="changes_applied">Changes applied</string>
<string name="network_status">Network: %1$s\n%2$s</string>
<string name="looking_for_apis">Looking for APIs</string>
<string name="please_wait">Please wait...</string>
<string name="unexpected_error_happens">Unexpected error happens</string>
<string name="advanced_biometric_logs_subject">Advanced BiometricPromptCompat Logs</string>
<string name="advanced_biometric_report_subject">Advanced BiometricPromptCompat Report</string>
<string name="see_attachment">See attachment</string>
<string name="send_email">Send e-mail</string>
<string name="file_selector">File Selector</string>
<string name="selected_file_cant_be_shared">The selected file can't be shared: %1$s</string>
<string name="biometric_demo_title">Biometric for Fragment: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more</string>
<string name="biometric_demo_subtitle">Biometric Subtitle: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more</string>
<string name="biometric_demo_description">Biometric Description: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more</string>
<string name="biometric_demo_cancel">Cancel: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more</string>
<string name="crypto_disabled">Crypto doesn't work or disabled</string>
<string name="crypto_encryption_result">Crypto encryption result=%1$s</string>
<string name="crypto_decryption_result">Crypto decryption result=%1$s</string>
<string name="biometric_demo_success">Succeeded - %1$s; %2$s</string>
<string name="biometric_demo_canceled">Canceled %1$s</string>
<string name="biometric_demo_failure">Failure: %1$s</string>
<string name="biometric_demo_ui_opened">onUIOpened</string>
<string name="biometric_demo_ui_closed">onUIClosed</string>
<string name="biometric_demo_start_setup">Start setup %1$s/%2$s</string>
<string name="biometric_demo_start_authenticate">Start authenticate %1$s/%2$s</string>
<string name="biometric_demo_no_hardware">No hardware for %1$s</string>
<string name="biometric_demo_not_enrolled">No enrolled biometric for - %1$s</string>
<string name="biometric_demo_temp_lockout">Biometric sensor temporary locked for %1$s\nTry again later</string>
<string name="biometric_demo_perm_lockout">Biometric sensor permanently locked for %1$s</string>
<string name="biometric_demo_unexpected_error_state">Unexpected error state for %1$s</string>
<string name="biometric_demo_error_title">Biometric Error</string>
```

- [ ] **Step 2: Replace hardcoded copy in the app fragments and mail helper**

Apply this shape:

```kotlin
val dialog = ProgressDialog.show(
    activity,
    "",
    getString(R.string.initialization_in_progress),
    true
)

Toast.makeText(
    AndroidContext.appContext,
    getString(R.string.changes_applied),
    Toast.LENGTH_LONG
).show()

binding?.text?.text = getString(
    R.string.network_status,
    NetworkApi.hasInternet().toString(),
    deviceInfo.toString()
)
```

And in `MailTo.kt`:

```kotlin
LogCat.logException(
    e,
    ctx.getString(R.string.file_selector),
    ctx.getString(R.string.selected_file_cant_be_shared, file)
)

builder.setText(ctx.getString(R.string.see_attachment))
builder.setChooserTitle(ctx.getString(R.string.send_email))
```

- [ ] **Step 3: Replace hardcoded biometric demo copy in `BiometricExtension.kt`**

Use `requireActivity().getString(...)` / `AndroidContext.appContext.getFixedContext().getString(...)` instead of raw literals:

```kotlin
.setTitle(getString(R.string.biometric_demo_title))
.setSubtitle(getString(R.string.biometric_demo_subtitle))
.setDescription(getString(R.string.biometric_demo_description))
.apply {
    setNegativeButtonText(getString(R.string.biometric_demo_cancel))
}

Toast.makeText(
    AndroidContext.appContext.getFixedContext(),
    getString(R.string.biometric_demo_success, confirmed.toString(), cryptoText),
    Toast.LENGTH_LONG
).show()
```

- [ ] **Step 4: Verify the targeted hardcoded strings are gone from app production code**

Run: `rg -n "Initialization in progress|Changes applied|Looking for API|Please wait|See attachment|Send e-mail|Biometric Error|Start setup|Start authenticate|Unexpected error happens" app/src/main/java`
Expected: no matches

- [ ] **Step 5: Run the app Kotlin/resource compile gate**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:mergeDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/dev/skomlach/biometric/app/FirstFragment.kt app/src/main/java/dev/skomlach/biometric/app/MainActivity.kt app/src/main/java/dev/skomlach/biometric/app/SecondFragment.kt app/src/main/java/dev/skomlach/biometric/app/utils/MailTo.kt app/src/main/java/dev/skomlach/biometric/app/utils/BiometricExtension.kt
git commit -m "feat: extract app localization strings"
```

### Task 3: Create Explicit English Resource Copies For Every Module

**Files:**
- Create: `app/src/main/res/values-en/strings.xml`
- Create: `common/src/main/res/values-en/strings.xml`
- Create: `biometric/src/main/res/values-en/strings.xml`
- Create: `biometric-custom-behavior/src/main/res/values-en/strings.xml`
- Create: `biometric-custom-face-tf/src/main/res/values-en/strings.xml`
- Create: `biometric-custom-voice/src/main/res/values-en/strings.xml`
- Create: `biometric-zkfinger/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Run the copy script to generate `values-en` from each default `values/strings.xml`**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\localization\copy_default_strings_to_en.ps1`
Expected: `Copied default strings into values-en for all modules.`

- [ ] **Step 2: Spot-check one generated English file per layer**

Verify files such as:

```xml
<!-- app/src/main/res/values-en/strings.xml -->
<string name="app_name">AdvancedBiometricPrompt Test</string>
<string name="looking_for_apis">Looking for APIs</string>

<!-- biometric-custom-voice/src/main/res/values-en/strings.xml -->
<string name="biometriccompat_voice_help_timeout">Voice authentication timed out</string>
<string name="biometriccompat_voice_auto_detected">Voice detected</string>
```

- [ ] **Step 3: Run the locale verification script again**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\localization\verify_offline_locales.ps1`
Expected: FAIL, but now only because the non-English locale files are still missing

- [ ] **Step 4: Commit the English fallback layer**

```bash
git add app/src/main/res/values-en/strings.xml common/src/main/res/values-en/strings.xml biometric/src/main/res/values-en/strings.xml biometric-custom-behavior/src/main/res/values-en/strings.xml biometric-custom-face-tf/src/main/res/values-en/strings.xml biometric-custom-voice/src/main/res/values-en/strings.xml biometric-zkfinger/src/main/res/values-en/strings.xml
git commit -m "feat: add explicit English resource fallbacks"
```

### Task 4: Add Offline Locale Packs For App, Common, And Biometric Core

**Files:**
- Create: `app/src/main/res/values-{de,fr,es,b+es+419,it,nl,ru,uk,zh-rCN,zh-rTW,ja,ko,id,vi,pt-rBR,hi,ar,tr}/strings.xml`
- Create: `common/src/main/res/values-{de,fr,es,b+es+419,it,nl,ru,uk,zh-rCN,zh-rTW,ja,ko,id,vi,pt-rBR,hi,ar,tr}/strings.xml`
- Create: `biometric/src/main/res/values-{de,fr,es,b+es+419,it,nl,ru,uk,zh-rCN,zh-rTW,ja,ko,id,vi,pt-rBR,hi,ar,tr}/strings.xml`

- [ ] **Step 1: Create the locale directories and translated app strings**

Each locale file must preserve the exact key set from `app/src/main/res/values/strings.xml`.
Use this structure in every locale file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">AdvancedBiometricPrompt Test</string>
    <string name="action_settings">Einstellungen</string>
    <string name="looking_for_apis">Suche nach APIs</string>
    <string name="please_wait">Bitte warten...</string>
    <string name="advanced_biometric_logs_subject">Advanced BiometricPromptCompat-Protokolle</string>
    <string name="biometric_demo_error_title">Biometrischer Fehler</string>
</resources>
```

- [ ] **Step 2: Create the locale directories and translated common strings**

Every locale file must preserve the exact key set from `common/src/main/res/values/strings.xml`, including placeholders:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="biometriccompat_channel_id">Biometrie</string>
    <string name="biometriccompat_allow_notifications_channel_perm">Benachrichtigungskanäle anzeigen erlauben</string>
    <string name="biometriccompat_allow_notifications_perm">Benachrichtigungen anzeigen erlauben</string>
    <string name="biometriccompat_permissions_request_failed">Die Aktion kann nicht ausgeführt werden, weil die erforderliche Berechtigung nicht erteilt wurde.</string>
    <string name="biometriccompat_request_perm">%1$s benötigt die folgende Berechtigung:\n%2$s</string>
</resources>
```

- [ ] **Step 3: Create the locale directories and translated biometric-core strings**

Every locale file must preserve the exact key set from `biometric/src/main/res/values/strings.xml`, including HTML/text formatting:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="biometriccompat_use_devicecredentials">Biometrische Sensoren sind derzeit nicht verfügbar.\nBitte verwende deine PIN, dein Muster oder dein Gerätepasswort, um deine Identität zu bestätigen.</string>
    <string name="biometriccompat_untrusted_a11y">%1$s\n\nWarnung: %2$s konnte die Sicherheit eines der Bedienungshilfedienste nicht verifizieren. Er könnte deine PIN, dein Muster oder dein Gerätepasswort aufzeichnen.\nFahre nur fort, wenn du dir absolut sicher bist, dass deine Aktionen sicher sind.</string>
    <string name="biometriccompat_face_error_lockout">Zu viele Versuche. Versuche es später erneut.</string>
    <string name="biometriccompat_biometric_dialog_default_title">Bestätige, dass du es bist</string>
    <string name="biometriccompat_screen_lock_prompt_message">Gib deine Displaysperre ein, um fortzufahren</string>
</resources>
```

- [ ] **Step 4: Run locale parity validation after the core batch**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\localization\verify_offline_locales.ps1`
Expected: FAIL only for the still-missing software-biometric locale files

- [ ] **Step 5: Run compile/resource proof for the core batch**

Run: `.\gradlew.bat :common:compileDebugKotlin :biometric:compileDebugKotlin :common:packageDebugResources :biometric:packageDebugResources :app:compileDebugKotlin :app:mergeDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res common/src/main/res biometric/src/main/res
git commit -m "feat: add offline locale pack for core modules"
```

### Task 5: Add Offline Locale Packs For Software Biometric Modules

**Files:**
- Create: `biometric-custom-behavior/src/main/res/values-{de,fr,es,b+es+419,it,nl,ru,uk,zh-rCN,zh-rTW,ja,ko,id,vi,pt-rBR,hi,ar,tr}/strings.xml`
- Create: `biometric-custom-face-tf/src/main/res/values-{de,fr,es,b+es+419,it,nl,ru,uk,zh-rCN,zh-rTW,ja,ko,id,vi,pt-rBR,hi,ar,tr}/strings.xml`
- Create: `biometric-custom-voice/src/main/res/values-{de,fr,es,b+es+419,it,nl,ru,uk,zh-rCN,zh-rTW,ja,ko,id,vi,pt-rBR,hi,ar,tr}/strings.xml`
- Create: `biometric-zkfinger/src/main/res/values-{de,fr,es,b+es+419,it,nl,ru,uk,zh-rCN,zh-rTW,ja,ko,id,vi,pt-rBR,hi,ar,tr}/strings.xml`

- [ ] **Step 1: Add translated behavior locale files**

Preserve the exact key set from `biometric-custom-behavior/src/main/res/values/strings.xml`, especially:

```xml
<string name="biometriccompat_behavior_help_enrolled">Verhaltensvorlage registriert: %1$s</string>
<string name="biometriccompat_behavior_overlay_title_enroll">Verhaltensprobe erstellen</string>
<string name="biometriccompat_behavior_hint_combined">Gib eine kurze Phrase ein und zeichne eine klare Unterschrift.</string>
<string name="biometriccompat_behavior_error_need_signature">Zeichne vor dem Fortfahren eine längere Unterschrift.</string>
```

- [ ] **Step 2: Add translated voice locale files**

Preserve the exact key set from `biometric-custom-voice/src/main/res/values/strings.xml`, especially:

```xml
<string name="biometriccompat_voice_help_enrolled">Stimmvorlage registriert: %1$s</string>
<string name="biometriccompat_voice_overlay_hint_enroll">Nimm %1$d natürliche Sprachproben auf. Sprich jedes Mal 2 bis 4 Sekunden lang.</string>
<string name="biometriccompat_voice_auto_enroll_recording_started">Sprachprobe %1$d von %2$d: Aufnahme gestartet</string>
<string name="biometriccompat_voice_status_recording">Sprachprobe wird aufgenommen...</string>
```

- [ ] **Step 3: Add translated FaceTF locale files**

Preserve the exact key set from `biometric-custom-face-tf/src/main/res/values/strings.xml`, especially:

```xml
<string name="biometriccompat_tf_face_help_model_not_detected">Dein Gesicht ist nicht zu sehen. Halte das Telefon auf Augenhöhe.</string>
<string name="biometriccompat_tf_face_help_model_no_camera_permissions">Für diese Funktion ist eine Kameraberechtigung erforderlich.</string>
<string name="biometriccompat_tf_face_prompt_start_auth">Richte dein Gesicht auf die Kamera aus</string>
<string name="biometriccompat_tf_face_prompt_start_enroll">Richte dein Gesicht aus, um FaceTF zu registrieren</string>
```

- [ ] **Step 4: Add translated ZKFinger locale files**

Preserve the exact key set from `biometric-zkfinger/src/main/res/values/strings.xml`, especially:

```xml
<string name="biometriccompat_zkfinger_help_enroll_start">Erforderliche ZK-Reader-Scans: %1$d</string>
<string name="biometriccompat_zkfinger_help_enroll_progress">Akzeptierte Scans: %1$d/%2$d. Hebe denselben Finger an und lege ihn erneut auf.</string>
<string name="biometriccompat_zkfinger_prompt_start_auth">Lege deinen Finger auf das ZK-Lesegerät</string>
<string name="biometriccompat_zkfinger_prompt_start_enroll">Lege deinen Finger auf das ZK-Lesegerät, um ihn zu registrieren</string>
```

- [ ] **Step 5: Run the locale parity validator after all locale files exist**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\localization\verify_offline_locales.ps1`
Expected: `Offline locale verification passed.`

- [ ] **Step 6: Run module compile/resource proof**

Run: `.\gradlew.bat :biometric-custom-behavior:compileDebugKotlin :biometric-custom-face-tf:compileDebugKotlin :biometric-custom-voice:compileDebugKotlin :biometric-zkfinger:compileDebugKotlin :biometric-custom-behavior:packageDebugResources :biometric-custom-face-tf:packageDebugResources :biometric-custom-voice:packageDebugResources :biometric-zkfinger:packageDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add biometric-custom-behavior/src/main/res biometric-custom-face-tf/src/main/res biometric-custom-voice/src/main/res biometric-zkfinger/src/main/res
git commit -m "feat: add offline locale pack for software biometrics"
```

### Task 6: Run Final Verification And Regressions

**Files:**
- Verify: `app/src/main/res/xml/locales_config.xml`
- Verify: `scripts/localization/verify_offline_locales.ps1`
- Verify: `app/src/main/res/**/strings.xml`
- Verify: `common/src/main/res/**/strings.xml`
- Verify: `biometric/src/main/res/**/strings.xml`
- Verify: `biometric-custom-behavior/src/main/res/**/strings.xml`
- Verify: `biometric-custom-face-tf/src/main/res/**/strings.xml`
- Verify: `biometric-custom-voice/src/main/res/**/strings.xml`
- Verify: `biometric-zkfinger/src/main/res/**/strings.xml`

- [ ] **Step 1: Re-run the full locale validator**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\localization\verify_offline_locales.ps1`
Expected: `Offline locale verification passed.`

- [ ] **Step 2: Re-run the full compile/resource proof**

Run: `.\gradlew.bat :common:compileDebugKotlin :biometric:compileDebugKotlin :biometric-custom-behavior:compileDebugKotlin :biometric-custom-face-tf:compileDebugKotlin :biometric-custom-voice:compileDebugKotlin :biometric-zkfinger:compileDebugKotlin :app:compileDebugKotlin :common:packageDebugResources :biometric:packageDebugResources :biometric-custom-behavior:packageDebugResources :biometric-custom-face-tf:packageDebugResources :biometric-custom-voice:packageDebugResources :biometric-zkfinger:packageDebugResources :app:mergeDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run a final hardcoded-string sweep for production code**

Run: `rg -n --glob "app/src/main/java/**/*.kt" --glob "common/src/main/java/**/*.kt" --glob "biometric/src/main/java/**/*.kt" --glob "biometric-custom-behavior/src/main/java/**/*.kt" --glob "biometric-custom-face-tf/src/main/java/**/*.kt" --glob "biometric-custom-voice/src/main/java/**/*.kt" --glob "biometric-zkfinger/src/main/java/**/*.kt" "Initialization in progress|Changes applied|Looking for APIs|Please wait|Unexpected error happens|See attachment|Send e-mail|Biometric Error|Voice detected|Checking behavior|Align your face with the camera|Place your finger on the ZK reader"`
Expected: no matches

- [ ] **Step 4: Run whitespace / XML diff sanity**

Run: `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check`
Expected: no whitespace errors

- [ ] **Step 5: Commit any final localized cleanup**

```bash
git status --short
```

Expected: no uncommitted code changes
