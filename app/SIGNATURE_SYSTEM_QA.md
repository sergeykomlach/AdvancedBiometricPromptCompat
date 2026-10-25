# Sample: власний підпис + обов’язкова системна біометрія

На головному екрані натиснути **Signature + system biometrics** / **Підпис + системна біометрія**.
Сценарій має власний `SignatureCaptureView`, а не бібліотечний pad чи behavior overlay.

## Підготовка

- Android 9+ із зареєстрованою системною біометрією. Якщо native API/сенсор/enrollment
  недоступні, demo не підтверджує дію і не пропонує software/PIN fallback.
- Для MATCHED спочатку зареєструвати підпис: головний екран → Dialog → Behavior →
  Signature → Enroll. Без behavior-профілю додатковий сигнал може бути UNAVAILABLE;
  це не заважає обов’язковому переходу до системної перевірки.
- Silent, Crypto та Allow device credentials на головному екрані не змінюють
  політику цього demo. Тут немає CryptoObject і немає device-credential fallback.
- Перевірки enrollment лишаються звичайним окремим flow; цей екран не створює та
  не перевчає профілі й не видаляє наявні дані.

## Основний flow

1. Намалювати свіжий чіткий підпис; завершити всі штрихи й натиснути **Verify signature and continue**.
2. Побачити окремий outcome Behavior: MATCHED, NOT_MATCHED або UNAVAILABLE.
3. Для кожного outcome має відкритися системна біометрія. До її успіху demo action
   залишається непідтвердженою. Behavior match сам не показує success.
4. Успішна системна біометрія підтверджує demo action; mismatch behavior не є veto.
   System failure/cancel/unavailable не підтверджують дію.
5. Натиснути **New signature**, повторити з іншим підписом. Незавершений або
   недостатньо якісний capture показує помилку вводу до запуску auth.

## Негативні та lifecycle сценарії

- Повторне натискання Submit під час роботи не запускає інший prompt.
- Cancel під час behavior evaluation, Back у системному prompt, Home, навігація
  назад, rotation/recreation: немає пізнього success; чернетка підпису та попередня
  авторизація не відновлюються. Для нової спроби потрібен новий capture.
- Відкладений callback попередньої спроби не змінює поточну.
- Cancel gesture, obscured/partially obscured touch, multitouch, понад 30 секунд,
  надмірна кількість точок або invalid coordinates: capture відхиляється; потрібен Restart.
- Скриншоти/Recents захищені під час перебування на екрані. Повернення фокуса після
  native prompt не знімає захист за вимкненого глобального Window secure. Після
  виходу відновлюється актуальна користувацька preference.
- Перевірити portrait/landscape, великий шрифт, scroll поза canvas, кілька pen strokes,
  light/dark та українську/англійську локаль на OnePlus.

## Межі доказів

Sample викликає публічний behavior manager як advisory evaluator. Це compatibility
observations власного UI: вони не стають trusted capture, hardware proof чи юридичним
підписом. Сирий Bundle існує лише під час синхронного parsing manager, після чого
очищується; він не передається системному Builder і не потрапляє до логів.

Єдиний авторизатор — окремий `BIOMETRIC_API/HARDWARE` request із
`SYSTEM_BIOMETRIC`, `allowBehaviorOnlySuccess=false` і credentials disabled.
System floor не означає автоматично Android Class 3; demo не використовує crypto.

JUnit/Robolectric перевіряють оркестрацію, policy, власний capture, privacy cleanup і
lifecycle. Реальна поява/успіх native prompt, OEM touch, screenshots та Frida
потребують окремого device QA. Повний контроль процесу залишається поза гарантіями
локального callback: [security contract](../docs/security/behavior-production-security.md).
