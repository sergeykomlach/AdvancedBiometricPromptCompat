package dev.skomlach.biometric.compat.utils.device

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.jaredrummler.android.device.DeviceName
import dev.skomlach.biometric.compat.utils.SystemPropertiesProxy
import dev.skomlach.biometric.compat.utils.device.DeviceInfoManager.agents
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

object DeviceModel {

    private val brand = Build.BRAND
    private val model = Build.MODEL

    fun getNames(): Set<String> {
        val strings = HashMap<String, String>()
        var s: String? = getSimpleDeviceName()
        s?.let {
            strings.put(it.toLowerCase(), fixVendorName(it))
        }
        s = getNameFromAssets()
        s?.let {
            strings.put(it.toLowerCase(), fixVendorName(it))
        }
        s = getNameFromDatabase()
        s?.let {
            strings.put(it.toLowerCase(), fixVendorName(it))
        }
        return HashSet<String>(strings.values)
    }

    private fun fixVendorName(string: String) :String{
        val parts = string.split(" ")

        var vendor = parts[0]
        if(vendor[0].isLowerCase()) {
            vendor = Character.toUpperCase(vendor[0]).toString() + vendor.substring(1)
        }
        return vendor + string.substring(vendor.length, string.length)

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

        try {
            val json = JSONObject(getJSON())
            val details = json.getJSONArray(brand)
            for (i in 0 until details.length()) {
                val jsonObject = details.getJSONObject(i)
                val m = jsonObject.getString("model")
                val name = jsonObject.getString("name")
                if (m.isNullOrEmpty() && name.isNullOrEmpty()) {
                    continue
                } else if(m == model){
                    BiometricLoggerImpl.e("AndroidModel - $jsonObject")
                    val modelParts = model.split(" ")
                    val nameParts = name.split(" ")
                    val fullName =
                        if (modelParts[0].length > nameParts[0].length && modelParts[0].startsWith(
                                nameParts[0],
                                true
                            )
                        ) model else name
                    return getName(brand, fullName).replace("  ", " ")
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
            var urlConnection: HttpURLConnection? = null
            val connectivityManager = AndroidContext.getAppContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (connectivityManager.activeNetworkInfo != null && connectivityManager.activeNetworkInfo!!
                    .isConnectedOrConnecting
            ) {
                return try {
                    urlConnection = Network.createConnection(
                        "https://github.com/androidtrackers/certified-android-devices/blob/master/by_brand.json?raw=true",
                        TimeUnit.SECONDS.toMillis(30).toInt()
                    )
                    urlConnection.requestMethod = "GET"
                    urlConnection.setRequestProperty("Content-Language", "en-US")
                    urlConnection.setRequestProperty("Accept-Language", "en-US")
                    urlConnection.setRequestProperty(
                        "User-Agent",
                        agents[SecureRandom().nextInt(agents.size)]
                    )
                    urlConnection.connect()
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    var inputStream: InputStream? = null
                    inputStream = urlConnection.inputStream
                    if (inputStream == null) inputStream = urlConnection.errorStream
                    Network.fastCopy(inputStream, byteArrayOutputStream)
                    inputStream.close()
                    val data = byteArrayOutputStream.toByteArray()
                    byteArrayOutputStream.close()
                    String(data)
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect()
                        urlConnection = null
                    }
                }
            }
        } catch (e: Throwable) {
            //ignore - old device cannt resolve SSL connection
            BiometricLoggerImpl.e(e)
        }

        try {
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