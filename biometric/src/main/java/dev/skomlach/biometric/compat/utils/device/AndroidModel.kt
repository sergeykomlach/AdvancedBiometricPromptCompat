package dev.skomlach.biometric.compat.utils.device

import android.os.AsyncTask
import android.os.Build
import android.text.TextUtils
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*

object AndroidModel {

    private val brand = Build.BRAND
    private val model = Build.MODEL

    var modelPair = HashMap<String, String?>()

    interface OnModelNameExtracted {
        fun onModel(model: String?)
    }

    fun getSimpleDeviceName(): String {
        return getName(brand, model)
    }

    fun checkInAssets(onModelNameExtracted: OnModelNameExtracted) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            BiometricLoggerImpl.e("AndroidModel.checkInAssets started")
            if (modelPair.isEmpty()) {
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
            }

            BiometricLoggerImpl.e("AndroidModel.checkInAssets ${modelPair.size}")
            val modelFromFile = modelPair[model]
            if (TextUtils.isEmpty(modelFromFile)) {
                if (modelPair.containsKey(model) && model.split("_").size > 1) {
                    onModelNameExtracted.onModel(model.replace("_", " "))
                } else
                    onModelNameExtracted.onModel(null)
            } else {
                onModelNameExtracted.onModel(modelFromFile ?: getName(brand, model))
            }
        }
    }

    fun checkInDb(onModelNameExtracted: OnModelNameExtracted) {
        DeviceName
            .with(AndroidContext.getAppContext())
            .request { info, _ ->
                AsyncTask.THREAD_POOL_EXECUTOR.execute {
                    onModelNameExtracted.onModel(
                        if (info != null) {
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
                            getName(brand, model)
                        }
                    )
                }
            }
    }

    private fun getName(vendor: String, model: String): String {
        if (model.startsWith(vendor, true))
            return model
        return capitalize(vendor) + " " + model
    }

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            Character.toUpperCase(first).toString() + s.substring(1)
        }
    }
}