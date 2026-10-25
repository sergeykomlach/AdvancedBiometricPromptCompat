# Multi-window: актуалізація до Android 17 зі збереженням legacy

## Резолюція

Оновлено `common` і прямі UI/lifecycle consumers у `biometric`. Legacy API/OEM/Flyme paths збережено. 18 focused tests і targeted debug assemble пройшли. Це не повна сертифікація Android 17/OEM і не міграція targetSdk застосунку.

Фокусну евристику `FrameworkFingerprintUiSession` видалено разом із її тестами. Pixel/model/API37 hardcode не повертався. **BIO-REG-007 знову OPEN:** для framework fingerprint-only маршруту без перевіреного контракту системного UI повертається звичайний compat dialog; на Pixel можливий попередній дубль. Це відоме обмеження, а не закритий дефект. Системний текст permission-діалогу з попередньої правки збережено.

## Що змінилося

- `MultiWindowSupport.get(activity)` прив'язує метрики до конкретного host через weak reference. Публічний `get()` залишено для старих інтеграцій; biometric Builder і dialog використовують Activity-scoped варіант.
- `get(activity, isInBubbleTask)` і `Builder.setInBubbleTask(Boolean)` дозволяють app-owned task resolver повідомити про дочірню Activity Bubble-task. Сам `isLaunchedFromBubble` не є доказом членства всіх Activity задачі. Це presentation hint, не authentication boundary. Без інтеграції app-owned resolver така інформація не вигадується.
- Valid API30+ current/maximum WindowMetrics мають пріоритет над visible-rect/Flyme heuristic. Fullscreen з IME, caption/taskbar/edge-to-edge більше не переходить у legacy detection лише через різницю розмірів content view.
- Modern navigation/status dimensions беруться з WindowInsets, safe bounds включають caption/system bars/cutout, але не IME. Resource/navigation-key heuristics залишені для відсутніх modern insets. Native panel resolution не визначає режим logical window.
- На Android 16+ великих екранах (`smallestScreenWidthDp >= 600`) бібліотека консервативно не встановлює orientation lock; windowed/square стани також не блокуються. Ця бібліотечна політика не стверджує, що ОС ігнорує lock для кожного targetSdk. Legacy phone behavior збережено.
- Compat dialog перераховує ширину на configuration/layout changes від власного Activity-host; listener видаляється при destroy view. Розміри не беруться з іншої resumed Activity.
- Multi-window getter у dialog більше не має побічного `stopAuth/startAuth`. Existing UDFPS placement fallback збережено.
- `AppBackgroundDetector` спостерігає конкретний host додатково до process. Host STOP/DESTROY завершує поточний запит, навіть якщо інша Activity лишається resumed. PAUSE сам по собі не означає, що host невидимий. Fragment tracking переведено з RESUME/PAUSE на START/STOP. Generation-owned pending tasks і listener cleanup збережено; Home/Recents/Power cancellation не послаблено.

## Зіставлення з RoboForm

Read-only перевірено:

- `rf-dev/roboform/android/java/app/src/main/java/com/siber/roboform/main/ui/bubblecompat/AppBubbleTaskSessionPolicy.kt`
- `.../AppBubbleController.kt` (`isActivityInActiveBubbleTask`)
- `.../util/localehelper/MultiWindowFocusSession.kt`

Використано висновок про Activity/task ownership та обмеженість launch flag. Код керування Bubble publication/task generations не копіювався. RoboForm не редагувався і не збирався. Після оновлення бібліотеки інтеграція може передати перевірений результат `AppBubbleController.isActivityInActiveBubbleTask(activity)` у `Builder.setInBubbleTask(...)` для нового auth flow. Ця інтеграція ще не виконана.

## Змінені source/test файли

```text
common/src/main/java/dev/skomlach/common/multiwindow/MultiWindowSupport.kt
common/src/main/java/dev/skomlach/common/multiwindow/WindowMetricsCompat.kt
common/src/main/java/dev/skomlach/common/multiwindow/WindowStatePolicy.kt
common/src/test/java/dev/skomlach/common/multiwindow/WindowStatePolicyTest.kt
biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt
biometric/src/main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt
biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialog.kt
biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt
biometric/src/main/java/dev/skomlach/biometric/compat/utils/appstate/AppBackgroundDetector.kt
biometric/src/test/java/dev/skomlach/biometric/compat/utils/appstate/AppBackgroundDetectorPolicyTest.kt
```

Видалені створені в попередній ітерації untracked файли: `biometric/.../impl/FrameworkFingerprintUiSession.kt`, `biometric/src/test/.../impl/FrameworkFingerprintUiSessionTest.kt`. Сторонні dirty/staged зміни збережено. Код `app`, SDK/dependencies, hardware enrollment, credentials і permission state не змінювалися. Commit/push не виконано.

## Перевірка

```powershell
.\gradlew.bat :common:testDebugUnitTest --tests "dev.skomlach.common.multiwindow.WindowStatePolicyTest" :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.utils.appstate.AppBackgroundDetectorPolicyTest" --tests "dev.skomlach.biometric.compat.impl.LegacyAuthStartDelayTest" :app:assembleDebug --init-script C:/Users/skoml/AppData/Local/Temp/codex-biometric-pixel9fold-20260905/device-debug.init.gradle --console=plain
```

PASS, 12s: WindowStatePolicy 13 tests, host lifecycle policy 3 tests, legacy start delay 2 tests; 0 failures/errors. Scoped `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat -c core.safecrlf=false diff --check -- <changed paths>`: PASS. Debug init script вимикає minify/shrinkResources; це не R8/release proof.

APK SHA256: `13EACC359B761D191B45934FB1D2B647F96E0D033567348C55CC9DE9B293D382`.

`adb -s adb-4A221FDKD001JR-O6LimJ._adb-tls-connect._tcp install -r app/build/outputs/apk/debug/app-debug.apk`: Success. Pixel 9 Pro Fold / Android 17 API37. Після розблокування користувачем Wi-Fi ADB перепідключено через `adb connect 192.168.0.10:34555`; hardware serial повторно перевірено: `4A221FDKD001JR`.

Device smoke на цьому APK, unfolded 2152×2076:

- Fullscreen AUTO/VOICE auth (`isRegister=false`): UI відкрився, root dialog bounds `[64,1065][2087,1940]` залишаються всередині host. Голосове підтвердження не виконувалося; завершення `onFailed` / `onUIClosed`, не auth success.
- Через launcher Split-Screen відкрито MainActivity зверху (`[0,0][2152,1026]`) та LeakCanary Activity цього ж пакета знизу (`[0,1050][2152,2076]`). `dumpsys activity` підтвердив multi-window.
- У верхньому host відкрито AUTO/VOICE setup: лог `19:03:35.518 isRegister=true`, `19:03:35.706 onUIOpened`; root compat dialog `[0,151][2152,1026]` відповідає верхньому host. Це перевірка геометрії setup-діалогу, не авторизації чи успішного enrollment. Accessibility tree захищеного діалогу не розкриває його внутрішні controls; їх layout окремо не підтверджено.
- Натискання у нижньому вікні не супроводжувалося негайним terminal callback, але без окремого доказу lifecycle transitions це не повна перевірка PAUSE/STOP policy.
- Setup завершився `19:04:05.699 onFailed`, `19:04:05.709 onUIClosed`; успішного enrollment не зафіксовано. Повернуто MainActivity у fullscreen та закрито список сценаріїв. У scoped process logs не виявлено `FATAL EXCEPTION`.

Використано `adb -s 192.168.0.10:34555 shell uiautomator dump /data/local/tmp/codex-window-ui.xml`, `shell dumpsys activity activities`, `logcat --pid=19457 -d -v time`, screenshot та UI input. Resize саме під час активного запиту і hardware success після resize не підтверджені.

Не запускалися: full suite, release/R8, instrumented tests, інші OEM/пристрої, RoboForm integration build, targetSdk37 migration. Bubble child-task, desktop/external display, Samsung legacy modes, hardware auth після resize потребують runtime matrix.

Джерела платформи: [Android 17 orientation/resizability changes](https://developer.android.com/about/versions/17/behavior-changes-17#device-form-factors), [multi-window lifecycle](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode#lifecycle).
