package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.SharedPreferences
import android.util.Base64
import dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

class BehaviorTemplateStore {
    private val prefs: SharedPreferences by lazy {
        getProtectedPreferences(STORAGE_NAME)
    }

    fun hasTemplate(): Boolean = templateNames().isNotEmpty()

    fun templateNames(): Collection<String> {
        return prefs.all.keys
            .filter { it.startsWith(TEMPLATE_PREFIX) }
            .map { it.removePrefix(TEMPLATE_PREFIX) }
            .sorted()
    }

    fun loadTemplates(): List<Pair<String, BehaviorSample>> {
        return templateNames().flatMap { tag ->
            prefs.getString(TEMPLATE_PREFIX + tag, null)
                ?.let { deserializeTemplates(it) }
                ?.map { sample -> tag to sample }
                .orEmpty()
        }
    }

    fun save(tag: String?, sample: BehaviorSample): String {
        val normalizedTag = sanitizeTag(tag) ?: UUID.randomUUID().toString()
        val storageKey = TEMPLATE_PREFIX + normalizedTag
        val samples = prefs.getString(storageKey, null)
            ?.let { deserializeTemplates(it) }
            .orEmpty()
            .filter { it.mode == sample.mode && it.phrase == sample.phrase }
            .takeLast(MAX_TEMPLATES_PER_TAG - 1) + sample
        prefs.edit()
            .putString(storageKey, serializeTemplates(samples))
            .apply()
        return normalizedTag
    }

    fun remove(tag: String?) {
        val editor = prefs.edit()
        if (tag.isNullOrBlank()) {
            templateNames().forEach { editor.remove(TEMPLATE_PREFIX + it) }
        } else {
            sanitizeTag(tag)?.let { editor.remove(TEMPLATE_PREFIX + it) }
        }
        editor.apply()
    }

    fun sanitizeTag(tag: String?): String? {
        return tag
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            ?.take(MAX_TAG_LENGTH)
    }

    private fun serializeTemplates(samples: List<BehaviorSample>): String {
        return listOf(FORMAT_VERSION_V2, samples.joinToString("~") { encode(serializeSample(it)) })
            .joinToString("|")
    }

    private fun serializeSample(sample: BehaviorSample): String {
        return listOf(
            sample.mode.name,
            encode(sample.phrase.orEmpty()),
            sample.keyDownTimesMs.joinToString(","),
            sample.keyUpTimesMs.joinToString(","),
            sample.strokePoints.joinToString(";") { point ->
                listOf(
                    point.x,
                    point.y,
                    point.timestampMs,
                    point.pressure ?: -1f,
                    point.size ?: -1f,
                    point.strokeId
                ).joinToString(",")
            }
        ).joinToString("|")
    }

    private fun deserializeTemplates(raw: String): List<BehaviorSample> {
        if (raw.length > MAX_SERIALIZED_TEMPLATE_CHARS) return emptyList()
        val parts = raw.split("|", limit = 2)
        if (parts.size == 2 && parts[0] == FORMAT_VERSION_V2) {
            return parts[1]
                .split("~")
                .mapNotNull { deserializeSample(decode(it).split("|")) }
                .takeLast(MAX_TEMPLATES_PER_TAG)
        }
        val legacyParts = raw.split("|")
        return deserializeSample(legacyParts)?.let { listOf(it) }.orEmpty()
    }

    private fun deserializeSample(parts: List<String>): BehaviorSample? {
        val offset = if (parts.size == 6 && parts[0] == FORMAT_VERSION_V1) 1 else 0
        if (parts.size - offset != 5) return null
        val mode = runCatching { BehaviorMode.valueOf(parts[offset]) }.getOrNull() ?: return null
        val sample = BehaviorSample(
            mode = mode,
            phrase = decode(parts[offset + 1]).ifBlank { null },
            keyDownTimesMs = parseLongList(parts[offset + 2]),
            keyUpTimesMs = parseLongList(parts[offset + 3]),
            strokePoints = parsePoints(parts[offset + 4])
        )
        return sample.takeIf { it.hasRequiredDataForMode() }
    }

    private fun parseLongList(raw: String): List<Long> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .take(MAX_TYPING_EVENTS)
            .mapNotNull { it.toLongOrNull() }
    }

    private fun parsePoints(raw: String): List<BehaviorPoint> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").take(MAX_SIGNATURE_POINTS).mapNotNull { encoded ->
            val values = encoded.split(",")
            if (values.size != 5 && values.size != 6) return@mapNotNull null
            val x = values[0].toFloatOrNull() ?: return@mapNotNull null
            val y = values[1].toFloatOrNull() ?: return@mapNotNull null
            val timestamp = values[2].toLongOrNull() ?: return@mapNotNull null
            if (!x.isFinite() ||
                !y.isFinite() ||
                kotlin.math.abs(x) > MAX_COORDINATE_ABS ||
                kotlin.math.abs(y) > MAX_COORDINATE_ABS ||
                timestamp < 0L
            ) {
                return@mapNotNull null
            }
            val pressure = values[3].toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
            val size = values[4].toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
            val strokeId = values.getOrNull(5)?.toIntOrNull() ?: 0
            BehaviorPoint(x, y, timestamp, pressure, size, strokeId)
        }
    }

    private fun encode(value: String): String {
        return Base64.encodeToString(value.toByteArray(UTF_8), Base64.NO_WRAP)
    }

    private fun decode(value: String): String {
        return runCatching {
            String(Base64.decode(value, Base64.NO_WRAP), UTF_8)
        }.getOrDefault("")
    }

    private companion object {
        const val STORAGE_NAME = "behavior_templates"
        const val TEMPLATE_PREFIX = "template_"
        const val FORMAT_VERSION_V1 = "v1"
        const val FORMAT_VERSION_V2 = "v2"
        const val MAX_TAG_LENGTH = 80
        const val MAX_TEMPLATES_PER_TAG = 5
        const val MAX_SERIALIZED_TEMPLATE_CHARS = 262_144
        const val MAX_TYPING_EVENTS = 512
        const val MAX_SIGNATURE_POINTS = 2048
        const val MAX_COORDINATE_ABS = 100_000f
    }
}
