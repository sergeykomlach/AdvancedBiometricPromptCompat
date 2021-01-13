package dev.skomlach.biometric.compat.utils.device

import android.content.Context
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Build
import android.text.TextUtils
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

object AndroidModel {
    //Meetup.com manage the list with devices
    const val GITHUB_URL =
        "https://raw.githubusercontent.com/meetup/android-device-names/master/android_models.properties"
    var modelPair = HashMap<String, String?>()

    interface OnModelNameExtracted {
        fun onModel(model: String?)
    }

    fun getAsync(context: Context, onModelNameExtracted: OnModelNameExtracted) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            BiometricLoggerImpl.e("AndroidModel.getAsync started")
            val sharedPreferences =
                SharedPreferenceProvider.getCryptoPreferences("BiometricModules")
            val lastChecked = sharedPreferences.getLong("models_lastChecked", 0)
            var set = sharedPreferences.getStringSet("models", HashSet())
            if (set?.isNotEmpty() == true &&
                System.currentTimeMillis() - lastChecked < TimeUnit.DAYS.toMillis(30)
            ) {
                BiometricLoggerImpl.e("AndroidModel.getAsync use cache")
                val stringHashMap = HashMap<String, String?>()
                for (strLine in set) {
                    if (TextUtils.isEmpty(strLine) || strLine.startsWith("#")) continue
                    val modelNamePair = strLine.split("=").toTypedArray()
                    if (modelNamePair.size == 2) stringHashMap[modelNamePair[0].trim { it <= ' ' }] =
                        modelNamePair[1].trim { it <= ' ' } else stringHashMap[modelNamePair[0].trim { it <= ' ' }] =
                        null
                }
                modelPair.clear()
                modelPair.putAll(stringHashMap)
                prefetchData(context, onModelNameExtracted)
                return@execute
            }
            val connectivityManager = context
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true) {
                BiometricLoggerImpl.e("AndroidModel.loadFromWeb")
                var inputStream: InputStream? = null
                var br: BufferedReader? = null
                var inputStreamReader: InputStreamReader? = null
                try {
                    val conn: HttpURLConnection = Network.createConnection(
                        GITHUB_URL, TimeUnit.SECONDS.toMillis(
                            30
                        ).toInt()
                    )
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    if (code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_BAD_REQUEST) {
                        inputStream = conn.inputStream
                        inputStreamReader = InputStreamReader(inputStream, "UTF-8")
                        br = BufferedReader(inputStreamReader)
                        var strLine: String? = null
                        set = HashSet()
                        val stringHashMap = HashMap<String, String?>()
                        while (br.readLine()?.also { strLine = it } != null) {
                            if (TextUtils.isEmpty(strLine) || strLine?.startsWith("#") == true) continue
                            set.add(strLine)
                            val modelNamePair = strLine?.split("=")?.toTypedArray()
                            if (modelNamePair?.size == 2) stringHashMap[modelNamePair[0].trim { it <= ' ' }] =
                                modelNamePair[1].trim { it <= ' ' } else stringHashMap[modelNamePair!![0].trim { it <= ' ' }] =
                                null
                        }
                        br.close()
                        sharedPreferences.edit().putStringSet("models", set)
                            .putLong("models_lastChecked", System.currentTimeMillis()).apply()
                        modelPair.clear()
                        modelPair.putAll(stringHashMap)
                        prefetchData(context, onModelNameExtracted)
                        return@execute
                    }
                } catch (e: Throwable) {
                    //ignore - old device cannt resolve SSL connection
                    if (e !is SSLHandshakeException) {
                        BiometricLoggerImpl.e(e, "AndroidModel")
                    }
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

            BiometricLoggerImpl.e("AndroidModel some error")
            prefetchData(context, onModelNameExtracted)

        }
    }

    private fun prefetchData(context: Context, onModelNameExtracted: OnModelNameExtracted) {
        val model = Build.MODEL
        val modelFromFile = modelPair[model]
        val connectivityManager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true && TextUtils.isEmpty(
                modelFromFile
            )
        ) {
            DeviceName.with(context).request { info, _ ->
                AsyncTask.THREAD_POOL_EXECUTOR.execute {
                    onModelNameExtracted.onModel(
                        if (info != null) {
                            capitalize(if (!TextUtils.isEmpty(info.manufacturer)) info.manufacturer else Build.BRAND) + " " + info.name
                        } else {
                            capitalize(Build.BRAND) + " " + model
                        }
                    )
                }
            }
        } else {
            onModelNameExtracted.onModel(modelFromFile ?: capitalize(Build.BRAND) + " " + model)
        }
    }

    fun capitalize(s: String?): String {
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