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
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Build.VERSION
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.AppOpsManagerCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat.logError
import dev.skomlach.common.logging.LogCat.logException
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils

object PermissionUtils {
    private val appContext = AndroidContext.appContext
    private val appOpCache: MutableMap<String, Boolean> = HashMap()

    private var isAllowedOverlayPermissionFlag: Boolean? = null
    private var isAllowedUsageStatPermissionFlag: Boolean? = null

    /**
     * Checks all given permissions have been granted.
     *
     * @param grantResults results
     * @return returns true if all permissions have been granted.
     */
    fun verifyPermissions(vararg grantResults: Int): Boolean {
        if (grantResults.isEmpty()) {
            return false
        }
        for (result: Int in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun isEnabledDontKeepActivities(context: Context): Boolean {
        try {
            return Settings.System.getInt(
                context.contentResolver,
                Settings.System.ALWAYS_FINISH_ACTIVITIES,
                0
            ) != 0 || (VERSION.SDK_INT >= 17 && Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ALWAYS_FINISH_ACTIVITIES,
                0
            ) != 0)
        } catch (e: Throwable) {
            logException(e)
        }
        return false
    }

    /**
     * Returns true if the Activity or Fragment has access to all given permissions.
     *
     * @param permissions permission list
     * @return returns true if the Activity or Fragment has access to all given permissions.
     */
    fun hasSelfPermissions(permissions: List<String>): Boolean {
        return hasSelfPermissions(*permissions.toTypedArray())
    }

    fun hasSelfPermissions(vararg permissions: String): Boolean {
        for (permission: String in permissions) {
            if (Utils.isAtLeastT && listOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ).contains(permission)
            )
                continue
            if (!isPermissionExistsInTheSystem(permission)) {
                continue
            }
            if (!isPermissionGranted(permission)) {
                return false
            }
        }
        return true
    }

    private fun isPermissionExistsInTheSystem(permission: String): Boolean {
        try {
            val lstGroups = appContext.packageManager.getAllPermissionGroups(0)
            lstGroups.add(null) // ungrouped permissions
            for (pgi: PermissionGroupInfo? in lstGroups) {
                pgi?.name?.let {
                    val lstPermissions = appContext.packageManager.queryPermissionsByGroup(it, 0)
                    for (pi: PermissionInfo in lstPermissions) {
                        if ((pi.name == permission)) {
                            return true
                        }
                    }
                }
            }
        } catch (ex: Throwable) {
            logException(ex)
        }
        return false
    }

    /**
     * Determine context has access to the given permission.
     *
     *
     * This is a workaround for RuntimeException of Parcel#readException.
     * For more detail, check this issue https://github.com/hotchemi/PermissionsDispatcher/issues/107
     *
     * @param permission permission
     * @return returns true if context has access to the given permission, false otherwise.
     * @see .hasSelfPermissions
     */
    private fun isPermissionGranted(permission: String): Boolean {
        var granted: Boolean
        try {
            val permissionToOp: String =
                AppOpCompatConstants.getAppOpFromPermission(permission) ?: ""
            //below Android 6
            if (permissionToOp.isEmpty()) {
                // in case of normal permissions(e.g. INTERNET)
                granted = PermissionChecker.checkSelfPermission(
                    appContext,
                    permission
                ) == PermissionChecker.PERMISSION_GRANTED
                logError("PermissionUtils.isPermissionGranted - normal permission - $permission - $granted")
                return granted
            }
            val noteOp: Int = try {
                AppOpsManagerCompat.noteOpNoThrow(
                    appContext,
                    permissionToOp,
                    Process.myUid(),
                    appContext.packageName
                )
            } catch (ignored: Throwable) {
                if (VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) appOpPermissionsCheckMiui(
                    permissionToOp,
                    Process.myUid(),
                    appContext.packageName
                ) else AppOpsManagerCompat.MODE_IGNORED
            }
            var appOpAllowed = noteOp == AppOpsManagerCompat.MODE_ALLOWED
            val appOpIgnored = noteOp == AppOpsManagerCompat.MODE_IGNORED
            if (appOpIgnored && appOpCache.containsKey(permission)) {
                appOpCache[permission]?.let {
                    appOpAllowed = it
                }
            } else {
                appOpCache[permission] = appOpAllowed
            }
            return if (isAppOpPermission(permission)) {
                granted = appOpAllowed
                logError("PermissionUtils.isPermissionGranted - appOp permission - $permission - $permissionToOp - $granted")
                granted
            } else {
                granted = appOpAllowed && PermissionChecker.checkSelfPermission(
                    appContext,
                    permission
                ) == PermissionChecker.PERMISSION_GRANTED
                logError("PermissionUtils.isPermissionGranted - danger permission - $permission - $permissionToOp - $granted")
                granted
            }
        } catch (t: Throwable) {
            logException(t)
        }
        granted = PermissionChecker.checkSelfPermission(
            appContext,
            permission
        ) == PermissionChecker.PERMISSION_GRANTED
        logError("PermissionUtils.isPermissionGranted - normal permission - $permission - $granted")
        return granted
    }

    private fun isAppOpPermission(manifestPermission: String): Boolean {
        try {
            val info = appContext.packageManager.getPermissionInfo(
                manifestPermission,
                PackageManager.GET_META_DATA
            )
            logError("PermissionUtils.isAppOpPermission - $manifestPermission $info")
            //https://developer.android.com/reference/android/content/pm/PermissionInfo.html#getProtection()
            return if (VERSION.SDK_INT >= 28) {
                (info.protection and PermissionInfo.PROTECTION_FLAG_APPOP) != 0 || (info.protectionLevel and PermissionInfo.PROTECTION_FLAG_APPOP) != 0
            } else {
                VERSION.SDK_INT >= 21 && (info.protectionLevel and PermissionInfo.PROTECTION_FLAG_APPOP) != 0
            }
        } catch (e: Throwable) {
            logException(e)
        }
        return false
    }

    fun getPermissions(targetPermissionsKes: List<String?>): HashMap<String, String> {
        if (targetPermissionsKes.isEmpty()) {
            throw RuntimeException("Provide at least one permission string")
        }
        val permissionsList = HashMap<String, String>()
        try {
            val manifestPermissions = appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.GET_PERMISSIONS
            ).requestedPermissions
            for (manifestPermission: String in manifestPermissions) try {
                if (!targetPermissionsKes.contains(manifestPermission)) continue
                val info = appContext.packageManager.getPermissionInfo(
                    manifestPermission,
                    PackageManager.GET_META_DATA
                )
                logError("PermissionUtils.getPermissions: " + manifestPermission + " " + (if (VERSION.SDK_INT >= 28) info.protection else info.protectionLevel))
                //https://developer.android.com/reference/android/content/pm/PermissionInfo.html#getProtection()
                if (VERSION.SDK_INT >= 28) {
                    if (info.protection == PermissionInfo.PROTECTION_NORMAL || info.protectionLevel == PermissionInfo.PROTECTION_NORMAL) continue
                } else if (info.protectionLevel == PermissionInfo.PROTECTION_NORMAL) continue
                if (hasSelfPermissions(manifestPermission)) {
                    continue
                }
                val permName = info.loadLabel(appContext.packageManager).toString()
                permissionsList[manifestPermission] = permName
            } catch (ignored: Throwable) {
            }
        } catch (e: Throwable) {
            logException(e)
        }
        return permissionsList
    }

    //Notification permissions
    val isAllowedNotificationsPermission: Boolean
        get() = if (Utils.isAtLeastT)
            hasSelfPermissions("android.permission.POST_NOTIFICATIONS") && NotificationManagerCompat.from(
                appContext
            ).areNotificationsEnabled()
        else
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    //Notification channel permissions
    fun isAllowedNotificationsChannelPermission(channelId: String?): Boolean {
        if (VERSION.SDK_INT < 26) {
            return true
        }
        return try {
            val notificationManager = appContext.getSystemService(
                NotificationManager::class.java
            )
            val notificationChannel = notificationManager.getNotificationChannel(channelId)

            return if (VERSION.SDK_INT >= 28) {
                logError(
                    "PermissionUtils.NotificationGroup " + notificationChannel.group + ":" + notificationManager.getNotificationChannelGroup(
                        notificationChannel.group
                    )?.isBlocked + "; NotificationChannel " + channelId + ":" + notificationChannel.importance
                )
                notificationManager.getNotificationChannelGroup(notificationChannel.group)?.isBlocked == true || notificationChannel.importance != NotificationManager.IMPORTANCE_NONE
            } else {
                logError(
                    "PermissionUtils.NotificationChannel " + channelId + ":" + notificationChannel.importance
                )
                notificationChannel.importance != NotificationManager.IMPORTANCE_NONE
            }
        } catch (e: Throwable) {
            logException(e)
            false
        }
    }

    val isAllowedOverlayPermission: Boolean
        get() {
            if (VERSION.SDK_INT >= 19 && isAllowedOverlayPermissionFlag != null) {
                return isAllowedOverlayPermissionFlag == true || isOverlayGrantedUseCheckOp
            }
            try {
                return isOverlayGrantedUseCheckOp.also { isAllowedOverlayPermissionFlag = it }
            } finally {
                if (VERSION.SDK_INT >= 19) {
                    startWatchingByPermission(
                        Manifest.permission.SYSTEM_ALERT_WINDOW
                    ) {
                        isAllowedOverlayPermissionFlag = isOverlayGrantedUseCheckOp
                    }
                }
            }
        }
    private val isOverlayGrantedUseCheckOp: Boolean
        get() {
            return if (VERSION.SDK_INT >= 23) {
                hasSelfPermissions(Manifest.permission.SYSTEM_ALERT_WINDOW) || Settings.canDrawOverlays(
                    appContext
                )
            } else {
                hasSelfPermissions(Manifest.permission.SYSTEM_ALERT_WINDOW)
            }
        }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun startWatchingByPermission(permission: String, runnable: Runnable) {
        if (isAppOpPermission(permission)) {
            try {
                val permissionToOp = AppOpCompatConstants.getAppOpFromPermission(permission) ?: ""
                val appOpsManager =
                    appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val pkgName = appContext.packageName
                appOpsManager.startWatchingMode(
                    (permissionToOp),
                    pkgName
                ) { op, packageName ->
                    if (permissionToOp == op && pkgName == packageName) {
                        logError("PermissionUtils.onOpChanged - $op - $packageName")
                        //https://stackoverflow.com/a/40649631
                        ExecutorHelper.postDelayed(runnable, 250)
                    }
                }
            } catch (e: Throwable) {
                logException(e)
            }
        }
    }

    val isAllowedPermissionForUsageStat: Boolean
        get() {
            if (VERSION.SDK_INT >= 23 && isAllowedUsageStatPermissionFlag != null) {
                return isAllowedUsageStatPermissionFlag == true || isUsageStatGrantedUseCheckOp
            }
            try {
                return isUsageStatGrantedUseCheckOp.also { isAllowedUsageStatPermissionFlag = it }
            } finally {
                if (VERSION.SDK_INT >= 23) {
                    startWatchingByPermission(
                        Manifest.permission.PACKAGE_USAGE_STATS
                    ) { isAllowedUsageStatPermissionFlag = isUsageStatGrantedUseCheckOp }
                }
            }
        }
    private val isUsageStatGrantedUseCheckOp: Boolean
        get() {
            return if (VERSION.SDK_INT < 23) {
                hasSelfPermissions(Manifest.permission.GET_TASKS)
            } else {
                hasSelfPermissions(Manifest.permission.PACKAGE_USAGE_STATS)
            }
        }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun appOpPermissionsCheckMiui(opCode: String?, uid: Int, pkg: String): Int {
        try {
            val manager = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val clazz: Class<*> = AppOpsManager::class.java
            val dispatchMethod = clazz.getMethod(
                "noteOpNoThrow",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            return dispatchMethod.invoke(manager, *arrayOf<Any?>(opCode, uid, pkg)) as Int
        } catch (e: Exception) {
            if (e.cause is IllegalArgumentException) {
                return AppOpsManagerCompat.MODE_ALLOWED
            }
            logException(e)
        }
        return AppOpsManagerCompat.MODE_IGNORED
    }
}