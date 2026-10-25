# Security cleanup і повторне рев’ю — 2026-10-25

Репозиторій: `C:\Users\skoml\StudioProjects_5\AdvancedBiometricPromptCompat`, гілка `main`.
Scope: поточний diff (235 шляхів на початку роботи) та прямі залежності змінених
auth/trust, behavior, Face-TF, common storage/network і `tools/security` поверхонь.
Це source review і локальна регресійна перевірка, без device pentest або оцінки
всіх незмінених модулів і зовнішніх залежностей.

## Підтверджені P2, виправлені в цьому проході

1. **Face-TF: прогрес challenge переживав непридатний кадр.**
   `TensorFlowFaceUnlockManager.processFaces()` скидав PAD/match counters, але
   залишав частковий або завершений face challenge після `InvalidFace`.
   Та сама identity могла продовжити старий challenge. Тепер ця гілка запускає
   новий challenge з CENTER, якщо challenge увімкнено. Два нові Robolectric-тести
   виконують справжню production-гілку менеджера, окремо для partial/completed
   state. Обидва падали до виправлення та пройшли після нього. Це дефект
   безперервності challenge; повного обходу незалежного PAD не доведено.

2. **Behavior: enrollment мовчки замінював пошкоджений профіль.**
   `BehaviorTemplateStore.save()` перетворював невдалий розбір на порожню історію.
   Тепер усі наявні записи перевіряються до запису; пошкодження спричиняє
   `ProtectedStorageUnavailableException`. Регресія перевіряє три некоректні
   формати, заміну того самого тегу, обхід іншим тегом, незмінність даних після
   відмови та відновлення лише після явного `remove()`. До виправлення тест падав.

3. **Tools/security: ручний inventory успадковував небезпечний Git-контекст.**
   `GIT_DIR` підставляв HEAD іншого fixture-репозиторію; локальний clean filter
   виконувався під час `git status` для зміненого файлу того самого розміру.
   Передумова — контроль середовища запуску або локального Git-конфігу, а не
   віддалений неавторизований доступ. Спільний `mobile_audit_git.py` тепер
   забезпечує ту саму ізоляцію для manual і auto: очищення `GIT_*`, вимкнення
   filters/hooks/fsmonitor і PATH поза checkout. Два тести відтворили обидва
   сценарії до виправлення. Перевірки inventory path/scope, hash freshness,
   symlink/reparse та точного JSON evidence не послаблені.

## Cleanup

- Прибрано застарілі часові параметри `hookDetectionFailure`, поля timestamp і
  `updateTimestamp()` у внутрішньому callback-коді. Відомий hook, як і раніше,
  безумовно відхиляє результат; rejected crypto object не повертається.
- Прибрано невикористані `BehaviorCaptureSessionRegistry.startedAt()` і
  `BehaviorBiometricManager.MAX_CAPTURE_DURATION_MS`.
- Прибрано зайве nullable/cast обчислення розміру перевірених записів behavior.
- Git helpers винесено без дублювання між manual/auto runner.
- Публічні callback/store API та runtime dependencies не змінені. Robolectric
  додано лише до тестових залежностей Face-TF.

Змінено 12 наявних файлів: `BehaviorBiometricManager.kt`, `BehaviorPromptData.kt`,
`BehaviorTemplateStore.kt`, `BehaviorTemplateStoreTest.kt` у behavior;
`TensorFlowFaceUnlockManager.kt` і `build.gradle` у Face-TF;
`BiometricPromptCompat.kt`, `HookDetectionResult.kt`, `HookDetectionResultTest.kt`
у biometric; `mobile_audit_auto.py`, `mobile_audit_inventory.py`,
`test_mobile_audit.py` у tools/security.
Додано `FaceChallengeInvalidFrameTest.kt`, `tools/security/mobile_audit_git.py`
і цей звіт. Повні шляхи та patch збережені у локальних артефактах нижче.

## Перевірка

Цільові Android-тести: **67 пройшли**, 0 failures/errors/skipped
(biometric — 19; behavior — 22; Face-TF — 26).

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*HookDetectionResultTest' --tests '*AuthFlowCompletionTest' --tests '*BiometricTrustPolicyTest' :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceChallenge*Test' --tests '*FaceAntiSpoofingWindowTest' --tests '*FaceAuthenticationAttemptPolicyTest' --tests '*FaceCaptureSessionTest' :biometric-custom-behavior:testDebugUnitTest --tests '*BehaviorTemplateStoreTest' --tests '*BehaviorSecurityBoundaryTest' --tests '*BehaviorProtectedStoreTest' --tests '*BehaviorRemovalSessionTest' --tests '*BehaviorManagerSecurityTest' --console=plain
```

Python suite: **58 tests, OK (skipped=3)** — 55 виконано успішно, 3 POSIX-тести
пропущено на Windows (executable bits, process groups/signals). Час — 350.418 s.
Фіктивні локальні engines використовувалися лише для unit-тестів runner;
реальний Codex-аудит або мережевий запуск моделей не виконувався.

```powershell
python -B -m unittest discover -s tools/security -p 'test_mobile_audit*.py'
```

Інтеграційна збірка: **BUILD SUCCESSFUL**, debug APK створено.
Наявна конфігурація debug має `minifyEnabled true`, тому assemble також виконав
`minifyDebugWithR8`. Release variant не запускався; build-конфігурацію app не змінено.

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

Red-перевірки до production fix:

```powershell
.\gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceChallengeInvalidFrameTest' :biometric-custom-behavior:testDebugUnitTest --tests '*BehaviorTemplateStoreTest.corruptRecordCannotBeReplacedOrBypassedByAnotherEnrollmentWithoutExplicitRemoval' --console=plain
.\gradlew.bat :biometric-custom-face-tf:testDebugUnitTest --tests '*FaceChallengeInvalidFrameTest' --console=plain
python -B -m unittest discover -s tools/security -p test_mobile_audit.py -k manual_inventory
```

Перший Gradle red зупинився на очікуваному assertion behavior до запуску face task;
окремий face red показав два очікувані assertions. Python red після уточнення
same-size filter fixture показав дві очікувані відмови. Початкова Python-команда
із прямим module name `test_mobile_audit...` з кореня не знайшла модуль; її ImportError
не враховується як підтвердження дефекту.

Scoped diff check: no whitespace errors. Індекс і staged entries побайтово
збігаються зі збереженим станом до роботи. Commit/push/staging не виконувалися.
Перевірка scoped diff та збереження index:

```powershell
python -B .tmp/security-cleanup-20261025T113705Z/verify_cleanup.py
```

Локальні артефакти: `.tmp/security-cleanup-20261025T113705Z/` — `verification.json`,
`cleanup.patch`, red/green logs і `assemble-debug.log`.

## Повторне рев’ю та межі

Незалежний повторний source review виправлень і прямих залежностей не виявив нових
підтверджених зауважень. Окремо переглянуті core/trust/OEM та common runtime зміни
не дали нових підтверджених P1/P2. Це не гарантія відсутності вразливостей у проекті.

Залишається відоме обмеження protected storage: MAC значення не прив’язаний до
логічного preference key/файлу, зберігається legacy migration і немає hardware
rollback resistance. Контроль app-private файлів дозволяє підміну/відкат чинних
записів; зовнішнього шляху такого контролю в перевіреному scope не знайдено.
Безпечне усунення потребує окремого версійованого формату і міграції, тут їх не
змінювали. Локальне виявлення hooking не атестує незмінений процес.

Не запускалися: реальні шість AI-етапів audit, повний Android test/lint suite,
release/connected tests, OnePlus або інший device QA, атаки Frida, camera inference
та фізичний PAD. OEM callback/teardown поведінка й FAR/FRR цим проходом не доведені.
