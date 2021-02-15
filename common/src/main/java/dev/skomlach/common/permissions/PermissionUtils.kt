package dev.skomlach.common.permissions;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.AppOpsManagerCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.PermissionChecker;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.logging.LogCat;
import dev.skomlach.common.misc.ExecutorHelper;

public final class PermissionUtils {
    private static final Map<String, Boolean> appOpCache = new HashMap<>();
    public static PermissionUtils INSTANCE = new PermissionUtils();
    private static Boolean isAllowedOverlayPermission = null;
    private static Boolean isAllowedUsageStatPermission = null;

    PermissionUtils() {
    }

    /**
     * Checks all given permissions have been granted.
     *
     * @param grantResults results
     * @return returns true if all permissions have been granted.
     */
    public boolean verifyPermissions(int... grantResults) {
        if (grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public boolean isEnabledDontKeepActivities(Context context) {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.ALWAYS_FINISH_ACTIVITIES, 0) != 0;
        } catch (Throwable e) {
            LogCat.logException(e);
        }
        return false;
    }

    /**
     * Returns true if the Activity or Fragment has access to all given permissions.
     *
     * @param permissions permission list
     * @return returns true if the Activity or Fragment has access to all given permissions.
     */
    public boolean hasSelfPermissions(@NonNull List<String> permissions) {
        return hasSelfPermissions(permissions.toArray(new String[permissions.size()]));
    }

    public boolean hasSelfPermissions(@NonNull String... permissions) {
        for (String permission : permissions) {
            if (!isPermissionExistsInTheSystem(permission)) {
                continue;
            }

            if (!isPermissionGranted(permission)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPermissionExistsInTheSystem(String permission) {
        try {
            List<PermissionGroupInfo> lstGroups = AndroidContext.getAppContext().getPackageManager().getAllPermissionGroups(0);
            lstGroups.add(null); // ungrouped permissions
            for (PermissionGroupInfo pgi : lstGroups) {

                String name = pgi == null ? null : pgi.name;
                List<PermissionInfo> lstPermissions = AndroidContext.getAppContext().getPackageManager().queryPermissionsByGroup(name, 0);
                for (PermissionInfo pi : lstPermissions) {
                    if (pi.name.equals(permission)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ex) {
            LogCat.logException(ex);
        }
        return false;
    }

    /**
     * Determine context has access to the given permission.
     * <p>
     * This is a workaround for RuntimeException of Parcel#readException.
     * For more detail, check this issue https://github.com/hotchemi/PermissionsDispatcher/issues/107
     *
     * @param permission permission
     * @return returns true if context has access to the given permission, false otherwise.
     * @see #hasSelfPermissions(String...)
     */
    private boolean isPermissionGranted(String permission) {
        boolean granted;
        try {

            String permissionToOp = AppOpCompatConstants.getAppOpFromPermission(permission);
            //below Android 6
            if (TextUtils.isEmpty(permissionToOp)) {
                // in case of normal permissions(e.g. INTERNET)
                granted = PermissionChecker.checkSelfPermission(AndroidContext.getAppContext(), permission) == PermissionChecker.PERMISSION_GRANTED;
                LogCat.logError("PermissionUtils.isPermissionGranted - normal permission - " + permission + " - " + granted);
                return granted;
            }

            int noteOp;
            try {
                noteOp = AppOpsManagerCompat.noteOpNoThrow(AndroidContext.getAppContext(), permissionToOp, Process.myUid(), AndroidContext.getAppContext().getPackageName());
            } catch (Throwable ignored) {
                noteOp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                        appOpPermissionsCheckMiui(permissionToOp, Process.myUid(), AndroidContext.getAppContext().getPackageName()) : AppOpsManagerCompat.MODE_IGNORED;
            }

            boolean appOpAllowed = noteOp == AppOpsManagerCompat.MODE_ALLOWED;
            boolean appOpIgnored = noteOp == AppOpsManagerCompat.MODE_IGNORED;

            if (appOpIgnored && appOpCache.containsKey(permission)) {
                appOpAllowed = appOpCache.get(permission);
            } else {
                appOpCache.put(permission, appOpAllowed);
            }

            if (isAppOpPermission(permission)) {
                granted = appOpAllowed;
                LogCat.logError("PermissionUtils.isPermissionGranted - appOp permission - " + permission + " - " + permissionToOp + " - " + granted);
                return granted;
            } else {
                granted = appOpAllowed && PermissionChecker.checkSelfPermission(AndroidContext.getAppContext(), permission) == PermissionChecker.PERMISSION_GRANTED;
                LogCat.logError("PermissionUtils.isPermissionGranted - danger permission - " + permission + " - " + permissionToOp + " - " + granted);
                return granted;
            }
        } catch (Throwable t) {
            LogCat.logException(t);
        }

        granted = PermissionChecker.checkSelfPermission(AndroidContext.getAppContext(), permission) == PermissionChecker.PERMISSION_GRANTED;
        LogCat.logError("PermissionUtils.isPermissionGranted - normal permission - " + permission + " - " + granted);
        return granted;
    }

    private boolean isAppOpPermission(String manifestPermission) {
        try {
            PermissionInfo info = AndroidContext.getAppContext().getPackageManager().getPermissionInfo(manifestPermission, PackageManager.GET_META_DATA);

            LogCat.logError("PermissionUtils.isAppOpPermission - " + manifestPermission + " " + info);
            //https://developer.android.com/reference/android/content/pm/PermissionInfo.html#getProtection()
            if (Build.VERSION.SDK_INT >= 28) {
                return (info.getProtection() & PermissionInfo.PROTECTION_FLAG_APPOP) != 0 || (info.protectionLevel & PermissionInfo.PROTECTION_FLAG_APPOP) != 0;
            } else {
                return (info.protectionLevel & PermissionInfo.PROTECTION_FLAG_APPOP) != 0;
            }
        } catch (Throwable e) {
            LogCat.logException(e);
        }

        return false;
    }

    public HashMap<String, String> getPermissions(@NonNull final List<String> targetPermissionsKes) {
        if (targetPermissionsKes.isEmpty()) {
            throw new RuntimeException("Provide at least one permission string");
        }

        HashMap<String, String> permissionsList = new HashMap<>();

        try {
            String[] manifestPermissions = AndroidContext.getAppContext().getPackageManager().getPackageInfo(AndroidContext.getAppContext().getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;

            for (String manifestPermission : manifestPermissions)
                try {
                    if (!targetPermissionsKes.contains(manifestPermission))
                        continue;
                    PermissionInfo info = AndroidContext.getAppContext().getPackageManager().getPermissionInfo(manifestPermission, PackageManager.GET_META_DATA);
                    LogCat.logError("PermissionUtils.getPermissions: " + manifestPermission + " " + (Build.VERSION.SDK_INT >= 28 ? info.getProtection() : info.protectionLevel));
                    //https://developer.android.com/reference/android/content/pm/PermissionInfo.html#getProtection()
                    if (Build.VERSION.SDK_INT >= 28) {
                        if (info.getProtection() == PermissionInfo.PROTECTION_NORMAL || info.protectionLevel == PermissionInfo.PROTECTION_NORMAL)
                            continue;
                    } else if (info.protectionLevel == PermissionInfo.PROTECTION_NORMAL)
                        continue;

                    if (hasSelfPermissions(manifestPermission)) {
                        continue;
                    }
                    String permName = info.loadLabel(AndroidContext.getAppContext().getPackageManager()).toString();
                    permissionsList.put(manifestPermission, permName);
                } catch (Throwable ignored) {

                }
        } catch (Throwable e) {
            LogCat.logException(e);
        }

        return permissionsList;
    }

    //Notification permissions
    public boolean isAllowedNotificationsPermission() {
        return NotificationManagerCompat.from(AndroidContext.getAppContext()).areNotificationsEnabled();
    }

    //Notification channel permissions
    public boolean isAllowedNotificationsChannelPermission(String channelId) {
        if (Build.VERSION.SDK_INT < 26) {
            return true;
        }
        try {
            NotificationManager notificationManager = AndroidContext.getAppContext().getSystemService(NotificationManager.class);
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelId);
            LogCat.logError("PermissionUtils.NotificationChannel " + channelId + ":" + notificationChannel.getImportance());
            return notificationChannel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        } catch (Throwable e) {
            LogCat.logException(e);
            return false;
        }
    }

    public boolean isAllowedOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 19 && isAllowedOverlayPermission != null) {
            return isAllowedOverlayPermission || isOverlayGrantedUseCheckOp();
        }
        try {
            return isAllowedOverlayPermission = isOverlayGrantedUseCheckOp();
        } finally {
            if (Build.VERSION.SDK_INT >= 19) {
                startWatchingByPermission(Manifest.permission.SYSTEM_ALERT_WINDOW, new Runnable() {
                    @Override
                    public void run() {
                        isAllowedOverlayPermission = isOverlayGrantedUseCheckOp();
                    }
                });
            }
        }
    }

    private boolean isOverlayGrantedUseCheckOp() {
        if (Build.VERSION.SDK_INT >= 23) {
            return hasSelfPermissions(Manifest.permission.SYSTEM_ALERT_WINDOW) || Settings.canDrawOverlays(AndroidContext.getAppContext());
        } else {
            return hasSelfPermissions(Manifest.permission.SYSTEM_ALERT_WINDOW);
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private void startWatchingByPermission(String permission, Runnable runnable) {
        if (isAppOpPermission(permission)) {
            try {
                String permissionToOp = AppOpCompatConstants.getAppOpFromPermission(permission);

                AppOpsManager appOpsManager = (AppOpsManager)
                        AndroidContext.getAppContext().getSystemService(Context.APP_OPS_SERVICE);
                final String pkgName = AndroidContext.getAppContext().getPackageName();

                appOpsManager.startWatchingMode(permissionToOp,
                        pkgName, new AppOpsManager.OnOpChangedListener() {
                            @Override
                            public void onOpChanged(String op, String packageName) {
                                if (TextUtils.equals(permissionToOp, op) && TextUtils.equals(pkgName, packageName)) {
                                    LogCat.logError("PermissionUtils.onOpChanged - " + op + " - " + packageName);
                                    //https://stackoverflow.com/a/40649631
                                    ExecutorHelper.INSTANCE.getHandler().postDelayed(runnable, 250);
                                }
                            }
                        });
            } catch (Throwable e) {
                LogCat.logException(e);
            }
        }
    }

    public boolean isAllowedPermissionForUsageStat() {
        if (Build.VERSION.SDK_INT >= 23 && isAllowedUsageStatPermission != null) {
            return isAllowedUsageStatPermission || isUsageStatGrantedUseCheckOp();
        }
        try {
            return isAllowedUsageStatPermission = isUsageStatGrantedUseCheckOp();
        } finally {
            if (Build.VERSION.SDK_INT >= 23) {
                startWatchingByPermission(Manifest.permission.PACKAGE_USAGE_STATS, new Runnable() {
                    @Override
                    public void run() {
                        isAllowedUsageStatPermission = isUsageStatGrantedUseCheckOp();
                    }
                });
            }
        }
    }

    private boolean isUsageStatGrantedUseCheckOp() {
        if (Build.VERSION.SDK_INT < 23) {
            return hasSelfPermissions(Manifest.permission.GET_TASKS);
        } else {
            return hasSelfPermissions(Manifest.permission.PACKAGE_USAGE_STATS);
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private int appOpPermissionsCheckMiui(String opCode, int uid, String pkg) {
        try {
            AppOpsManager manager = (AppOpsManager) AndroidContext.getAppContext().getSystemService(Context.APP_OPS_SERVICE);
            Class<?> clazz = AppOpsManager.class;
            Method dispatchMethod = clazz.getMethod("noteOpNoThrow", String.class, int.class, String.class);
            return (Integer) dispatchMethod.invoke(manager, new Object[]{opCode, uid, pkg});
        } catch (Exception e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                return AppOpsManagerCompat.MODE_ALLOWED;
            }
            LogCat.logException(e);
        }
        return AppOpsManagerCompat.MODE_IGNORED;
    }
}