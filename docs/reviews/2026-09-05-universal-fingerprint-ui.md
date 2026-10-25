# Універсалізація fingerprint UI та системний текст дозволів

**Історичний, частково відкликаний результат:** алгоритм за фокусом видалено після виявлення Samsung/Bubble/Split-Screen ризиків; його 7 tests більше не входять у код. Системний permission-текст збережено. [Актуальна реалізація та статус BIO-REG-007](2026-09-05-multiwindow-android17.md).

Цей звіт доповнює попередній Pixel QA. Старий виняток Pixel 9 Pro Fold / API 37+ видалено; попередні device results не є доказом нового алгоритму.

## Зміни

- `biometric/.../utils/DevicesWithKnownBugs.kt`: прибрано `hasSystemSideFingerprintUi` і перевірку моделі/API 37. Наявні LG/OnePlus/UDFPS правила збережено.
- `biometric/.../impl/FrameworkFingerprintUiSession.kt`: session-scoped керування fallback. Для одиночного framework fingerprint route, який раніше потребував compat UI, спочатку запускається сенсор; через 600 ms fallback дозволено лише для resumed host із фокусом та без saved fragment state. Передача фокуса іншому вікну відкладає показ; пізня передача фокуса ховає вже відкритий fallback. Повернення фокуса дозволяє його відновити після паузи. Результат не кешується за моделлю/прошивкою.
- `biometric/.../impl/BiometricPromptGenericImpl.kt`: інтеграція лише для `API23FingerprintModule`/`SupportFingerprintModule` і fingerprint-only execution scope; пізній fallback не перезапускає authentication. Listener і pending work видаляються при завершенні; generation guard відхиляє застарілу роботу.
- `biometric/.../impl/dialogs/BiometricPromptCompatDialogImpl.kt`: окремий focus listener для цього маршруту; приховування/відновлення вікна без dismiss/cancel сенсора. Для звичайних OEM/software маршрутів збережено попередню поведінку.
- `biometric/.../BiometricPromptCompat.kt`: фонові biometric icons не дублюють UI під час арбітражу framework fingerprint route.
- `common/.../permissionui/PermissionSettingsText.kt`, `PermissionsFragment.kt`, `common/src/main/AndroidManifest.xml`: системний handler `MANAGE_APP_PERMISSIONS` знаходиться через `MATCH_SYSTEM_ONLY` і вузький manifest query; з його ресурсів читається `permission_rationale_permission_settings_message` у поточній конфігурації/локалі. Код пакета не завантажується. Немає залежності від hidden getter `getPermissionControllerPackageName`. Відсутність ресурсу/handler, exception або несумісний формат ведуть до локального EN/UK fallback. Перелік дозволів додається окремим абзацом.
- Тести: `FrameworkFingerprintUiSessionTest.kt`, `PermissionSettingsTextTest.kt`.

## Межі

Фокус — лише сигнал керування UI, не доказ присутності саме biometric prompt і не доказ sensor identity/strength. Шторка повідомлень теж може приховати fallback; це не породжує success. Криптографія, callbacks результатів і permission grants не послаблені. Алгоритм не обіцяє автоматичного розпізнавання non-focusable OEM overlays. Mixed/software routes і відомі UDFPS exceptions не переведено на новий алгоритм; інші OEM та всі версії Android не сертифіковано.

## Перевірка

Команда:

```powershell
.\gradlew.bat :common:testDebugUnitTest --tests "dev.skomlach.common.permissionui.PermissionSettingsTextTest" --tests "dev.skomlach.common.permissionui.PermissionRequestPolicyTest" :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.FrameworkFingerprintUiSessionTest" --tests "dev.skomlach.biometric.compat.impl.LegacyAuthStartDelayTest" :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain
```

- Перша спроба: compile failure через непублічний `permissionControllerPackageName`; виправлено на resolution системного handler.
- Друга спроба: PASS, 11s. 15 tests: permission text 2, permission policy 4, fingerprint UI session 7, legacy start delay 2; 0 failures/errors.
- Після виправлення порядку присвоєння dialog перед синхронним show повторено лише `:app:assembleDebug` з тим самим init script і `--console=plain`: PASS, 7s.
- Scoped `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- <changed tracked paths>`: PASS. Пошук Pixel/model/API37 exception у зміненій UI policy: збігів немає.
- Фінальний APK SHA256: `CB38011084AE6847EBFFF7E37136D1B518A1AB8448452AB1D48200A10BA2F713`.
- Release/minify/R8/full suite/instrumented tests, інші пристрої та OEM не запускалися. Debug init script вимикає minify/shrink; код `app` не змінено. Commit/push не виконано.

## Pixel QA

Pixel 9 Pro Fold / API 37, serial `adb-4A221FDKD001JR-O6LimJ._adb-tls-connect._tcp`.

Системний обробник дозволів: `com.google.android.permissioncontroller/com.android.permissioncontroller.permission.ui.ManagePermissionsActivity`. Команда `cmd overlay lookup --verbose com.google.android.permissioncontroller com.android.permissioncontroller:string/permission_rationale_permission_settings_message` підтвердила UK ресурс: «Змініть доступ цього додатка в налаштуваннях конфіденційності».

Фінальний `adb -s adb-4A221FDKD001JR-O6LimJ._adb-tls-connect._tcp install -r app/build/outputs/apk/debug/app-debug.apk`: Success.

- AUTO + FINGERPRINT: SystemUI показує fingerprint-only prompt. Дамп вікон містить системний `BiometricPrompt` та головну Activity, без додаткового app dialog. `doNotShowDialog=false` підтверджує, що старий шлях примусового приховування не використано; fallback не створився завдяки арбітражу.
- Скасування: о 17:52:16 доставлено `CANCELED_BY_USER`; о 17:52:29 повторний запуск відкрив системний запит. Користувач підтвердив: «лише системний діалог». Успішне прикладання пальця в цьому повторі не підтверджено callback; не зараховувати старий fingerprint success як новий доказ.
- Permission dialog: для відтворення USER_FIXED тимчасово відкликано `RECORD_AUDIO` і встановлено user-set/user-fixed; AUTO + VOICE відкрив діалог із точним системним UK текстом, описом дозволу й кнопкою «Налаштування». Це перевірка вибору ресурсу у фінальному APK, а не лише shell lookup. Діалог скасовано, мікрофон відновлено: `granted=true`, flags `USER_SET|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED`, як до QA. Credentials fallback повернуто у початковий enabled стан. Voice/hardware enrollment не змінювалися.
- Тестові dialogs закрито. Пізня поява native window, non-Pixel rear/side sensors і lifecycle races — unit/static proof, не device proof.

Докази: [fingerprint callbacks](evidence/2026-09-05-universal-fingerprint-events.log), [permission dialog text](evidence/2026-09-05-system-permission-dialog.txt).

## Точні змінені source/test файли цього доповнення

```text
biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt
biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt
biometric/src/main/java/dev/skomlach/biometric/compat/impl/FrameworkFingerprintUiSession.kt
biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt
biometric/src/main/java/dev/skomlach/biometric/compat/utils/DevicesWithKnownBugs.kt
biometric/src/test/java/dev/skomlach/biometric/compat/impl/FrameworkFingerprintUiSessionTest.kt
common/src/main/AndroidManifest.xml
common/src/main/java/dev/skomlach/common/permissionui/PermissionSettingsText.kt
common/src/main/java/dev/skomlach/common/permissionui/PermissionsFragment.kt
common/src/test/java/dev/skomlach/common/permissionui/PermissionSettingsTextTest.kt
```

Також оновлено цей звіт, `2026-09-05-biometric-review-fixes.md`, `2026-09-05-pixel-regression-verification.md`, `biometric-regression-backlog.md` та два наведені evidence файли. EN/UK fallback resources залишилися з попередньої правки без нових змін.

Джерело системного ключа: [AOSP PermissionController strings](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/res/values/strings.xml).
