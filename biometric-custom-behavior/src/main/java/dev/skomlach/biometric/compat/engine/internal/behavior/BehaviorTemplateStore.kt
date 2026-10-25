package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.common.storage.editProtected
import dev.skomlach.common.storage.protectedKeys
import dev.skomlach.common.storage.ProtectedStorageUnavailableException
import android.content.SharedPreferences
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkScope
import android.util.Base64
import dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

class BehaviorTemplateStore internal constructor(preferences: () -> SharedPreferences) {
    constructor() : this({ getProtectedPreferences(STORAGE_NAME) })

    private val prefs: SharedPreferences by lazy(preferences)

    internal fun newWorkSession() = WORK_SCOPE.newSession()

    internal fun checkAndRememberSample(sample: BehaviorSample, nowMs: Long, isEnrollment: Boolean) =
        REPLAY_HISTORY.checkAndRemember(sample, nowMs, isEnrollment)

    fun hasTemplate(): Boolean = templateNames().isNotEmpty()

    fun templateNames(): Collection<String> {
        val keys = prefs.protectedKeys().filter { it.startsWith(TEMPLATE_PREFIX) }
        if (keys.size > MAX_PROFILES) throw ProtectedStorageUnavailableException("Behavior profile capacity exceeded")
        return keys
            .map { it.removePrefix(TEMPLATE_PREFIX) }
            .sorted()
    }

    fun loadTemplates(): List<Pair<String, BehaviorSample>> {
        val records = templateNames().map { tag -> tag to prefs.getString(TEMPLATE_PREFIX + tag, null) }
        if (records.sumOf { it.second?.length?.toLong() ?: 0L } > MAX_TOTAL_TEMPLATE_CHARS)
            throw ProtectedStorageUnavailableException("Behavior storage capacity exceeded")
        return records.flatMap { (tag, raw) ->
            val samples = raw?.let { deserializeTemplates(it) }.orEmpty()
            if (samples.isEmpty()) throw ProtectedStorageUnavailableException("Invalid behavior enrollment record")
            samples.map { sample -> tag to sample }
        }
    }

    fun save(tag: String?, sample: BehaviorSample): String = synchronized(WORK_SCOPE.lock) {
        val normalized = sample.normalizedForMode().let { it.copy(
            keyDownTimesMs = ArrayList(it.keyDownTimesMs), keyUpTimesMs = ArrayList(it.keyUpTimesMs),
            strokePoints = ArrayList(it.strokePoints)) }
        require(normalized.hasValidStructure() && normalized.hasRequiredDataForMode() &&
            normalized.qualityIssue() == BehaviorQualityIssue.NONE) { "Invalid behavior enrollment sample" }
        val normalizedTag = sanitizeTag(tag) ?: UUID.randomUUID().toString()
        val storageKey = TEMPLATE_PREFIX + normalizedTag
        val keys = prefs.protectedKeys().filter { it.startsWith(TEMPLATE_PREFIX) }
        require(keys.size <= MAX_PROFILES && (storageKey in keys || keys.size < MAX_PROFILES)) {
            "Behavior profile capacity exceeded"
        }
        val records = keys.associateWith {
            prefs.getString(it, null)
                ?: throw ProtectedStorageUnavailableException("Invalid behavior enrollment record")
        }
        // Enrollment must not silently repair or bypass a corrupted existing profile.
        val histories = records.mapValues { (_, raw) ->
            deserializeTemplates(raw).takeIf { it.isNotEmpty() }
                ?: throw ProtectedStorageUnavailableException("Invalid behavior enrollment record")
        }
        val samples = histories[storageKey]
            .orEmpty()
            .filter { it.mode == normalized.mode && it.phrase == normalized.phrase }
            .takeLast(MAX_TEMPLATES_PER_TAG - 1) + normalized
        // Keep the newest captures within the same bound enforced by the reader.
        var retained = samples
        var serialized = serializeTemplates(retained)
        while (serialized.length > MAX_SERIALIZED_TEMPLATE_CHARS && retained.size > 1) {
            retained = retained.drop(1)
            serialized = serializeTemplates(retained)
        }
        require(serialized.length <= MAX_SERIALIZED_TEMPLATE_CHARS) { "Behavior template exceeds storage limit" }
        val otherChars = records.filterKeys { it != storageKey }.values.sumOf { it.length.toLong() }
        require(otherChars + serialized.length <= MAX_TOTAL_TEMPLATE_CHARS) { "Behavior storage capacity exceeded" }
        prefs.editProtected { putString(storageKey, serialized) }
        normalizedTag
    }

    fun remove(tag: String?) {
        WORK_SCOPE.revoke {
            prefs.editProtected {
                if (tag.isNullOrBlank()) {
                    prefs.protectedKeys().filter { it.startsWith(TEMPLATE_PREFIX) }.forEach { remove(it) }
                } else {
                    sanitizeTag(tag)?.let { remove(TEMPLATE_PREFIX + it) }
                }
            }
        }
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
            val entries = parts[1].split("~")
            if (entries.size > MAX_TEMPLATES_PER_TAG) return emptyList()
            return entries.map { encoded ->
                val decoded = decode(encoded) ?: return emptyList()
                deserializeSample(decoded.split("|")) ?: return emptyList()
            }
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
            phrase = (decode(parts[offset + 1]) ?: return null).ifBlank { null },
            keyDownTimesMs = parseLongList(parts[offset + 2]) ?: return null,
            keyUpTimesMs = parseLongList(parts[offset + 3]) ?: return null,
            strokePoints = parsePoints(parts[offset + 4]) ?: return null
        )
        return sample.normalizedForMode().takeIf {
            it.hasValidStructure() && it.hasRequiredDataForMode() && it.qualityIssue() == BehaviorQualityIssue.NONE
        }
    }

    private fun parseLongList(raw: String): List<Long>? {
        if (raw.isBlank()) return emptyList()
        val values = raw.split(",")
        if (values.size > MAX_TYPING_EVENTS) return null
        return values.map { it.toLongOrNull() ?: return null }
    }

    private fun parsePoints(raw: String): List<BehaviorPoint>? {
        if (raw.isBlank()) return emptyList()
        val entries = raw.split(";")
        if (entries.size > MAX_SIGNATURE_POINTS) return null
        return entries.map { encoded ->
            val values = encoded.split(",")
            if (values.size != 5 && values.size != 6) return null
            val x = values[0].toFloatOrNull() ?: return null
            val y = values[1].toFloatOrNull() ?: return null
            val timestamp = values[2].toLongOrNull() ?: return null
            if (!x.isFinite() ||
                !y.isFinite() ||
                kotlin.math.abs(x) > MAX_COORDINATE_ABS ||
                kotlin.math.abs(y) > MAX_COORDINATE_ABS ||
                timestamp < 0L
            ) {
                return null
            }
            val pressure = values[3].toFloatOrNull() ?: return null
            val size = values[4].toFloatOrNull() ?: return null
            if (!pressure.isFinite() || !size.isFinite() || pressure < -1f || size < -1f ||
                (pressure < 0f && pressure != -1f) || (size < 0f && size != -1f)) return null
            val strokeId = if (values.size == 6) values[5].toIntOrNull() ?: return null else 0
            if (strokeId < 0) return null
            BehaviorPoint(x, y, timestamp, pressure.takeIf { it >= 0f }, size.takeIf { it >= 0f }, strokeId)
        }
    }

    private fun encode(value: String): String {
        return Base64.encodeToString(value.toByteArray(UTF_8), Base64.NO_WRAP)
    }

    private fun decode(value: String): String? {
        return runCatching {
            String(Base64.decode(value, Base64.NO_WRAP), UTF_8)
        }.getOrNull()
    }

    private companion object {
        // Revoke work across all managers and public store instances before deleting enrollment.
        val WORK_SCOPE = SoftwareBiometricWorkScope()
        val REPLAY_HISTORY = BehaviorReplayHistory()
        const val STORAGE_NAME = "behavior_templates"
        const val TEMPLATE_PREFIX = "template_"
        const val FORMAT_VERSION_V1 = "v1"
        const val FORMAT_VERSION_V2 = "v2"
        const val MAX_TAG_LENGTH = 80
        const val MAX_PROFILES = 32
        const val MAX_TOTAL_TEMPLATE_CHARS = 2_097_152L
        const val MAX_TEMPLATES_PER_TAG = 5
        const val MAX_SERIALIZED_TEMPLATE_CHARS = 262_144
        const val MAX_TYPING_EVENTS = 512
        const val MAX_SIGNATURE_POINTS = 2048
        const val MAX_COORDINATE_ABS = 100_000f
    }
}
