$behaviorKeys = @(
    'biometriccompat_behavior_help_timeout',
    'biometriccompat_behavior_help_unavailable',
    'biometriccompat_behavior_help_sample_missing',
    'biometriccompat_behavior_help_not_registered',
    'biometriccompat_behavior_help_enrolled',
    'biometriccompat_behavior_help_accepted',
    'biometriccompat_behavior_help_not_matched',
    'biometriccompat_behavior_help_lockout',
    'biometriccompat_behavior_help_lockout_permanent',
    'biometriccompat_behavior_help_sample_valid',
    'biometriccompat_behavior_help_typing_phrase_short',
    'biometriccompat_behavior_help_typing_sample_short',
    'biometriccompat_behavior_help_typing_event_mismatch',
    'biometriccompat_behavior_help_typing_too_fast',
    'biometriccompat_behavior_help_typing_too_uniform',
    'biometriccompat_behavior_help_signature_sample_short',
    'biometriccompat_behavior_help_signature_path_short',
    'biometriccompat_behavior_help_signature_shape_small',
    'biometriccompat_behavior_help_signature_shape_simple',
    'biometriccompat_behavior_help_signature_duplicate_points',
    'biometriccompat_behavior_button_content_description',
    'biometriccompat_behavior_overlay_title_enroll',
    'biometriccompat_behavior_overlay_title_auth',
    'biometriccompat_behavior_mode_typing',
    'biometriccompat_behavior_mode_signature',
    'biometriccompat_behavior_mode_combined',
    'biometriccompat_behavior_phrase_hint',
    'biometriccompat_behavior_signature_label',
    'biometriccompat_behavior_action_enroll',
    'biometriccompat_behavior_action_verify',
    'biometriccompat_behavior_hint_typing',
    'biometriccompat_behavior_hint_signature',
    'biometriccompat_behavior_hint_combined',
    'biometriccompat_behavior_error_touch_obscured',
    'biometriccompat_behavior_error_phrase_too_long',
    'biometriccompat_behavior_error_need_typing',
    'biometriccompat_behavior_error_need_signature',
    'biometriccompat_behavior_key_space',
    'biometriccompat_behavior_status_checking'
)

$tfFaceKeys = @(
    'biometriccompat_tf_face_help_timeout',
    'biometriccompat_tf_face_help_canceled_by_new_operation',
    'biometriccompat_tf_face_help_model_not_available',
    'biometriccompat_tf_face_help_model_enrollment_tag_not_provided',
    'biometriccompat_tf_face_help_model_not_registered',
    'biometriccompat_tf_face_help_model_image_is_blurry',
    'biometriccompat_tf_face_help_model_fake_face_detected',
    'biometriccompat_tf_face_help_model_retry',
    'biometriccompat_tf_face_help_model_too_many_faces',
    'biometriccompat_tf_face_help_model_look_straight_ahead',
    'biometriccompat_tf_face_help_model_too_dark',
    'biometriccompat_tf_face_help_model_no_camera_permissions',
    'biometriccompat_tf_face_help_model_no_front_camera',
    'biometriccompat_tf_face_help_model_camera_low_res',
    'biometriccompat_tf_face_help_model_camera_locked_out',
    'biometriccompat_tf_face_help_model_camera_disabled',
    'biometriccompat_tf_face_help_too_many_attempts_try_later',
    'biometriccompat_tf_face_help_too_many_attempts_permanent',
    'biometriccompat_tf_face_help_model_already_registered',
    'biometriccompat_tf_face_help_model_not_detected',
    'biometriccompat_tf_face_help_camera_error',
    'biometriccompat_tf_face_help_model_error_generic',
    'biometriccompat_tf_face_prompt_start_auth',
    'biometriccompat_tf_face_prompt_start_enroll'
)

$zkFingerKeys = @(
    'biometriccompat_zkfinger_help_timeout',
    'biometriccompat_zkfinger_help_canceled_by_new_operation',
    'biometriccompat_zkfinger_help_sensor_not_found',
    'biometriccompat_zkfinger_help_usb_permission_denied',
    'biometriccompat_zkfinger_help_sensor_unavailable',
    'biometriccompat_zkfinger_help_not_registered',
    'biometriccompat_zkfinger_help_already_registered',
    'biometriccompat_zkfinger_help_same_finger_required',
    'biometriccompat_zkfinger_help_scan_again',
    'biometriccompat_zkfinger_help_enroll_start',
    'biometriccompat_zkfinger_help_enroll_progress',
    'biometriccompat_zkfinger_help_enroll_finalizing',
    'biometriccompat_zkfinger_help_template_error',
    'biometriccompat_zkfinger_help_too_many_attempts_try_later',
    'biometriccompat_zkfinger_help_too_many_attempts_permanent',
    'biometriccompat_zkfinger_prompt_start_auth',
    'biometriccompat_zkfinger_prompt_start_enroll'
)

$appKeys = @(
    'app_name',
    'no_data',
    'fullscreen',
    'window_secured',
    'silent_auth_no_ui',
    'crypto_data_encryption',
    'allow_device_credentials'
)

$sharedBiometricKeys = @(
    'biometriccompat_use_devicecredentials',
    'biometriccompat_untrusted_a11y',
    'biometriccompat_widegamut_error',
    'biometriccompat_permession_error',
    'biometriccompat_camera_blocked',
    'biometriccompat_cryptography_not_supported_error',
    'biometriccompat_credentials_error',
    'biometriccompat_cryptography_failed_error',
    'biometriccompat_long_init_error',
    'biometriccompat_untrusted_a11y_error',
    'biometriccompat_window_error',
    'biometriccompat_api_disabled_error',
    'biometriccompat_start_authentication_error',
    'biometriccompat_generic_error',
    'biometriccompat_generic_error_with_code',
    'biometriccompat_required_crypto_rejected_error',
    'biometriccompat_required_crypto_missing_error',
    'biometriccompat_activity_destroyed_error',
    'biometriccompat_internal_error',
    'biometriccompat_sensor_privacy_start_use_camera_notification_content_title',
    'biometriccompat_face_sensor_privacy_enabled',
    'biometriccompat_face_error_canceled',
    'biometriccompat_face_error_hw_not_available',
    'biometriccompat_face_error_hw_not_present',
    'biometriccompat_face_error_lockout',
    'biometriccompat_face_error_lockout_permanent',
    'biometriccompat_face_error_lockout_screen_lock',
    'biometriccompat_face_error_no_space',
    'biometriccompat_face_error_not_enrolled',
    'biometriccompat_face_error_security_update_required',
    'biometriccompat_face_error_timeout',
    'biometriccompat_face_error_unable_to_process',
    'biometriccompat_face_error_user_canceled',
    'biometriccompat_face_error_vendor_unknown',
    'biometriccompat_fingerprint_dialog_default_subtitle',
    'biometriccompat_face_dialog_default_subtitle',
    'biometriccompat_face_acquired_insufficient',
    'biometriccompat_face_acquired_too_bright',
    'biometriccompat_face_acquired_too_dark',
    'biometriccompat_face_acquired_too_close',
    'biometriccompat_face_acquired_too_far',
    'biometriccompat_face_acquired_too_high',
    'biometriccompat_face_acquired_too_low',
    'biometriccompat_face_acquired_too_right',
    'biometriccompat_face_acquired_too_left',
    'biometriccompat_face_acquired_poor_gaze',
    'biometriccompat_face_acquired_not_detected',
    'biometriccompat_face_acquired_too_much_motion',
    'biometriccompat_face_acquired_recalibrate',
    'biometriccompat_face_acquired_too_different',
    'biometriccompat_face_acquired_too_similar',
    'biometriccompat_face_acquired_pan_too_extreme',
    'biometriccompat_face_acquired_tilt_too_extreme',
    'biometriccompat_face_acquired_roll_too_extreme',
    'biometriccompat_face_acquired_obscured',
    'biometriccompat_face_acquired_sensor_dirty',
    'biometriccompat_face_acquired_dark_glasses_detected',
    'biometriccompat_face_acquired_mouth_covering_detected',
    'biometriccompat_face_acquired_recalibrate_alt',
    'biometriccompat_face_acquired_dark_glasses_detected_alt',
    'biometriccompat_face_acquired_mouth_covering_detected_alt',
    'biometriccompat_use_screen_lock_label',
    'biometriccompat_screen_lock_prompt_message',
    'biometriccompat_fingerprint_error_lockout',
    'biometriccompat_fingerprint_not_recognized'
)

function Save-Xml([xml]$doc, [string]$path) {
    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Indent = $true
    $settings.IndentChars = '    '
    $settings.NewLineChars = "`r`n"
    $settings.NewLineHandling = [System.Xml.NewLineHandling]::Replace
    $settings.Encoding = New-Object System.Text.UTF8Encoding($false)
    $writer = [System.Xml.XmlWriter]::Create($path, $settings)
    $doc.Save($writer)
    $writer.Dispose()
}

function Escape-AndroidString([string]$value) {
    $value = $value -replace "\\\\'", "’"
    return $value.Replace("'", "’")
}

function Set-NodeValue([System.Xml.XmlElement]$node, [string]$value) {
    $value = Escape-AndroidString $value
    if ($value -match '</?[A-Za-z][^>]*>') {
        $node.InnerXml = $value
    }
    else {
        $node.InnerText = $value
    }
}

function Set-StringValues([string]$path, [string[]]$keys, [string[]]$values) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing locale file: $path"
    }
    [xml]$doc = Get-Content -LiteralPath $path -Raw
    for ($i = 0; $i -lt $keys.Count; $i++) {
        $key = $keys[$i]
        $value = $values[$i]
        $node = @($doc.resources.string | Where-Object { $_.name -eq $key })[0]
        if ($null -eq $node) {
            throw "Missing key '$key' in $path"
        }
        Set-NodeValue $node $value
    }
    Save-Xml $doc $path
}

function Apply-Rows([string[]]$rows, [string[]]$keys, [string]$module) {
    foreach ($row in $rows) {
        if ([string]::IsNullOrWhiteSpace($row)) { continue }
        $parts = $row.Trim() -split '~'
        $locale = $parts[0].Trim()
        $values = $parts[1..($parts.Length - 1)]
        if ($values.Length -ne $keys.Length) {
            throw "$module locale $locale has $($values.Length) values, expected $($keys.Length)"
        }
        $path = Join-Path (Get-Location).Path ("{0}\src\main\res\{1}\strings.xml" -f $module, $locale)
        Set-StringValues $path $keys $values
    }
}

function Apply-KeyValueRows([string[]]$rows, [string]$module) {
    $grouped = @{}
    foreach ($row in $rows) {
        if ([string]::IsNullOrWhiteSpace($row)) { continue }
        $parts = $row.Trim() -split '\|', 3
        if ($parts.Length -ne 3) {
            throw "Invalid key-value row: $row"
        }
        $locale = $parts[0].Trim()
        $key = $parts[1].Trim()
        $value = $parts[2]
        if (-not $grouped.ContainsKey($locale)) {
            $grouped[$locale] = [ordered]@{}
        }
        $grouped[$locale][$key] = $value
    }

    foreach ($locale in $grouped.Keys) {
        $path = Join-Path (Get-Location).Path ("{0}\src\main\res\{1}\strings.xml" -f $module, $locale)
        [xml]$doc = Get-Content -LiteralPath $path -Raw
        foreach ($entry in $grouped[$locale].GetEnumerator()) {
            $node = @($doc.resources.string | Where-Object { $_.name -eq $entry.Key })[0]
            if ($null -eq $node) {
                throw "Missing key '$($entry.Key)' in $path"
            }
            Set-NodeValue $node $entry.Value
        }
        Save-Xml $doc $path
    }
}

function Replace-InFile([string]$path, [string]$from, [string]$to) {
    $content = Get-Content -LiteralPath $path -Raw
    $updated = $content.Replace($from, $to)
    if ($updated -ne $content) {
        Set-Content -LiteralPath $path -Value $updated -Encoding utf8NoBOM
    }
}

$appRows = @'
values-de~AdvancedBiometricPrompt-Test~Keine Daten~Vollbild~Fenster geschützt~Stille Authentifizierung (ohne UI)~Krypto (Datenverschlüsselung)~Geräteanmeldedaten zulassen
values-fr~Test AdvancedBiometricPrompt~Aucune donnée~Plein écran~Fenêtre sécurisée~Authentification silencieuse (sans interface)~Crypto (chiffrement des données)~Autoriser les identifiants de l'appareil
values-es~Prueba de AdvancedBiometricPrompt~Sin datos~Pantalla completa~Ventana segura~Autenticación silenciosa (sin UI)~Cripto (cifrado de datos)~Permitir credenciales del dispositivo
values-b+es+419~Prueba de AdvancedBiometricPrompt~Sin datos~Pantalla completa~Ventana segura~Autenticación silenciosa (sin UI)~Cripto (cifrado de datos)~Permitir credenciales del dispositivo
values-it~Test AdvancedBiometricPrompt~Nessun dato~Schermo intero~Finestra protetta~Autenticazione silenziosa (senza UI)~Crypto (crittografia dei dati)~Consenti credenziali del dispositivo
values-nl~AdvancedBiometricPrompt-test~Geen gegevens~Volledig scherm~Venster beveiligd~Stille authenticatie (zonder UI)~Crypto (gegevensversleuteling)~Apparaatgegevens toestaan
values-ru~Тест AdvancedBiometricPrompt~Нет данных~Полноэкранный режим~Окно защищено~Тихая аутентификация (без UI)~Крипто (шифрование данных)~Разрешить учетные данные устройства
values-uk~Тест AdvancedBiometricPrompt~Немає даних~Повноекранний режим~Вікно захищене~Тиха автентифікація (без UI)~Крипто (шифрування даних)~Дозволити облікові дані пристрою
values-zh-rCN~AdvancedBiometricPrompt 测试~无数据~全屏~窗口已保护~静默认证（无界面）~加密（数据加密）~允许设备凭据
values-zh-rTW~AdvancedBiometricPrompt 測試~無資料~全螢幕~視窗已受保護~靜默驗證（無介面）~加密（資料加密）~允許裝置憑證
values-ja~AdvancedBiometricPrompt テスト~データなし~全画面~保護されたウィンドウ~サイレント認証（UIなし）~暗号化（データ暗号化）~デバイス認証情報を許可
values-ko~AdvancedBiometricPrompt 테스트~데이터 없음~전체 화면~창 보호됨~무음 인증(UI 없음)~암호화(데이터 암호화)~기기 자격 증명 허용
values-id~Tes AdvancedBiometricPrompt~Tidak ada data~Layar penuh~Jendela diamankan~Autentikasi senyap (tanpa UI)~Kripto (enkripsi data)~Izinkan kredensial perangkat
values-vi~Bản thử AdvancedBiometricPrompt~Không có dữ liệu~Toàn màn hình~Cửa sổ được bảo vệ~Xác thực im lặng (không UI)~Mã hóa (mã hóa dữ liệu)~Cho phép thông tin xác thực của thiết bị
values-pt-rBR~Teste do AdvancedBiometricPrompt~Sem dados~Tela cheia~Janela protegida~Autenticação silenciosa (sem UI)~Cripto (criptografia de dados)~Permitir credenciais do dispositivo
values-hi~AdvancedBiometricPrompt परीक्षण~कोई डेटा नहीं~पूर्ण स्क्रीन~विंडो सुरक्षित~शांत प्रमाणीकरण (बिना UI)~क्रिप्टो (डेटा एन्क्रिप्शन)~डिवाइस क्रेडेंशियल की अनुमति दें
values-ar~اختبار AdvancedBiometricPrompt~لا توجد بيانات~ملء الشاشة~النافذة مؤمنة~مصادقة صامتة (من دون واجهة)~تشفير (تشفير البيانات)~السماح ببيانات اعتماد الجهاز
values-tr~AdvancedBiometricPrompt Testi~Veri yok~Tam ekran~Pencere korumalı~Sessiz kimlik doğrulama (UI yok)~Kripto (veri şifreleme)~Cihaz kimlik bilgilerine izin ver
'@ -split '\r?\n'

$tfFaceRows = @'
values-de~Authentifizierung abgelaufen~Durch neuen Vorgang abgebrochen~Gesichtserkennungsmodelle nicht verfügbar~Registrierungs-Tag wurde nicht angegeben~Biometrie nicht registriert~Gesicht ist unscharf, halten Sie still~Falsches Gesicht erkannt~Gesicht nicht erkannt. Versuchen Sie es erneut~Zu viele Gesichter. Es darf nur ein Gesicht vorhanden sein~Schauen Sie geradeaus~Zu dunkel~Für diese Funktion ist eine Kameraberechtigung erforderlich.~Keine Frontkamera~Kameraauflösung zu niedrig~Kamera gesperrt~Kamera deaktiviert~Face Unlock ist gesperrt. Versuchen Sie es später erneut~Face Unlock ist dauerhaft gesperrt~Dieses Gesicht ist bereits registriert~Ihr Gesicht ist nicht sichtbar. Halten Sie das Telefon auf Augenhöhe~Kamerafehler (%1$d)~Gesichtskamera ist nicht verfügbar~Richten Sie Ihr Gesicht zur Kamera aus~Richten Sie Ihr Gesicht aus, um FaceTF zu registrieren
values-fr~Délai d'authentification dépassé~Annulé par une nouvelle opération~Modèles de détection du visage indisponibles~Tag d'inscription non fourni~Biométrie non enregistrée~Le visage est flou, restez immobile~Faux visage détecté~Visage non reconnu. Réessayez~Trop de visages. Il ne doit y avoir qu'un seul visage~Regardez droit devant vous~Trop sombre~L'autorisation de la caméra est requise pour utiliser cette fonctionnalité.~Pas de caméra frontale~Résolution de la caméra trop faible~Caméra verrouillée~Caméra désactivée~Face Unlock est verrouillé. Réessayez plus tard~Face Unlock est verrouillé définitivement~Ce visage est déjà enregistré~Impossible de voir votre visage. Tenez le téléphone à hauteur des yeux~Erreur de caméra (%1$d)~La caméra faciale n'est pas disponible~Alignez votre visage avec la caméra~Alignez votre visage pour enregistrer FaceTF
values-es~La autenticación agotó el tiempo de espera~Cancelado por una nueva operación~Modelos de detección facial no disponibles~No se proporcionó la etiqueta de registro~Biometría no registrada~La cara está borrosa, quédate quieto~Se detectó una cara falsa~Cara no reconocida. Inténtalo de nuevo~Demasiadas caras. Solo debe haber una~Mira al frente~Demasiado oscuro~Se requiere permiso de cámara para usar esta función.~No hay cámara frontal~Resolución de cámara baja~Cámara bloqueada~Cámara deshabilitada~Face Unlock está bloqueado. Inténtalo más tarde~Face Unlock está bloqueado permanentemente~Esta cara ya está registrada~No se puede ver tu cara. Mantén el teléfono a la altura de los ojos~Error de cámara (%1$d)~La cámara facial no está disponible~Alinea tu cara con la cámara~Alinea tu cara para registrar FaceTF
values-b+es+419~La autenticación agotó el tiempo de espera~Cancelado por una nueva operación~Modelos de detección facial no disponibles~No se proporcionó la etiqueta de registro~Biometría no registrada~La cara está borrosa, quédate quieto~Se detectó una cara falsa~Cara no reconocida. Inténtalo de nuevo~Demasiadas caras. Solo debe haber una~Mira al frente~Demasiado oscuro~Se requiere permiso de cámara para usar esta función.~No hay cámara frontal~Resolución de cámara baja~Cámara bloqueada~Cámara deshabilitada~Face Unlock está bloqueado. Inténtalo más tarde~Face Unlock está bloqueado permanentemente~Esta cara ya está registrada~No se puede ver tu cara. Mantén el teléfono a la altura de los ojos~Error de cámara (%1$d)~La cámara facial no está disponible~Alinea tu cara con la cámara~Alinea tu cara para registrar FaceTF
values-it~Timeout dell'autenticazione~Annullato da una nuova operazione~Modelli di rilevamento del volto non disponibili~Tag di registrazione non fornito~Biometria non registrata~Il volto è sfocato, resta fermo~Volto falso rilevato~Volto non riconosciuto. Riprova~Troppi volti. Deve esserci un solo volto~Guarda dritto davanti a te~Troppo scuro~Per usare questa funzione è necessaria l'autorizzazione della fotocamera.~Nessuna fotocamera frontale~Risoluzione della fotocamera bassa~Fotocamera bloccata~Fotocamera disabilitata~Face Unlock è bloccato. Riprova più tardi~Face Unlock è bloccato in modo permanente~Questo volto è già registrato~Impossibile vedere il tuo volto. Tieni il telefono all'altezza degli occhi~Errore della fotocamera (%1$d)~La fotocamera per il volto non è disponibile~Allinea il volto con la fotocamera~Allinea il volto per registrare FaceTF
values-nl~Authenticatie time-out~Geannuleerd door een nieuwe bewerking~Modellen voor gezichtsdetectie niet beschikbaar~Registratietag niet opgegeven~Biometrie niet geregistreerd~Gezicht is wazig, houd stil~Nepgezicht gedetecteerd~Gezicht niet herkend. Probeer het opnieuw~Te veel gezichten. Er mag maar één gezicht zijn~Kijk recht vooruit~Te donker~Cameramachtiging is vereist om deze functie te gebruiken.~Geen camera aan de voorkant~Cameraresolutie te laag~Camera vergrendeld~Camera uitgeschakeld~Face Unlock is vergrendeld. Probeer het later opnieuw~Face Unlock is permanent vergrendeld~Dit gezicht is al geregistreerd~Je gezicht is niet zichtbaar. Houd je telefoon op ooghoogte~Camerafout (%1$d)~Gezichtscamera is niet beschikbaar~Lijn je gezicht uit met de camera~Lijn je gezicht uit om FaceTF te registreren
values-ru~Время аутентификации истекло~Отменено новой операцией~Модели распознавания лица недоступны~Тег регистрации не указан~Биометрия не зарегистрирована~Лицо размыто, не двигайтесь~Обнаружено поддельное лицо~Лицо не распознано. Попробуйте снова~Слишком много лиц. Должно быть только одно лицо~Смотрите прямо перед собой~Слишком темно~Для использования этой функции требуется разрешение на камеру.~Нет фронтальной камеры~Низкое разрешение камеры~Камера заблокирована~Камера отключена~Face Unlock заблокирован. Попробуйте позже~Face Unlock заблокирован навсегда~Это лицо уже зарегистрировано~Не видно ваше лицо. Держите телефон на уровне глаз~Ошибка камеры (%1$d)~Камера для распознавания лица недоступна~Совместите лицо с камерой~Совместите лицо, чтобы зарегистрировать FaceTF
values-uk~Час автентифікації вичерпано~Скасовано новою операцією~Моделі розпізнавання обличчя недоступні~Тег реєстрації не вказано~Біометрію не зареєстровано~Обличчя розмите, не рухайтеся~Виявлено підроблене обличчя~Обличчя не розпізнано. Спробуйте ще раз~Забагато облич. Має бути лише одне обличчя~Дивіться прямо перед собою~Надто темно~Для використання цієї функції потрібен дозвіл на камеру.~Немає фронтальної камери~Низька роздільна здатність камери~Камеру заблоковано~Камеру вимкнено~Face Unlock заблоковано. Спробуйте пізніше~Face Unlock заблоковано назавжди~Це обличчя вже зареєстровано~Не видно вашого обличчя. Тримайте телефон на рівні очей~Помилка камери (%1$d)~Камера для розпізнавання обличчя недоступна~Сумістіть обличчя з камерою~Сумістіть обличчя, щоб зареєструвати FaceTF
values-zh-rCN~认证超时~已被新操作取消~人脸检测模型不可用~未提供注册标签~生物识别未注册~人脸模糊，请保持不动~检测到伪造人脸~未识别人脸。请重试~人脸过多。画面中只能有一张脸~请直视前方~太暗了~使用此功能需要相机权限。~没有前置摄像头~摄像头分辨率过低~摄像头已锁定~摄像头已禁用~Face Unlock 已锁定。请稍后重试~Face Unlock 已被永久锁定~这张脸已注册~看不到你的脸。请将手机保持在眼睛高度~相机错误 (%1$d)~人脸相机不可用~将你的脸对准相机~将你的脸对准以注册 FaceTF
values-zh-rTW~驗證逾時~已被新操作取消~人臉偵測模型無法使用~未提供註冊標籤~生物辨識未註冊~人臉模糊，請保持不動~偵測到偽造人臉~無法辨識人臉。請再試一次~人臉太多。畫面中只能有一張臉~請直視前方~太暗了~使用此功能需要相機權限。~沒有前置相機~相機解析度過低~相機已鎖定~相機已停用~Face Unlock 已鎖定。請稍後再試~Face Unlock 已永久鎖定~這張臉已註冊~看不到你的臉。請將手機保持在眼睛高度~相機錯誤 (%1$d)~人臉相機無法使用~將你的臉對準相機~將你的臉對準以註冊 FaceTF
values-ja~認証がタイムアウトしました~新しい操作によってキャンセルされました~顔検出モデルを利用できません~登録タグが指定されていません~生体情報が登録されていません~顔がぼやけています。動かないでください~偽の顔が検出されました~顔を認識できませんでした。もう一度お試しください~顔が多すぎます。顔は1つだけにしてください~正面を向いてください~暗すぎます~この機能を使うにはカメラ権限が必要です。~前面カメラがありません~カメラ解像度が低すぎます~カメラがロックされています~カメラが無効です~Face Unlock はロックされています。後でもう一度お試しください~Face Unlock は永久にロックされています~この顔はすでに登録されています~顔が見えません。スマートフォンを目の高さに保ってください~カメラエラー (%1$d)~顔用カメラを利用できません~顔をカメラに合わせてください~FaceTF を登録するために顔を合わせてください
values-ko~인증 시간이 초과되었습니다~새 작업으로 인해 취소되었습니다~얼굴 감지 모델을 사용할 수 없습니다~등록 태그가 제공되지 않았습니다~생체 정보가 등록되지 않았습니다~얼굴이 흐립니다. 가만히 계세요~가짜 얼굴이 감지되었습니다~얼굴을 인식하지 못했습니다. 다시 시도하세요~얼굴이 너무 많습니다. 한 사람의 얼굴만 있어야 합니다~정면을 바라보세요~너무 어둡습니다~이 기능을 사용하려면 카메라 권한이 필요합니다.~전면 카메라가 없습니다~카메라 해상도가 낮습니다~카메라가 잠겨 있습니다~카메라가 비활성화되었습니다~Face Unlock 이 잠겼습니다. 나중에 다시 시도하세요~Face Unlock 이 영구적으로 잠겼습니다~이 얼굴은 이미 등록되어 있습니다~얼굴이 보이지 않습니다. 휴대전화를 눈높이에 맞춰 주세요~카메라 오류 (%1$d)~얼굴 카메라를 사용할 수 없습니다~얼굴을 카메라에 맞추세요~FaceTF 등록을 위해 얼굴을 맞추세요
values-id~Waktu autentikasi habis~Dibatalkan oleh operasi baru~Model deteksi wajah tidak tersedia~Tag pendaftaran tidak diberikan~Biometrik belum terdaftar~Wajah buram, jangan bergerak~Wajah palsu terdeteksi~Wajah tidak dikenali. Coba lagi~Terlalu banyak wajah. Hanya boleh ada satu wajah~Lihat lurus ke depan~Terlalu gelap~Izin kamera diperlukan untuk menggunakan fitur ini.~Tidak ada kamera depan~Resolusi kamera rendah~Kamera terkunci~Kamera dinonaktifkan~Face Unlock terkunci. Coba lagi nanti~Face Unlock terkunci permanen~Wajah ini sudah terdaftar~Wajah Anda tidak terlihat. Pegang ponsel setinggi mata~Kesalahan kamera (%1$d)~Kamera wajah tidak tersedia~Sejajarkan wajah Anda dengan kamera~Sejajarkan wajah Anda untuk mendaftarkan FaceTF
values-vi~Đã hết thời gian xác thực~Đã bị hủy bởi thao tác mới~Không có mô hình phát hiện khuôn mặt~Chưa cung cấp thẻ đăng ký~Sinh trắc học chưa được đăng ký~Khuôn mặt bị mờ, hãy đứng yên~Đã phát hiện khuôn mặt giả~Không nhận ra khuôn mặt. Hãy thử lại~Có quá nhiều khuôn mặt. Chỉ được có một khuôn mặt~Nhìn thẳng về phía trước~Quá tối~Cần quyền camera để dùng tính năng này.~Không có camera trước~Độ phân giải camera thấp~Camera bị khóa~Camera bị vô hiệu hóa~Face Unlock đã bị khóa. Hãy thử lại sau~Face Unlock đã bị khóa vĩnh viễn~Khuôn mặt này đã được đăng ký~Không thấy khuôn mặt của bạn. Hãy giữ điện thoại ngang tầm mắt~Lỗi camera (%1$d)~Không thể dùng camera khuôn mặt~Căn khuôn mặt của bạn với camera~Căn khuôn mặt để đăng ký FaceTF
values-pt-rBR~Tempo limite de autenticação esgotado~Cancelado por uma nova operação~Modelos de detecção facial indisponíveis~Tag de registro não fornecida~Biometria não registrada~O rosto está borrado, fique parado~Rosto falso detectado~Rosto não reconhecido. Tente novamente~Rostos demais. Deve haver apenas um rosto~Olhe para frente~Escuro demais~A permissão da câmera é necessária para usar este recurso.~Sem câmera frontal~Resolução da câmera baixa~Câmera bloqueada~Câmera desativada~O Face Unlock está bloqueado. Tente novamente mais tarde~O Face Unlock está bloqueado permanentemente~Este rosto já está registrado~Não foi possível ver seu rosto. Mantenha o telefone na altura dos olhos~Erro de câmera (%1$d)~A câmera facial não está disponível~Alinhe seu rosto com a câmera~Alinhe seu rosto para cadastrar o FaceTF
values-hi~प्रमाणीकरण का समय समाप्त हो गया~नए ऑपरेशन द्वारा रद्द किया गया~चेहरा पहचान मॉडल उपलब्ध नहीं हैं~नामांकन टैग प्रदान नहीं किया गया~बायोमेट्रिक पंजीकृत नहीं है~चेहरा धुंधला है, स्थिर रहें~नकली चेहरा मिला~चेहरा पहचाना नहीं गया। फिर से कोशिश करें~बहुत सारे चेहरे हैं। केवल एक चेहरा होना चाहिए~सीधे सामने देखें~बहुत अंधेरा है~इस सुविधा का उपयोग करने के लिए कैमरा अनुमति आवश्यक है।~फ्रंट कैमरा नहीं है~कैमरा रिज़ॉल्यूशन कम है~कैमरा लॉक है~कैमरा अक्षम है~Face Unlock लॉक है। बाद में फिर से प्रयास करें~Face Unlock स्थायी रूप से लॉक है~यह चेहरा पहले से पंजीकृत है~आपका चेहरा दिखाई नहीं दे रहा। फ़ोन को आंखों की ऊँचाई पर रखें~कैमरा त्रुटि (%1$d)~फेस कैमरा उपलब्ध नहीं है~अपने चेहरे को कैमरे के साथ संरेखित करें~FaceTF नामांकन के लिए चेहरे को संरेखित करें
values-ar~انتهت مهلة المصادقة~أُلغي بسبب عملية جديدة~نماذج اكتشاف الوجه غير متاحة~لم يتم توفير وسم التسجيل~القياسات الحيوية غير مسجلة~الوجه ضبابي، ابق ثابتًا~تم اكتشاف وجه مزيف~لم يتم التعرف على الوجه. حاول مرة أخرى~هناك وجوه كثيرة جدًا. يجب أن يكون هناك وجه واحد فقط~انظر مباشرة إلى الأمام~مظلم جدًا~يلزم إذن الكاميرا لاستخدام هذه الميزة.~لا توجد كاميرا أمامية~دقة الكاميرا منخفضة~الكاميرا مقفلة~الكاميرا معطلة~Face Unlock مقفل. حاول مرة أخرى لاحقًا~Face Unlock مقفل بشكل دائم~هذا الوجه مسجل بالفعل~لا يمكن رؤية وجهك. أمسك الهاتف على مستوى العين~خطأ في الكاميرا (%1$d)~كاميرا الوجه غير متاحة~حاذِ وجهك مع الكاميرا~حاذِ وجهك لتسجيل FaceTF
values-tr~Kimlik doğrulama zaman aşımına uğradı~Yeni işlem nedeniyle iptal edildi~Yüz algılama modelleri kullanılamıyor~Kayıt etiketi sağlanmadı~Biyometri kayıtlı değil~Yüz bulanık, kıpırdamayın~Sahte yüz algılandı~Yüz tanınmadı. Tekrar deneyin~Çok fazla yüz var. Yalnızca bir yüz olmalı~Dümdüz karşıya bakın~Çok karanlık~Bu özelliği kullanmak için kamera izni gerekir.~Ön kamera yok~Kamera çözünürlüğü düşük~Kamera kilitlendi~Kamera devre dışı bırakıldı~Face Unlock kilitlendi. Daha sonra tekrar deneyin~Face Unlock kalıcı olarak kilitlendi~Bu yüz zaten kayıtlı~Yüzünüz görünmüyor. Telefonu göz hizasında tutun~Kamera hatası (%1$d)~Yüz kamerası kullanılamıyor~Yüzünüzü kamerayla hizalayın~FaceTF kaydı için yüzünüzü hizalayın
'@ -split '\r?\n'

$zkFingerRows = @'
values-de~Zeitüberschreitung bei der Fingerabdruckauthentifizierung~Durch neuen Vorgang abgebrochen~ZK-Fingerabdruckleser nicht verbunden~USB-Berechtigung für ZK-Fingerabdruckleser verweigert~ZK-Fingerabdruckleser nicht verfügbar~Fingerabdruck nicht registriert~Dieser Fingerabdruck ist bereits registriert~Verwenden Sie für alle Registrierungsscans denselben Finger~Legen Sie den Finger erneut auf~Erforderliche ZK-Leserscans: %1$d~Akzeptierte Scans: %1$d/%2$d. Heben Sie denselben Finger an und legen Sie ihn erneut auf~Fingerabdruckregistrierung wird abgeschlossen~Fingerabdruckvorlage konnte nicht verarbeitet werden~ZK-Fingerabdruckleser ist gesperrt. Versuchen Sie es später erneut~ZK-Fingerabdruckleser ist dauerhaft gesperrt~Legen Sie Ihren Finger auf den ZK-Leser~Legen Sie Ihren Finger zum Registrieren auf den ZK-Leser
values-fr~Le délai d'authentification par empreinte digitale est dépassé~Annulé par une nouvelle opération~Le lecteur d'empreintes ZK n'est pas connecté~Autorisation USB refusée pour le lecteur d'empreintes ZK~Le lecteur d'empreintes ZK n'est pas disponible~Empreinte non enregistrée~Cette empreinte est déjà enregistrée~Utilisez le même doigt pour tous les scans d'inscription~Scannez à nouveau votre doigt~Scans ZK requis : %1$d~Scans acceptés : %1$d/%2$d. Soulevez puis reposez le même doigt~Finalisation de l'inscription de l'empreinte~Impossible de traiter le modèle d'empreinte~Le lecteur d'empreintes ZK est verrouillé. Réessayez plus tard~Le lecteur d'empreintes ZK est verrouillé de façon permanente~Placez votre doigt sur le lecteur ZK~Placez votre doigt sur le lecteur ZK pour l'inscription
values-es~Se agotó el tiempo de autenticación de huella digital~Cancelado por una nueva operación~El lector de huellas ZK no está conectado~Permiso USB denegado para el lector de huellas ZK~El lector de huellas ZK no está disponible~Huella no registrada~Esta huella ya está registrada~Usa el mismo dedo para todos los escaneos de registro~Vuelve a escanear tu dedo~Escaneos requeridos del lector ZK: %1$d~Escaneos aceptados: %1$d/%2$d. Levanta y vuelve a colocar el mismo dedo~Finalizando el registro de la huella~No se pudo procesar la plantilla de huella~El lector de huellas ZK está bloqueado. Inténtalo más tarde~El lector de huellas ZK está bloqueado permanentemente~Coloca tu dedo en el lector ZK~Coloca tu dedo en el lector ZK para registrarlo
values-b+es+419~Se agotó el tiempo de autenticación de huella digital~Cancelado por una nueva operación~El lector de huellas ZK no está conectado~Permiso USB denegado para el lector de huellas ZK~El lector de huellas ZK no está disponible~Huella no registrada~Esta huella ya está registrada~Usa el mismo dedo para todos los escaneos de registro~Vuelve a escanear tu dedo~Escaneos requeridos del lector ZK: %1$d~Escaneos aceptados: %1$d/%2$d. Levanta y vuelve a colocar el mismo dedo~Finalizando el registro de la huella~No se pudo procesar la plantilla de huella~El lector de huellas ZK está bloqueado. Inténtalo más tarde~El lector de huellas ZK está bloqueado permanentemente~Coloca tu dedo en el lector ZK~Coloca tu dedo en el lector ZK para registrarlo
values-it~Timeout dell'autenticazione con impronta digitale~Annullato da una nuova operazione~Lettore di impronte ZK non collegato~Autorizzazione USB negata per il lettore di impronte ZK~Lettore di impronte ZK non disponibile~Impronta non registrata~Questa impronta è già registrata~Usa lo stesso dito per tutte le scansioni di registrazione~Scansiona di nuovo il dito~Scansioni richieste del lettore ZK: %1$d~Scansioni accettate: %1$d/%2$d. Solleva e riappoggia lo stesso dito~Finalizzazione della registrazione dell'impronta~Impossibile elaborare il modello di impronta~Il lettore di impronte ZK è bloccato. Riprova più tardi~Il lettore di impronte ZK è bloccato in modo permanente~Posiziona il dito sul lettore ZK~Posiziona il dito sul lettore ZK per registrarlo
values-nl~Time-out bij vingerafdrukauthenticatie~Geannuleerd door een nieuwe bewerking~ZK-vingerafdruklezer niet verbonden~USB-toestemming geweigerd voor ZK-vingerafdruklezer~ZK-vingerafdruklezer niet beschikbaar~Vingerafdruk niet geregistreerd~Deze vingerafdruk is al geregistreerd~Gebruik dezelfde vinger voor alle registratiescans~Scan uw vinger opnieuw~Vereiste ZK-lezerscans: %1$d~Geaccepteerde scans: %1$d/%2$d. Til dezelfde vinger op en plaats deze opnieuw~Registratie van vingerafdruk wordt afgerond~Vingerafdruksjabloon kon niet worden verwerkt~ZK-vingerafdruklezer is vergrendeld. Probeer het later opnieuw~ZK-vingerafdruklezer is permanent vergrendeld~Plaats uw vinger op de ZK-lezer~Plaats uw vinger op de ZK-lezer om te registreren
values-ru~Время аутентификации по отпечатку истекло~Отменено новой операцией~Сканер отпечатков ZK не подключен~Разрешение USB для сканера отпечатков ZK отклонено~Сканер отпечатков ZK недоступен~Отпечаток не зарегистрирован~Этот отпечаток уже зарегистрирован~Используйте один и тот же палец для всех сканирований при регистрации~Снова приложите палец~Требуемое число сканов ZK: %1$d~Принято сканов: %1$d/%2$d. Поднимите и снова приложите тот же палец~Завершение регистрации отпечатка~Не удалось обработать шаблон отпечатка~Сканер отпечатков ZK заблокирован. Попробуйте позже~Сканер отпечатков ZK заблокирован навсегда~Приложите палец к считывателю ZK~Приложите палец к считывателю ZK для регистрации
values-uk~Час автентифікації за відбитком вичерпано~Скасовано новою операцією~Сканер відбитків ZK не підключено~Дозвіл USB для сканера відбитків ZK відхилено~Сканер відбитків ZK недоступний~Відбиток не зареєстровано~Цей відбиток уже зареєстровано~Використовуйте той самий палець для всіх сканувань під час реєстрації~Прикладіть палець ще раз~Потрібна кількість сканувань ZK: %1$d~Прийнято сканувань: %1$d/%2$d. Підніміть і знову прикладіть той самий палець~Завершення реєстрації відбитка~Не вдалося обробити шаблон відбитка~Сканер відбитків ZK заблоковано. Спробуйте пізніше~Сканер відбитків ZK заблоковано назавжди~Прикладіть палець до зчитувача ZK~Прикладіть палець до зчитувача ZK для реєстрації
values-zh-rCN~指纹认证超时~已被新操作取消~ZK 指纹读取器未连接~ZK 指纹读取器的 USB 权限被拒绝~ZK 指纹读取器不可用~指纹未注册~该指纹已注册~注册时请始终使用同一根手指~请再次扫描手指~所需 ZK 读取器扫描次数：%1$d~已接受扫描：%1$d/%2$d。抬起并再次放下同一根手指~正在完成指纹注册~无法处理指纹模板~ZK 指纹读取器已锁定。请稍后重试~ZK 指纹读取器已被永久锁定~请将手指放在 ZK 读取器上~请将手指放在 ZK 读取器上进行注册
values-zh-rTW~指紋驗證逾時~已被新操作取消~ZK 指紋讀取器未連接~ZK 指紋讀取器的 USB 權限遭拒~ZK 指紋讀取器無法使用~指紋未註冊~此指紋已註冊~註冊時請始終使用同一根手指~請再次掃描手指~所需 ZK 讀取器掃描次數：%1$d~已接受掃描：%1$d/%2$d。抬起並再次放下同一根手指~正在完成指紋註冊~無法處理指紋範本~ZK 指紋讀取器已鎖定。請稍後再試~ZK 指紋讀取器已永久鎖定~請將手指放在 ZK 讀取器上~請將手指放在 ZK 讀取器上進行註冊
values-ja~指紋認証がタイムアウトしました~新しい操作によってキャンセルされました~ZK 指紋リーダーが接続されていません~ZK 指紋リーダーの USB 権限が拒否されました~ZK 指紋リーダーを利用できません~指紋が登録されていません~この指紋はすでに登録されています~登録中のすべてのスキャンで同じ指を使用してください~もう一度指をスキャンしてください~必要な ZK リーダースキャン数: %1$d~受け付けたスキャン: %1$d/%2$d。同じ指を離して再度置いてください~指紋登録を完了しています~指紋テンプレートを処理できませんでした~ZK 指紋リーダーはロックされています。後でもう一度お試しください~ZK 指紋リーダーは永久にロックされています~ZK リーダーに指を置いてください~登録するには ZK リーダーに指を置いてください
values-ko~지문 인증 시간이 초과되었습니다~새 작업으로 인해 취소되었습니다~ZK 지문 리더가 연결되지 않았습니다~ZK 지문 리더에 대한 USB 권한이 거부되었습니다~ZK 지문 리더를 사용할 수 없습니다~지문이 등록되지 않았습니다~이 지문은 이미 등록되어 있습니다~등록 스캔 전체에서 같은 손가락을 사용하세요~손가락을 다시 스캔하세요~필요한 ZK 리더 스캔 수: %1$d~승인된 스캔: %1$d/%2$d. 같은 손가락을 떼었다가 다시 올리세요~지문 등록을 마무리하는 중입니다~지문 템플릿을 처리할 수 없습니다~ZK 지문 리더가 잠겼습니다. 나중에 다시 시도하세요~ZK 지문 리더가 영구적으로 잠겼습니다~손가락을 ZK 리더에 올리세요~등록하려면 손가락을 ZK 리더에 올리세요
values-id~Waktu autentikasi sidik jari habis~Dibatalkan oleh operasi baru~Pembaca sidik jari ZK tidak terhubung~Izin USB ditolak untuk pembaca sidik jari ZK~Pembaca sidik jari ZK tidak tersedia~Sidik jari belum terdaftar~Sidik jari ini sudah terdaftar~Gunakan jari yang sama untuk semua pemindaian pendaftaran~Pindai jari Anda lagi~Jumlah pemindaian pembaca ZK yang diperlukan: %1$d~Pemindaian diterima: %1$d/%2$d. Angkat lalu letakkan kembali jari yang sama~Menyelesaikan pendaftaran sidik jari~Tidak dapat memproses template sidik jari~Pembaca sidik jari ZK terkunci. Coba lagi nanti~Pembaca sidik jari ZK terkunci permanen~Letakkan jari Anda pada pembaca ZK~Letakkan jari Anda pada pembaca ZK untuk mendaftar
values-vi~Đã hết thời gian xác thực vân tay~Đã bị hủy bởi thao tác mới~Đầu đọc vân tay ZK chưa được kết nối~Quyền USB cho đầu đọc vân tay ZK bị từ chối~Đầu đọc vân tay ZK không khả dụng~Vân tay chưa được đăng ký~Vân tay này đã được đăng ký~Hãy dùng cùng một ngón tay cho mọi lần quét đăng ký~Hãy quét lại ngón tay của bạn~Số lần quét đầu đọc ZK cần có: %1$d~Đã chấp nhận: %1$d/%2$d. Nhấc lên rồi đặt lại cùng một ngón tay~Đang hoàn tất đăng ký vân tay~Không thể xử lý mẫu vân tay~Đầu đọc vân tay ZK đã bị khóa. Hãy thử lại sau~Đầu đọc vân tay ZK đã bị khóa vĩnh viễn~Đặt ngón tay của bạn lên đầu đọc ZK~Đặt ngón tay lên đầu đọc ZK để đăng ký
values-pt-rBR~Tempo esgotado na autenticação por impressão digital~Cancelado por uma nova operação~Leitor de impressão digital ZK não conectado~Permissão USB negada para o leitor de impressão digital ZK~Leitor de impressão digital ZK indisponível~Impressão digital não cadastrada~Esta impressão digital já está cadastrada~Use o mesmo dedo em todas as leituras do cadastro~Leia seu dedo novamente~Leituras necessárias do leitor ZK: %1$d~Leituras aceitas: %1$d/%2$d. Levante e coloque o mesmo dedo novamente~Finalizando o cadastro da impressão digital~Não foi possível processar o modelo de impressão digital~O leitor de impressão digital ZK está bloqueado. Tente novamente mais tarde~O leitor de impressão digital ZK está bloqueado permanentemente~Coloque o dedo no leitor ZK~Coloque o dedo no leitor ZK para cadastrar
values-hi~फ़िंगरप्रिंट प्रमाणीकरण का समय समाप्त हो गया~नए ऑपरेशन द्वारा रद्द किया गया~ZK फ़िंगरप्रिंट रीडर कनेक्ट नहीं है~ZK फ़िंगरप्रिंट रीडर के लिए USB अनुमति अस्वीकृत~ZK फ़िंगरप्रिंट रीडर उपलब्ध नहीं है~फ़िंगरप्रिंट पंजीकृत नहीं है~यह फ़िंगरप्रिंट पहले से पंजीकृत है~नामांकन के सभी स्कैन के लिए वही उंगली उपयोग करें~अपनी उंगली फिर से स्कैन करें~आवश्यक ZK रीडर स्कैन: %1$d~स्वीकृत स्कैन: %1$d/%2$d। उसी उंगली को उठाकर फिर से रखें~फ़िंगरप्रिंट नामांकन पूरा किया जा रहा है~फ़िंगरप्रिंट टेम्पलेट संसाधित नहीं किया जा सका~ZK फ़िंगरप्रिंट रीडर लॉक है। बाद में फिर से प्रयास करें~ZK फ़िंगरप्रिंट रीडर स्थायी रूप से लॉक है~अपनी उंगली ZK रीडर पर रखें~पंजीकरण के लिए अपनी उंगली ZK रीडर पर रखें
values-ar~انتهت مهلة مصادقة بصمة الإصبع~أُلغي بسبب عملية جديدة~قارئ بصمات ZK غير متصل~تم رفض إذن USB لقارئ بصمات ZK~قارئ بصمات ZK غير متاح~بصمة الإصبع غير مسجلة~بصمة الإصبع هذه مسجلة بالفعل~استخدم الإصبع نفسه في جميع عمليات المسح أثناء التسجيل~امسح إصبعك مرة أخرى~عدد عمليات المسح المطلوبة لقارئ ZK: %1$d~عمليات المسح المقبولة: %1$d/%2$d. ارفع الإصبع نفسه ثم ضعه مرة أخرى~يتم إنهاء تسجيل بصمة الإصبع~تعذر معالجة قالب بصمة الإصبع~قارئ بصمات ZK مقفل. حاول مرة أخرى لاحقًا~قارئ بصمات ZK مقفل بشكل دائم~ضع إصبعك على قارئ ZK~ضع إصبعك على قارئ ZK للتسجيل
values-tr~Parmak izi kimlik doğrulama zaman aşımına uğradı~Yeni işlem nedeniyle iptal edildi~ZK parmak izi okuyucu bağlı değil~ZK parmak izi okuyucu için USB izni reddedildi~ZK parmak izi okuyucu kullanılamıyor~Parmak izi kayıtlı değil~Bu parmak izi zaten kayıtlı~Kayıt taramalarının tümünde aynı parmağı kullanın~Parmağınızı tekrar tarayın~Gerekli ZK okuyucu taramaları: %1$d~Kabul edilen taramalar: %1$d/%2$d. Aynı parmağı kaldırıp tekrar yerleştirin~Parmak izi kaydı tamamlanıyor~Parmak izi şablonu işlenemedi~ZK parmak izi okuyucu kilitlendi. Daha sonra tekrar deneyin~ZK parmak izi okuyucu kalıcı olarak kilitlendi~Parmağınızı ZK okuyucuya yerleştirin~Kaydolmak için parmağınızı ZK okuyucuya yerleştirin
'@ -split '\r?\n'

$behaviorRows = @'
values-de~Zeitüberschreitung bei der Verhaltensauthentifizierung~Verhaltensauthentifizierung ist nicht verfügbar~Verhaltensprobe fehlt oder ist unvollständig~Verhaltensbiometrie ist nicht registriert~Verhaltensvorlage registriert: %1$s~Verhaltensbiometrie akzeptiert~Verhaltensbiometrie stimmte nicht überein~Verhaltensauthentifizierung ist vorübergehend gesperrt~Verhaltensauthentifizierung ist dauerhaft gesperrt~Verhaltensprobe ist gültig~Der Tipptext ist zu kurz~Die Eingabeprobe ist zu kurz~Die Eingabeprobe entspricht nicht der Phrase. Geben Sie die Phrase manuell ein.~Die Eingabeprobe ist zu schnell. Tippen Sie die Phrase natürlich.~Die Eingabeprobe hat zu wenig zeitliche Variation~Die Signaturprobe ist zu kurz~Der Signaturpfad ist zu kurz~Die Signaturform ist zu klein~Die Signaturform ist zu einfach. Zeichnen Sie Ihre normale Unterschrift.~Die Signaturprobe hat zu viele wiederholte Punkte. Zeichnen Sie die Unterschrift natürlich.~Verhaltenserfassung~Verhaltensprobe erstellen~Verhalten bestätigen~Tippen~Signatur~Kombiniert~Phrase eingeben~Signatur zeichnen~Registrieren~Prüfen~Tippen Sie mindestens 5 Zeichen in natürlichem Rhythmus.~Zeichnen Sie eine klare Signatur mit ausreichender Länge und Form.~Geben Sie eine kurze Phrase ein und zeichnen Sie eine klare Signatur.~Berührungseingabe wird verdeckt. Entfernen Sie Bildschirm-Overlays und versuchen Sie es erneut.~Verhaltensphrase ist zu lang. Verwenden Sie eine kürzere Phrase.~Geben Sie mindestens 5 Zeichen ein, bevor Sie fortfahren.~Zeichnen Sie eine längere Signatur, bevor Sie fortfahren.~Leerzeichen~Verhalten wird geprüft
values-fr~Le délai d'authentification comportementale est dépassé~L'authentification comportementale n'est pas disponible~L'échantillon comportemental est manquant ou incomplet~La biométrie comportementale n'est pas enregistrée~Modèle comportemental enregistré : %1$s~Biométrie comportementale acceptée~La biométrie comportementale ne correspond pas~L'authentification comportementale est temporairement verrouillée~L'authentification comportementale est verrouillée de façon permanente~L'échantillon comportemental est valide~La phrase saisie est trop courte~L'échantillon de saisie est trop court~L'échantillon de saisie ne correspond pas à la phrase. Saisissez la phrase manuellement.~L'échantillon de saisie est trop rapide. Saisissez la phrase naturellement.~L'échantillon de saisie manque de variation temporelle~L'échantillon de signature est trop court~Le tracé de la signature est trop court~La forme de la signature est trop petite~La forme de la signature est trop simple. Dessinez votre signature habituelle.~L'échantillon de signature contient trop de points répétés. Dessinez la signature naturellement.~Capture comportementale~Créer un échantillon comportemental~Confirmez le comportement~Saisie~Signature~Combiné~Saisir la phrase~Dessiner la signature~Enregistrer~Vérifier~Saisissez au moins 5 caractères avec un rythme naturel.~Dessinez une signature nette avec une longueur et une forme suffisantes.~Saisissez une phrase courte et dessinez une signature nette.~La saisie tactile est masquée. Retirez les superpositions d'écran et réessayez.~La phrase comportementale est trop longue. Utilisez une phrase plus courte.~Saisissez au moins 5 caractères avant de continuer.~Dessinez une signature plus longue avant de continuer.~espace~Vérification du comportement
values-es~Se agotó el tiempo de autenticación de comportamiento~La autenticación de comportamiento no está disponible~La muestra de comportamiento falta o está incompleta~No hay biometría de comportamiento registrada~Plantilla de comportamiento registrada: %1$s~Biometría de comportamiento aceptada~La biometría de comportamiento no coincidió~La autenticación de comportamiento está bloqueada temporalmente~La autenticación de comportamiento está bloqueada permanentemente~La muestra de comportamiento es válida~La frase de escritura es demasiado corta~La muestra de escritura es demasiado corta~La muestra de escritura no coincide con la frase. Escribe la frase manualmente.~La muestra de escritura es demasiado rápida. Escribe la frase de forma natural.~La muestra de escritura tiene muy poca variación temporal~La muestra de firma es demasiado corta~La trayectoria de la firma es demasiado corta~La forma de la firma es demasiado pequeña~La forma de la firma es demasiado simple. Dibuja tu firma habitual.~La muestra de firma tiene demasiados puntos repetidos. Dibuja la firma de forma natural.~Captura de comportamiento~Crear muestra de comportamiento~Confirmar comportamiento~Escritura~Firma~Combinado~Escribe la frase~Dibuja la firma~Registrar~Verificar~Escribe al menos 5 caracteres con un ritmo natural.~Dibuja una firma clara con suficiente longitud y forma.~Escribe una frase corta y dibuja una firma clara.~La entrada táctil está oculta. Quita las superposiciones de pantalla e inténtalo de nuevo.~La frase de comportamiento es demasiado larga. Usa una frase más corta.~Escribe al menos 5 caracteres antes de continuar.~Dibuja una firma más larga antes de continuar.~espacio~Comprobando el comportamiento
values-b+es+419~Se agotó el tiempo de autenticación de comportamiento~La autenticación de comportamiento no está disponible~La muestra de comportamiento falta o está incompleta~No hay biometría de comportamiento registrada~Plantilla de comportamiento registrada: %1$s~Biometría de comportamiento aceptada~La biometría de comportamiento no coincidió~La autenticación de comportamiento está bloqueada temporalmente~La autenticación de comportamiento está bloqueada permanentemente~La muestra de comportamiento es válida~La frase de escritura es demasiado corta~La muestra de escritura es demasiado corta~La muestra de escritura no coincide con la frase. Escribe la frase manualmente.~La muestra de escritura es demasiado rápida. Escribe la frase de forma natural.~La muestra de escritura tiene muy poca variación temporal~La muestra de firma es demasiado corta~La trayectoria de la firma es demasiado corta~La forma de la firma es demasiado pequeña~La forma de la firma es demasiado simple. Dibuja tu firma habitual.~La muestra de firma tiene demasiados puntos repetidos. Dibuja la firma de forma natural.~Captura de comportamiento~Crear muestra de comportamiento~Confirmar comportamiento~Escritura~Firma~Combinado~Escribe la frase~Dibuja la firma~Registrar~Verificar~Escribe al menos 5 caracteres con un ritmo natural.~Dibuja una firma clara con suficiente longitud y forma.~Escribe una frase corta y dibuja una firma clara.~La entrada táctil está oculta. Quita las superposiciones de pantalla e inténtalo de nuevo.~La frase de comportamiento es demasiado larga. Usa una frase más corta.~Escribe al menos 5 caracteres antes de continuar.~Dibuja una firma más larga antes de continuar.~espacio~Comprobando el comportamiento
values-it~Timeout dell'autenticazione comportamentale~L'autenticazione comportamentale non è disponibile~Il campione comportamentale manca o è incompleto~La biometria comportamentale non è registrata~Modello comportamentale registrato: %1$s~Biometria comportamentale accettata~La biometria comportamentale non corrisponde~L'autenticazione comportamentale è temporaneamente bloccata~L'autenticazione comportamentale è bloccata in modo permanente~Il campione comportamentale è valido~La frase digitata è troppo corta~Il campione di digitazione è troppo corto~Il campione di digitazione non corrisponde alla frase. Digita la frase manualmente.~Il campione di digitazione è troppo veloce. Digita la frase in modo naturale.~Il campione di digitazione ha troppo poca variazione temporale~Il campione di firma è troppo corto~Il tracciato della firma è troppo corto~La forma della firma è troppo piccola~La forma della firma è troppo semplice. Disegna la tua firma abituale.~Il campione di firma ha troppi punti ripetuti. Disegna la firma in modo naturale.~Acquisizione comportamentale~Crea campione comportamentale~Conferma comportamento~Digitazione~Firma~Combinato~Digita la frase~Disegna la firma~Registra~Verifica~Digita almeno 5 caratteri con un ritmo naturale.~Disegna una firma chiara con lunghezza e forma sufficienti.~Digita una frase breve e disegna una firma chiara.~L'input touch è oscurato. Rimuovi le sovrapposizioni dello schermo e riprova.~La frase comportamentale è troppo lunga. Usa una frase più corta.~Digita almeno 5 caratteri prima di continuare.~Disegna una firma più lunga prima di continuare.~spazio~Controllo del comportamento
values-nl~Time-out bij gedragsauthenticatie~Gedragsauthenticatie is niet beschikbaar~Gedragsmonster ontbreekt of is onvolledig~Gedragsbiometrie is niet geregistreerd~Gedragssjabloon geregistreerd: %1$s~Gedragsbiometrie geaccepteerd~Gedragsbiometrie kwam niet overeen~Gedragsauthenticatie is tijdelijk vergrendeld~Gedragsauthenticatie is permanent vergrendeld~Gedragsmonster is geldig~De typzin is te kort~Het typmonster is te kort~Het typmonster komt niet overeen met de zin. Typ de zin handmatig.~Het typmonster is te snel. Typ de zin op een natuurlijke manier.~Het typmonster heeft te weinig tijdsvariatie~Het handtekeningmonster is te kort~Het handtekeningpad is te kort~De vorm van de handtekening is te klein~De vorm van de handtekening is te eenvoudig. Teken je normale handtekening.~Het handtekeningmonster heeft te veel herhaalde punten. Teken de handtekening op een natuurlijke manier.~Gedrag vastleggen~Gedragsmonster maken~Gedrag bevestigen~Typen~Handtekening~Gecombineerd~Typ de zin~Teken de handtekening~Registreren~Verifiëren~Typ minstens 5 tekens in een natuurlijk ritme.~Teken een duidelijke handtekening met voldoende lengte en vorm.~Typ een korte zin en teken een duidelijke handtekening.~Aanraakinvoer wordt geblokkeerd. Verwijder schermoverlays en probeer het opnieuw.~De gedragszin is te lang. Gebruik een kortere zin.~Typ minstens 5 tekens voordat u doorgaat.~Teken een langere handtekening voordat u doorgaat.~spatie~Gedrag controleren
values-ru~Время аутентификации по поведению истекло~Аутентификация по поведению недоступна~Поведенческий образец отсутствует или неполон~Биометрия поведения не зарегистрирована~Шаблон поведения зарегистрирован: %1$s~Биометрия поведения подтверждена~Биометрия поведения не совпала~Аутентификация по поведению временно заблокирована~Аутентификация по поведению заблокирована навсегда~Поведенческий образец корректен~Фраза для ввода слишком короткая~Образец ввода слишком короткий~Образец ввода не соответствует фразе. Введите фразу вручную.~Образец ввода слишком быстрый. Вводите фразу естественно.~В образце ввода слишком мало временной вариативности~Образец подписи слишком короткий~Траектория подписи слишком короткая~Форма подписи слишком мала~Форма подписи слишком простая. Нарисуйте свою обычную подпись.~В образце подписи слишком много повторяющихся точек. Нарисуйте подпись естественно.~Захват поведения~Создать образец поведения~Подтвердите поведение~Ввод~Подпись~Комбинированный~Введите фразу~Нарисуйте подпись~Зарегистрировать~Проверить~Введите не менее 5 символов в естественном ритме.~Нарисуйте четкую подпись достаточной длины и формы.~Введите короткую фразу и нарисуйте четкую подпись.~Сенсорный ввод перекрыт. Уберите наложения на экран и попробуйте снова.~Фраза поведения слишком длинная. Используйте более короткую фразу.~Введите не менее 5 символов, прежде чем продолжить.~Нарисуйте более длинную подпись, прежде чем продолжить.~пробел~Проверка поведения
values-uk~Час автентифікації поведінки вичерпано~Автентифікація поведінки недоступна~Зразок поведінки відсутній або неповний~Біометрію поведінки не зареєстровано~Шаблон поведінки зареєстровано: %1$s~Біометрію поведінки підтверджено~Біометрія поведінки не збіглася~Автентифікацію поведінки тимчасово заблоковано~Автентифікацію поведінки заблоковано назавжди~Зразок поведінки коректний~Фраза для введення надто коротка~Зразок введення надто короткий~Зразок введення не відповідає фразі. Введіть фразу вручну.~Зразок введення надто швидкий. Вводьте фразу природно.~У зразку введення замало часової варіації~Зразок підпису надто короткий~Траєкторія підпису надто коротка~Форма підпису надто мала~Форма підпису надто проста. Намалюйте свій звичайний підпис.~У зразку підпису забагато повторюваних точок. Намалюйте підпис природно.~Захоплення поведінки~Створіть зразок поведінки~Підтвердьте поведінку~Введення~Підпис~Комбінований~Введіть фразу~Намалюйте підпис~Зареєструвати~Перевірити~Введіть щонайменше 5 символів у природному ритмі.~Намалюйте чіткий підпис достатньої довжини та форми.~Введіть коротку фразу та намалюйте чіткий підпис.~Сенсорний ввід перекрито. Приберіть накладки з екрана й спробуйте ще раз.~Фраза поведінки надто довга. Використайте коротшу фразу.~Введіть щонайменше 5 символів, перш ніж продовжити.~Намалюйте довший підпис, перш ніж продовжити.~пробіл~Перевірка поведінки
values-zh-rCN~行为认证超时~行为认证不可用~行为样本缺失或不完整~行为生物识别未注册~行为模板已注册：%1$s~行为生物识别已接受~行为生物识别不匹配~行为认证已被临时锁定~行为认证已被永久锁定~行为样本有效~输入短语太短~输入样本太短~输入样本与短语不匹配。请手动输入该短语。~输入样本过快。请自然地输入该短语。~输入样本的时间变化太少~签名样本太短~签名轨迹太短~签名形状太小~签名形状过于简单。请绘制你的常用签名。~签名样本中重复点过多。请自然地绘制签名。~行为采集~创建行为样本~确认行为~输入~签名~组合~输入短语~绘制签名~注册~验证~请以自然节奏输入至少 5 个字符。~请绘制一个长度和形状都足够清晰的签名。~请输入一段短语并绘制清晰的签名。~触摸输入被遮挡。请移除屏幕覆盖层后重试。~行为短语过长。请使用更短的短语。~继续之前请至少输入 5 个字符。~继续之前请绘制更长的签名。~空格~正在检查行为
values-zh-rTW~行為驗證逾時~行為驗證無法使用~行為樣本缺失或不完整~行為生物辨識未註冊~行為範本已註冊：%1$s~行為生物辨識已接受~行為生物辨識不相符~行為驗證已暫時鎖定~行為驗證已永久鎖定~行為樣本有效~輸入短語太短~輸入樣本太短~輸入樣本與短語不相符。請手動輸入該短語。~輸入樣本過快。請自然地輸入該短語。~輸入樣本的時間變化太少~簽名樣本太短~簽名軌跡太短~簽名形狀太小~簽名形狀過於簡單。請繪製你的平常簽名。~簽名樣本中重複點過多。請自然地繪製簽名。~行為擷取~建立行為樣本~確認行為~輸入~簽名~組合~輸入短語~繪製簽名~註冊~驗證~請以自然節奏輸入至少 5 個字元。~請繪製一個長度和形狀都足夠清晰的簽名。~請輸入一段短語並繪製清晰的簽名。~觸控輸入被遮擋。請移除螢幕覆蓋層後再試一次。~行為短語過長。請使用較短的短語。~繼續之前請至少輸入 5 個字元。~繼續之前請繪製更長的簽名。~空格~正在檢查行為
values-ja~行動認証がタイムアウトしました~行動認証は利用できません~行動サンプルが不足しているか不完全です~行動生体認証が登録されていません~行動テンプレートを登録しました: %1$s~行動生体認証が受け入れられました~行動生体認証が一致しませんでした~行動認証は一時的にロックされています~行動認証は恒久的にロックされています~行動サンプルは有効です~入力フレーズが短すぎます~入力サンプルが短すぎます~入力サンプルがフレーズと一致しません。フレーズを手動で入力してください。~入力サンプルが速すぎます。自然なリズムで入力してください。~入力サンプルの時間変化が少なすぎます~署名サンプルが短すぎます~署名の軌跡が短すぎます~署名の形が小さすぎます~署名の形が単純すぎます。通常の署名を描いてください。~署名サンプルに繰り返し点が多すぎます。自然に署名を描いてください。~行動キャプチャ~行動サンプルを作成~行動を確認してください~入力~署名~組み合わせ~フレーズを入力~署名を描く~登録~確認~自然なリズムで5文字以上入力してください。~十分な長さと形のはっきりした署名を描いてください。~短いフレーズを入力し、はっきりした署名を描いてください。~タッチ入力が遮られています。画面オーバーレイを削除してもう一度お試しください。~行動フレーズが長すぎます。短いフレーズを使用してください。~続行する前に5文字以上入力してください。~続行する前にもっと長い署名を描いてください。~スペース~行動を確認しています
values-ko~행동 인증 시간이 초과되었습니다~행동 인증을 사용할 수 없습니다~행동 샘플이 없거나 불완전합니다~행동 생체 정보가 등록되지 않았습니다~행동 템플릿이 등록되었습니다: %1$s~행동 생체 정보가 승인되었습니다~행동 생체 정보가 일치하지 않았습니다~행동 인증이 일시적으로 잠겼습니다~행동 인증이 영구적으로 잠겼습니다~행동 샘플이 유효합니다~입력 문구가 너무 짧습니다~입력 샘플이 너무 짧습니다~입력 샘플이 문구와 일치하지 않습니다. 문구를 직접 입력하세요.~입력 샘플이 너무 빠릅니다. 자연스럽게 입력하세요.~입력 샘플의 시간 변화가 너무 적습니다~서명 샘플이 너무 짧습니다~서명 경로가 너무 짧습니다~서명 모양이 너무 작습니다~서명 모양이 너무 단순합니다. 평소 서명을 그리세요.~서명 샘플에 반복 점이 너무 많습니다. 자연스럽게 서명을 그리세요.~행동 캡처~행동 샘플 만들기~행동 확인~입력~서명~결합~문구 입력~서명 그리기~등록~확인~자연스러운 리듬으로 최소 5자를 입력하세요.~길이와 형태가 충분한 선명한 서명을 그리세요.~짧은 문구를 입력하고 선명한 서명을 그리세요.~터치 입력이 가려져 있습니다. 화면 오버레이를 제거하고 다시 시도하세요.~행동 문구가 너무 깁니다. 더 짧은 문구를 사용하세요.~계속하기 전에 최소 5자를 입력하세요.~계속하기 전에 더 긴 서명을 그리세요.~공백~행동을 확인하는 중
values-id~Waktu autentikasi perilaku habis~Autentikasi perilaku tidak tersedia~Sampel perilaku tidak ada atau tidak lengkap~Biometrik perilaku belum terdaftar~Template perilaku terdaftar: %1$s~Biometrik perilaku diterima~Biometrik perilaku tidak cocok~Autentikasi perilaku dikunci sementara~Autentikasi perilaku dikunci permanen~Sampel perilaku valid~Frasa pengetikan terlalu pendek~Sampel pengetikan terlalu pendek~Sampel pengetikan tidak sesuai dengan frasa. Ketik frasa secara manual.~Sampel pengetikan terlalu cepat. Ketik frasa secara alami.~Sampel pengetikan memiliki terlalu sedikit variasi waktu~Sampel tanda tangan terlalu pendek~Jejak tanda tangan terlalu pendek~Bentuk tanda tangan terlalu kecil~Bentuk tanda tangan terlalu sederhana. Gambar tanda tangan biasa Anda.~Sampel tanda tangan memiliki terlalu banyak titik berulang. Gambar tanda tangan secara alami.~Pengambilan perilaku~Buat sampel perilaku~Konfirmasi perilaku~Mengetik~Tanda tangan~Gabungan~Ketik frasa~Gambar tanda tangan~Daftarkan~Verifikasi~Ketik setidaknya 5 karakter dengan ritme alami.~Gambar tanda tangan yang jelas dengan panjang dan bentuk yang memadai.~Ketik frasa pendek dan gambar tanda tangan yang jelas.~Input sentuh tertutup. Hapus overlay layar lalu coba lagi.~Frasa perilaku terlalu panjang. Gunakan frasa yang lebih pendek.~Ketik setidaknya 5 karakter sebelum melanjutkan.~Gambar tanda tangan yang lebih panjang sebelum melanjutkan.~spasi~Memeriksa perilaku
values-vi~Đã hết thời gian xác thực hành vi~Xác thực hành vi không khả dụng~Mẫu hành vi thiếu hoặc chưa đầy đủ~Sinh trắc học hành vi chưa được đăng ký~Mẫu hành vi đã đăng ký: %1$s~Đã chấp nhận sinh trắc học hành vi~Sinh trắc học hành vi không khớp~Xác thực hành vi tạm thời bị khóa~Xác thực hành vi bị khóa vĩnh viễn~Mẫu hành vi hợp lệ~Cụm từ nhập quá ngắn~Mẫu nhập quá ngắn~Mẫu nhập không khớp với cụm từ. Hãy nhập cụm từ theo cách thủ công.~Mẫu nhập quá nhanh. Hãy nhập cụm từ một cách tự nhiên.~Mẫu nhập có quá ít biến thiên về thời gian~Mẫu chữ ký quá ngắn~Đường đi của chữ ký quá ngắn~Hình dạng chữ ký quá nhỏ~Hình dạng chữ ký quá đơn giản. Hãy vẽ chữ ký thường dùng của bạn.~Mẫu chữ ký có quá nhiều điểm lặp lại. Hãy vẽ chữ ký một cách tự nhiên.~Thu hành vi~Tạo mẫu hành vi~Xác nhận hành vi~Nhập~Chữ ký~Kết hợp~Nhập cụm từ~Vẽ chữ ký~Đăng ký~Xác minh~Hãy nhập ít nhất 5 ký tự với nhịp điệu tự nhiên.~Hãy vẽ một chữ ký rõ ràng với đủ độ dài và hình dạng.~Hãy nhập một cụm từ ngắn và vẽ một chữ ký rõ ràng.~Dữ liệu chạm bị che khuất. Hãy gỡ lớp phủ màn hình rồi thử lại.~Cụm từ hành vi quá dài. Hãy dùng cụm từ ngắn hơn.~Hãy nhập ít nhất 5 ký tự trước khi tiếp tục.~Hãy vẽ một chữ ký dài hơn trước khi tiếp tục.~dấu cách~Đang kiểm tra hành vi
values-pt-rBR~Tempo esgotado na autenticação comportamental~A autenticação comportamental não está disponível~A amostra comportamental está ausente ou incompleta~A biometria comportamental não está cadastrada~Modelo comportamental cadastrado: %1$s~Biometria comportamental aceita~A biometria comportamental não correspondeu~A autenticação comportamental está temporariamente bloqueada~A autenticação comportamental está permanentemente bloqueada~A amostra comportamental é válida~A frase digitada é muito curta~A amostra de digitação é muito curta~A amostra de digitação não corresponde à frase. Digite a frase manualmente.~A amostra de digitação é muito rápida. Digite a frase naturalmente.~A amostra de digitação tem pouca variação temporal~A amostra da assinatura é muito curta~O traçado da assinatura é muito curto~A forma da assinatura é muito pequena~A forma da assinatura é muito simples. Desenhe sua assinatura normal.~A amostra da assinatura tem pontos repetidos demais. Desenhe a assinatura naturalmente.~Captura comportamental~Criar amostra comportamental~Confirmar comportamento~Digitação~Assinatura~Combinado~Digite a frase~Desenhe a assinatura~Cadastrar~Verificar~Digite pelo menos 5 caracteres com ritmo natural.~Desenhe uma assinatura nítida com comprimento e forma suficientes.~Digite uma frase curta e desenhe uma assinatura nítida.~A entrada por toque está encoberta. Remova as sobreposições da tela e tente novamente.~A frase comportamental é muito longa. Use uma frase mais curta.~Digite pelo menos 5 caracteres antes de continuar.~Desenhe uma assinatura mais longa antes de continuar.~espaço~Verificando comportamento
values-hi~व्यवहार प्रमाणीकरण का समय समाप्त हो गया~व्यवहार प्रमाणीकरण उपलब्ध नहीं है~व्यवहार नमूना गायब है या अधूरा है~व्यवहार बायोमेट्रिक पंजीकृत नहीं है~व्यवहार टेम्पलेट पंजीकृत: %1$s~व्यवहार बायोमेट्रिक स्वीकार किया गया~व्यवहार बायोमेट्रिक मेल नहीं खाया~व्यवहार प्रमाणीकरण अस्थायी रूप से लॉक है~व्यवहार प्रमाणीकरण स्थायी रूप से लॉक है~व्यवहार नमूना मान्य है~टाइपिंग वाक्यांश बहुत छोटा है~टाइपिंग नमूना बहुत छोटा है~टाइपिंग नमूना वाक्यांश से मेल नहीं खाता। वाक्यांश को मैन्युअल रूप से टाइप करें।~टाइपिंग नमूना बहुत तेज है। वाक्यांश को स्वाभाविक रूप से टाइप करें।~टाइपिंग नमूने में समय-आधारित विविधता बहुत कम है~हस्ताक्षर नमूना बहुत छोटा है~हस्ताक्षर पथ बहुत छोटा है~हस्ताक्षर का आकार बहुत छोटा है~हस्ताक्षर का आकार बहुत सरल है। अपना सामान्य हस्ताक्षर बनाएं।~हस्ताक्षर नमूने में बहुत अधिक दोहराए गए बिंदु हैं। हस्ताक्षर स्वाभाविक रूप से बनाएं।~व्यवहार कैप्चर~व्यवहार नमूना बनाएं~व्यवहार की पुष्टि करें~टाइपिंग~हस्ताक्षर~संयुक्त~वाक्यांश टाइप करें~हस्ताक्षर बनाएं~पंजीकृत करें~सत्यापित करें~स्वाभाविक लय के साथ कम से कम 5 अक्षर टाइप करें।~पर्याप्त लंबाई और आकार के साथ स्पष्ट हस्ताक्षर बनाएं।~एक छोटी पंक्ति टाइप करें और स्पष्ट हस्ताक्षर बनाएं।~स्पर्श इनपुट ढका हुआ है। स्क्रीन ओवरले हटाएँ और फिर से प्रयास करें।~व्यवहार वाक्यांश बहुत लंबा है। छोटा वाक्यांश उपयोग करें।~जारी रखने से पहले कम से कम 5 अक्षर टाइप करें।~जारी रखने से पहले लंबा हस्ताक्षर बनाएं।~स्पेस~व्यवहार जाँचा जा रहा है
values-ar~انتهت مهلة المصادقة السلوكية~المصادقة السلوكية غير متاحة~عينة السلوك مفقودة أو غير مكتملة~القياسات الحيوية السلوكية غير مسجلة~تم تسجيل قالب السلوك: %1$s~تم قبول القياسات الحيوية السلوكية~لم تتطابق القياسات الحيوية السلوكية~المصادقة السلوكية مقفلة مؤقتًا~المصادقة السلوكية مقفلة بشكل دائم~عينة السلوك صالحة~عبارة الكتابة قصيرة جدًا~عينة الكتابة قصيرة جدًا~عينة الكتابة لا تطابق العبارة. اكتب العبارة يدويًا.~عينة الكتابة سريعة جدًا. اكتب العبارة بشكل طبيعي.~تباين التوقيت في عينة الكتابة قليل جدًا~عينة التوقيع قصيرة جدًا~مسار التوقيع قصير جدًا~شكل التوقيع صغير جدًا~شكل التوقيع بسيط جدًا. ارسم توقيعك المعتاد.~تحتوي عينة التوقيع على نقاط متكررة كثيرة جدًا. ارسم التوقيع بشكل طبيعي.~التقاط السلوك~إنشاء عينة سلوك~أكّد السلوك~الكتابة~التوقيع~مجمّع~اكتب العبارة~ارسم التوقيع~تسجيل~تحقق~اكتب ما لا يقل عن 5 أحرف بإيقاع طبيعي.~ارسم توقيعًا واضحًا بطول وشكل كافيين.~اكتب عبارة قصيرة وارسم توقيعًا واضحًا.~إدخال اللمس محجوب. أزل طبقات التراكب عن الشاشة وحاول مرة أخرى.~عبارة السلوك طويلة جدًا. استخدم عبارة أقصر.~اكتب ما لا يقل عن 5 أحرف قبل المتابعة.~ارسم توقيعًا أطول قبل المتابعة.~مسافة~جارٍ التحقق من السلوك
values-tr~Davranış kimlik doğrulama zaman aşımına uğradı~Davranış kimlik doğrulama kullanılamıyor~Davranış örneği eksik veya tamamlanmamış~Davranış biyometrisi kayıtlı değil~Davranış şablonu kaydedildi: %1$s~Davranış biyometrisi kabul edildi~Davranış biyometrisi eşleşmedi~Davranış kimlik doğrulama geçici olarak kilitli~Davranış kimlik doğrulama kalıcı olarak kilitli~Davranış örneği geçerli~Yazma ifadesi çok kısa~Yazma örneği çok kısa~Yazma örneği ifadeyle eşleşmiyor. İfadeyi manuel olarak yazın.~Yazma örneği çok hızlı. İfadeyi doğal şekilde yazın.~Yazma örneğinde zaman çeşitliliği çok az~İmza örneği çok kısa~İmza yolu çok kısa~İmza şekli çok küçük~İmza şekli çok basit. Normal imzanızı çizin.~İmza örneğinde çok fazla tekrar eden nokta var. İmzayı doğal şekilde çizin.~Davranış yakalama~Davranış örneği oluştur~Davranışı doğrula~Yazma~İmza~Birleşik~İfadeyi yazın~İmzayı çizin~Kaydet~Doğrula~Doğal ritimle en az 5 karakter yazın.~Yeterli uzunlukta ve biçimde net bir imza çizin.~Kısa bir ifade yazın ve net bir imza çizin.~Dokunmatik giriş engelleniyor. Ekran kaplamalarını kaldırıp tekrar deneyin.~Davranış ifadesi çok uzun. Daha kısa bir ifade kullanın.~Devam etmeden önce en az 5 karakter yazın.~Devam etmeden önce daha uzun bir imza çizin.~boşluk~Davranış kontrol ediliyor
'@ -split '\r?\n'

$sharedBiometricRows = @'
values-de|biometriccompat_use_devicecredentials|Biometrische Sensoren sind derzeit nicht verfügbar.\nBitte verwenden Sie Ihre PIN, Ihr Muster oder Ihr Gerätepasswort, um Ihre Identität zu bestätigen.
values-de|biometriccompat_untrusted_a11y|%1$s\n\nWarnung: %2$s konnte die Sicherheit eines der Bedienungshilfedienste nicht überprüfen. Er könnte Ihre PIN, Ihr Muster oder Ihr Gerätepasswort aufzeichnen.\nFahren Sie nur fort, wenn Sie absolut sicher sind, dass Ihre Aktionen sicher sind.
values-de|biometriccompat_widegamut_error|WideGamut verhindert die ordnungsgemäße Funktion des optischen Sensors
values-de|biometriccompat_permession_error|Erforderliche Berechtigungen wurden nicht erteilt
values-de|biometriccompat_camera_blocked|Kamerasensor durch Datenschutzschalter blockiert
values-de|biometriccompat_cryptography_not_supported_error|Kryptografie wird mit der DeviceCredential-Ausweichoption nicht unterstützt
values-de|biometriccompat_credentials_error|Anmeldedaten wurden angefordert, aber der Nutzer hat die Bestätigung abgebrochen
values-de|biometriccompat_cryptography_failed_error|Bei der kryptografischen Überprüfung ist ein Fehler aufgetreten
values-de|biometriccompat_long_init_error|Die Initialisierung dauert zu lange
values-de|biometriccompat_untrusted_a11y_error|Verdächtiger Bedienungshilfedienst erkannt. Der Nutzer hat die biometrische Prüfung abgebrochen
values-de|biometriccompat_window_error|Die biometrische Authentifizierung kann nicht gestartet werden, weil sich das App-Fenster nicht im Vordergrund befindet
values-de|biometriccompat_api_disabled_error|Die biometrische API ist deaktiviert
values-de|biometriccompat_start_authentication_error|Die biometrische Authentifizierung kann nicht gestartet werden
values-de|biometriccompat_generic_error|Bei der biometrischen Authentifizierung ist ein Fehler aufgetreten
values-de|biometriccompat_generic_error_with_code|Authentifizierungsfehler (%1$d)
values-de|biometriccompat_required_crypto_rejected_error|Das erforderliche kryptografische Objekt wurde abgelehnt
values-de|biometriccompat_required_crypto_missing_error|Die biometrische Authentifizierung wurde ohne das erforderliche kryptografische Ergebnis abgeschlossen
values-de|biometriccompat_activity_destroyed_error|Die biometrische Authentifizierung wurde unterbrochen, weil die Aktivität zerstört wurde
values-de|biometriccompat_internal_error|Es ist ein interner biometrischer Fehler aufgetreten
values-de|biometriccompat_sensor_privacy_start_use_camera_notification_content_title|Gerätekamera entsperren
values-de|biometriccompat_face_sensor_privacy_enabled|Um Face Unlock zu verwenden, aktivieren Sie <b>Kamerazugriff</b> unter Einstellungen > Datenschutz
values-de|biometriccompat_face_error_canceled|Gesichtsvorgang abgebrochen.
values-de|biometriccompat_face_error_hw_not_available|Gesicht kann nicht verifiziert werden. Hardware nicht verfügbar.
values-de|biometriccompat_face_error_hw_not_present|Face Unlock wird auf diesem Gerät nicht unterstützt
values-de|biometriccompat_face_error_lockout|Zu viele Versuche. Versuchen Sie es später erneut.
values-de|biometriccompat_face_error_lockout_permanent|Zu viele Versuche. Face Unlock ist nicht verfügbar.
values-de|biometriccompat_face_error_lockout_screen_lock|Zu viele Versuche. Verwenden Sie stattdessen die Bildschirmsperre.
values-de|biometriccompat_face_error_no_space|Neue Gesichtsdaten können nicht gespeichert werden. Löschen Sie zuerst alte Daten.
values-de|biometriccompat_face_error_not_enrolled|Sie haben Face Unlock nicht eingerichtet
values-de|biometriccompat_face_error_security_update_required|Sensor vorübergehend deaktiviert.
values-de|biometriccompat_face_error_timeout|Versuchen Sie Face Unlock erneut
values-de|biometriccompat_face_error_unable_to_process|Gesicht kann nicht verifiziert werden. Versuchen Sie es erneut.
values-de|biometriccompat_face_error_user_canceled|Face Unlock wurde vom Nutzer abgebrochen
values-de|biometriccompat_face_error_vendor_unknown|Etwas ist schiefgelaufen. Versuchen Sie es erneut.
values-de|biometriccompat_fingerprint_dialog_default_subtitle|Verwenden Sie Ihren Fingerabdruck, um fortzufahren
values-de|biometriccompat_face_dialog_default_subtitle|Verwenden Sie Ihr Gesicht, um fortzufahren
values-de|biometriccompat_face_acquired_insufficient|Ihr Gesichtsmodell kann nicht erstellt werden. Versuchen Sie es erneut.
values-de|biometriccompat_face_acquired_too_bright|Zu hell. Versuchen Sie eine weichere Beleuchtung.
values-de|biometriccompat_face_acquired_too_dark|Nicht genug Licht
values-de|biometriccompat_face_acquired_too_close|Halten Sie das Telefon weiter weg
values-de|biometriccompat_face_acquired_too_far|Halten Sie das Telefon näher heran
values-de|biometriccompat_face_acquired_too_high|Halten Sie das Telefon höher
values-de|biometriccompat_face_acquired_too_low|Halten Sie das Telefon tiefer
values-de|biometriccompat_face_acquired_too_right|Bewegen Sie das Telefon nach links
values-de|biometriccompat_face_acquired_too_left|Bewegen Sie das Telefon nach rechts
values-de|biometriccompat_face_acquired_poor_gaze|Bitte schauen Sie direkter auf Ihr Gerät.
values-de|biometriccompat_face_acquired_not_detected|Ihr Gesicht ist nicht sichtbar. Halten Sie das Telefon auf Augenhöhe.
values-de|biometriccompat_face_acquired_too_much_motion|Zu viel Bewegung. Halten Sie das Telefon ruhig.
values-de|biometriccompat_face_acquired_recalibrate|Bitte richten Sie Face Unlock erneut ein.
values-de|biometriccompat_face_acquired_too_different|Gesicht nicht erkannt. Versuchen Sie es erneut.
values-de|biometriccompat_face_acquired_too_similar|Ändern Sie die Position Ihres Kopfes leicht
values-de|biometriccompat_face_acquired_pan_too_extreme|Schauen Sie direkter auf Ihr Telefon
values-de|biometriccompat_face_acquired_tilt_too_extreme|Schauen Sie direkter auf Ihr Telefon
values-de|biometriccompat_face_acquired_roll_too_extreme|Schauen Sie direkter auf Ihr Telefon
values-de|biometriccompat_face_acquired_obscured|Entfernen Sie alles, was Ihr Gesicht verdeckt.
values-de|biometriccompat_face_acquired_sensor_dirty|Reinigen Sie den oberen Rand Ihres Bildschirms, einschließlich des schwarzen Balkens
values-de|biometriccompat_face_acquired_dark_glasses_detected|@string/biometriccompat_face_acquired_dark_glasses_detected_alt
values-de|biometriccompat_face_acquired_mouth_covering_detected|@string/biometriccompat_face_acquired_mouth_covering_detected_alt
values-de|biometriccompat_face_acquired_recalibrate_alt|Ihr Gesichtsmodell kann nicht erstellt werden. Versuchen Sie es erneut.
values-de|biometriccompat_face_acquired_dark_glasses_detected_alt|Dunkle Brille erkannt. Ihr Gesicht muss vollständig sichtbar sein.
values-de|biometriccompat_face_acquired_mouth_covering_detected_alt|Gesichtsabdeckung erkannt. Ihr Gesicht muss vollständig sichtbar sein.
values-de|biometriccompat_use_screen_lock_label|Bildschirmsperre verwenden
values-de|biometriccompat_screen_lock_prompt_message|Geben Sie Ihre Bildschirmsperre ein, um fortzufahren
values-de|biometriccompat_fingerprint_error_lockout|Zu viele Versuche. Bitte versuchen Sie es später erneut.
values-de|biometriccompat_fingerprint_not_recognized|Nicht erkannt
values-fr|biometriccompat_use_devicecredentials|Les capteurs biométriques ne sont pas disponibles pour le moment.\nVeuillez utiliser votre code PIN, votre schéma ou le mot de passe de l’appareil pour confirmer votre identité.
values-fr|biometriccompat_untrusted_a11y|%1$s\n\nAvertissement : %2$s n’a pas pu vérifier la sécurité de l’un des services d’accessibilité. Il peut enregistrer votre code PIN, votre schéma ou le mot de passe de l’appareil.\nContinuez uniquement si vous êtes absolument certain que vos actions sont sûres.
values-fr|biometriccompat_widegamut_error|WideGamut empêche le bon fonctionnement du capteur optique
values-fr|biometriccompat_permession_error|Les autorisations requises n’ont pas été accordées
values-fr|biometriccompat_camera_blocked|Capteur photo bloqué par le commutateur de confidentialité
values-fr|biometriccompat_cryptography_not_supported_error|La cryptographie n’est pas prise en charge avec le repli DeviceCredential
values-fr|biometriccompat_credentials_error|Des identifiants ont été demandés, mais l’utilisateur a annulé la vérification
values-fr|biometriccompat_cryptography_failed_error|Une erreur s’est produite lors de la vérification cryptographique
values-fr|biometriccompat_long_init_error|L’initialisation prend trop de temps
values-fr|biometriccompat_untrusted_a11y_error|Service d’accessibilité suspect détecté. L’utilisateur a décidé d’interrompre la biométrie
values-fr|biometriccompat_window_error|Impossible de démarrer l’authentification biométrique, car la fenêtre de l’application n’est pas au premier plan
values-fr|biometriccompat_api_disabled_error|L’API biométrique est désactivée
values-fr|biometriccompat_start_authentication_error|Impossible de démarrer l’authentification biométrique
values-fr|biometriccompat_generic_error|Une erreur s’est produite pendant l’authentification biométrique
values-fr|biometriccompat_generic_error_with_code|Erreur d’authentification (%1$d)
values-fr|biometriccompat_required_crypto_rejected_error|L’objet cryptographique requis a été rejeté
values-fr|biometriccompat_required_crypto_missing_error|L’authentification biométrique s’est terminée sans le résultat cryptographique requis
values-fr|biometriccompat_activity_destroyed_error|L’authentification biométrique a été interrompue parce que l’activité a été détruite
values-fr|biometriccompat_internal_error|Une erreur biométrique interne s’est produite
values-fr|biometriccompat_sensor_privacy_start_use_camera_notification_content_title|Débloquer la caméra de l’appareil
values-fr|biometriccompat_face_sensor_privacy_enabled|Pour utiliser Face Unlock, activez <b>Accès à la caméra</b> dans Paramètres > Confidentialité
values-fr|biometriccompat_face_error_canceled|Opération faciale annulée.
values-fr|biometriccompat_face_error_hw_not_available|Impossible de vérifier le visage. Matériel indisponible.
values-fr|biometriccompat_face_error_hw_not_present|Face Unlock n’est pas pris en charge sur cet appareil
values-fr|biometriccompat_face_error_lockout|Trop de tentatives. Réessayez plus tard.
values-fr|biometriccompat_face_error_lockout_permanent|Trop de tentatives. Face Unlock n’est pas disponible.
values-fr|biometriccompat_face_error_lockout_screen_lock|Trop de tentatives. Utilisez plutôt le verrouillage de l’écran.
values-fr|biometriccompat_face_error_no_space|Impossible de stocker de nouvelles données faciales. Supprimez d’abord les anciennes.
values-fr|biometriccompat_face_error_not_enrolled|Vous n’avez pas configuré Face Unlock
values-fr|biometriccompat_face_error_security_update_required|Capteur temporairement désactivé.
values-fr|biometriccompat_face_error_timeout|Réessayez Face Unlock
values-fr|biometriccompat_face_error_unable_to_process|Impossible de vérifier le visage. Réessayez.
values-fr|biometriccompat_face_error_user_canceled|Face Unlock annulé par l’utilisateur
values-fr|biometriccompat_face_error_vendor_unknown|Un problème est survenu. Réessayez.
values-fr|biometriccompat_fingerprint_dialog_default_subtitle|Utilisez votre empreinte digitale pour continuer
values-fr|biometriccompat_face_dialog_default_subtitle|Utilisez votre visage pour continuer
values-fr|biometriccompat_face_acquired_insufficient|Impossible de créer votre modèle facial. Réessayez.
values-fr|biometriccompat_face_acquired_too_bright|Trop de lumière. Essayez un éclairage plus doux.
values-fr|biometriccompat_face_acquired_too_dark|Lumière insuffisante
values-fr|biometriccompat_face_acquired_too_close|Éloignez le téléphone
values-fr|biometriccompat_face_acquired_too_far|Rapprochez le téléphone
values-fr|biometriccompat_face_acquired_too_high|Montez le téléphone
values-fr|biometriccompat_face_acquired_too_low|Descendez le téléphone
values-fr|biometriccompat_face_acquired_too_right|Déplacez le téléphone vers la gauche
values-fr|biometriccompat_face_acquired_too_left|Déplacez le téléphone vers la droite
values-fr|biometriccompat_face_acquired_poor_gaze|Veuillez regarder votre appareil plus directement.
values-fr|biometriccompat_face_acquired_not_detected|Impossible de voir votre visage. Tenez le téléphone à hauteur des yeux.
values-fr|biometriccompat_face_acquired_too_much_motion|Trop de mouvement. Tenez le téléphone immobile.
values-fr|biometriccompat_face_acquired_recalibrate|Veuillez réenregistrer votre visage.
values-fr|biometriccompat_face_acquired_too_different|Visage non reconnu. Réessayez.
values-fr|biometriccompat_face_acquired_too_similar|Changez légèrement la position de votre tête
values-fr|biometriccompat_face_acquired_pan_too_extreme|Regardez votre téléphone plus directement
values-fr|biometriccompat_face_acquired_tilt_too_extreme|Regardez votre téléphone plus directement
values-fr|biometriccompat_face_acquired_roll_too_extreme|Regardez votre téléphone plus directement
values-fr|biometriccompat_face_acquired_obscured|Retirez tout ce qui cache votre visage.
values-fr|biometriccompat_face_acquired_sensor_dirty|Nettoyez le haut de votre écran, y compris la barre noire
values-fr|biometriccompat_face_acquired_dark_glasses_detected|@string/biometriccompat_face_acquired_dark_glasses_detected_alt
values-fr|biometriccompat_face_acquired_mouth_covering_detected|@string/biometriccompat_face_acquired_mouth_covering_detected_alt
values-fr|biometriccompat_face_acquired_recalibrate_alt|Impossible de créer votre modèle facial. Réessayez.
values-fr|biometriccompat_face_acquired_dark_glasses_detected_alt|Lunettes sombres détectées. Votre visage doit être entièrement visible.
values-fr|biometriccompat_face_acquired_mouth_covering_detected_alt|Visage couvert détecté. Votre visage doit être entièrement visible.
values-fr|biometriccompat_use_screen_lock_label|Utiliser le verrouillage de l’écran
values-fr|biometriccompat_screen_lock_prompt_message|Saisissez votre verrouillage d’écran pour continuer
values-fr|biometriccompat_fingerprint_error_lockout|Trop de tentatives. Veuillez réessayer plus tard.
values-fr|biometriccompat_fingerprint_not_recognized|Non reconnu
values-es|biometriccompat_use_devicecredentials|Los sensores biométricos no están disponibles en este momento.\nUsa tu PIN, patrón o contraseña del dispositivo para confirmar tu identidad.
values-es|biometriccompat_untrusted_a11y|%1$s\n\nAdvertencia: %2$s no pudo verificar la seguridad de uno de los servicios de accesibilidad. Puede registrar tu PIN, patrón o contraseña del dispositivo.\nContinúa solo si estás completamente seguro de que tus acciones son seguras.
values-es|biometriccompat_widegamut_error|WideGamut impide el funcionamiento correcto del sensor óptico
values-es|biometriccompat_permession_error|No se concedieron los permisos requeridos
values-es|biometriccompat_camera_blocked|El sensor de la cámara está bloqueado por el interruptor de privacidad
values-es|biometriccompat_cryptography_not_supported_error|La criptografía no es compatible con el respaldo DeviceCredential
values-es|biometriccompat_credentials_error|Se solicitaron credenciales, pero el usuario canceló la verificación
values-es|biometriccompat_cryptography_failed_error|Se produjo un error durante la verificación criptográfica
values-es|biometriccompat_long_init_error|La inicialización tarda demasiado
values-es|biometriccompat_untrusted_a11y_error|Se detectó un servicio de accesibilidad sospechoso. El usuario decidió interrumpir la biometría
values-es|biometriccompat_window_error|No se puede iniciar la autenticación biométrica porque la ventana de la aplicación no está en primer plano
values-es|biometriccompat_api_disabled_error|La API biométrica está deshabilitada
values-es|biometriccompat_start_authentication_error|No se puede iniciar la autenticación biométrica
values-es|biometriccompat_generic_error|Algo salió mal durante la autenticación biométrica
values-es|biometriccompat_generic_error_with_code|Error de autenticación (%1$d)
values-es|biometriccompat_required_crypto_rejected_error|Se rechazó el objeto criptográfico requerido
values-es|biometriccompat_required_crypto_missing_error|La autenticación biométrica finalizó sin el resultado criptográfico requerido
values-es|biometriccompat_activity_destroyed_error|La autenticación biométrica se interrumpió porque la actividad fue destruida
values-es|biometriccompat_internal_error|Se produjo un error biométrico interno
values-es|biometriccompat_sensor_privacy_start_use_camera_notification_content_title|Desbloquear cámara del dispositivo
values-es|biometriccompat_face_sensor_privacy_enabled|Para usar Face Unlock, activa <b>Acceso a la cámara</b> en Ajustes > Privacidad
values-es|biometriccompat_face_error_canceled|Operación facial cancelada.
values-es|biometriccompat_face_error_hw_not_available|No se puede verificar el rostro. Hardware no disponible.
values-es|biometriccompat_face_error_hw_not_present|Face Unlock no es compatible con este dispositivo
values-es|biometriccompat_face_error_lockout|Demasiados intentos. Inténtalo de nuevo más tarde.
values-es|biometriccompat_face_error_lockout_permanent|Demasiados intentos. Face Unlock no está disponible.
values-es|biometriccompat_face_error_lockout_screen_lock|Demasiados intentos. Usa el bloqueo de pantalla en su lugar.
values-es|biometriccompat_face_error_no_space|No se pueden almacenar nuevos datos faciales. Elimina primero los antiguos.
values-es|biometriccompat_face_error_not_enrolled|No has configurado Face Unlock
values-es|biometriccompat_face_error_security_update_required|Sensor deshabilitado temporalmente.
values-es|biometriccompat_face_error_timeout|Vuelve a intentar Face Unlock
values-es|biometriccompat_face_error_unable_to_process|No se puede verificar el rostro. Inténtalo de nuevo.
values-es|biometriccompat_face_error_user_canceled|Face Unlock cancelado por el usuario
values-es|biometriccompat_face_error_vendor_unknown|Algo salió mal. Inténtalo de nuevo.
values-es|biometriccompat_fingerprint_dialog_default_subtitle|Usa tu huella digital para continuar
values-es|biometriccompat_face_dialog_default_subtitle|Usa tu rostro para continuar
values-es|biometriccompat_face_acquired_insufficient|No se puede crear tu modelo facial. Inténtalo de nuevo.
values-es|biometriccompat_face_acquired_too_bright|Demasiada luz. Prueba con una iluminación más suave.
values-es|biometriccompat_face_acquired_too_dark|No hay suficiente luz
values-es|biometriccompat_face_acquired_too_close|Aleja el teléfono
values-es|biometriccompat_face_acquired_too_far|Acerca el teléfono
values-es|biometriccompat_face_acquired_too_high|Sube el teléfono
values-es|biometriccompat_face_acquired_too_low|Baja el teléfono
values-es|biometriccompat_face_acquired_too_right|Mueve el teléfono hacia tu izquierda
values-es|biometriccompat_face_acquired_too_left|Mueve el teléfono hacia tu derecha
values-es|biometriccompat_face_acquired_poor_gaze|Mira tu dispositivo más directamente.
values-es|biometriccompat_face_acquired_not_detected|No se puede ver tu rostro. Mantén el teléfono a la altura de los ojos.
values-es|biometriccompat_face_acquired_too_much_motion|Demasiado movimiento. Mantén el teléfono quieto.
values-es|biometriccompat_face_acquired_recalibrate|Vuelve a registrar tu rostro.
values-es|biometriccompat_face_acquired_too_different|Rostro no reconocido. Inténtalo de nuevo.
values-es|biometriccompat_face_acquired_too_similar|Cambia ligeramente la posición de tu cabeza
values-es|biometriccompat_face_acquired_pan_too_extreme|Mira el teléfono más directamente
values-es|biometriccompat_face_acquired_too_low|Baja el teléfono
values-es|biometriccompat_face_acquired_tilt_too_extreme|Mira el teléfono más directamente
values-es|biometriccompat_face_acquired_roll_too_extreme|Mira el teléfono más directamente
values-es|biometriccompat_face_acquired_obscured|Quita cualquier cosa que cubra tu rostro.
values-es|biometriccompat_face_acquired_sensor_dirty|Limpia la parte superior de la pantalla, incluida la barra negra
values-es|biometriccompat_face_acquired_dark_glasses_detected|@string/biometriccompat_face_acquired_dark_glasses_detected_alt
values-es|biometriccompat_face_acquired_mouth_covering_detected|@string/biometriccompat_face_acquired_mouth_covering_detected_alt
values-es|biometriccompat_face_acquired_recalibrate_alt|No se puede crear tu modelo facial. Inténtalo de nuevo.
values-es|biometriccompat_face_acquired_dark_glasses_detected_alt|Se detectaron gafas oscuras. Tu rostro debe estar totalmente visible.
values-es|biometriccompat_face_acquired_mouth_covering_detected_alt|Se detectó una cubierta facial. Tu rostro debe estar totalmente visible.
values-es|biometriccompat_use_screen_lock_label|Usar bloqueo de pantalla
values-es|biometriccompat_screen_lock_prompt_message|Introduce tu bloqueo de pantalla para continuar
values-es|biometriccompat_fingerprint_error_lockout|Demasiados intentos. Vuelve a intentarlo más tarde.
values-es|biometriccompat_fingerprint_not_recognized|No reconocido
values-b+es+419|biometriccompat_use_devicecredentials|Los sensores biométricos no están disponibles en este momento.\nUsa tu PIN, patrón o contraseña del dispositivo para confirmar tu identidad.
values-b+es+419|biometriccompat_untrusted_a11y|%1$s\n\nAdvertencia: %2$s no pudo verificar la seguridad de uno de los servicios de accesibilidad. Puede registrar tu PIN, patrón o contraseña del dispositivo.\nContinúa solo si estás completamente seguro de que tus acciones son seguras.
values-b+es+419|biometriccompat_widegamut_error|WideGamut impide el funcionamiento correcto del sensor óptico
values-b+es+419|biometriccompat_permession_error|No se concedieron los permisos requeridos
values-b+es+419|biometriccompat_camera_blocked|El sensor de la cámara está bloqueado por el interruptor de privacidad
values-b+es+419|biometriccompat_cryptography_not_supported_error|La criptografía no es compatible con el respaldo DeviceCredential
values-b+es+419|biometriccompat_credentials_error|Se solicitaron credenciales, pero el usuario canceló la verificación
values-b+es+419|biometriccompat_cryptography_failed_error|Se produjo un error durante la verificación criptográfica
values-b+es+419|biometriccompat_long_init_error|La inicialización tarda demasiado
values-b+es+419|biometriccompat_untrusted_a11y_error|Se detectó un servicio de accesibilidad sospechoso. El usuario decidió interrumpir la biometría
values-b+es+419|biometriccompat_window_error|No se puede iniciar la autenticación biométrica porque la ventana de la aplicación no está en primer plano
values-b+es+419|biometriccompat_api_disabled_error|La API biométrica está deshabilitada
values-b+es+419|biometriccompat_start_authentication_error|No se puede iniciar la autenticación biométrica
values-b+es+419|biometriccompat_generic_error|Algo salió mal durante la autenticación biométrica
values-b+es+419|biometriccompat_generic_error_with_code|Error de autenticación (%1$d)
values-b+es+419|biometriccompat_required_crypto_rejected_error|Se rechazó el objeto criptográfico requerido
values-b+es+419|biometriccompat_required_crypto_missing_error|La autenticación biométrica finalizó sin el resultado criptográfico requerido
values-b+es+419|biometriccompat_activity_destroyed_error|La autenticación biométrica se interrumpió porque la actividad fue destruida
values-b+es+419|biometriccompat_internal_error|Se produjo un error biométrico interno
values-b+es+419|biometriccompat_sensor_privacy_start_use_camera_notification_content_title|Desbloquear cámara del dispositivo
values-b+es+419|biometriccompat_face_sensor_privacy_enabled|Para usar Face Unlock, activa <b>Acceso a la cámara</b> en Ajustes > Privacidad
values-b+es+419|biometriccompat_face_error_canceled|Operación facial cancelada.
values-b+es+419|biometriccompat_face_error_hw_not_available|No se puede verificar el rostro. Hardware no disponible.
values-b+es+419|biometriccompat_face_error_hw_not_present|Face Unlock no es compatible con este dispositivo
values-b+es+419|biometriccompat_face_error_lockout|Demasiados intentos. Inténtalo de nuevo más tarde.
values-b+es+419|biometriccompat_face_error_lockout_permanent|Demasiados intentos. Face Unlock no está disponible.
values-b+es+419|biometriccompat_face_error_lockout_screen_lock|Demasiados intentos. Usa el bloqueo de pantalla en su lugar.
values-b+es+419|biometriccompat_face_error_no_space|No se pueden almacenar nuevos datos faciales. Elimina primero los antiguos.
values-b+es+419|biometriccompat_face_error_not_enrolled|No has configurado Face Unlock
values-b+es+419|biometriccompat_face_error_security_update_required|Sensor deshabilitado temporalmente.
values-b+es+419|biometriccompat_face_error_timeout|Vuelve a intentar Face Unlock
values-b+es+419|biometriccompat_face_error_unable_to_process|No se puede verificar el rostro. Inténtalo de nuevo.
values-b+es+419|biometriccompat_face_error_user_canceled|Face Unlock cancelado por el usuario
values-b+es+419|biometriccompat_face_error_vendor_unknown|Algo salió mal. Inténtalo de nuevo.
values-b+es+419|biometriccompat_fingerprint_dialog_default_subtitle|Usa tu huella digital para continuar
values-b+es+419|biometriccompat_face_dialog_default_subtitle|Usa tu rostro para continuar
values-b+es+419|biometriccompat_face_acquired_insufficient|No se puede crear tu modelo facial. Inténtalo de nuevo.
values-b+es+419|biometriccompat_face_acquired_too_bright|Demasiada luz. Prueba con una iluminación más suave.
values-b+es+419|biometriccompat_face_acquired_too_dark|No hay suficiente luz
values-b+es+419|biometriccompat_face_acquired_too_close|Aleja el teléfono
values-b+es+419|biometriccompat_face_acquired_too_far|Acerca el teléfono
values-b+es+419|biometriccompat_face_acquired_too_high|Sube el teléfono
values-b+es+419|biometriccompat_face_acquired_too_low|Baja el teléfono
values-b+es+419|biometriccompat_face_acquired_too_right|Mueve el teléfono hacia tu izquierda
values-b+es+419|biometriccompat_face_acquired_too_left|Mueve el teléfono hacia tu derecha
values-b+es+419|biometriccompat_face_acquired_poor_gaze|Mira tu dispositivo más directamente.
values-b+es+419|biometriccompat_face_acquired_not_detected|No se puede ver tu rostro. Mantén el teléfono a la altura de los ojos.
values-b+es+419|biometriccompat_face_acquired_too_much_motion|Demasiado movimiento. Mantén el teléfono quieto.
values-b+es+419|biometriccompat_face_acquired_recalibrate|Vuelve a registrar tu rostro.
values-b+es+419|biometriccompat_face_acquired_too_different|Rostro no reconocido. Inténtalo de nuevo.
values-b+es+419|biometriccompat_face_acquired_too_similar|Cambia ligeramente la posición de tu cabeza
values-b+es+419|biometriccompat_face_acquired_pan_too_extreme|Mira el teléfono más directamente
values-b+es+419|biometriccompat_face_acquired_tilt_too_extreme|Mira el teléfono más directamente
values-b+es+419|biometriccompat_face_acquired_roll_too_extreme|Mira el teléfono más directamente
values-b+es+419|biometriccompat_face_acquired_obscured|Quita cualquier cosa que cubra tu rostro.
values-b+es+419|biometriccompat_face_acquired_sensor_dirty|Limpia la parte superior de la pantalla, incluida la barra negra
values-b+es+419|biometriccompat_face_acquired_dark_glasses_detected|@string/biometriccompat_face_acquired_dark_glasses_detected_alt
values-b+es+419|biometriccompat_face_acquired_mouth_covering_detected|@string/biometriccompat_face_acquired_mouth_covering_detected_alt
values-b+es+419|biometriccompat_face_acquired_recalibrate_alt|No se puede crear tu modelo facial. Inténtalo de nuevo.
values-b+es+419|biometriccompat_face_acquired_dark_glasses_detected_alt|Se detectaron gafas oscuras. Tu rostro debe estar totalmente visible.
values-b+es+419|biometriccompat_face_acquired_mouth_covering_detected_alt|Se detectó una cubierta facial. Tu rostro debe estar totalmente visible.
values-b+es+419|biometriccompat_use_screen_lock_label|Usar bloqueo de pantalla
values-b+es+419|biometriccompat_screen_lock_prompt_message|Introduce tu bloqueo de pantalla para continuar
values-b+es+419|biometriccompat_fingerprint_error_lockout|Demasiados intentos. Vuelve a intentarlo más tarde.
values-b+es+419|biometriccompat_fingerprint_not_recognized|No reconocido
'@ -split '\r?\n'

$sharedBiometricLocaleRows = @'
values-it~I sensori biometrici non sono al momento disponibili.\nUsa il PIN, la sequenza o la password del dispositivo per confermare la tua identità.~%1$s\n\nAvviso: %2$s non è riuscito a verificare la sicurezza di uno dei servizi di accessibilità: potrebbe registrare il PIN, la sequenza o la password del dispositivo.\nContinua solo se sei assolutamente sicuro che le tue azioni siano sicure.~WideGamut impedisce il corretto funzionamento del sensore ottico~Le autorizzazioni richieste non sono state concesse~Il sensore della fotocamera è bloccato dall’interruttore della privacy~La crittografia non è supportata con il fallback DeviceCredential~Sono state richieste le credenziali, ma l’utente ha annullato la verifica~Si è verificato un errore durante la verifica crittografica~L’inizializzazione richiede troppo tempo~Rilevato un servizio di accessibilità sospetto. L’utente ha deciso di interrompere la verifica biometrica~Impossibile avviare l’autenticazione biometrica perché la finestra dell’app non è in primo piano~L’API biometrica è disabilitata~Impossibile avviare l’autenticazione biometrica~Si è verificato un problema durante l’autenticazione biometrica~Errore di autenticazione (%1$d)~L’oggetto crittografico richiesto è stato rifiutato~L’autenticazione biometrica è stata completata senza il risultato crittografico richiesto~L’autenticazione biometrica è stata interrotta perché l’attività è stata distrutta~Si è verificato un errore biometrico interno~Sblocca la fotocamera del dispositivo~Per usare Face Unlock, attiva <b>Accesso alla fotocamera</b> in Impostazioni > Privacy~Operazione facciale annullata.~Impossibile verificare il volto. Hardware non disponibile.~Face Unlock non è supportato su questo dispositivo~Troppi tentativi. Riprova più tardi.~Troppi tentativi. Face Unlock non è disponibile.~Troppi tentativi. Usa invece il blocco schermo.~Impossibile memorizzare nuovi dati del volto. Elimina prima quelli vecchi.~Non hai configurato Face Unlock~Sensore temporaneamente disabilitato.~Riprova Face Unlock~Impossibile verificare il volto. Riprova.~Face Unlock annullato dall’utente~Si è verificato un problema. Riprova.~Usa l’impronta digitale per continuare~Usa il volto per continuare~Impossibile creare il modello del tuo volto. Riprova.~Troppa luce. Prova con un’illuminazione più morbida.~Luce insufficiente~Allontana il telefono~Avvicina il telefono~Sposta il telefono più in alto~Sposta il telefono più in basso~Sposta il telefono verso sinistra~Sposta il telefono verso destra~Guarda il dispositivo più direttamente.~Impossibile vedere il tuo volto. Tieni il telefono all’altezza degli occhi.~Troppo movimento. Tieni fermo il telefono.~Registra di nuovo il tuo volto.~Volto non riconosciuto. Riprova.~Cambia leggermente la posizione della testa~Guarda il telefono più direttamente~Guarda il telefono più direttamente~Guarda il telefono più direttamente~Rimuovi tutto ciò che copre il tuo volto.~Pulisci la parte superiore dello schermo, inclusa la barra nera~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~Impossibile creare il modello del tuo volto. Riprova.~Occhiali scuri rilevati. Il tuo volto deve essere completamente visibile.~Copertura del volto rilevata. Il tuo volto deve essere completamente visibile.~Usa il blocco schermo~Inserisci il blocco schermo per continuare~Troppi tentativi. Riprova più tardi.~Non riconosciuto
values-nl~Biometrische sensoren zijn momenteel niet beschikbaar.\nGebruik je pincode, patroon of apparaatwachtwoord om je identiteit te bevestigen.~%1$s\n\nWaarschuwing: %2$s kon de beveiliging van een van de toegankelijkheidsservices niet verifiëren. Deze service kan je pincode, patroon of apparaatwachtwoord registreren.\nGa alleen verder als je er volledig zeker van bent dat je acties veilig zijn.~WideGamut verhindert dat de optische sensor correct werkt~Vereiste machtigingen zijn niet verleend~Camerasensor geblokkeerd door de privacy-schakelaar~Cryptografie wordt niet ondersteund met DeviceCredential-terugval~Referenties zijn aangevraagd, maar de gebruiker heeft de verificatie geannuleerd~Er is een fout opgetreden tijdens de cryptografische verificatie~Initialisatie duurt te lang~Verdachte toegankelijkheidsservice gedetecteerd. De gebruiker heeft besloten de biometrische verificatie te onderbreken~Kan biometrische verificatie niet starten omdat het appvenster niet op de voorgrond staat~De biometrische API is uitgeschakeld~Kan biometrische verificatie niet starten~Er is iets misgegaan tijdens de biometrische verificatie~Authenticatiefout (%1$d)~Het vereiste cryptografische object is geweigerd~Biometrische verificatie is voltooid zonder het vereiste cryptografische resultaat~Biometrische verificatie is onderbroken omdat de activiteit is vernietigd~Er is een interne biometrische fout opgetreden~Apparaatcamera deblokkeren~Schakel <b>Cameratoegang</b> in via Instellingen > Privacy om Face Unlock te gebruiken~Gezichtsbewerking geannuleerd.~Kan gezicht niet verifiëren. Hardware niet beschikbaar.~Face Unlock wordt niet ondersteund op dit apparaat~Te veel pogingen. Probeer het later opnieuw.~Te veel pogingen. Face Unlock is niet beschikbaar.~Te veel pogingen. Gebruik in plaats daarvan de schermvergrendeling.~Kan geen nieuwe gezichtsgegevens opslaan. Verwijder eerst oude gegevens.~Je hebt Face Unlock niet ingesteld~Sensor tijdelijk uitgeschakeld.~Probeer Face Unlock opnieuw~Kan gezicht niet verifiëren. Probeer het opnieuw.~Face Unlock geannuleerd door gebruiker~Er is iets misgegaan. Probeer het opnieuw.~Gebruik je vingerafdruk om door te gaan~Gebruik je gezicht om door te gaan~Kan je gezichtsmodel niet maken. Probeer het opnieuw.~Te helder. Probeer zachtere verlichting.~Niet genoeg licht~Houd de telefoon verder weg~Houd de telefoon dichterbij~Houd de telefoon hoger~Houd de telefoon lager~Beweeg de telefoon naar links~Beweeg de telefoon naar rechts~Kijk directer naar je apparaat.~Je gezicht is niet zichtbaar. Houd de telefoon op ooghoogte.~Te veel beweging. Houd de telefoon stil.~Registreer je gezicht opnieuw.~Gezicht niet herkend. Probeer het opnieuw.~Verander de stand van je hoofd een beetje~Kijk directer naar je telefoon~Kijk directer naar je telefoon~Kijk directer naar je telefoon~Verwijder alles wat je gezicht bedekt.~Maak de bovenkant van je scherm schoon, inclusief de zwarte balk~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~Kan je gezichtsmodel niet maken. Probeer het opnieuw.~Donkere bril gedetecteerd. Je gezicht moet volledig zichtbaar zijn.~Gezichtsbedekking gedetecteerd. Je gezicht moet volledig zichtbaar zijn.~Schermvergrendeling gebruiken~Voer je schermvergrendeling in om door te gaan~Te veel pogingen. Probeer het later opnieuw.~Niet herkend
values-ru~Биометрические датчики сейчас недоступны.\nПожалуйста, используйте PIN-код, графический ключ или пароль устройства, чтобы подтвердить свою личность.~%1$s\n\nВнимание: %2$s не смог проверить безопасность одной из служб специальных возможностей. Она может записывать ваш PIN-код, графический ключ или пароль устройства.\nПродолжайте, только если вы полностью уверены, что ваши действия безопасны.~WideGamut мешает корректной работе оптического датчика~Не предоставлены необходимые разрешения~Датчик камеры заблокирован переключателем конфиденциальности~Криптография не поддерживается при резервном использовании DeviceCredential~Учётные данные были запрошены, но пользователь отменил проверку~Во время криптографической проверки произошла ошибка~Инициализация занимает слишком много времени~Обнаружена подозрительная служба специальных возможностей. Пользователь решил прервать биометрическую проверку~Не удалось запустить биометрическую аутентификацию, потому что окно приложения не находится на переднем плане~Биометрический API отключён~Не удалось запустить биометрическую аутентификацию~Во время биометрической аутентификации что-то пошло не так~Ошибка аутентификации (%1$d)~Требуемый криптографический объект был отклонён~Биометрическая аутентификация завершилась без требуемого криптографического результата~Биометрическая аутентификация была прервана, поскольку активность была уничтожена~Произошла внутренняя биометрическая ошибка~Разблокируйте камеру устройства~Чтобы использовать Face Unlock, включите <b>Доступ к камере</b> в разделе Настройки > Конфиденциальность~Операция с лицом отменена.~Не удаётся проверить лицо. Оборудование недоступно.~Face Unlock не поддерживается на этом устройстве~Слишком много попыток. Повторите позже.~Слишком много попыток. Face Unlock недоступен.~Слишком много попыток. Вместо этого используйте блокировку экрана.~Невозможно сохранить новые данные лица. Сначала удалите старые.~Face Unlock не настроен~Датчик временно отключён.~Попробуйте Face Unlock ещё раз~Не удаётся проверить лицо. Повторите попытку.~Face Unlock отменён пользователем~Что-то пошло не так. Повторите попытку.~Используйте отпечаток пальца, чтобы продолжить~Используйте лицо, чтобы продолжить~Не удаётся создать модель вашего лица. Повторите попытку.~Слишком ярко. Попробуйте более мягкое освещение.~Недостаточно света~Отодвиньте телефон дальше~Поднесите телефон ближе~Поднимите телефон выше~Опустите телефон ниже~Сдвиньте телефон влево~Сдвиньте телефон вправо~Смотрите на устройство более прямо.~Не видно ваше лицо. Держите телефон на уровне глаз.~Слишком много движения. Держите телефон неподвижно.~Заново зарегистрируйте лицо.~Лицо не распознано. Повторите попытку.~Немного измените положение головы~Смотрите на телефон более прямо~Смотрите на телефон более прямо~Смотрите на телефон более прямо~Уберите всё, что закрывает ваше лицо.~Очистите верхнюю часть экрана, включая чёрную полоску~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~Не удаётся создать модель вашего лица. Повторите попытку.~Обнаружены тёмные очки. Ваше лицо должно быть полностью видно.~Обнаружено покрытие лица. Ваше лицо должно быть полностью видно.~Использовать блокировку экрана~Введите блокировку экрана, чтобы продолжить~Слишком много попыток. Повторите позже.~Не распознано
values-uk~Біометричні сенсори зараз недоступні.\nБудь ласка, використайте PIN-код, графічний ключ або пароль пристрою, щоб підтвердити свою особу.~%1$s\n\nУвага: %2$s не зміг перевірити безпеку однієї зі служб спеціальних можливостей. Вона може записувати ваш PIN-код, графічний ключ або пароль пристрою.\nПродовжуйте, лише якщо ви повністю впевнені, що ваші дії безпечні.~WideGamut заважає коректній роботі оптичного сенсора~Не надано потрібні дозволи~Сенсор камери заблоковано перемикачем конфіденційності~Криптографія не підтримується при резервному використанні DeviceCredential~Було запитано облікові дані, але користувач скасував перевірку~Під час криптографічної перевірки сталася помилка~Ініціалізація триває надто довго~Виявлено підозрілу службу спеціальних можливостей. Користувач вирішив перервати біометричну перевірку~Не вдалося запустити біометричну автентифікацію, оскільки вікно застосунку не перебуває на передньому плані~Біометричний API вимкнено~Не вдалося запустити біометричну автентифікацію~Під час біометричної автентифікації щось пішло не так~Помилка автентифікації (%1$d)~Потрібний криптографічний об’єкт було відхилено~Біометричну автентифікацію завершено без потрібного криптографічного результату~Біометричну автентифікацію перервано, оскільки активність було знищено~Сталася внутрішня біометрична помилка~Розблокуйте камеру пристрою~Щоб використовувати Face Unlock, увімкніть <b>Доступ до камери</b> у розділі Налаштування > Конфіденційність~Операцію з обличчям скасовано.~Не вдається перевірити обличчя. Обладнання недоступне.~Face Unlock не підтримується на цьому пристрої~Надто багато спроб. Спробуйте пізніше.~Надто багато спроб. Face Unlock недоступний.~Надто багато спроб. Натомість використайте блокування екрана.~Не вдається зберегти нові дані обличчя. Спочатку видаліть старі.~Face Unlock не налаштовано~Сенсор тимчасово вимкнено.~Спробуйте Face Unlock ще раз~Не вдається перевірити обличчя. Спробуйте ще раз.~Face Unlock скасовано користувачем~Щось пішло не так. Спробуйте ще раз.~Використайте відбиток пальця, щоб продовжити~Використайте обличчя, щоб продовжити~Не вдається створити модель вашого обличчя. Спробуйте ще раз.~Надто яскраво. Спробуйте м’якше освітлення.~Недостатньо світла~Відсуньте телефон далі~Піднесіть телефон ближче~Підніміть телефон вище~Опустіть телефон нижче~Посуньте телефон ліворуч~Посуньте телефон праворуч~Дивіться на пристрій більш прямо.~Не видно вашого обличчя. Тримайте телефон на рівні очей.~Занадто багато руху. Тримайте телефон нерухомо.~Повторно зареєструйте своє обличчя.~Обличчя не розпізнано. Спробуйте ще раз.~Трохи змініть положення голови~Дивіться на телефон більш прямо~Дивіться на телефон більш прямо~Дивіться на телефон більш прямо~Приберіть усе, що закриває ваше обличчя.~Очистьте верхню частину екрана, включно з чорною смугою~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~Не вдається створити модель вашого обличчя. Спробуйте ще раз.~Виявлено темні окуляри. Ваше обличчя має бути повністю видимим.~Виявлено покриття обличчя. Ваше обличчя має бути повністю видимим.~Використати блокування екрана~Введіть блокування екрана, щоб продовжити~Надто багато спроб. Спробуйте пізніше.~Не розпізнано
values-pt-rBR~Os sensores biométricos não estão disponíveis no momento.\nUse seu PIN, padrão ou senha do dispositivo para confirmar sua identidade.~%1$s\n\nAviso: %2$s não conseguiu verificar a segurança de um dos serviços de acessibilidade. Ele pode registrar seu PIN, padrão ou senha do dispositivo.\nContinue apenas se tiver certeza absoluta de que suas ações são seguras.~WideGamut impede o funcionamento correto do sensor óptico~As permissões necessárias não foram concedidas~O sensor da câmera está bloqueado pela chave de privacidade~A criptografia não é compatível com o fallback DeviceCredential~As credenciais foram solicitadas, mas o usuário cancelou a verificação~Ocorreu um erro durante a verificação criptográfica~A inicialização está demorando demais~Serviço de acessibilidade suspeito detectado. O usuário decidiu interromper a verificação biométrica~Não foi possível iniciar a autenticação biométrica porque a janela do app não está em primeiro plano~A API biométrica está desativada~Não foi possível iniciar a autenticação biométrica~Algo deu errado durante a autenticação biométrica~Erro de autenticação (%1$d)~O objeto criptográfico necessário foi rejeitado~A autenticação biométrica foi concluída sem o resultado criptográfico necessário~A autenticação biométrica foi interrompida porque a atividade foi destruída~Ocorreu um erro biométrico interno~Desbloquear câmera do dispositivo~Para usar o Face Unlock, ative o <b>Acesso à câmera</b> em Configurações > Privacidade~Operação facial cancelada.~Não foi possível verificar o rosto. Hardware indisponível.~O Face Unlock não é compatível com este dispositivo~Tentativas demais. Tente novamente mais tarde.~Tentativas demais. O Face Unlock não está disponível.~Tentativas demais. Use o bloqueio de tela no lugar.~Não é possível armazenar novos dados faciais. Exclua primeiro os antigos.~Você ainda não configurou o Face Unlock~Sensor temporariamente desativado.~Tente o Face Unlock novamente~Não foi possível verificar o rosto. Tente novamente.~Face Unlock cancelado pelo usuário~Algo deu errado. Tente novamente.~Use sua impressão digital para continuar~Use seu rosto para continuar~Não foi possível criar o modelo do seu rosto. Tente novamente.~Luz demais. Tente uma iluminação mais suave.~Luz insuficiente~Afaste o telefone~Aproxime o telefone~Erga mais o telefone~Abaixe mais o telefone~Mova o telefone para a esquerda~Mova o telefone para a direita~Olhe mais diretamente para o dispositivo.~Não foi possível ver seu rosto. Mantenha o telefone na altura dos olhos.~Movimento demais. Mantenha o telefone firme.~Cadastre seu rosto novamente.~Rosto não reconhecido. Tente novamente.~Mude um pouco a posição da cabeça~Olhe mais diretamente para o telefone~Olhe mais diretamente para o telefone~Olhe mais diretamente para o telefone~Remova qualquer coisa que esteja cobrindo seu rosto.~Limpe a parte superior da tela, incluindo a faixa preta~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~Não foi possível criar o modelo do seu rosto. Tente novamente.~Óculos escuros detectados. Seu rosto deve estar totalmente visível.~Cobertura facial detectada. Seu rosto deve estar totalmente visível.~Usar bloqueio de tela~Digite o bloqueio de tela para continuar~Tentativas demais. Tente novamente mais tarde.~Não reconhecido
values-tr~Biyometrik sensörler şu anda kullanılamıyor.\nKimliğinizi doğrulamak için lütfen PIN’inizi, deseninizi veya cihaz parolanızı kullanın.~%1$s\n\nUyarı: %2$s, erişilebilirlik hizmetlerinden birinin güvenliğini doğrulayamadı. PIN’inizi, deseninizi veya cihaz parolanızı kaydedebilir.\nYalnızca yaptıklarınızın güvenli olduğundan tamamen eminseniz devam edin.~WideGamut optik sensörün düzgün çalışmasını engelliyor~Gerekli izinler verilmedi~Kamera sensörü gizlilik anahtarı tarafından engellendi~DeviceCredential geri dönüşüyle kriptografi desteklenmiyor~Kimlik bilgileri istendi, ancak kullanıcı doğrulamayı iptal etti~Kriptografik doğrulama sırasında bir hata oluştu~Başlatma çok uzun sürüyor~Şüpheli bir erişilebilirlik hizmeti algılandı. Kullanıcı biyometrik doğrulamayı kesmeye karar verdi~Uygulama penceresi ön planda olmadığı için biyometrik doğrulama başlatılamıyor~Biyometrik API devre dışı~Biyometrik doğrulama başlatılamıyor~Biyometrik doğrulama sırasında bir sorun oluştu~Kimlik doğrulama hatası (%1$d)~Gerekli kriptografik nesne reddedildi~Biyometrik doğrulama gerekli kriptografik sonuç olmadan tamamlandı~Etkinlik yok edildiği için biyometrik doğrulama kesildi~Dahili bir biyometrik hata oluştu~Cihaz kamerasının engelini kaldır~Face Unlock’u kullanmak için Ayarlar > Gizlilik bölümünde <b>Kamera erişimi</b>ni açın~Yüz işlemi iptal edildi.~Yüz doğrulanamıyor. Donanım kullanılamıyor.~Face Unlock bu cihazda desteklenmiyor~Çok fazla deneme. Daha sonra tekrar deneyin.~Çok fazla deneme. Face Unlock kullanılamıyor.~Çok fazla deneme. Bunun yerine ekran kilidini kullanın.~Yeni yüz verileri depolanamıyor. Önce eski verileri silin.~Face Unlock kurulmamış~Sensör geçici olarak devre dışı bırakıldı.~Face Unlock’u tekrar deneyin~Yüz doğrulanamıyor. Tekrar deneyin.~Face Unlock kullanıcı tarafından iptal edildi~Bir sorun oluştu. Tekrar deneyin.~Devam etmek için parmak izinizi kullanın~Devam etmek için yüzünüzü kullanın~Yüz modeliniz oluşturulamıyor. Tekrar deneyin.~Çok parlak. Daha yumuşak bir ışık deneyin.~Yeterli ışık yok~Telefonu daha uzağa götürün~Telefonu daha yakına getirin~Telefonu daha yukarı kaldırın~Telefonu daha aşağı indirin~Telefonu sola kaydırın~Telefonu sağa kaydırın~Cihaza daha doğrudan bakın.~Yüzünüz görünmüyor. Telefonu göz hizasında tutun.~Çok fazla hareket var. Telefonu sabit tutun.~Yüzünüzü yeniden kaydedin.~Yüz tanınmadı. Tekrar deneyin.~Başınızın konumunu biraz değiştirin~Telefonunuza daha doğrudan bakın~Telefonunuza daha doğrudan bakın~Telefonunuza daha doğrudan bakın~Yüzünüzü kapatan her şeyi kaldırın.~Siyah çubuk dahil ekranın üst kısmını temizleyin~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~Yüz modeliniz oluşturulamıyor. Tekrar deneyin.~Koyu gözlük algılandı. Yüzünüz tamamen görünür olmalıdır.~Yüz örtüsü algılandı. Yüzünüz tamamen görünür olmalıdır.~Ekran kilidini kullan~Devam etmek için ekran kilidinizi girin~Çok fazla deneme. Lütfen daha sonra tekrar deneyin.~Tanınmadı
values-id~Sensor biometrik saat ini tidak tersedia.\nGunakan PIN, pola, atau kata sandi perangkat Anda untuk mengonfirmasi identitas Anda.~%1$s\n\nPeringatan: %2$s tidak dapat memverifikasi keamanan salah satu layanan aksesibilitas. Layanan tersebut mungkin merekam PIN, pola, atau kata sandi perangkat Anda.\nLanjutkan hanya jika Anda benar-benar yakin bahwa tindakan Anda aman.~WideGamut menghambat kerja sensor optik dengan benar~Izin yang diperlukan tidak diberikan~Sensor kamera diblokir oleh sakelar privasi~Kriptografi tidak didukung dengan cadangan DeviceCredential~Kredensial diminta, tetapi pengguna membatalkan verifikasi~Terjadi kesalahan selama verifikasi kriptografi~Inisialisasi memakan waktu terlalu lama~Layanan aksesibilitas yang mencurigakan terdeteksi. Pengguna memutuskan untuk menghentikan verifikasi biometrik~Tidak dapat memulai autentikasi biometrik karena jendela aplikasi tidak berada di latar depan~API biometrik dinonaktifkan~Tidak dapat memulai autentikasi biometrik~Terjadi masalah saat autentikasi biometrik~Kesalahan autentikasi (%1$d)~Objek kriptografi yang diperlukan ditolak~Autentikasi biometrik selesai tanpa hasil kriptografi yang diperlukan~Autentikasi biometrik terhenti karena aktivitas dihancurkan~Terjadi kesalahan biometrik internal~Buka blokir kamera perangkat~Untuk menggunakan Face Unlock, aktifkan <b>Akses kamera</b> di Setelan > Privasi~Operasi wajah dibatalkan.~Tidak dapat memverifikasi wajah. Perangkat keras tidak tersedia.~Face Unlock tidak didukung di perangkat ini~Terlalu banyak percobaan. Coba lagi nanti.~Terlalu banyak percobaan. Face Unlock tidak tersedia.~Terlalu banyak percobaan. Gunakan kunci layar sebagai gantinya.~Tidak dapat menyimpan data wajah baru. Hapus data lama terlebih dahulu.~Anda belum menyiapkan Face Unlock~Sensor dinonaktifkan sementara.~Coba Face Unlock lagi~Tidak dapat memverifikasi wajah. Coba lagi.~Face Unlock dibatalkan oleh pengguna~Terjadi masalah. Coba lagi.~Gunakan sidik jari Anda untuk melanjutkan~Gunakan wajah Anda untuk melanjutkan~Tidak dapat membuat model wajah Anda. Coba lagi.~Terlalu terang. Coba pencahayaan yang lebih lembut.~Pencahayaan tidak cukup~Jauhkan telepon~Dekatkan telepon~Naikkan telepon~Turunkan telepon~Geser telepon ke kiri~Geser telepon ke kanan~Lihat perangkat Anda lebih lurus.~Wajah Anda tidak terlihat. Pegang telepon setinggi mata.~Gerakan terlalu banyak. Pegang telepon tetap stabil.~Daftarkan ulang wajah Anda.~Wajah tidak dikenali. Coba lagi.~Ubah posisi kepala Anda sedikit~Lihat telepon Anda lebih lurus~Lihat telepon Anda lebih lurus~Lihat telepon Anda lebih lurus~Singkirkan apa pun yang menutupi wajah Anda.~Bersihkan bagian atas layar, termasuk bilah hitam~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~Tidak dapat membuat model wajah Anda. Coba lagi.~Kacamata gelap terdeteksi. Wajah Anda harus terlihat sepenuhnya.~Penutup wajah terdeteksi. Wajah Anda harus terlihat sepenuhnya.~Gunakan kunci layar~Masukkan kunci layar Anda untuk melanjutkan~Terlalu banyak percobaan. Silakan coba lagi nanti.~Tidak dikenali
values-vi~Cảm biến sinh trắc học hiện không khả dụng.\nVui lòng dùng mã PIN, hình mở khóa hoặc mật khẩu thiết bị để xác nhận danh tính của bạn.~%1$s\n\nCảnh báo: %2$s không thể xác minh mức độ an toàn của một trong các dịch vụ trợ năng. Dịch vụ đó có thể ghi lại mã PIN, hình mở khóa hoặc mật khẩu thiết bị của bạn.\nChỉ tiếp tục nếu bạn hoàn toàn chắc chắn rằng các thao tác của mình là an toàn.~WideGamut cản trở cảm biến quang học hoạt động đúng cách~Chưa được cấp các quyền cần thiết~Cảm biến camera bị chặn bởi công tắc riêng tư~Không hỗ trợ mật mã học với phương án dự phòng DeviceCredential~Đã yêu cầu thông tin xác thực nhưng người dùng đã hủy xác minh~Đã xảy ra lỗi trong quá trình xác minh mật mã học~Quá trình khởi tạo mất quá nhiều thời gian~Phát hiện dịch vụ trợ năng đáng ngờ. Người dùng đã quyết định dừng xác minh sinh trắc học~Không thể bắt đầu xác thực sinh trắc học vì cửa sổ ứng dụng không ở nền trước~API sinh trắc học đã bị tắt~Không thể bắt đầu xác thực sinh trắc học~Đã xảy ra sự cố trong quá trình xác thực sinh trắc học~Lỗi xác thực (%1$d)~Đối tượng mật mã bắt buộc đã bị từ chối~Xác thực sinh trắc học đã hoàn tất mà không có kết quả mật mã bắt buộc~Xác thực sinh trắc học đã bị gián đoạn vì màn hình hiện tại đã bị hủy~Đã xảy ra lỗi sinh trắc học nội bộ~Bỏ chặn camera của thiết bị~Để dùng Face Unlock, hãy bật <b>Quyền truy cập camera</b> trong Cài đặt > Quyền riêng tư~Đã hủy thao tác khuôn mặt.~Không thể xác minh khuôn mặt. Phần cứng không khả dụng.~Face Unlock không được hỗ trợ trên thiết bị này~Quá nhiều lần thử. Hãy thử lại sau.~Quá nhiều lần thử. Face Unlock không khả dụng.~Quá nhiều lần thử. Hãy dùng khóa màn hình thay thế.~Không thể lưu dữ liệu khuôn mặt mới. Hãy xóa dữ liệu cũ trước.~Bạn chưa thiết lập Face Unlock~Cảm biến tạm thời bị vô hiệu hóa.~Hãy thử Face Unlock lại~Không thể xác minh khuôn mặt. Hãy thử lại.~Face Unlock đã bị người dùng hủy~Đã xảy ra sự cố. Hãy thử lại.~Dùng vân tay của bạn để tiếp tục~Dùng khuôn mặt của bạn để tiếp tục~Không thể tạo mô hình khuôn mặt của bạn. Hãy thử lại.~Quá sáng. Hãy thử ánh sáng dịu hơn.~Không đủ ánh sáng~Đưa điện thoại ra xa hơn~Đưa điện thoại lại gần hơn~Nâng điện thoại cao hơn~Hạ điện thoại thấp hơn~Di chuyển điện thoại sang trái~Di chuyển điện thoại sang phải~Hãy nhìn trực diện hơn vào thiết bị.~Không nhìn thấy khuôn mặt của bạn. Hãy giữ điện thoại ngang tầm mắt.~Chuyển động quá nhiều. Hãy giữ điện thoại ổn định.~Hãy đăng ký lại khuôn mặt của bạn.~Khuôn mặt không được nhận ra. Hãy thử lại.~Hãy thay đổi nhẹ vị trí đầu của bạn~Hãy nhìn trực diện hơn vào điện thoại~Hãy nhìn trực diện hơn vào điện thoại~Hãy nhìn trực diện hơn vào điện thoại~Hãy bỏ mọi thứ đang che khuôn mặt của bạn.~Lau sạch phần trên của màn hình, bao gồm cả dải màu đen~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~Không thể tạo mô hình khuôn mặt của bạn. Hãy thử lại.~Phát hiện kính tối màu. Khuôn mặt của bạn phải hiển thị hoàn toàn.~Phát hiện vật che mặt. Khuôn mặt của bạn phải hiển thị hoàn toàn.~Dùng khóa màn hình~Nhập khóa màn hình của bạn để tiếp tục~Quá nhiều lần thử. Vui lòng thử lại sau.~Không nhận ra
values-zh-rCN~生物识别传感器当前不可用。\n请使用您的 PIN 码、图案或设备密码来确认您的身份。~%1$s\n\n警告：%2$s 无法验证其中一项无障碍服务的安全性。它可能会记录您的 PIN 码、图案或设备密码。\n只有在您完全确定自己的操作是安全的情况下才继续。~WideGamut 会阻止光学传感器正常工作~未授予所需权限~摄像头传感器已被隐私开关阻止~使用 DeviceCredential 回退时不支持加密功能~已请求凭据，但用户取消了验证~加密验证期间发生错误~初始化耗时过长~检测到可疑的无障碍服务。用户决定中断生物识别验证~无法启动生物识别身份验证，因为应用窗口不在前台~生物识别 API 已被禁用~无法启动生物识别身份验证~生物识别身份验证期间出现问题~身份验证错误 (%1$d)~所需的加密对象已被拒绝~生物识别身份验证已完成，但没有所需的加密结果~由于 activity 已销毁，生物识别身份验证被中断~发生了内部生物识别错误~解除设备摄像头的阻止~要使用 Face Unlock，请在 设置 > 隐私 中打开<b>相机访问权限</b>~人脸操作已取消。~无法验证人脸。硬件不可用。~此设备不支持 Face Unlock~尝试次数过多。请稍后再试。~尝试次数过多。Face Unlock 不可用。~尝试次数过多。请改用屏幕锁。~无法存储新的人脸数据。请先删除旧数据。~您尚未设置 Face Unlock~传感器已暂时禁用。~请再次尝试 Face Unlock~无法验证人脸。请重试。~Face Unlock 已被用户取消~出现问题。请重试。~使用您的指纹继续~使用您的面容继续~无法创建您的人脸模型。请重试。~光线太强。请尝试更柔和的光线。~光线不足~将手机拿远一些~将手机靠近一些~将手机抬高一些~将手机放低一些~将手机向左移动~将手机向右移动~请更正面地看向设备。~看不到您的脸。请将手机保持在眼睛高度。~移动过多。请保持手机稳定。~请重新录入您的人脸。~无法识别人脸。请重试。~请稍微调整头部位置~请更正面地看向手机~请更正面地看向手机~请更正面地看向手机~移除任何遮挡您脸部的物体。~清洁屏幕顶部，包括黑色条带~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~无法创建您的人脸模型。请重试。~检测到深色眼镜。您的脸部必须完全可见。~检测到面部遮挡物。您的脸部必须完全可见。~使用屏幕锁~输入屏幕锁以继续~尝试次数过多。请稍后再试。~无法识别
values-zh-rTW~生物辨識感測器目前無法使用。\n請使用您的 PIN 碼、圖案或裝置密碼來確認您的身分。~%1$s\n\n警告：%2$s 無法驗證其中一項無障礙服務的安全性。它可能會記錄您的 PIN 碼、圖案或裝置密碼。\n只有在您完全確定自己的操作是安全的情況下才繼續。~WideGamut 會阻止光學感測器正常運作~未授予所需權限~相機感測器已被隱私開關封鎖~使用 DeviceCredential 備援時不支援密碼學功能~已要求憑證，但使用者取消了驗證~密碼學驗證期間發生錯誤~初始化耗時過長~偵測到可疑的無障礙服務。使用者決定中斷生物辨識驗證~無法啟動生物辨識驗證，因為應用程式視窗不在前景~生物辨識 API 已停用~無法啟動生物辨識驗證~生物辨識驗證期間發生問題~驗證錯誤 (%1$d)~所需的密碼物件已被拒絕~生物辨識驗證已完成，但沒有所需的密碼結果~由於 activity 已被銷毀，生物辨識驗證已中斷~發生內部生物辨識錯誤~解除裝置相機的封鎖~若要使用 Face Unlock，請在 設定 > 隱私權 中開啟<b>相機存取權</b>~臉部操作已取消。~無法驗證臉部。硬體無法使用。~此裝置不支援 Face Unlock~嘗試次數過多。請稍後再試。~嘗試次數過多。Face Unlock 無法使用。~嘗試次數過多。請改用螢幕鎖。~無法儲存新的人臉資料。請先刪除舊資料。~您尚未設定 Face Unlock~感測器已暫時停用。~請再次嘗試 Face Unlock~無法驗證臉部。請再試一次。~Face Unlock 已被使用者取消~發生問題。請再試一次。~使用您的指紋繼續~使用您的臉部繼續~無法建立您的人臉模型。請再試一次。~光線太強。請嘗試更柔和的光線。~光線不足~將手機拿遠一些~將手機靠近一些~將手機抬高一些~將手機放低一些~將手機向左移動~將手機向右移動~請更正面地看向裝置。~看不到您的臉。請將手機保持在眼睛高度。~移動過多。請保持手機穩定。~請重新登錄您的臉部。~無法辨識臉部。請再試一次。~請稍微調整頭部位置~請更正面地看向手機~請更正面地看向手機~請更正面地看向手機~移除任何遮擋您臉部的物體。~清潔螢幕頂部，包括黑色條帶~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~無法建立您的人臉模型。請再試一次。~偵測到深色眼鏡。您的臉部必須完全可見。~偵測到面部遮擋物。您的臉部必須完全可見。~使用螢幕鎖~輸入螢幕鎖以繼續~嘗試次數過多。請稍後再試。~無法辨識
values-ja~生体認証センサーは現在利用できません。\n本人確認のため、PIN、パターン、または端末のパスワードを使用してください。~%1$s\n\n警告: %2$s はアクセシビリティ サービスの 1 つの安全性を確認できませんでした。PIN、パターン、または端末のパスワードを記録する可能性があります。\n操作が安全であると完全に確信できる場合にのみ続行してください。~WideGamut により光学センサーが正常に動作できません~必要な権限が付与されていません~カメラセンサーがプライバシー切り替えでブロックされています~DeviceCredential フォールバックでは暗号化はサポートされていません~認証情報が要求されましたが、ユーザーが確認をキャンセルしました~暗号化の検証中にエラーが発生しました~初期化に時間がかかりすぎています~疑わしいアクセシビリティ サービスが検出されました。ユーザーは生体認証の確認を中断することにしました~アプリのウィンドウが前面にないため、生体認証を開始できません~生体認証 API は無効です~生体認証を開始できません~生体認証中に問題が発生しました~認証エラー (%1$d)~必要な暗号オブジェクトが拒否されました~必要な暗号結果なしで生体認証が完了しました~activity が破棄されたため、生体認証が中断されました~内部生体認証エラーが発生しました~端末のカメラのブロックを解除~Face Unlock を使用するには、設定 > プライバシー で<b>カメラへのアクセス</b>を有効にしてください~顔の操作がキャンセルされました。~顔を確認できません。ハードウェアを利用できません。~この端末では Face Unlock はサポートされていません~試行回数が多すぎます。後でもう一度お試しください。~試行回数が多すぎます。Face Unlock は利用できません。~試行回数が多すぎます。代わりに画面ロックを使用してください。~新しい顔データを保存できません。先に古いデータを削除してください。~Face Unlock が設定されていません~センサーは一時的に無効です。~Face Unlock をもう一度お試しください~顔を確認できません。もう一度お試しください。~Face Unlock はユーザーによってキャンセルされました~問題が発生しました。もう一度お試しください。~続行するには指紋を使用してください~続行するには顔を使用してください~顔モデルを作成できません。もう一度お試しください。~明るすぎます。より柔らかい照明を試してください。~光が足りません~スマートフォンを少し離してください~スマートフォンをもう少し近づけてください~スマートフォンをもう少し高くしてください~スマートフォンをもう少し低くしてください~スマートフォンを左に動かしてください~スマートフォンを右に動かしてください~端末をもっと正面から見てください。~顔が見えません。スマートフォンを目の高さに保ってください。~動きが多すぎます。スマートフォンをしっかり固定してください。~顔を再登録してください。~顔を認識できませんでした。もう一度お試しください。~頭の位置を少し変えてください~スマートフォンをもっと正面から見てください~スマートフォンをもっと正面から見てください~スマートフォンをもっと正面から見てください~顔を隠しているものを取り除いてください。~黒いバーを含め、画面上部を清掃してください~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~顔モデルを作成できません。もう一度お試しください。~濃い色の眼鏡が検出されました。顔全体が見えている必要があります。~顔を覆うものが検出されました。顔全体が見えている必要があります。~画面ロックを使用~続行するには画面ロックを入力してください~試行回数が多すぎます。後でもう一度お試しください。~認識されませんでした
values-ko~생체 인식 센서를 지금 사용할 수 없습니다.\n본인 확인을 위해 PIN, 패턴 또는 기기 비밀번호를 사용하세요.~%1$s\n\n경고: %2$s에서 접근성 서비스 중 하나의 보안을 확인할 수 없습니다. PIN, 패턴 또는 기기 비밀번호를 기록할 수 있습니다.\n동작이 안전하다고 완전히 확신하는 경우에만 계속하세요.~WideGamut로 인해 광학 센서가 제대로 작동하지 않습니다~필요한 권한이 부여되지 않았습니다~개인정보 보호 스위치로 인해 카메라 센서가 차단되었습니다~DeviceCredential 대체 경로에서는 암호화 기능이 지원되지 않습니다~자격 증명이 요청되었지만 사용자가 확인을 취소했습니다~암호화 검증 중 오류가 발생했습니다~초기화에 너무 오래 걸립니다~의심스러운 접근성 서비스가 감지되었습니다. 사용자가 생체 인증을 중단하기로 결정했습니다~앱 창이 전면에 없어서 생체 인증을 시작할 수 없습니다~생체 인식 API가 비활성화되었습니다~생체 인증을 시작할 수 없습니다~생체 인증 중 문제가 발생했습니다~인증 오류 (%1$d)~필수 암호화 객체가 거부되었습니다~필수 암호화 결과 없이 생체 인증이 완료되었습니다~activity가 삭제되어 생체 인증이 중단되었습니다~내부 생체 인식 오류가 발생했습니다~기기 카메라 차단 해제~Face Unlock을 사용하려면 설정 > 개인정보 보호에서 <b>카메라 액세스</b>를 켜세요~얼굴 작업이 취소되었습니다.~얼굴을 확인할 수 없습니다. 하드웨어를 사용할 수 없습니다.~이 기기에서는 Face Unlock을 지원하지 않습니다~시도 횟수가 너무 많습니다. 나중에 다시 시도하세요.~시도 횟수가 너무 많습니다. Face Unlock을 사용할 수 없습니다.~시도 횟수가 너무 많습니다. 대신 화면 잠금을 사용하세요.~새 얼굴 데이터를 저장할 수 없습니다. 먼저 이전 데이터를 삭제하세요.~Face Unlock이 설정되지 않았습니다~센서가 일시적으로 비활성화되었습니다.~Face Unlock을 다시 시도하세요~얼굴을 확인할 수 없습니다. 다시 시도하세요.~사용자가 Face Unlock을 취소했습니다~문제가 발생했습니다. 다시 시도하세요.~계속하려면 지문을 사용하세요~계속하려면 얼굴을 사용하세요~얼굴 모델을 만들 수 없습니다. 다시 시도하세요.~너무 밝습니다. 더 부드러운 조명을 사용해 보세요.~빛이 부족합니다~휴대전화를 더 멀리 이동하세요~휴대전화를 더 가까이 가져오세요~휴대전화를 더 높이 올리세요~휴대전화를 더 낮추세요~휴대전화를 왼쪽으로 이동하세요~휴대전화를 오른쪽으로 이동하세요~기기를 더 정면으로 바라보세요.~얼굴이 보이지 않습니다. 휴대전화를 눈높이에 맞추세요.~움직임이 너무 많습니다. 휴대전화를 안정적으로 유지하세요.~얼굴을 다시 등록하세요.~얼굴을 인식하지 못했습니다. 다시 시도하세요.~머리 위치를 약간 바꾸세요~휴대전화를 더 정면으로 바라보세요~휴대전화를 더 정면으로 바라보세요~휴대전화를 더 정면으로 바라보세요~얼굴을 가리는 것을 모두 치우세요.~검은 막대를 포함해 화면 상단을 닦아 주세요~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~얼굴 모델을 만들 수 없습니다. 다시 시도하세요.~짙은 안경이 감지되었습니다. 얼굴이 완전히 보여야 합니다.~얼굴 가림이 감지되었습니다. 얼굴이 완전히 보여야 합니다.~화면 잠금 사용~계속하려면 화면 잠금을 입력하세요~시도 횟수가 너무 많습니다. 나중에 다시 시도하세요.~인식되지 않음
values-hi~बायोमेट्रिक सेंसर अभी उपलब्ध नहीं हैं।\nअपनी पहचान की पुष्टि करने के लिए कृपया अपना PIN, पैटर्न या डिवाइस पासवर्ड इस्तेमाल करें।~%1$s\n\nचेतावनी: %2$s एक्सेसिबिलिटी सेवाओं में से एक की सुरक्षा सत्यापित नहीं कर सका। यह आपका PIN, पैटर्न या डिवाइस पासवर्ड रिकॉर्ड कर सकता है।\nकेवल तभी आगे बढ़ें जब आपको पूरी तरह यकीन हो कि आपकी कार्रवाई सुरक्षित है।~WideGamut ऑप्टिकल सेंसर को सही तरह से काम करने से रोकता है~आवश्यक अनुमतियाँ प्रदान नहीं की गईं~कैमरा सेंसर गोपनीयता स्विच द्वारा अवरुद्ध है~DeviceCredential फ़ॉलबैक के साथ क्रिप्टोग्राफी समर्थित नहीं है~क्रेडेंशियल्स मांगे गए, लेकिन उपयोगकर्ता ने सत्यापन रद्द कर दिया~क्रिप्टोग्राफ़िक सत्यापन के दौरान त्रुटि हुई~आरंभ करने में बहुत समय लग रहा है~संदिग्ध एक्सेसिबिलिटी सेवा का पता चला। उपयोगकर्ता ने बायोमेट्रिक सत्यापन रोकने का निर्णय लिया~बायोमेट्रिक प्रमाणीकरण शुरू नहीं किया जा सकता क्योंकि ऐप विंडो अग्रभूमि में नहीं है~बायोमेट्रिक API अक्षम है~बायोमेट्रिक प्रमाणीकरण शुरू नहीं किया जा सकता~बायोमेट्रिक प्रमाणीकरण के दौरान कुछ गलत हुआ~प्रमाणीकरण त्रुटि (%1$d)~आवश्यक क्रिप्टोग्राफ़िक ऑब्जेक्ट अस्वीकार कर दिया गया~आवश्यक क्रिप्टोग्राफ़िक परिणाम के बिना बायोमेट्रिक प्रमाणीकरण पूरा हुआ~activity नष्ट होने के कारण बायोमेट्रिक प्रमाणीकरण बाधित हो गया~आंतरिक बायोमेट्रिक त्रुटि हुई~डिवाइस कैमरा अनब्लॉक करें~Face Unlock का उपयोग करने के लिए सेटिंग्स > गोपनीयता में <b>कैमरा एक्सेस</b> चालू करें~चेहरे की प्रक्रिया रद्द की गई।~चेहरे की पुष्टि नहीं की जा सकती। हार्डवेयर उपलब्ध नहीं है।~इस डिवाइस पर Face Unlock समर्थित नहीं है~बहुत अधिक प्रयास। बाद में फिर से कोशिश करें।~बहुत अधिक प्रयास। Face Unlock उपलब्ध नहीं है।~बहुत अधिक प्रयास। इसके बजाय स्क्रीन लॉक का उपयोग करें।~नया चेहरा डेटा सहेजा नहीं जा सकता। पहले पुराना डेटा हटाएँ।~आपने Face Unlock सेट अप नहीं किया है~सेंसर अस्थायी रूप से अक्षम है।~Face Unlock फिर से आज़माएँ~चेहरे की पुष्टि नहीं की जा सकती। फिर से कोशिश करें।~Face Unlock उपयोगकर्ता द्वारा रद्द किया गया~कुछ गलत हुआ। फिर से कोशिश करें।~जारी रखने के लिए अपनी उंगली की छाप इस्तेमाल करें~जारी रखने के लिए अपना चेहरा इस्तेमाल करें~आपके चेहरे का मॉडल नहीं बनाया जा सकता। फिर से कोशिश करें।~बहुत तेज़ रोशनी है। थोड़ी नरम रोशनी आज़माएँ।~पर्याप्त रोशनी नहीं है~फ़ोन को थोड़ा दूर ले जाएँ~फ़ोन को थोड़ा पास लाएँ~फ़ोन को थोड़ा ऊपर उठाएँ~फ़ोन को थोड़ा नीचे करें~फ़ोन को बाईं ओर ले जाएँ~फ़ोन को दाईं ओर ले जाएँ~कृपया अपने डिवाइस की ओर अधिक सीधे देखें।~आपका चेहरा दिखाई नहीं दे रहा है। फ़ोन को आँखों के स्तर पर रखें।~बहुत ज़्यादा हरकत है। फ़ोन को स्थिर रखें।~कृपया अपना चेहरा फिर से पंजीकृत करें।~चेहरा पहचाना नहीं गया। फिर से कोशिश करें।~अपने सिर की स्थिति थोड़ी बदलें~अपने फ़ोन की ओर अधिक सीधे देखें~अपने फ़ोन की ओर अधिक सीधे देखें~अपने फ़ोन की ओर अधिक सीधे देखें~अपने चेहरे को ढकने वाली हर चीज़ हटा दें।~काली पट्टी सहित स्क्रीन के ऊपरी हिस्से को साफ करें~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~आपके चेहरे का मॉडल नहीं बनाया जा सकता। फिर से कोशिश करें।~गहरे रंग का चश्मा पाया गया। आपका चेहरा पूरी तरह दिखाई देना चाहिए।~चेहरा ढकने वाली चीज़ पाई गई। आपका चेहरा पूरी तरह दिखाई देना चाहिए।~स्क्रीन लॉक का उपयोग करें~जारी रखने के लिए अपना स्क्रीन लॉक दर्ज करें~बहुत अधिक प्रयास। कृपया बाद में फिर से कोशिश करें।~पहचाना नहीं गया
values-ar~أجهزة الاستشعار الحيوية غير متاحة الآن.\nيُرجى استخدام رقم PIN أو النمط أو كلمة مرور الجهاز لتأكيد هويتك.~%1$s\n\nتحذير: لم يتمكن %2$s من التحقق من أمان إحدى خدمات إمكانية الوصول. قد تسجل رقم PIN أو النمط أو كلمة مرور الجهاز.\nلا تتابع إلا إذا كنت متأكدًا تمامًا من أن إجراءاتك آمنة.~يمنع WideGamut المستشعر البصري من العمل بشكل صحيح~لم يتم منح الأذونات المطلوبة~تم حظر مستشعر الكاميرا بواسطة مفتاح الخصوصية~التشفير غير مدعوم مع الرجوع الاحتياطي إلى DeviceCredential~تم طلب بيانات الاعتماد، لكن المستخدم ألغى التحقق~حدث خطأ أثناء التحقق من التشفير~تستغرق عملية التهيئة وقتًا طويلًا جدًا~تم اكتشاف خدمة إمكانية وصول مشبوهة. قرر المستخدم إيقاف التحقق الحيوي~تعذر بدء المصادقة الحيوية لأن نافذة التطبيق ليست في المقدمة~واجهة برمجة التطبيقات الحيوية معطلة~تعذر بدء المصادقة الحيوية~حدث خطأ أثناء المصادقة الحيوية~خطأ في المصادقة (%1$d)~تم رفض الكائن التشفيري المطلوب~اكتملت المصادقة الحيوية بدون النتيجة التشفيرية المطلوبة~تمت مقاطعة المصادقة الحيوية لأن activity تم تدميرها~حدث خطأ حيوي داخلي~إلغاء حظر كاميرا الجهاز~لاستخدام Face Unlock، فعّل <b>الوصول إلى الكاميرا</b> في الإعدادات > الخصوصية~تم إلغاء عملية الوجه.~يتعذر التحقق من الوجه. الجهاز غير متاح.~Face Unlock غير مدعوم على هذا الجهاز~محاولات كثيرة جدًا. حاول مرة أخرى لاحقًا.~محاولات كثيرة جدًا. Face Unlock غير متاح.~محاولات كثيرة جدًا. استخدم قفل الشاشة بدلًا من ذلك.~لا يمكن حفظ بيانات وجه جديدة. احذف البيانات القديمة أولًا.~لم تقم بإعداد Face Unlock~تم تعطيل المستشعر مؤقتًا.~حاول Face Unlock مرة أخرى~يتعذر التحقق من الوجه. حاول مرة أخرى.~تم إلغاء Face Unlock بواسطة المستخدم~حدث خطأ ما. حاول مرة أخرى.~استخدم بصمة إصبعك للمتابعة~استخدم وجهك للمتابعة~يتعذر إنشاء نموذج وجهك. حاول مرة أخرى.~الإضاءة شديدة جدًا. جرّب إضاءة أكثر نعومة.~لا توجد إضاءة كافية~أبعد الهاتف أكثر~قرّب الهاتف أكثر~ارفع الهاتف أعلى~اخفض الهاتف أكثر~حرّك الهاتف إلى اليسار~حرّك الهاتف إلى اليمين~انظر إلى جهازك بشكل أكثر مباشرة.~لا يمكن رؤية وجهك. أمسك الهاتف عند مستوى العين.~هناك حركة كثيرة جدًا. أمسك الهاتف بثبات.~أعد تسجيل وجهك.~لم يتم التعرف على الوجه. حاول مرة أخرى.~غيّر وضع رأسك قليلًا~انظر إلى هاتفك بشكل أكثر مباشرة~انظر إلى هاتفك بشكل أكثر مباشرة~انظر إلى هاتفك بشكل أكثر مباشرة~أزل أي شيء يحجب وجهك.~نظّف الجزء العلوي من الشاشة، بما في ذلك الشريط الأسود~@string/biometriccompat_face_acquired_dark_glasses_detected_alt~@string/biometriccompat_face_acquired_mouth_covering_detected_alt~يتعذر إنشاء نموذج وجهك. حاول مرة أخرى.~تم اكتشاف نظارات داكنة. يجب أن يكون وجهك مرئيًا بالكامل.~تم اكتشاف غطاء للوجه. يجب أن يكون وجهك مرئيًا بالكامل.~استخدم قفل الشاشة~أدخل قفل الشاشة للمتابعة~محاولات كثيرة جدًا. يُرجى المحاولة مرة أخرى لاحقًا.~غير معروف
'@ -split '\r?\n'

Apply-Rows $appRows $appKeys 'app'
Apply-Rows $behaviorRows $behaviorKeys 'biometric-custom-behavior'
Apply-Rows $tfFaceRows $tfFaceKeys 'biometric-custom-face-tf'
Apply-Rows $zkFingerRows $zkFingerKeys 'biometric-zkfinger'
Apply-KeyValueRows $sharedBiometricRows 'biometric'
Apply-Rows $sharedBiometricLocaleRows $sharedBiometricKeys 'biometric'

$biometricResRoot = Join-Path (Get-Location).Path 'biometric\src\main\res'
Replace-InFile (Join-Path $biometricResRoot 'values-ru\strings.xml') 'потому что activity была уничтожена' 'поскольку активность была уничтожена'
Replace-InFile (Join-Path $biometricResRoot 'values-uk\strings.xml') 'activity було знищено' 'активність було знищено'
Replace-InFile (Join-Path $biometricResRoot 'values-pt-rBR\strings.xml') 'a activity foi destruída' 'a atividade foi destruída'
Replace-InFile (Join-Path $biometricResRoot 'values-id\strings.xml') 'activity dihancurkan' 'aktivitas dihancurkan'
Replace-InFile (Join-Path $biometricResRoot 'values-vi\strings.xml') 'слишком' 'quá'
Replace-InFile (Join-Path $biometricResRoot 'values-vi\strings.xml') 'vì activity đã bị hủy' 'vì màn hình hiện tại đã bị hủy'
Replace-InFile (Join-Path $biometricResRoot 'values-zh-rCN\strings.xml') '由于 activity 已销毁' '由于当前界面已销毁'
Replace-InFile (Join-Path $biometricResRoot 'values-zh-rTW\strings.xml') '由於 activity 已被銷毀' '由於目前畫面已被銷毀'
Replace-InFile (Join-Path $biometricResRoot 'values-ja\strings.xml') 'activity が破棄されたため' 'アクティビティが破棄されたため'
Replace-InFile (Join-Path $biometricResRoot 'values-ko\strings.xml') 'activity가 삭제되어' '액티비티가 종료되어'
Replace-InFile (Join-Path $biometricResRoot 'values-hi\strings.xml') 'activity नष्ट होने के कारण' 'ऐक्टिविटी नष्ट होने के कारण'
Replace-InFile (Join-Path $biometricResRoot 'values-ar\strings.xml') 'لأن activity تم تدميرها' 'لأنه تم تدمير الشاشة الحالية'
