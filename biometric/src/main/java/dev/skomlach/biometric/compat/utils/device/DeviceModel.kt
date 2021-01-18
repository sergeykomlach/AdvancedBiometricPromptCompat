package dev.skomlach.biometric.compat.utils.device

import android.os.Build
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object DeviceModel {

    private val brand = Build.BRAND
    private val model = Build.MODEL

    fun getNames(): Set<String> {
        val strings = HashMap<String, String>()
        var s: String? = getSimpleDeviceName()
        s?.let {
            strings.put(it.toLowerCase(), it)
        }
        s = getNameFromAssets()
        s?.let {
            strings.put(it.toLowerCase(), it)
        }
        s = getNameFromDatabase()
        s?.let {
            strings.put(it.toLowerCase(), it)
        }
        return HashSet<String>(strings.values)
    }

    private fun getSimpleDeviceName(): String {
        val s =
            SystemPropertiesProxy.get(AndroidContext.getAppContext(), "ro.config.marketing_name")
        return if (!s.isNullOrEmpty())
            getName(brand, s)
        else
            getName(brand, model)
    }

    @WorkerThread
    private fun getNameFromAssets(): String? {

        BiometricLoggerImpl.e("AndroidModel.getNameFromAssets started")

        var modelPair = HashMap<String, String?>()

        var inputStream: InputStream? = null
        var br: BufferedReader? = null
        var inputStreamReader: InputStreamReader? = null
        try {

            inputStream =
                AndroidContext.getAppContext().assets.open("android_models.properties")
            inputStreamReader = InputStreamReader(inputStream, "UTF-8")
            br = BufferedReader(inputStreamReader)
            var strLine: String? = null


            while (br.readLine()?.also { strLine = it } != null) {
                if (TextUtils.isEmpty(strLine) || strLine?.startsWith("#") == true) continue
                val modelNamePair = strLine?.split("=")?.toTypedArray()
                if (modelNamePair?.size == 2) modelPair[modelNamePair[0].trim { it <= ' ' }] =
                    modelNamePair[1].trim { it <= ' ' } else modelPair[modelNamePair!![0].trim { it <= ' ' }] =
                    null
            }
            br.close()
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e, "AndroidModel")
        } finally {
            try {
                br?.close()
            } catch (e: IOException) {
            }
            try {
                inputStreamReader?.close()
            } catch (e: IOException) {
            }
            try {
                inputStream?.close()
            } catch (e: IOException) {
            }
        }


        BiometricLoggerImpl.e("AndroidModel.getNameFromAssets ${modelPair.size}")
        val modelFromFile = modelPair[model]
        return if (TextUtils.isEmpty(modelFromFile)) {
            if (modelPair.containsKey(model) && model.split("_").size > 1) {
                model.replace("_", " ")
            } else
                null
        } else {
            modelFromFile
        }
    }

    @WorkerThread
    private fun getNameFromDatabase(): String? {
        val info = DeviceName
            .getDeviceInfo(AndroidContext.getAppContext())
        return if (info != null) {
            val modelParts = model.split(" ")
            val nameParts = info.name.split(" ")
            val fullName =
                if (modelParts[0].length > nameParts[0].length && modelParts[0].startsWith(
                        nameParts[0],
                        true
                    )
                ) model else info.name

            getName(
                if (!TextUtils.isEmpty(info.manufacturer)) info.manufacturer else brand,
                fullName
            )
        } else {
            null
        }
    }

    private fun getName(vendor: String, model: String): String {
        if (model.startsWith(vendor, true))
            return model
        return "$vendor $model"
    }

}