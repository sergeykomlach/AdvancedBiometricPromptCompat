package dev.skomlach.biometric.compat.utils.device

import android.os.Build
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.biometric.compat.utils.SystemPropertiesProxy
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object DeviceModel {

    private val brand = (Build.BRAND ?: "").replace("  ", " ")
    private val model = (Build.MODEL ?: "").replace("  ", " ")
    private val device = (Build.DEVICE ?: "").replace("  ", " ")

    fun getNames(): Set<String> {
        val strings = HashMap<String, String>()
        var s: String? = getSimpleDeviceName()
        BiometricLoggerImpl.e("AndroidModel - $s")
        s?.let {
            strings.put(it.toLowerCase(Locale.ROOT), fixVendorName(it))
        }
        s = getNameFromAssets()
        s?.let {
            strings.put(it.toLowerCase(Locale.ROOT), fixVendorName(it))
        }
        s = getNameFromDatabase()
        s?.let {
            strings.put(it.toLowerCase(Locale.ROOT), fixVendorName(it))
        }
        BiometricLoggerImpl.e("AndroidModel.names ${strings.values}")
        return HashSet<String>(strings.values)
    }

    private fun fixVendorName(string: String): String {
        val parts = string.split(" ")

        var vendor = parts[0]
        if (vendor[0].isLowerCase()) {
            vendor = Character.toUpperCase(vendor[0]).toString() + vendor.substring(1)
        }
        return (vendor + string.substring(vendor.length, string.length)).trim()
    }

    private fun getSimpleDeviceName(): String? {
        SystemPropertiesProxy.get(AndroidContext.getAppContext(), "ro.config.marketing_name")?.let {
            return getName(brand, it)
        }
        return null
    }

    @WorkerThread
    private fun getNameFromAssets(): String? {

        BiometricLoggerImpl.e("AndroidModel.getNameFromAssets started")

        try {
            val json = JSONObject(getJSON() ?: return null)
            for (key in json.keys()) {
                if (brand.equals(key, ignoreCase = true)) {
                    val details = json.getJSONArray(key)
                    for (i in 0 until details.length()) {
                        val jsonObject = details.getJSONObject(i)
                        val m = jsonObject.getString("model")
                        val name = jsonObject.getString("name")
                        val d = jsonObject.getString("device")
                        if (name.isNullOrEmpty()) {
                            continue
                        } else if (!m.isNullOrEmpty() && model.equals(m, ignoreCase = true)) {
                            BiometricLoggerImpl.e("AndroidModel - $jsonObject")
                            val fullName = getFullName(model, name)
                            return getName(brand, fullName)
                        } else if (!d.isNullOrEmpty() && device.equals(d, ignoreCase = true)) {
                            BiometricLoggerImpl.e("AndroidModel - $jsonObject")
                            val fullName = getFullName(model, name)
                            return getName(brand, fullName)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e, "AndroidModel")
        }
        return null
    }

    //tools
    private fun getJSON(): String? {
        try {
           //https://github.com/androidtrackers/certified-android-devices/
           val inputStream =
                AndroidContext.getAppContext().assets.open("by_brand.json")
            val byteArrayOutputStream = ByteArrayOutputStream()
            Network.fastCopy(inputStream, byteArrayOutputStream)
            inputStream.close()
            byteArrayOutputStream.close()
            val data = byteArrayOutputStream.toByteArray()
            return String(data)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return null;
    }

    @WorkerThread
    private fun getNameFromDatabase(): String? {
        val info = DeviceName
            .getDeviceInfo(AndroidContext.getAppContext())
        BiometricLoggerImpl.e("AndroidModel - {${info.codename}; ${info.name}; ${info.marketName}; ${info.model}; }")
        return if (info != null) {
            val fullName = getFullName(model, info.name)
            getName(
                if (!TextUtils.isEmpty(info.manufacturer)) info.manufacturer else brand,
                fullName
            )
        } else {
            null
        }
    }

    private fun getFullName(modelName: String, name: String): String {
        val modelParts = modelName.split(" ")
        val nameParts = name.split(" ")

        return if (modelParts[0].length > nameParts[0].length && modelParts[0].startsWith(
                nameParts[0],
                true
            )
        ) modelName else name
    }

    private fun getName(vendor: String, model: String): String {
        if (model.startsWith(vendor, true))
            return model
        return "$vendor $model"
    }
}