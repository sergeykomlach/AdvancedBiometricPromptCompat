/**Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 * Licensed under the Apache License, Version 2.0 (the "License");
 * http://www.apache.org/licenses/LICENSE-2.0
 * @author s.komlach
 * @date 2021/11/3
 */

package dev.skomlach.common.storage

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat

fun SharedPreferences.Editor.applyOrCommit(){
    //Due to enabled DeviceEncryption, using apply() sometimes do not save data correctly
    //Probably cause encryption takes too long time
    //Example: OnePlus 8T + Android 11
    if(isDeviceEncryptionEnabled())
        this.commit()
    else
        this.apply()
}

private fun isDeviceEncryptionEnabled(): Boolean{
    try {
        val devicePolicyManager =
            AndroidContext.appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val storageStatus: Int = devicePolicyManager.storageEncryptionStatus

        if (storageStatus == DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED) {
            return false
        }

        //https://stackoverflow.com/a/35293668
        val disabledOrDefault =
            storageStatus == DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE ||
                    Build.VERSION.SDK_INT >= 23 && storageStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY

        return !disabledOrDefault
    } catch (e: Throwable){
        LogCat.logException(e)
        return true
    }
}