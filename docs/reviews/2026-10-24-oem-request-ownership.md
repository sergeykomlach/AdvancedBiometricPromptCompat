OEM error/cancel: ownership початкового запиту
============================================

24 жовтня 2026, `C:\Users\skoml\StudioProjects_5\AdvancedBiometricPromptCompat`, `main`.

Залишкову OEM-проблему з попередньої сесії закрито на рівні коду й цільових JVM-тестів: старі error/cancel, відкладений cleanup та vendor-події не мають звертатися до replacement. Доставка справжніх terminal errors поточного запиту після локального скасування збережена. Публічні сигнатури не змінені. Device/OEM runtime не перевірявся.

**Що змінилося і чому**

- Core окремо відрізняє «ще активний» і «залишається останнім запитом». Owner має лише atomic-прапорець; weak map не утримує module/signal через свої значення. Успіх і feedback потребують активності; failure/cancel після teardown дозволені лише для поточного owner.
- Новий owner/signal резервується до скасування попереднього. Reentrant старт із cancel callback може створити ще новіший запит, тому перед SDK start відбувається повторна перевірка. Core startup catch також очищує лише початковий signal.
- Скасування спочатку адресно вилучає очікуваний signal, потім викликає cancel поза monitor мапи. Старе завершення не видаляє запис нової операції.
- Cancel-all і clean використовують snapshot module→signal із compare-and-remove кожного запиту. Інші початкові signals залишаються доступними для teardown, доки reentrant replacement не скасує свого попередника перед новим SDK start. Скасування за snapshot не охоплює новий запит.
- У 15 OEM-модулях усі 43 виклики module-global Core cancellation замінено на скасування через captured listener. Ownership перевіряється перед обробкою старої помилки й після зовнішнього onFailure, який може одразу запустити новий запит. Cleanup не застосовується до нового signal.
- Відкладений postCancelTask належить конкретному listener; стара задача не може зняти нову задачу з черги. Власник перевіряється і перед плануванням, і під час виконання вже отриманого Runnable.
- Start/retry та відкладені surface-start серіалізовано в межах модуля; актуальність перевіряється всередині. Software cleanup використовує початкові token/signal/timeout замість поточних mutable полів.
- Усі 16 вбудованих module entrypoints захищають також прямі виклики з raw/null listener. Вони створюють локальний ownership wrapper, повторно використовують його для retry, інвалідують попередній перед його cleanup та перевіряють актуальність після reentry. Provider ID для software feedback отримується ліниво через tag(), тому runtime.moduleId збережено.
- Facelock та Lava тепер захоплюють proxy/callback конкретної спроби до постановки події в чергу. Старі service connections перевіряють identity перед зміною shared state. Cleanup інвалідує callback/connection до зовнішніх викликів; Facelock mapping видаляється навіть за remote-помилки чи disconnect. Існуючий публічний FaceLockHelper.initFacelock() збережено.

**Рев’ю**

Три незалежні read-only перевірки охопили Core/software, дубльовані OEM-шляхи та Facelock/Lava helpers. Під час рев’ю виправлені cleanup mapping/catch, конкурентний start/retry, черга зі mutable callback та provider-ID регресія. Додатковий тест порядку cancel-all відтворив запуск replacement до teardown попередника; алгоритм виправлено, а тест зроблено незалежним від порядку HashMap.

Фінальні вузькі повторні рев’ю нових конкретних дефектів у внесених правках не знайшли. Це не повний повторний security audit інших підсистем.

**Цільові перевірки**

Початкове відтворення до production-змін:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.core.CoreAuthenticationRequestTest' --console=plain
```

Результат: 5 tests, 4 очікувані assertion failures. Відтворено stale error/cancel, видалення reentrant replacement із реєстру, перезапис нового вкладеного старту та вплив cancel-all. Canceled-current storage error уже проходив як контроль збереження контракту. Лог: `.tmp/oem-request-ownership-20261024/red-gradle.log`.

Перший цільовий gate і його повтор після зміни cancel-all:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.core.CoreAuthenticationRequestTest' --tests 'dev.skomlach.biometric.compat.engine.core.ModuleFeedbackListenerTest' --tests 'dev.skomlach.biometric.compat.engine.LegacyBiometricCancellationTest' --tests 'dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricSessionCallbackTest' --tests 'dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricCallbackGateTest' --tests 'dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricFailureReasonTest' --tests 'dev.skomlach.biometric.compat.engine.internal.OemCallbackSessionTest' --tests 'dev.skomlach.biometric.compat.engine.internal.ServiceBindingStateTest' --tests 'dev.skomlach.biometric.compat.engine.internal.face.facelock.FaceLockCallbackEventTest' --tests 'dev.skomlach.biometric.compat.impl.PendingAuthStartTest' --console=plain
```

Обидва запуски: BUILD SUCCESSFUL, 53 tests у 10 класах, 0 failures/errors/skipped. Логи: `green-gradle.log` та `final-green-gradle.log` у тій самій evidence-папці. Між ними окремо відтворено новий дефект порядку:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.core.CoreAuthenticationRequestTest.cancelAllStopsItsSnapshotAndPreservesReentrantReplacement' --console=plain
```

Результат: очікувані 1 test / 1 failure; лог `cancel-order-red-gradle.log`. Після усунення залежності тесту від HashMap повторено лише `CoreAuthenticationRequestTest` першою наведеною командою: BUILD SUCCESSFUL, 7 tests; лог `deterministic-core-green-gradle.log`.

Остаточний gate після захисту direct callers і збереження provider ID:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests 'dev.skomlach.biometric.compat.engine.core.CoreAuthenticationRequestTest' --tests 'dev.skomlach.biometric.compat.engine.core.ModuleFeedbackListenerTest' --tests 'dev.skomlach.biometric.compat.engine.LegacyBiometricCancellationTest' --tests 'dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricSessionCallbackTest' --tests 'dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricCallbackGateTest' --tests 'dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricFailureReasonTest' --tests 'dev.skomlach.biometric.compat.engine.internal.OemCallbackSessionTest' --tests 'dev.skomlach.biometric.compat.engine.internal.ServiceBindingStateTest' --tests 'dev.skomlach.biometric.compat.engine.internal.face.facelock.FaceLockCallbackEventTest' --tests 'dev.skomlach.biometric.compat.impl.PendingAuthStartTest' --tests 'dev.skomlach.biometric.compat.engine.core.ModuleAuthenticationRequestsTest' --console=plain
```

**BUILD SUCCESSFUL; 56 tests, 11 classes, 0 failures, 0 errors, 0 skipped.** Production `:biometric:compileDebugKotlin` також пройшов у dependency graph цієї команди. Лог: `.tmp/oem-request-ownership-20261024/complete-green-gradle.log`.

Перевірка diff:

```powershell
git --work-tree='C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat' --git-dir='C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/.git' diff --check -- biometric docs/reviews/2026-10-24-attack-defense-security.md
```

Result: exit 0. Нові untracked Kotlin/docs перевірені окремо на trailing whitespace. Staged diff звірений зі snapshot до сесії; власних staged змін немає.

**Модуль, файли та збереження попередньої роботи**

Змінений Gradle-модуль: `:biometric`, Debug. Прямі споживачі — optional biometric providers, biometric-ktx і sample app — окремо не збиралися, оскільки існуючі public API сигнатури/resources/manifest/dependencies не змінювалися.

27 Kotlin production/test файлів:

- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/core/Core.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/core/ModuleFeedbackListener.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/AbstractBiometricModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/android/AndroidFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/facelock/FaceLock.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/facelock/FaceLockHelper.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/facelock/FacelockOldModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/hihonor/Hihonor3DFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/hihonor/HihonorFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/huawei/Huawei3DFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/huawei/HuaweiFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/lava/FaceunlockLavaModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/lava/FaceVerifyManager.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/miui/MiuiFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/oppo/OppoFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/samsung/SamsungFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/soter/SoterFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/API23FingerprintModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/SoterFingerprintUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/iris/android/AndroidIrisUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/iris/samsung/SamsungIrisUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt`
- `biometric/src/test/java/dev/skomlach/biometric/compat/engine/core/ModuleFeedbackListenerTest.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/OemCallbackSession.kt`
- `biometric/src/test/java/dev/skomlach/biometric/compat/engine/core/CoreAuthenticationRequestTest.kt`
- `biometric/src/test/java/dev/skomlach/biometric/compat/engine/internal/OemCallbackSessionTest.kt`
- `biometric/src/test/java/dev/skomlach/biometric/compat/engine/core/ModuleAuthenticationRequestsTest.kt`

Додано цей звіт і позначено закриття OEM-залишку в `2026-10-24-attack-defense-security.md`. Evidence та before snapshots: `.tmp/oem-request-ownership-20261024/`. Попередні зміни збережені; комітів і push немає.

**Не виконано та залишкові інтеграційні ризики**

Повну suite, APK/app assemble, release/R8, lint, instrumented/connected tests, device QA та перевірку зовнішнього Maven consumer не запускали. Gradle процеси виконувалися послідовно головним агентом.

Unit-тести покривають адресний signal cleanup, canceled-current terminal errors, reentrant start/cancel, порядок predecessor teardown, старі відкладені задачі, raw/direct callers та ownership черги. Реальна Binder/SurfaceHolder/OEM SDK поведінка, callback threading і прийнятність на пристроях залишаються runtime-неперевіреними. Не заявляється device-підтвердження або усунення інших ризиків сховища, debug TLS, hook-евристик чи software-biometric replay із попереднього звіту.

