Сесія «атака → оборона → повторне рев’ю», 24 жовтня 2026
================================================================

Перевірено поточний робочий каталог `C:\Users\skoml\StudioProjects_5\AdvancedBiometricPromptCompat`, гілка `main`. Результат первинної сесії: 6 підтверджених дефектів виправлено; OEM lifecycle-дефект закрито частково. Це обмежена перевірка поточного коду й конкретних атакувальних сценаріїв, а не доказ відсутності інших вразливостей.

Оновлення: залишковий OEM error/cancel дефект закрито наступними змінами коду та цільовими тестами. Див. [окремий звіт про ownership запитів](2026-10-24-oem-request-ownership.md). Нижче збережено результати та межі первинної сесії; її позначка «частково» більше не описує поточний OEM-код.

**Межі та критерій зупинки**

Виконано три обмежені проходи: первинна атака й захисні зміни; незалежне повторне рев’ю з виправленням випадку `listener == null` та повідомлення про скасований підпис; фінальне рев’ю змінених шляхів і цільова перевірка. У фінальному проході нових конкретних дефектів внесених правок не виявлено. Подальше розширення зупинено на межах, де потрібні зміна контракту OEM-сеансів, міграція сховища або фізичні перевірки.

Статично перевірені бібліотечні auth/crypto callbacks, скасування та повторний запуск, software registry, FaceTF/PAD/challenge, voice/behavior/Sherpa, захищені preferences/Keystore, accessibility/privacy, мережеві запити/redirects і бібліотечні manifest-компоненти. Додатково переглянуті sample app, Kotlin extensions і USB/native boundary ZKFinger. OEM SDK і native binaries не декомпілювалися та не виконувалися. Не проводилися CVE-аудит усіх залежностей, повний аудит секретів і аналіз усіх історичних комітів. Додаткові агенти працювали лише на читання.

На початку існували сторонні staged/unstaged/untracked зміни. Їх не скинуто й не застейджено. У двох уже змінених файлах `Core.kt` та `MiuiFaceUnlockModule.kt` порівняння зі знімком до сесії показало лише по одній доданій/заміненій нами ділянці; попередні правки збережені. Комітів і push не було.

**Підтверджені знахідки та оборона**

1. **P1 — неоднозначне PAD-вікно могло дозволити spoof-кадр. Виправлено.**

   `FaceAntiSpoofingWindow.add()` раніше повертав PASS для будь-якого прогрітого вікна, що не задовольняло всі умови SPOOF. Наприклад, за порога 0.28 і minimumFrames=3 послідовності `[0.1, 0.1, 0.8]` та `[0.8, 0.8, 0.1]` дозволяли успіх. У першому випадку останній кадр сам класифікований як spoof; у другому більшість кадрів spoof.

   Тепер PASS вимагає окремого live-кворуму, середньої оцінки нижче порога й live-оцінки поточного кадру. Суперечливі вікна залишаються PENDING; сильне spoof-свідчення залишається SPOOF. Пороги, конфігурація та публічний API збережені.

   Доказ: обидві нові регресії падали до виправлення та пройшли після нього. Це доказ дефекту рішення PAD, а не відтворений фізичний обхід камерою.

2. **P2 — запізнілий auth-success після скасування. Виправлено для Core/LegacyBiometric.**

   Core передавав перевірку початкового cancellation signal у `ModuleFeedbackListener`, але адаптер застосовував її лише до підказок. Пізній OEM success міг дійти до публічного споживача Core/LegacyBiometric без owner; високорівневі prompt paths додатково мали власні gates.

   Тепер адаптер перевіряє активність початкового запиту перед onSuccess. onFailure/onCanceled після teardown збережено: деякі software-провайдери скасовують signal перед повідомленням про справжню помилку захищеного сховища. Core створює wrapper і за відсутності зовнішнього listener.

   Доказ: нова регресія запізнілого успіху падала до виправлення; пройшли тести старого/нового запиту, nullable listener і збереження terminal errors/cancellation. OEM hardware runtime не виконувався.

3. **P2 — yaw challenge не був прив’язаний до розпізнаної особи. Виправлено на рівні коду.**

   Рухи голови перевірялися до розпізнавання; після завершення challenge очищувався для всього сеансу. Незареєстроване обличчя могло виконати рухи перед наступним зображенням зареєстрованої особи. Це обхід додаткового challenge, не доведений обхід незалежного PAD.

   Перевірка руху тепер відбувається після валідного збігу ID; прив’язка зберігається після завершення challenge. Зміна ID, mismatch, розрив capture або кілька облич скидають свідчення. Enrollment не змінений; стандартний `faceChallengeEnabled=false` збережений.

   Доказ: unit-тести binding, challenge, guard та основного match-policy пройшли; production wiring повторно переглянуто. Розпізнавання користувача на потрібних кутах yaw потребує перевірки камерою: частота false reject/повторів не вимірювалася.

4. **P2 — скасований підпис залишався придатним для передачі. Виправлено на рівні коду.**

   SignaturePad обробляв ACTION_CANCEL як ACTION_UP, тож зразок не отримував ознаки invalidated. Тепер cancel очищує намальовану траєкторію й captured input, встановлює captureInvalidated і показує наявну помилку. Поточний зразок не можна підтвердити; для нового capture потрібно повторно відкрити overlay.

   Доказ: цільові тести політики цілісності й capture-сеансу та Debug compilation пройшли. Прив’язку MotionEvent → View callback перевірено статично; UI/gesture runtime-тест не проводився.

5. **P2 — підміна назви accessibility-пакета обходила попередження. Виправлено.**

   Автоматична довіра спиралася на `system app OR package-name allowlist` та заявлений самим сервісом `isAccessibilityTool`. Застосунок міг зайняти відсутню назву стороннього screenreader і прибрати явну згоду користувача.

   Автоматична довіра тепер потребує перевіреного PackageManager системного/оновленого системного походження та accessibility-tool ознаки. Сторонні screenreader-сервіси зберігають наявний шлях попередження й згоди. Бібліотека не заявляє, що це усуває можливості вже дозволеного шкідливого accessibility-сервісу.

   Доказ: тести provenance flags і агрегування всіх enabled services пройшли; caller до credential consent переглянуто. Пристрій з підробленим accessibility APK не тестувався. [Android API: isAccessibilityTool](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#isAccessibilityTool()) описує цю ознаку; вона сама не встановлює походження пакета.

6. **P2 — Retry-After міг блокувати фонового виконавця на необмежений час. Виправлено.**

   Серверний Long множився на 1000 без межі, після чого виконувався Thread.sleep. Тепер затримка обмежується 5 секундами до множення; кількість retries залишається 3. Невалідні значення використовують обмежений backoff 1/2/4 секунди. Connection закривається перед очікуванням; interrupt відновлюється й зупиняє повтори.

   Доказ: тести нормального, величезного, Long.MAX_VALUE, overflow-text, нульового, від’ємного та нечислового значення пройшли. Реальний HTTP 429 transport-тест не проводився.

7. **P2 — старий OEM retry міг запускати сенсор із mutable state нової сесії. Спочатку частково виправлено; залишок закрито окремою сесією.**

   Відкладений authenticateInternal читав mutable originalCancellationSignal. Скасування/новий start між плануванням і виконанням дозволяли старому retry зачепити новий запит.

   На вході 13 OEM authenticateInternal і 2 Facelock/Lava authenticate додано перевірку початкового Core listener. Старі скасовані retries більше не повинні запускати SDK через ці входи; цей висновок підтверджений source tracing, unit-тестом activity contract і compilation, без сенсорного runtime. Випадок null consumer закритий обов’язковим wrapper.

   **Історичний залишок первинної сесії:** старі OEM onAuthenticationError/postCancelTask могли виконувати безумовний `Core.cancelAuthentication(module)` і старі failure/cancel notifications після старту нового запиту. Цей шлях та прямі виклики вбудованих OEM-модулів тепер захищені ownership і скасуванням початкового signal; див. окремий звіт. Перевірка реальних OEM callback-послідовностей пристроями залишається невиконаною.

**Інші незакриті ризики та обмеження**

- **Сховище за компрометації app-private data.** MAC значення не прив’язаний до логічного preference key/файлу; можливе перенесення чинного ciphertext між записами. Підтримуються legacy configs із приватними `bio_key/bio_hash`; за читання й запису цих даних можливе також створення legacy-записів. Зовнішнього шляху такого доступу не знайдено. Нові записи використовують Keystore-derived ключі; цей висновок не означає, що plaintext legacy key розшифровує всі нові ciphertext. Безпечне виправлення потребує версійованого прив’язаного формату, міграції та визначеної rollback-політики. Ключі/дані не видалялися і fallback до plaintext не додавався.
- **Hook detection — евристична доступність.** Listener на localhost:27042/27047 вважається hooking. Інший застосунок може зайняти порт і спричинити відмову; підтвердження власника процесу на Android обмежене. Евристику не послаблено без альтернативного достовірного сигналу.
- **Debug sample TLS.** `App.kt` вмикає monitoring лише в BuildConfig.DEBUG, але його `OkUrlFactory` на старих Android/Huawei/Honor застосовує trust-all client із вимкненим hostname verification. Це існуючий ризик debug-збірки на недовіреній мережі; production activation не знайдено. Workaround не змінювався, щоб не зламати заявлену підтримку пристроїв без transport QA.
- **Програмна біометрія та replay.** Відтворення голосу/обличчя й caller-supplied behavior data не стають апаратно підтвердженою біометрією після цих правок. Профілі залишаються software/passive/untrusted; app-flow crypto не оголошується hardware user-auth-bound.
- **Пристрої та native SDK.** Не перевірені фактичні camera/audio/PAD false accept/false reject, OEM retry/error races, accessibility UI, USB-від’єднання, native SDK чи Android Keystore на пристроях. Публікація/споживання зовнішнім застосунком не підтверджені.

**Виконані команди та результати**

Спершу RED, після додавання регресій і до production fix:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.core.ModuleFeedbackListenerTest' :biometric-custom-face-tf:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.internal.face.tensorflow.FaceAntiSpoofingWindowTest' --console=plain
```

Результат: очікуваний BUILD FAILED, 12 tests, 3 assertion failures: canceledRequestCannotDeliverALateSuccess, spoofTerminalFrameCannotReuseEarlierLiveEvidence, oneLiveFrameCannotAuthorizeASpoofDominatedWindow. Інших помилок тестів у цьому запуску не було. Лог: `.tmp/attack-defense-20261024/red-gradle.log`.

Після завершення логічної пачки правок:

```powershell
.\gradlew.bat :common:testDebugUnitTest --tests 'dev.skomlach.common.protection.AccessibilityServiceIdentityTest' --tests 'dev.skomlach.common.translate.WebRetryPolicyTest' --tests 'dev.skomlach.common.storage.ProtectedStorageFailureTest' --tests 'dev.skomlach.common.network.NetworkApiSecurityTest' :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.core.ModuleFeedbackListenerTest' --tests 'dev.skomlach.biometric.compat.engine.LegacyBiometricCancellationTest' --tests 'dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricSessionCallbackTest' :biometric-custom-face-tf:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.internal.face.tensorflow.FaceAntiSpoofingWindowTest' --tests 'dev.skomlach.biometric.compat.engine.internal.face.tensorflow.FaceAntiSpoofingPolicyTest' --tests 'dev.skomlach.biometric.compat.engine.internal.face.tensorflow.FaceChallengeIdentityBindingTest' --tests 'dev.skomlach.biometric.compat.engine.internal.face.tensorflow.FaceChallengePolicyTest' --tests 'dev.skomlach.biometric.compat.engine.internal.face.tensorflow.FaceChallengeGuardPolicyTest' --tests 'dev.skomlach.biometric.compat.engine.internal.face.tensorflow.FaceAuthenticationAttemptPolicyTest' :biometric-custom-behavior:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.internal.behavior.BehaviorInputIntegrityPolicyTest' --tests 'dev.skomlach.biometric.compat.engine.internal.behavior.BehaviorCaptureSessionPolicyTest' --console=plain
```

Результат: BUILD SUCCESSFUL, 68 tests, 0 failures, 0 errors, 0 skipped, 15 classes. За модулями: common 14, biometric 16, face-tf 30, behavior 8. Відповідні compileDebugKotlin також виконані. Лог: `.tmp/attack-defense-20261024/green-gradle.log`. Є попередження deprecated Android APIs/Gradle options; gates не вимагають warning-free output.

Під час фінальної перевірки helper активності залишено internal, щоб не додавати protected API до публічного AbstractBiometricModule. Через зміну production input повторено лише відповідні auth-тести:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.core.ModuleFeedbackListenerTest' --tests 'dev.skomlach.biometric.compat.engine.LegacyBiometricCancellationTest' --tests 'dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricSessionCallbackTest' --console=plain
```

Результат: BUILD SUCCESSFUL, 16 tests у 3 класах, 0 failures/errors/skipped. Лог: `.tmp/attack-defense-20261024/final-auth-gradle.log`. Решту вже успішних gates не повторювали, бо їхні відповідні входи не змінювалися.

Перевірка diff:

```powershell
git --work-tree='C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat' --git-dir='C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/.git' diff --check
```

Результат: exit 0, whitespace errors немає. Нові untracked source/test файли й звіт окремо перевірені на trailing whitespace: 0 збігів. Staged state містить лише попередній docs/publishing/2026-10-02-common-release-readiness.md; власних staged змін немає. Окремо використано `git diff --no-index` із власними before snapshots для Core.kt та MiuiFaceUnlockModule.kt; різниця містила лише сесійні зміни. Snapshot directory: `.tmp/attack-defense-20261024/before/`.

Повну suite, root build/check/assemble/lint, APK, release/R8, instrumented/connected tests і device QA свідомо не запускали. Зміни обмежені Kotlin-кодом та unit-тестами чотирьох модулів, без public API/resource/manifest/dependency змін; перевірка відповідає цільовій драбині репозиторію. Gradle-команди виконувалися послідовно одним головним агентом.

**Змінені файли**

Модулі: `:common`, `:biometric`, `:biometric-custom-face-tf`, `:biometric-custom-behavior`. Прямі споживачі core/common: app, biometric-ktx та optional provider-модулі; їх окрема інтеграційна збірка не запускалася.

31 Kotlin production/test files:

- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/core/Core.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/core/ModuleFeedbackListener.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/AbstractBiometricModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/API23FingerprintModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/SoterFingerprintUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/iris/android/AndroidIrisUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/iris/samsung/SamsungIrisUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/android/AndroidFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/miui/MiuiFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/soter/SoterFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/hihonor/Hihonor3DFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/hihonor/HihonorFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/huawei/Huawei3DFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/huawei/HuaweiFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/samsung/SamsungFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/oppo/OppoFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/facelock/FacelockOldModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/lava/FaceunlockLavaModule.kt`
- `biometric/src/test/java/dev/skomlach/biometric/compat/engine/core/ModuleFeedbackListenerTest.kt`
- `common/src/main/java/dev/skomlach/common/protection/A11yDetection.kt`
- `common/src/main/java/dev/skomlach/common/protection/AccessibilityServiceIdentity.kt`
- `common/src/main/java/dev/skomlach/common/translate/LocalizationHelper.kt`
- `common/src/main/java/dev/skomlach/common/translate/WebRetryPolicy.kt`
- `common/src/test/java/dev/skomlach/common/protection/AccessibilityServiceIdentityTest.kt`
- `common/src/test/java/dev/skomlach/common/translate/WebRetryPolicyTest.kt`
- `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceAntiSpoofingPolicy.kt`
- `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceChallengePolicy.kt`
- `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceUnlockManager.kt`
- `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceAntiSpoofingWindowTest.kt`
- `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceChallengeIdentityBindingTest.kt`
- `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureController.kt`

Додано цей звіт: `docs/reviews/2026-10-24-attack-defense-security.md`. Нові source/test файли WebRetryPolicy.kt, WebRetryPolicyTest.kt, FaceChallengeIdentityBindingTest.kt і звіт залишені unstaged. Тимчасові знімки та logs не є продуктом для публікації.

