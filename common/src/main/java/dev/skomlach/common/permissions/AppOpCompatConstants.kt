/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.common.permissions

import android.Manifest
import android.os.Build.VERSION
import android.text.TextUtils
import androidx.core.app.AppOpsManagerCompat
import java.util.*

object AppOpCompatConstants {
    /**
     * Access to coarse location information.
     */
    val OPSTR_COARSE_LOCATION: String = "android:coarse_location"

    /**
     * Access to fine location information.
     */
    val OPSTR_FINE_LOCATION: String = "android:fine_location"

    /**
     * Continually monitoring location data.
     */
    val OPSTR_MONITOR_LOCATION: String = "android:monitor_location"

    /**
     * Continually monitoring location data with a relatively high power request.
     */
    val OPSTR_MONITOR_HIGH_POWER_LOCATION: String = "android:monitor_location_high_power"

    /**
     * Access to [android.app.usage.UsageStatsManager].
     */
    val OPSTR_GET_USAGE_STATS: String = "android:get_usage_stats"

    /**
     * Activate a VPN connection without user intervention.
     */
    val OPSTR_ACTIVATE_VPN: String = "android:activate_vpn"

    /**
     * Allows an application to read the user's contacts data.
     */
    val OPSTR_READ_CONTACTS: String = "android:read_contacts"

    /**
     * Allows an application to write to the user's contacts data.
     */
    val OPSTR_WRITE_CONTACTS: String = "android:write_contacts"

    /**
     * Allows an application to read the user's call log.
     */
    val OPSTR_READ_CALL_LOG: String = "android:read_call_log"

    /**
     * Allows an application to write to the user's call log.
     */
    val OPSTR_WRITE_CALL_LOG: String = "android:write_call_log"

    /**
     * Allows an application to read the user's calendar data.
     */
    val OPSTR_READ_CALENDAR: String = "android:read_calendar"

    /**
     * Allows an application to write to the user's calendar data.
     */
    val OPSTR_WRITE_CALENDAR: String = "android:write_calendar"

    /**
     * Allows an application to initiate a phone call.
     */
    val OPSTR_CALL_PHONE: String = "android:call_phone"

    /**
     * Allows an application to read SMS messages.
     */
    val OPSTR_READ_SMS: String = "android:read_sms"

    /**
     * Allows an application to receive SMS messages.
     */
    val OPSTR_RECEIVE_SMS: String = "android:receive_sms"

    /**
     * Allows an application to receive MMS messages.
     */
    val OPSTR_RECEIVE_MMS: String = "android:receive_mms"

    /**
     * Allows an application to receive WAP push messages.
     */
    val OPSTR_RECEIVE_WAP_PUSH: String = "android:receive_wap_push"

    /**
     * Allows an application to send SMS messages.
     */
    val OPSTR_SEND_SMS: String = "android:send_sms"

    /**
     * Required to be able to access the camera device.
     */
    val OPSTR_CAMERA: String = "android:camera"

    /**
     * Required to be able to access the microphone device.
     */
    val OPSTR_RECORD_AUDIO: String = "android:record_audio"

    /**
     * Required to access phone state related information.
     */
    val OPSTR_READ_PHONE_STATE: String = "android:read_phone_state"

    /**
     * Required to access phone state related information.
     */
    val OPSTR_ADD_VOICEMAIL: String = "android:add_voicemail"

    /**
     * Access APIs for SIP calling over VOIP or WiFi
     */
    val OPSTR_USE_SIP: String = "android:use_sip"

    /**
     * Access APIs for diverting outgoing calls
     */
    val OPSTR_PROCESS_OUTGOING_CALLS: String = "android:process_outgoing_calls"

    /**
     * Use the fingerprint API.
     */
    val OPSTR_USE_FINGERPRINT: String = "android:use_fingerprint"

    /**
     * Access to body sensors such as heart rate, etc.
     */
    val OPSTR_BODY_SENSORS: String = "android:body_sensors"

    /**
     * Read previously received cell broadcast messages.
     */
    val OPSTR_READ_CELL_BROADCASTS: String = "android:read_cell_broadcasts"

    /**
     * Inject mock location into the system.
     */
    val OPSTR_MOCK_LOCATION: String = "android:mock_location"

    /**
     * Read external storage.
     */
    val OPSTR_READ_EXTERNAL_STORAGE: String = "android:read_external_storage"

    /**
     * Write external storage.
     */
    val OPSTR_WRITE_EXTERNAL_STORAGE: String = "android:write_external_storage"

    /**
     * Required to draw on top of other apps.
     */
    val OPSTR_SYSTEM_ALERT_WINDOW: String = "android:system_alert_window"

    /**
     * Required to write/modify/update system settingss.
     */
    val OPSTR_WRITE_SETTINGS: String = "android:write_settings"

    /**
     * Get device accounts.
     */
    val OPSTR_GET_ACCOUNTS: String = "android:get_accounts"
    val OPSTR_READ_PHONE_NUMBERS: String = "android:read_phone_numbers"

    /**
     * Access to picture-in-picture.
     */
    val OPSTR_PICTURE_IN_PICTURE: String = "android:picture_in_picture"
    val OPSTR_INSTANT_APP_START_FOREGROUND: String = "android:instant_app_start_foreground"

    /**
     * Answer incoming phone calls
     */
    val OPSTR_ANSWER_PHONE_CALLS: String = "android:answer_phone_calls"

    /**
     * Accept call handover
     */
    val OPSTR_ACCEPT_HANDOVER: String = "android:accept_handover"
    val OPSTR_GPS: String = "android:gps"
    val OPSTR_VIBRATE: String = "android:vibrate"
    val OPSTR_WIFI_SCAN: String = "android:wifi_scan"
    val OPSTR_POST_NOTIFICATION: String = "android:post_notification"
    val OPSTR_NEIGHBORING_CELLS: String = "android:neighboring_cells"
    val OPSTR_WRITE_SMS: String = "android:write_sms"
    val OPSTR_RECEIVE_EMERGENCY_BROADCAST: String = "android:receive_emergency_broadcast"
    val OPSTR_READ_ICC_SMS: String = "android:read_icc_sms"
    val OPSTR_WRITE_ICC_SMS: String = "android:write_icc_sms"
    val OPSTR_ACCESS_NOTIFICATIONS: String = "android:access_notifications"
    val OPSTR_PLAY_AUDIO: String = "android:play_audio"
    val OPSTR_READ_CLIPBOARD: String = "android:read_clipboard"
    val OPSTR_WRITE_CLIPBOARD: String = "android:write_clipboard"
    val OPSTR_TAKE_MEDIA_BUTTONS: String = "android:take_media_buttons"
    val OPSTR_TAKE_AUDIO_FOCUS: String = "android:take_audio_focus"
    val OPSTR_AUDIO_MASTER_VOLUME: String = "android:audio_master_volume"
    val OPSTR_AUDIO_VOICE_VOLUME: String = "android:audio_voice_volume"
    val OPSTR_AUDIO_RING_VOLUME: String = "android:audio_ring_volume"
    val OPSTR_AUDIO_MEDIA_VOLUME: String = "android:audio_media_volume"
    val OPSTR_AUDIO_ALARM_VOLUME: String = "android:audio_alarm_volume"
    val OPSTR_AUDIO_NOTIFICATION_VOLUME: String = "android:audio_notification_volume"
    val OPSTR_AUDIO_BLUETOOTH_VOLUME: String = "android:audio_bluetooth_volume"
    val OPSTR_WAKE_LOCK: String = "android:wake_lock"
    val OPSTR_MUTE_MICROPHONE: String = "android:mute_microphone"
    val OPSTR_TOAST_WINDOW: String = "android:toast_window"
    val OPSTR_PROJECT_MEDIA: String = "android:project_media"
    val OPSTR_WRITE_WALLPAPER: String = "android:write_wallpaper"
    val OPSTR_ASSIST_STRUCTURE: String = "android:assist_structure"
    val OPSTR_ASSIST_SCREENSHOT: String = "android:assist_screenshot"
    val OPSTR_TURN_SCREEN_ON: String = "android:turn_screen_on"
    val OPSTR_RUN_IN_BACKGROUND: String = "android:run_in_background"
    val OPSTR_AUDIO_ACCESSIBILITY_VOLUME: String = "android:audio_accessibility_volume"
    val OPSTR_REQUEST_INSTALL_PACKAGES: String = "android:request_install_packages"
    val OPSTR_RUN_ANY_IN_BACKGROUND: String = "android:run_any_in_background"
    val OPSTR_CHANGE_WIFI_STATE: String = "android:change_wifi_state"
    val OPSTR_REQUEST_DELETE_PACKAGES: String = "android:request_delete_packages"
    val OPSTR_BIND_ACCESSIBILITY_SERVICE: String = "android:bind_accessibility_service"
    val OPSTR_MANAGE_IPSEC_TUNNELS: String = "android:manage_ipsec_tunnels"
    val OPSTR_START_FOREGROUND: String = "android:start_foreground"
    val OPSTR_BLUETOOTH_SCAN: String = "android:bluetooth_scan"

    /**
     * This maps each operation to the public string constant for it.
     */
    private val sOpToString: Array<String> = arrayOf(
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
        OPSTR_BLUETOOTH_SCAN
    )
    private val sOpPerms: Array<String?> = arrayOf(
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
        null,  // no permission required for notifications
        null,  // neighboring cells shares the coarse location perm
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_SMS,
        null,  // no permission required for writing sms
        Manifest.permission.RECEIVE_SMS,
        null,  //android.Manifest.permission.RECEIVE_EMERGENCY_BROADCAST,
        Manifest.permission.RECEIVE_MMS,
        Manifest.permission.RECEIVE_WAP_PUSH,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        null,  // no permission required for writing icc sms
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        null,  //android.Manifest.permission.ACCESS_NOTIFICATIONS,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        null,  // no permission for playing audio
        null,  // no permission for reading clipboard
        null,  // no permission for writing clipboard
        null,  // no permission for taking media buttons
        null,  // no permission for taking audio focus
        null,  // no permission for changing master volume
        null,  // no permission for changing voice volume
        null,  // no permission for changing ring volume
        null,  // no permission for changing media volume
        null,  // no permission for changing alarm volume
        null,  // no permission for changing notification volume
        null,  // no permission for changing bluetooth volume
        Manifest.permission.WAKE_LOCK,
        null,  // no permission for generic location monitoring
        null,  // no permission for high power location monitoring
        Manifest.permission.PACKAGE_USAGE_STATS,
        null,  // no permission for muting/unmuting microphone
        null,  // no permission for displaying toasts
        null,  // no permission for projecting media
        null,  // no permission for activating vpn
        null,  // no permission for supporting wallpaper
        null,  // no permission for receiving assist structure
        null,  // no permission for receiving assist screenshot
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ADD_VOICEMAIL,
        Manifest.permission.USE_SIP,
        Manifest.permission.PROCESS_OUTGOING_CALLS,
        Manifest.permission.USE_FINGERPRINT,
        Manifest.permission.BODY_SENSORS,
        null,  //Manifest.permission.READ_CELL_BROADCASTS,
        null,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        null,  // no permission for turning the screen on
        Manifest.permission.GET_ACCOUNTS,
        null,  // no permission for running in background
        null,  // no permission for changing accessibility volume
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        null,  // no permission for entering picture-in-picture on hide
        Manifest.permission.INSTANT_APP_FOREGROUND_SERVICE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        null,  // no permission for OP_RUN_ANY_IN_BACKGROUND
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.REQUEST_DELETE_PACKAGES,
        Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
        Manifest.permission.ACCEPT_HANDOVER,
        null,  // no permission for OP_MANAGE_IPSEC_TUNNELS
        Manifest.permission.FOREGROUND_SERVICE,
        null
    )
    private val permissionToApOps: HashMap<String, String> = HashMap()

    init {
        for (i in sOpPerms.indices) {
            sOpPerms[i]?.let {
                permissionToApOps[it] = sOpToString[i]
            }
        }
    }

    fun getAppOpFromPermission(permission: String): String? {
        val result: String? = AppOpsManagerCompat.permissionToOp(permission)
        return if (!TextUtils.isEmpty(result)) {
            result
        } else if (VERSION.SDK_INT >= 23) {
            permissionToApOps[permission]
        } else {
            null
        }
    }
}