package dev.skomlach.common.device

import android.util.JsonReader
import androidx.annotation.Keep
import java.io.StringReader

@Keep
data class DeviceSpec(
    val phoneName: String,
    val specs: Map<String, String>,
    val metadata: Map<String, String>
)


fun manualParseDeviceSpec(json: String): DeviceSpec {
    var phoneName = ""
    val specs = LinkedHashMap<String, String>(128)
    val metadata = LinkedHashMap<String, String>(16)

    JsonReader(StringReader(json)).use { reader ->
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when {
                key == "phone_name" -> phoneName = reader.nextString()
                key == "_metadata" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        metadata[reader.nextName()] = reader.nextString()
                    }
                    reader.endObject()
                }

                reader.peek() == android.util.JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        specs[reader.nextName()] = reader.nextString()
                    }
                    reader.endObject()
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    val finalPhoneName = phoneName.ifBlank { metadata["phone_name"] ?: "" }

    return DeviceSpec(finalPhoneName, specs, metadata)
}