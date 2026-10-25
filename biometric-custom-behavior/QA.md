# Behavior: передача в QA

Статус: кандидат для ручного QA. Автоматичні регресії та збірка не замінюють
перевірку на пристрої. Режим залишається експериментальним software biometric;
пороги matching не калібровані за FAR/FRR та не дають hardware-backed гарантій.

## Підготовка sample app

1. Установити `app/build/outputs/apk/debug/app-debug.apk`. Для незалежних сценаріїв
   використовувати окремі тестові дані/інсталяцію; очищення app data видаляє всі профілі.
2. Вимкнути **Silent auth**, **Crypto** та **Allow device credentials**.
   Device credentials можуть завершити flow іншим способом і приховати результат behavior.
3. Кнопка **Dialog** відкриває список enrollment. Вибрати рядок
   `LEGACY_API/BIOMETRIC_BEHAVIOR` (або відповідний доступний behavior request).
4. Compat prompt автоматично відкриває **BehaviorCaptureFragment**, вибрати Typing, Signature або Combined.
   Enrollment: **Enroll**. На головному екрані відповідний рядок запускає authentication:
   **той самий режим → Verify**. Кожна спроба потребує свіжого введення.
5. Записати модель, API Android, версію APK/хеш, орієнтацію, розмір/масштаб шрифту,
   мову, активні accessibility services та точний callback/повідомлення.
6. Enrollment не змінено. Для behavior-only authentication за замовчуванням очікується
   `INSUFFICIENT_TRUST` до capture. Для окремої перевірки matching консюмер має явно
   налаштувати `BiometricTrustPolicy(COMPATIBILITY, allowBehaviorOnlySuccess=true)`.
   Sample app не вмикає цей дозвіл автоматично. Mixed request із допустимим primary
   provider перевіряє стандартний supplemental flow.

## Основні сценарії

- **Typing:** enroll фразу щонайменше з 5 символів через вбудовану клавіатуру,
  з природним ритмом; автентифікувати ту саму фразу свіжим введенням.
  Verify натиснути протягом 2 секунд після останньої клавіші.
  Інша фраза, paste/autofill-подібне або надто рівномірне введення мають відхилятися.
- **Signature:** enroll чіткий непрямолінійний підпис з кількома рухами/штрихами.
  Перевірити свіжий схожий підпис, інший підпис, коротку пряму та повторені точки.
  Чернетка тексту, введена до переходу Combined → Signature, не впливає на matching.
- **Combined:** набрати фразу, малювати підпис довше 2 секунд, Verify до завершення
  30-секундної сесії. Правильні обидва канали — спроба matching; відсутній або
  неправильний канал — відмова. Відлік починається при відкритті/Restart.
- Повторне enrollment з тим самим tag через API: до 5 останніх зразків,
  із додатковим лімітом серіалізованого профілю 262144 символи. Великі зразки
  витісняють найстаріші; після перезапуску процесу профіль читається.

## UI, відновлення та lifecycle

- Відкрити та скасувати prompt кілька разів: без crash, попередні input/path
  очищені. Кнопки активації B та overlay немає; загальні title/status/cancel видимі.
  **Restart** очищає capture і створює новий nonce,
  зберігаючи вибраний режим. Перемикання режиму саме по собі не прибирає помилку interruption.
- Після CANCEL жесту, obscured touch або multitouch показується відмова;
  наступні звичайні дотики не відновлюють старий capture. Restart після усунення причини
  дозволяє новий capture. Старий/повторно поданий nonce не проходить.
- Почекати понад 30 секунд, спробувати ввід/Verify: timeout. Restart має працювати.
  Повторити з вимкненням екрана/sleep: сон враховується в загальному deadline.
- Compact portrait/landscape, split-screen, великий шрифт, light/dark,
  українська/англійська/RTL: тіло прокручується, Restart та Enroll/Verify доступні.
  Вертикальний штрих у pad не прокручує панель; прокрутка поза pad працює.
- Скасувати весь prompt / background / rotation до submit та під час відкладеного
  результату: завершений flow не повертає пізній success; наступне відкриття працює.
- Consumer-owned views: перевірити typing/backspace, signature та зняття listeners;
  попередні touch-filter, accessibility/autofill protection і батьківські views
  збережені. Ці views належать caller і не переносяться між parent.
  Для власного екрана використати `BehaviorCaptureBinding`: жодного бібліотечного
  вікна до submit; autocomplete/autocorrect/suggestions/autofill вимкнені під час
  monitoring, початкові settings відновлені після close. Перевірити password,
  numeric/multiline та `AutoCompleteTextView` adapter.
- Mixed authentication: capture behavior перший. За замовчуванням ANY/ALL після
  behavior success, mismatch або provider timeout переходять до допустимих primary
  providers; результат behavior — лише `onSupplementalResult`, не `onSucceeded`.
  ALL вимагає всіх допущених primary; supplemental mismatch не блокує їхній успіх.
  З явним behavior-primary opt-in і compatibility floor ANY може завершитися на
  behavior, а ALL також вимагає його успіху. Вищий floor не обходиться opt-in.
  Cancel/Back скасовує всю операцію; під час handoff немає
  двох вікон або пізнього відкриття після скасування.

## Негативні контракти

- Hook-positive process: capture припиняється, delayed success відхиляється навіть
  після 500 мс, новий Builder не скидає сигнал. Негативний detector не доводить
  відсутності Frida. Підміну matcher/callback, delayed injection, обходи detector
  та читання input/decryption boundaries перевіряти окремим runtime assessment.
- Capture extras містять лише handle, без фрази/масивів. Raw payload не підміняє
  sealed sample; role mismatch/expiry/consume не переходять у raw fallback.
  Старий Bundle після close не повертає sample.
- Activity recreation закриває ownerless capture без відновлення тексту/nonce та
  без пізнього success. Перевірити saved view state й consumer settings на OnePlus.
- Store overflow відхиляє нові профілі без видалення старих. Legacy overflow має
  бути unavailable; явне видалення зайвих/усіх профілів відновлює роботу.
- Consumer із backup має застосувати виключення з
  [security contract](../docs/security/behavior-production-security.md) та перевірити
  cloud backup і device transfer фактичного release artifact.

- Без зразка в PASSIVE/Silent — відмова, без прихованого успіху.
- Crypto для цього provider не підтримується: очікується `CRYPTO_ERROR`.
- Повтор payload у межах 30 секунд, у тому числі після іншої спроби або зі зміненим
  timestamp origin/нерелевантним каналом, відхиляється. Повтор enrollment дозволений;
  його payload не можна негайно використати для authentication.
- Видалення enrollment через інший store під час pending result скасовує success.
- Недоступний protected storage/Keystore — відмова; fallback на plaintext не вмикати.
- Untrusted accessibility/overlays — відмова strict capture відповідно до чинної
  whitelist policy. Після QA відновити початкові системні налаштування.
- У логах немає фраз, сирих timestamps/координат/pressure; діагностика агрегована.
  Після серії mismatch перевірити тимчасовий lockout та його завершення.

## Автоматична перевірка

З кореня checkout, послідовно:

```powershell
.\gradlew.bat :biometric-custom-behavior:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
```

Bundle/View/MotionEvent регресії виконуються через Robolectric на API 29.
Store roundtrip інжектує справжні SharedPreferences і перевіряє codec/ліміт/retention;
це не доказ роботи Android Keystore на фізичному пристрої. Фактичний touch dispatch,
OEM layout, stylus, accessibility, захищене сховище та статистика false accept/reject
потребують окремих результатів ручного QA.
