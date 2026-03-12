package dev.skomlach.common.device

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

@Keep
data class DeviceSpec(
    val phoneName: String,
    val specs: Map<String, String>,
    val metadata: Map<String, String>
)

fun gsonForGsmarena(): Gson =
    GsonBuilder()
        .registerTypeAdapter(DeviceSpec::class.java, DeviceSpecDeserializer())
        .create()


class DeviceSpecDeserializer : JsonDeserializer<DeviceSpec> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DeviceSpec {
        val obj = json.asJsonObject

        // ---- phone name with fallbacks ----
        val phoneName =
            obj.getAsJsonPrimitive("phone_name")?.asString?.trim()
                ?: obj.getAsJsonObject("_metadata")
                    ?.getAsJsonPrimitive("phone_name")
                    ?.asString
                    ?.trim()
                ?: obj.getAsJsonPrimitive("name")?.asString?.trim()
                ?: ""

        val specs = LinkedHashMap<String, String>(256)
        val metadata = LinkedHashMap<String, String>(16)

        for ((key, value) in obj.entrySet()) {
            when {
                key == "_metadata" && value.isJsonObject -> {
                    for ((mk, mv) in value.asJsonObject.entrySet()) {
                        val v = mv.toSimpleString()
                        if (v.isNotBlank()) metadata[mk] = v
                    }
                }

                value.isJsonObject -> {
                    for ((sk, sv) in value.asJsonObject.entrySet()) {
                        val v = sv.toSimpleString().trim()
                        if (v.isNotEmpty()) specs[sk] = v
                    }
                }

                else -> Unit
            }
        }

        return DeviceSpec(
            phoneName = phoneName,
            specs = specs,
            metadata = metadata
        )
    }

    private fun JsonElement.toSimpleString(): String = try {
        when {
            isJsonNull -> ""
            isJsonPrimitive -> asJsonPrimitive.asString
            else -> toString().trim('"')
        }
    } catch (_: Throwable) {
        toString().trim('"')
    }
}