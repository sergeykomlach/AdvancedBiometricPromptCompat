# Protected storage v3 — реалізація і перевірка

Виправлено прив’язку encrypted preferences до логічного ключа й фізичного файла.
Новий формат v3 використовує AES-GCM із version/domain/file/key AAD. Публічний
конструктор збережено; новий формат завжди потребує Keystore-backed primary key,
навіть якщо старий config-only прапорець legacy fallback явно вимкнув fail-closed.

[Опис формату, переходів міграції та меж](../security/protected-preferences-v3.md).

## Реалізація

- `BoundPreferenceCipher`: HMAC-SHA256 domain derivation з primary integrity key;
  окремі AEAD domains для names/values/header; випадкові nonce, однозначне
  кодування file identity і nullable key. CE/DE та різні файли не взаємозамінні.
- `EncryptedSharedPreferences`: атомарне переписування всього legacy snapshot,
  строгий typed parser, відмова при duplicate aliases, відсутність v3→legacy
  fallback. Names-only enumeration і явне видалення corrupt values збережені.
- `PreferenceMigrationFence`: per-file Keystore pending alias із digest source
  snapshot, потім durable preference commit і committed alias. Digest має domain,
  кількість записів, кількість і довжини полів, включно з corrupt raw types під
  час явно дозволеного reset.
- Pending допускає retry лише початкового snapshot; валідний v3 завершує
  перерваний fence. Failed commit отруює спільний per-file стан усіх wrapper-ів
  до перезапуску, навіть коли backing memory вже змінилася.
- Explicit clear записує свіжий v3 без довіри до пошкоджених старих даних;
  user editors працюють із логічними ключами під спільним lock. Protected apply
  тепер синхронно підтверджує durable commit. Listeners викликаються після успіху
  поза lock; міграція сама їх не викликає.
- Legacy key discovery лінивий і read-only: device-ID salt-only state
  підтримується, відсутні ключі/salt не генеруються, unrelated fallback не
  ініціалізується після успішного попереднього config.

## Змінені файли й модулі

Production і test config — `:common`:

- `common/src/main/java/dev/skomlach/common/storage/EncryptedSharedPreferences.kt`
- `common/src/main/java/dev/skomlach/common/storage/SharedPreferenceProvider.kt`
- `common/src/main/java/dev/skomlach/common/storage/BoundPreferenceCipher.kt` — новий
- `common/src/main/java/dev/skomlach/common/storage/PreferenceMigrationFence.kt` — новий
- `common/build.gradle` — Robolectric test dependency/resources
- `common/src/test/java/dev/skomlach/common/storage/ProtectedStorageFailureTest.kt`
- `common/src/test/java/dev/skomlach/common/storage/EncryptedPreferencesBindingTest.kt` — новий
- `common/src/test/java/dev/skomlach/common/storage/EncryptedPreferencesMigrationTest.kt` — новий
- `common/src/test/java/dev/skomlach/common/storage/LegacyPreferenceKeyMaterialTest.kt` — новий
- `common/src/test/java/dev/skomlach/common/storage/ProtectedPreferenceFixture.kt` — новий

Інтеграційна регресія у `:biometric-custom-behavior`:
`src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorProtectedStoreTest.kt` —
оновлено внутрішню fixture без зміни production API.

Документація: `docs/security/behavior-production-security.md`, новий
`docs/security/protected-preferences-v3.md` і цей звіт.
`:app` та залежні biometric providers зібрані через targeted debug assemble.

## Регресії та результати

Фінальний запуск: **41 тест пройшов**, failures/errors/skipped — 0.
`:common` — 37; `:biometric-custom-behavior` — 4.

Покрито підміну ciphertext між logical keys, перенесення всього файла та окремих
values між файлами, value/header domain substitution, unknown/malformed v3,
legacy injection і missing-header downgrade; міграцію всіх типів/old configs;
null/empty/sentinel keys; pending retry/recovery/reset; commit false/exception
після зміни memory map; malformed records і duplicate aliases; listeners,
durable apply, два concurrent editor-и; strict Keystore-only selection і
однозначність source digest для corrupt raw records.

```powershell
.\gradlew.bat :common:testDebugUnitTest --tests 'dev.skomlach.common.storage.*' :biometric-custom-behavior:testDebugUnitTest --tests '*BehaviorProtectedStoreTest' --tests '*BehaviorTemplateStoreTest' --console=plain
```

Початкові 4 binding/migration regressions впали на старій реалізації assertions,
а не помилками fixture. Під час реалізації знайдено й виправлено recovery routing,
salt-only legacy lookup і створення missing legacy salt. Окрема red-регресія
відтворила слабкий explicit fallback для v3; ще одна — структурну неоднозначність
source digest для corrupt raw Set records. Всі фінальні регресії пройшли.

Додаткові точні red-команди:

```powershell
.\gradlew.bat :common:testDebugUnitTest --tests '*EncryptedPreferencesBindingTest' --console=plain
.\gradlew.bat :common:testDebugUnitTest --tests '*EncryptedPreferencesMigrationTest.publicV3StorageNeverCreatesLegacyMaterialEvenWhenLegacyFallbackWasExplicitlyEnabled' --console=plain
.\gradlew.bat :common:testDebugUnitTest --tests '*EncryptedPreferencesMigrationTest.migrationDigestCannotConfuseSetElementsWithAnotherRawRecord' --console=plain
```

Debug APK: фінальний targeted assemble із тимчасовим local init script.
Він вимикає debug minify/shrink, щоб не запускати R8 згідно з AGENTS.md;
tracked app build config не змінено. Жодний release variant не запускався.

```powershell
.\gradlew.bat :app:assembleDebug --init-script .tmp/storage-binding-20261025T161211Z/debug-no-shrinking.init.gradle --console=plain
python -B .tmp/storage-binding-20261025T161211Z/verify_storage.py
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Локальні артефакти: `.tmp/storage-binding-20261025T161211Z/` — red/green logs,
`assemble-debug-final.log`, `storage.patch`, `verification.json`.
Verifier перевіряє scoped whitespace diff, незмінність інших tracked файлів,
побайтову незмінність index/staged entries, результати тестів і відсутність R8.
Commit, push і staging не виконувалися.

## Рев’ю та межі доказу

Два незалежні source review перевірили формат, прямі залежності й міграцію;
знайдені зауваження виправлено та повторно переглянуто. Нових підтверджених
дефектів після виправлень не виявлено.

Memory/proxy fences у тестах підтверджують sequencing і fail-closed semantics,
але не реальну AndroidKeyStore alias durability чи direct boot. Device QA,
reboot/crash на фізичному пристрої, Frida/attestation, release/R8/connected tests,
повний Android test/lint suite та tools/security audit не виконувалися.

Legacy не дозволяє ретроспективно довести відсутність попередньої підміни. Fence
блокує повернення до legacy, але не replay старого валідного v3 у тому самому
file/key, видалення записів чи контроль app code/Keystore під UID додатка.
V3 є одностороннім upgrade: старі binaries його не читають; multi-process
SharedPreferences і фізичне перейменування файлів не підтримуються протоколом.
