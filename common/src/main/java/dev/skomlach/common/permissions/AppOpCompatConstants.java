package dev.skomlach.common.permissions;

import android.Manifest;
import android.text.TextUtils;

import androidx.core.app.AppOpsManagerCompat;

import java.util.HashMap;

import static android.os.Build.VERSION.SDK_INT;

public class AppOpCompatConstants {
    /**
     * Access to coarse location information.
     */
    public static final String OPSTR_COARSE_LOCATION = "android:coarse_location";
    /**
     * Access to fine location information.
     */
    public static final String OPSTR_FINE_LOCATION =
            "android:fine_location";
    /**
     * Continually monitoring location data.
     */
    public static final String OPSTR_MONITOR_LOCATION
            = "android:monitor_location";
    /**
     * Continually monitoring location data with a relatively high power request.
     */
    public static final String OPSTR_MONITOR_HIGH_POWER_LOCATION
            = "android:monitor_location_high_power";
    /**
     * Access to {@link android.app.usage.UsageStatsManager}.
     */
    public static final String OPSTR_GET_USAGE_STATS
            = "android:get_usage_stats";
    /**
     * Activate a VPN connection without user intervention.
     */

    public static final String OPSTR_ACTIVATE_VPN
            = "android:activate_vpn";
    /**
     * Allows an application to read the user's contacts data.
     */
    public static final String OPSTR_READ_CONTACTS
            = "android:read_contacts";
    /**
     * Allows an application to write to the user's contacts data.
     */
    public static final String OPSTR_WRITE_CONTACTS
            = "android:write_contacts";
    /**
     * Allows an application to read the user's call log.
     */
    public static final String OPSTR_READ_CALL_LOG
            = "android:read_call_log";
    /**
     * Allows an application to write to the user's call log.
     */
    public static final String OPSTR_WRITE_CALL_LOG
            = "android:write_call_log";
    /**
     * Allows an application to read the user's calendar data.
     */
    public static final String OPSTR_READ_CALENDAR
            = "android:read_calendar";
    /**
     * Allows an application to write to the user's calendar data.
     */
    public static final String OPSTR_WRITE_CALENDAR
            = "android:write_calendar";
    /**
     * Allows an application to initiate a phone call.
     */
    public static final String OPSTR_CALL_PHONE
            = "android:call_phone";
    /**
     * Allows an application to read SMS messages.
     */
    public static final String OPSTR_READ_SMS
            = "android:read_sms";
    /**
     * Allows an application to receive SMS messages.
     */
    public static final String OPSTR_RECEIVE_SMS
            = "android:receive_sms";
    /**
     * Allows an application to receive MMS messages.
     */
    public static final String OPSTR_RECEIVE_MMS
            = "android:receive_mms";
    /**
     * Allows an application to receive WAP push messages.
     */
    public static final String OPSTR_RECEIVE_WAP_PUSH
            = "android:receive_wap_push";
    /**
     * Allows an application to send SMS messages.
     */
    public static final String OPSTR_SEND_SMS
            = "android:send_sms";
    /**
     * Required to be able to access the camera device.
     */
    public static final String OPSTR_CAMERA
            = "android:camera";
    /**
     * Required to be able to access the microphone device.
     */
    public static final String OPSTR_RECORD_AUDIO
            = "android:record_audio";
    /**
     * Required to access phone state related information.
     */
    public static final String OPSTR_READ_PHONE_STATE
            = "android:read_phone_state";
    /**
     * Required to access phone state related information.
     */
    public static final String OPSTR_ADD_VOICEMAIL
            = "android:add_voicemail";
    /**
     * Access APIs for SIP calling over VOIP or WiFi
     */
    public static final String OPSTR_USE_SIP
            = "android:use_sip";
    /**
     * Access APIs for diverting outgoing calls
     */
    public static final String OPSTR_PROCESS_OUTGOING_CALLS
            = "android:process_outgoing_calls";
    /**
     * Use the fingerprint API.
     */
    public static final String OPSTR_USE_FINGERPRINT
            = "android:use_fingerprint";
    /**
     * Access to body sensors such as heart rate, etc.
     */
    public static final String OPSTR_BODY_SENSORS
            = "android:body_sensors";
    /**
     * Read previously received cell broadcast messages.
     */
    public static final String OPSTR_READ_CELL_BROADCASTS
            = "android:read_cell_broadcasts";
    /**
     * Inject mock location into the system.
     */
    public static final String OPSTR_MOCK_LOCATION
            = "android:mock_location";
    /**
     * Read external storage.
     */
    public static final String OPSTR_READ_EXTERNAL_STORAGE
            = "android:read_external_storage";
    /**
     * Write external storage.
     */
    public static final String OPSTR_WRITE_EXTERNAL_STORAGE
            = "android:write_external_storage";
    /**
     * Required to draw on top of other apps.
     */
    public static final String OPSTR_SYSTEM_ALERT_WINDOW
            = "android:system_alert_window";
    /**
     * Required to write/modify/update system settingss.
     */
    public static final String OPSTR_WRITE_SETTINGS
            = "android:write_settings";
    /**
     * Get device accounts.
     */

    public static final String OPSTR_GET_ACCOUNTS
            = "android:get_accounts";
    public static final String OPSTR_READ_PHONE_NUMBERS
            = "android:read_phone_numbers";
    /**
     * Access to picture-in-picture.
     */
    public static final String OPSTR_PICTURE_IN_PICTURE
            = "android:picture_in_picture";

    public static final String OPSTR_INSTANT_APP_START_FOREGROUND
            = "android:instant_app_start_foreground";
    /**
     * Answer incoming phone calls
     */
    public static final String OPSTR_ANSWER_PHONE_CALLS
            = "android:answer_phone_calls";
    /**
     * Accept call handover
     */

    public static final String OPSTR_ACCEPT_HANDOVER
            = "android:accept_handover";

    public static final String OPSTR_GPS = "android:gps";

    public static final String OPSTR_VIBRATE = "android:vibrate";

    public static final String OPSTR_WIFI_SCAN = "android:wifi_scan";

    public static final String OPSTR_POST_NOTIFICATION = "android:post_notification";

    public static final String OPSTR_NEIGHBORING_CELLS = "android:neighboring_cells";

    public static final String OPSTR_WRITE_SMS = "android:write_sms";

    public static final String OPSTR_RECEIVE_EMERGENCY_BROADCAST =
            "android:receive_emergency_broadcast";

    public static final String OPSTR_READ_ICC_SMS = "android:read_icc_sms";

    public static final String OPSTR_WRITE_ICC_SMS = "android:write_icc_sms";

    public static final String OPSTR_ACCESS_NOTIFICATIONS = "android:access_notifications";

    public static final String OPSTR_PLAY_AUDIO = "android:play_audio";

    public static final String OPSTR_READ_CLIPBOARD = "android:read_clipboard";

    public static final String OPSTR_WRITE_CLIPBOARD = "android:write_clipboard";

    public static final String OPSTR_TAKE_MEDIA_BUTTONS = "android:take_media_buttons";

    public static final String OPSTR_TAKE_AUDIO_FOCUS = "android:take_audio_focus";

    public static final String OPSTR_AUDIO_MASTER_VOLUME = "android:audio_master_volume";

    public static final String OPSTR_AUDIO_VOICE_VOLUME = "android:audio_voice_volume";

    public static final String OPSTR_AUDIO_RING_VOLUME = "android:audio_ring_volume";

    public static final String OPSTR_AUDIO_MEDIA_VOLUME = "android:audio_media_volume";

    public static final String OPSTR_AUDIO_ALARM_VOLUME = "android:audio_alarm_volume";

    public static final String OPSTR_AUDIO_NOTIFICATION_VOLUME =
            "android:audio_notification_volume";

    public static final String OPSTR_AUDIO_BLUETOOTH_VOLUME = "android:audio_bluetooth_volume";

    public static final String OPSTR_WAKE_LOCK = "android:wake_lock";

    public static final String OPSTR_MUTE_MICROPHONE = "android:mute_microphone";

    public static final String OPSTR_TOAST_WINDOW = "android:toast_window";

    public static final String OPSTR_PROJECT_MEDIA = "android:project_media";

    public static final String OPSTR_WRITE_WALLPAPER = "android:write_wallpaper";

    public static final String OPSTR_ASSIST_STRUCTURE = "android:assist_structure";

    public static final String OPSTR_ASSIST_SCREENSHOT = "android:assist_screenshot";

    public static final String OPSTR_TURN_SCREEN_ON = "android:turn_screen_on";

    public static final String OPSTR_RUN_IN_BACKGROUND = "android:run_in_background";

    public static final String OPSTR_AUDIO_ACCESSIBILITY_VOLUME =
            "android:audio_accessibility_volume";

    public static final String OPSTR_REQUEST_INSTALL_PACKAGES = "android:request_install_packages";

    public static final String OPSTR_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background";

    public static final String OPSTR_CHANGE_WIFI_STATE = "android:change_wifi_state";

    public static final String OPSTR_REQUEST_DELETE_PACKAGES = "android:request_delete_packages";

    public static final String OPSTR_BIND_ACCESSIBILITY_SERVICE =
            "android:bind_accessibility_service";

    public static final String OPSTR_MANAGE_IPSEC_TUNNELS = "android:manage_ipsec_tunnels";

    public static final String OPSTR_START_FOREGROUND = "android:start_foreground";

    public static final String OPSTR_BLUETOOTH_SCAN = "android:bluetooth_scan";
    /**
     * This maps each operation to the public string constant for it.
     */
    private static final String[] sOpToString = new String[]{
            OPSTR_COARSE_LOCATION,
            OPSTR_FINE_LOCATION,
            OPSTR_GPS,
            OPSTR_VIBRATE,
            OPSTR_READ_CONTACTS,
            OPSTR_WRITE_CONTACTS,
            OPSTR_READ_CALL_LOG,
            OPSTR_WRITE_CALL_LOG,
            OPSTR_READ_CALENDAR,
            OPSTR_WRITE_CALENDAR,
            OPSTR_WIFI_SCAN,
            OPSTR_POST_NOTIFICATION,
            OPSTR_NEIGHBORING_CELLS,
            OPSTR_CALL_PHONE,
            OPSTR_READ_SMS,
            OPSTR_WRITE_SMS,
            OPSTR_RECEIVE_SMS,
            OPSTR_RECEIVE_EMERGENCY_BROADCAST,
            OPSTR_RECEIVE_MMS,
            OPSTR_RECEIVE_WAP_PUSH,
            OPSTR_SEND_SMS,
            OPSTR_READ_ICC_SMS,
            OPSTR_WRITE_ICC_SMS,
            OPSTR_WRITE_SETTINGS,
            OPSTR_SYSTEM_ALERT_WINDOW,
            OPSTR_ACCESS_NOTIFICATIONS,
            OPSTR_CAMERA,
            OPSTR_RECORD_AUDIO,
            OPSTR_PLAY_AUDIO,
            OPSTR_READ_CLIPBOARD,
            OPSTR_WRITE_CLIPBOARD,
            OPSTR_TAKE_MEDIA_BUTTONS,
            OPSTR_TAKE_AUDIO_FOCUS,
            OPSTR_AUDIO_MASTER_VOLUME,
            OPSTR_AUDIO_VOICE_VOLUME,
            OPSTR_AUDIO_RING_VOLUME,
            OPSTR_AUDIO_MEDIA_VOLUME,
            OPSTR_AUDIO_ALARM_VOLUME,
            OPSTR_AUDIO_NOTIFICATION_VOLUME,
            OPSTR_AUDIO_BLUETOOTH_VOLUME,
            OPSTR_WAKE_LOCK,
            OPSTR_MONITOR_LOCATION,
            OPSTR_MONITOR_HIGH_POWER_LOCATION,
            OPSTR_GET_USAGE_STATS,
            OPSTR_MUTE_MICROPHONE,
            OPSTR_TOAST_WINDOW,
            OPSTR_PROJECT_MEDIA,
            OPSTR_ACTIVATE_VPN,
            OPSTR_WRITE_WALLPAPER,
            OPSTR_ASSIST_STRUCTURE,
            OPSTR_ASSIST_SCREENSHOT,
            OPSTR_READ_PHONE_STATE,
            OPSTR_ADD_VOICEMAIL,
            OPSTR_USE_SIP,
            OPSTR_PROCESS_OUTGOING_CALLS,
            OPSTR_USE_FINGERPRINT,
            OPSTR_BODY_SENSORS,
            OPSTR_READ_CELL_BROADCASTS,
            OPSTR_MOCK_LOCATION,
            OPSTR_READ_EXTERNAL_STORAGE,
            OPSTR_WRITE_EXTERNAL_STORAGE,
            OPSTR_TURN_SCREEN_ON,
            OPSTR_GET_ACCOUNTS,
            OPSTR_RUN_IN_BACKGROUND,
            OPSTR_AUDIO_ACCESSIBILITY_VOLUME,
            OPSTR_READ_PHONE_NUMBERS,
            OPSTR_REQUEST_INSTALL_PACKAGES,
            OPSTR_PICTURE_IN_PICTURE,
            OPSTR_INSTANT_APP_START_FOREGROUND,
            OPSTR_ANSWER_PHONE_CALLS,
            OPSTR_RUN_ANY_IN_BACKGROUND,
            OPSTR_CHANGE_WIFI_STATE,
            OPSTR_REQUEST_DELETE_PACKAGES,
            OPSTR_BIND_ACCESSIBILITY_SERVICE,
            OPSTR_ACCEPT_HANDOVER,
            OPSTR_MANAGE_IPSEC_TUNNELS,
            OPSTR_START_FOREGROUND,
            OPSTR_BLUETOOTH_SCAN,
    };

    private static final String[] sOpPerms = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            null,
            Manifest.permission.VIBRATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.ACCESS_WIFI_STATE,
            null, // no permission required for notifications
            null, // neighboring cells shares the coarse location perm
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_SMS,
            null, // no permission required for writing sms
            Manifest.permission.RECEIVE_SMS,
            null,//android.Manifest.permission.RECEIVE_EMERGENCY_BROADCAST,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            null, // no permission required for writing icc sms
            Manifest.permission.WRITE_SETTINGS,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            null,//android.Manifest.permission.ACCESS_NOTIFICATIONS,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            null, // no permission for playing audio
            null, // no permission for reading clipboard
            null, // no permission for writing clipboard
            null, // no permission for taking media buttons
            null, // no permission for taking audio focus
            null, // no permission for changing master volume
            null, // no permission for changing voice volume
            null, // no permission for changing ring volume
            null, // no permission for changing media volume
            null, // no permission for changing alarm volume
            null, // no permission for changing notification volume
            null, // no permission for changing bluetooth volume
            Manifest.permission.WAKE_LOCK,
            null, // no permission for generic location monitoring
            null, // no permission for high power location monitoring
            Manifest.permission.PACKAGE_USAGE_STATS,
            null, // no permission for muting/unmuting microphone
            null, // no permission for displaying toasts
            null, // no permission for projecting media
            null, // no permission for activating vpn
            null, // no permission for supporting wallpaper
            null, // no permission for receiving assist structure
            null, // no permission for receiving assist screenshot
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ADD_VOICEMAIL,
            Manifest.permission.USE_SIP,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.USE_FINGERPRINT,
            Manifest.permission.BODY_SENSORS,
            null,//Manifest.permission.READ_CELL_BROADCASTS,
            null,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            null, // no permission for turning the screen on
            Manifest.permission.GET_ACCOUNTS,
            null, // no permission for running in background
            null, // no permission for changing accessibility volume
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            null, // no permission for entering picture-in-picture on hide
            Manifest.permission.INSTANT_APP_FOREGROUND_SERVICE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            null, // no permission for OP_RUN_ANY_IN_BACKGROUND
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.REQUEST_DELETE_PACKAGES,
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            Manifest.permission.ACCEPT_HANDOVER,
            null, // no permission for OP_MANAGE_IPSEC_TUNNELS
            Manifest.permission.FOREGROUND_SERVICE,
            null, // no permission for OP_BLUETOOTH_SCAN
    };

    private static final HashMap<String, String> permissionToApOps = new HashMap<>();

    static {
        for (int i = 0; i < sOpPerms.length; i++) {
            String perm = sOpPerms[i];
            if (perm == null)
                continue;

            permissionToApOps.put(perm, sOpToString[i]);
        }
    }

    protected static String getAppOpFromPermission(String permission) {
        String result = AppOpsManagerCompat.permissionToOp(permission);
        if (!TextUtils.isEmpty(result)) {
            return result;
        } else if (SDK_INT >= 23) {
            return permissionToApOps.get(permission);
        } else {
            return null;
        }
    }
}
