# Біометричні регресії: виправлення та Pixel QA — 2026-09-05

Актуальні зміни: [Android 17 multi-window](2026-09-05-multiwindow-android17.md). Фокусну евристику відкликано, BIO-REG-007 знову відкрито; наведені нижче fingerprint UI успіхи належать попереднім збіркам.

Останнє оновлення: [універсалізація fingerprint UI та системний permission-текст](2026-09-05-universal-fingerprint-ui.md). Нижче збережено історичні збірки та їх QA; не переносити їхні результати автоматично на новий алгоритм.

## Наступна правка тексту permission-діалогу

Після основного QA користувач звернув увагу на повідомлення про адміністратора у проміжній збірці. У `common` додано окремий `biometriccompat_permissions_settings_message` (EN/UK), який пояснює потрібну дію:

> Щоб продовжити, надайте потрібні дозволи в налаштуваннях застосунку:

Далі підставляється перелік дозволів. Кнопка використовує наявний переклад «Налаштування». Загальний текст `permissions_request_failed` для інших помилок не переписано.

Змінено `common/src/main/java/dev/skomlach/common/permissionui/PermissionsFragment.kt`, `common/src/main/res/values/strings.xml`, `common/src/main/res/values-uk/strings.xml`.

Команда: `.\gradlew.bat :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain` — PASS, 30s. Scoped `git diff --check` для цих трьох файлів — PASS. Unit/full-suite/release/R8 та повторний permission-denial flow не запускалися для текстової правки.

Новий APK SHA256: `73710AD00D68F58C9253871D1BEE06B3615684F903E464EB3F558F9B0C7C77AF`. Нижче збережено артефакт і докази попереднього повного QA; вони не є повторною UI-перевіркою нового тексту.

`adb -s adb-4A221FDKD001JR-O6LimJ._adb-tls-connect._tcp install -r app/build/outputs/apk/debug/app-debug.apk` — Success. Новий текст встановлено на Pixel; повторно цей діалог на пристрої не відкривався.

## Резолюція

Виправлення внесено в `biometric` і `common`. Повного release sign-off немає: початковий LeakCanary retention ще не локалізовано, first hardware enrollment і system-fatal fallback потребують окремого device replay. Загальний статус задач — у [беклозі](biometric-regression-backlog.md). Код `app`, залежності, hardware templates та сторонні локальні зміни не редагувалися. Commit/push не виконувалися.

## Пристрій і артефакт

- Checkout: `main`, HEAD `91163809` + working-tree changes цієї задачі.
- Pixel 9 Pro Fold, Android 17 / API 37.
- ОС: `google/comet/comet:17/CP2A.260805.005/15828068:user/release-keys`.
- Serial: `adb-4A221FDKD001JR-O6LimJ._adb-tls-connect._tcp`; інші підключені пристрої не використовувалися.
- Unfolded, UI bounds 2152×2076 та 2076×2152. Folded не перевірено.
- Package `dev.skomlach.biometric.app`, versionCode 280, versionName 2.4, minSdk 23, targetSdk 35.
- Фінальний APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA256: `25C7E7DD327D9F05076806168B722BF00A8DA62D38037277D22832D1380BB664`.
- Фінальний `adb install -r`: Success; package lastUpdateTime `2026-09-05 17:09:42`.
- Для debug QA тимчасовий init script вимкнув minify/shrinkResources, оскільки repo debug variant вмикав їх. `app/build.gradle` не змінено. Це не перевірка R8/release.
- Credentials fallback вимкнено через demo UI для sensor-specific перевірок, після QA повернуто початкове enabled; crypto/silent/window-secure у UI вимкнені. RECORD_AUDIO спочатку denied + USER_SET/USER_FIXED, після Settings UI grant — granted + USER_SET. Voice template створено користувачем у процесі QA.

## Що змінено

### BIO-REG-001: preflight перед системним enrollment

`BiometricPromptCompat.awaitingSystemEnrollment` відділяє перевірку до відкриття Settings від перевірки після повернення. Порожній pending software scope більше не зупиняє запит до першого hardware enrollment. Після повернення маршрути перевибираються, звичайний preflight відновлюється. STATIC/compile; жодного hardware template на Pixel для відтворення порожнього enrollment не видаляли.

### BIO-REG-002: старі та нові зашифровані записи

`AppFlowCipherFactory` створює нові записи з 20-byte GCM IV: 4-byte format identifier + 128 random bits. Це справжній IV, цілком переданий AES-GCM; `Cipher.iv` залишається придатним для прямого використання. Споживач має зберігати весь IV, не припускати довжину 12 bytes. Публічні сигнатури crypto result/purpose не змінено.

Новий формат вимагає protected random secret і не пробує legacy key. Historical 12-byte IV підтримує два попередні типи записів: старий reversed(alias) key і вже створений v3 protected random key. Для такого historical record після AEAD tag failure protected key допускається спроба legacy key. Plaintext не видається до перевірки GCM tag; AAD передається обом кандидатам. External `unlockWithAppSecret` не вмикає цю сумісність. Помилка не видаляє ключі чи ciphertext.

JCA provider для compatibility cipher локальний, не реєструється глобально. Пряме використання захищеного конструктора `Cipher(spi, ...)` виявило відмінність Android і було замінено стандартним `Cipher.getInstance(..., provider)`. Остаточні 9 crypto tests пройшли на JVM та Android runtime самого Pixel.

Тести повторно створюють store над тим самим fake backing map; це моделювання повторного читання, а не реальний upgrade APK / process death / відновлення Android Keystore. Новий IV формат перевірено на Android provider; downgrade старої бібліотеки й споживачі з hardcoded 12-byte IV не перевірені. Legacy compatibility не підвищує старі записи до hardware-bound security.

### BIO-REG-003/008: hardware confirmation та software completion

System prompt terminal callback скидає active flag; pending software отримує власний UI. Для setup software targets відокремлено від hardware confirmation; system success не завершує voice enrollment. Software stages не стартують до потрібного hardware confirmation. Completion scope фіксується на старті: permission failure не може вилучити обов’язковий target і перетворити помилку на hardware-only success. Скасований software route стає terminal result.

На Pixel доведено system success → Voice enrollment → `onSucceeded(BIOMETRIC_VOICE)`, а потім успішну voice authentication. Fatal-system → software, ALL/cancel races і permission failure останнього route перевірені частково логікою/policy tests; повний orchestration device replay цих негативних гілок ще потрібний.

### BIO-REG-005/006: чесний sensor-specific результат

AUTO для конкретного типу використовує доступний legacy hardware route. На Pixel FINGERPRINT запускає окремий FingerprintManager шлях. Generic BIOMETRIC_API без доказу сенсора повертає `UNSUPPORTED_AUTHENTICATION_TYPE` для FACE/FINGERPRINT; загальний system biometric success маркується ANY. Це явне обмеження FACE-only через generic API, а не реалізація face-only фільтра.

Один тип у primary не доводить фактичний сенсор. STRONG/WEAK — класи дозволених автентифікаторів; WEAK включає STRONG. Callback не повідомляє фактичний strength. AOSP передає лише authenticationType, а hidden identifier заповнюється null: рефлексія result не відновить ці відкинуті дані. Прихований sensorId selector вимагає системних дозволів.

Джерела: [AndroidX Authenticators](https://developer.android.com/reference/androidx/biometric/BiometricManager.Authenticators), [AuthenticationResult](https://developer.android.com/reference/androidx/biometric/BiometricPrompt.AuthenticationResult), [AOSP result constructor](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/biometrics/BiometricPrompt.java#1190). Конкретний strength Pixel face на цій прошивці не вимірювався.

### BIO-REG-007: один fingerprint UI

AOSP FingerprintService може обгорнути public FingerprintManager request у sensor-specific system prompt для side/UDFPS sensors. Це відтворено на Pixel: GenericImpl створював ще й compat dialog. Для перевіреного Pixel 9 Pro Fold / API37+ та framework fingerprint modules compat dialog тепер не створюється; background biometric icons також не дублюються. Інші legacy/software UI збережені. Правило свідомо обмежене перевіреним пристроєм; наявна UDFPS policy збережена.

[AOSP FingerprintService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/biometrics/sensors/fingerprint/FingerprintService.java#333).

### BIO-REG-009: значення setup для вже enrolled hardware

`setupBiometric` за замовчуванням запитує новий hardware enrollment, коли він відсутній. Для вже enrolled hardware підтвердження особи є очікуваним етапом. Явний `enrollNewHardwareBiometric=true` відкриває system enrollment. Не ототожнювати auth success із додаванням hardware template. Явне створення нового hardware template в цьому QA не виконувалось.

### BIO-REG-010: відновлення доступу до мікрофона

У старому runtime callback не зберігався факт denial: при USER_FIXED request одразу повертав denied без UI, і setup завершувався canceled. Callback тепер запам’ятовує denied; коли rationale відсутній, пропонує Settings. Settings result обробляється також при RESULT_CANCELED, без очікування нового pause/resume. Перехід відкриває App Details напряму; повідомлення не приписує звичайний denial адміністратору. Capture залишається залежним від реального grant.

На Pixel пройдено USER_FIXED → Settings → grant → повернення → voice capture; потім voice enrollment і auth. Перша runtime відмова та reuse того самого Builder окремо не відтворювалися. [Android permission behavior](https://developer.android.com/training/permissions/requesting).

### BIO-REG-004: Toast retention

Library Toast в Api28 legacy-error callback прибрано; software feedback живе у dialog, системний prompt керує власними помилками. Початковий screenshot показує static `LeakCanary.ToastEventListener.toastCurrentlyShown` → Toast → detached View; Activity.mDestroyed=false. Це не доводить leak знищеної Activity. Demo має інші Toast у MainActivity/BiometricExtension, код app поза scope. Heap trace після фінального APK не знято; відсутність нового повідомлення не є доказом усунення початкового retention. Задача лишається Investigate.

## Device evidence

| Сценарій | Результат | Збірка / межі |
| --- | --- | --- |
| BIOMETRIC_API/FINGERPRINT та FACE | Явний UNSUPPORTED_AUTHENTICATION_TYPE, без хибного sensor success | batch 1, UI hierarchy |
| AUTO/FINGERPRINT до другої правки | System fingerprint-only + нижній compat dialog | batch 1, log + підтвердження користувача |
| AUTO/FINGERPRINT після правки | `doNotShowDialog=true`; користувач: лише system fingerprint-only | batch 2 |
| LEGACY/VOICE за USER_FIXED | До правки immediate denied/canceled; після — Settings action | batch 1 → batch 2 |
| Settings → grant → app | Capture запущено; permission реально granted | batch 2; нові тексти/direct Settings action у final пройшли compile |
| Setup AUTO/ANY | Користувач підтвердив system → voice; 17:07:00 onSucceeded(BIOMETRIC_VOICE) | batch 2 |
| Наступна LEGACY/VOICE auth | 17:07:13 onSucceeded(BIOMETRIC_VOICE) | batch 2 |
| Crypto compatibility | OK (9 tests) на Android runtime / Conscrypt | Ті самі crypto classes, що включені у final APK; окремий DEX harness |
| AUTO/FACE | Явний unsupported result | final APK, UI hierarchy |
| LEGACY/FINGERPRINT | System fingerprint-only, doNotShowDialog=true; 17:12:40 onSucceeded(BIOMETRIC_FINGERPRINT) | final APK; користувач підтвердив пальцем, callback у logcat |

Результати batch 1/2 не приписуються автоматично final APK. Після batch 2 змінено crypto, fixed completion scope та Settings action/copy. Final APK складено й встановлено; повторено зазначені final smoke сценарії, не всю матрицю.

Збережені вибіркові докази без аудіоданих чи voice features: [final flow events](evidence/2026-09-05-pixel-final-flow-events.log), [voice enrollment/auth](evidence/2026-09-05-pixel-voice-flow-events.log), [Android crypto tests](evidence/2026-09-05-pixel-crypto-tests.log).

## Команди та результати

Робочий каталог: `C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat`.

Для компактності нижче `$qa` означає `C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905`, `$s` — наведений вище Pixel serial. Усі Gradle виклики послідовні, з `--console=plain`.

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.Api28EnrollmentCompletionTest" --tests "dev.skomlach.biometric.compat.impl.AuthenticationCompletionPolicyTest" --console=plain
# PASS, batch 1.

.\gradlew.bat :app:assembleDebug --init-script "$qa/device-debug.init.gradle" --console=plain
# PASS, batch 1, 49s.

.\gradlew.bat :common:testDebugUnitTest --tests "dev.skomlach.common.permissionui.PermissionRequestPolicyTest" :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.Api28EnrollmentCompletionTest" --tests "dev.skomlach.biometric.compat.impl.LegacyAuthStartDelayTest" :app:assembleDebug --init-script "$qa/device-debug.init.gradle" --console=plain
# common: PASS (4 tests); biometric compile failed on one stale renamed property reference.

.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.Api28EnrollmentCompletionTest" --tests "dev.skomlach.biometric.compat.impl.LegacyAuthStartDelayTest" :app:assembleDebug --init-script "$qa/device-debug.init.gradle" --console=plain
# PASS after fixing that reference, batch 2, 22s; 5 completion + 2 delay tests.

.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.crypto.AppFlowCipherMigrationTest" --tests "dev.skomlach.biometric.compat.crypto.AppFlowSecretStoreTest" --console=plain
# First test compile failed (ByteArray.isNullOrEmpty); after correction PASS, 9 tests, 16s.

.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.crypto.AppFlowCipherMigrationTest" --console=plain
# PASS after Android provider initialization fix, 6 tests, 12s.

.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.impl.Api28EnrollmentCompletionTest" :app:assembleDebug --init-script "$qa/device-debug.init.gradle" --console=plain
# Final PASS, 5 tests + APK, 9s.

adb -s $s install -r app/build/outputs/apk/debug/app-debug.apk
# Success for batch 1, batch 2 and final.

adb -s $s shell dalvikvm -cp /data/local/tmp/codex-biometric-crypto-tests-v2.zip org.junit.runner.JUnitCore dev.skomlach.biometric.compat.crypto.AppFlowCipherMigrationTest dev.skomlach.biometric.compat.crypto.AppFlowSecretStoreTest
# Final Android run: OK (9 tests), 3.348s.

git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check -- biometric common docs/reviews docs/superpowers/plans/2026-06-10-biometric-production-readiness.md
# PASS (only Git line-ending conversion warnings).
```

Android crypto harness: D8 37.0.0, `--min-api 26 --lib <SDK>/platforms/android-35/android.jar --output <qa>/crypto-device-tests-v2.zip`; inputs are current AppFlowCipherFactory/LegacyReadProvider/LegacyReadCipherSpi/AppFlowSecretStore/AppFlowSessionStore classes, the two named test classes, JUnit 4.13.2, Kotlin stdlib 2.4.10 and Hamcrest 1.3. ZIP pushed to `/data/local/tmp`; no app credentials/templates read. First Android run failed 3/9 with `Cipher not initialized`; after the provider fix all 9 passed. This is crypto/provider proof, not Android Keystore storage instrumentation.

## Changed files

`biometric/src/main/java/dev/skomlach/biometric/compat/`:

- AuthenticationFailureReason.kt; BiometricPromptCompat.kt.
- crypto/AppFlowCipherFactory.kt (new), AppFlowCryptoStorage.kt, AppFlowCryptographyManagerInterface.kt, AppFlowSecretStore.kt, AppFlowSessionStore.kt, BiometricCryptoObjectHelper.kt.
- impl/AuthenticationCompletionPolicy.kt, BiometricPromptApi28Impl.kt, BiometricPromptGenericImpl.kt.
- utils/DevicesWithKnownBugs.kt; utils/activityView/WindowForegroundBlurring.kt.
- Resources: biometric/src/main/res/values/strings.xml, values-uk/strings.xml.
- Tests: biometric/src/test/java/dev/skomlach/biometric/compat/impl/Api28EnrollmentCompletionTest.kt, crypto/AppFlowCipherMigrationTest.kt (new).

`common/src/main/java/dev/skomlach/common/permissionui/PermissionsFragment.kt`.

Documentation: this report, biometric-regression-backlog.md and 2026-09-05-biometric-review-fixes.md. The earlier plan link and unchanged user screenshot remain in the existing local documentation changes.

## Not run / remaining risks

No full suite, release/minified/R8, all-variant build, emulator, other device family, destructive enrollment reset, commit or push. No final heap dump. No real APK downgrade/upgrade with stored legacy ciphertext, Android Keystore process-death test, folded matrix, first hardware enrollment, or system-fatal injection. Do not label these gaps as passed. Generic FACE-only remains unsupported unless a real sensor-specific route becomes available; strength inference is not a substitute.
