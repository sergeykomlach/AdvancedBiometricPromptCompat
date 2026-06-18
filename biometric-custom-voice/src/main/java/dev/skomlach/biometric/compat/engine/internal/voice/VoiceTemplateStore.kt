package dev.skomlach.biometric.compat.engine.internal.voice

import android.content.SharedPreferences
import android.util.Base64
import dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

data class VoiceTemplate(
    val tag: String,
    val phrase: String?,
    val embedding: FloatArray,
    val gmmModel: GmmVoiceModel? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceTemplate
        return tag == other.tag &&
            phrase == other.phrase &&
            embedding.contentEquals(other.embedding) &&
            gmmModel == other.gmmModel
    }

    override fun hashCode(): Int {
        var result = tag.hashCode()
        result = 31 * result + (phrase?.hashCode() ?: 0)
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + (gmmModel?.hashCode() ?: 0)
        return result
    }
}

class VoiceTemplateStore {
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

    fun loadTemplates(): List<VoiceTemplate> {
        return templateNames().flatMap { tag ->
            prefs.getString(TEMPLATE_PREFIX + tag, null)
                ?.let { deserializeTemplates(tag, it) }
                .orEmpty()
        }
    }

    fun save(tag: String?, phrase: String?, embedding: FloatArray): String {
        return saveAll(tag, phrase, listOf(embedding))
    }

    fun saveAll(
        tag: String?,
        phrase: String?,
        embeddings: List<FloatArray>,
        featureBatches: List<List<FloatArray>> = emptyList()
    ): String {
        val normalizedTag = sanitizeTag(tag) ?: UUID.randomUUID().toString()
        val storageKey = TEMPLATE_PREFIX + normalizedTag
        val existingTemplates = prefs.getString(storageKey, null)
            ?.let { deserializeTemplates(normalizedTag, it) }
            .orEmpty()
        val incomingTemplates = trainVoiceTemplates(normalizedTag, phrase, embeddings, featureBatches)
        if (incomingTemplates.isEmpty()) return normalizedTag
        val templates = mergeVoiceTemplates(
            existing = existingTemplates,
            incoming = incomingTemplates,
            maxTemplates = MAX_TEMPLATES_PER_TAG
        )
        prefs.edit()
            .putString(storageKey, serializeTemplates(templates))
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

    private fun serializeTemplates(templates: List<VoiceTemplate>): String {
        return listOf(
            FORMAT_VERSION,
            templates.joinToString("~") { template ->
                encode(
                    listOf(
                        encode(template.phrase.orEmpty()),
                        formatFloatArray(template.embedding),
                        template.gmmModel?.let { encode(serializeGmmModel(it)) }.orEmpty()
                    ).joinToString("|")
                )
            }
        ).joinToString("|")
    }

    private fun deserializeTemplates(tag: String, raw: String): List<VoiceTemplate> {
        val parts = raw.split("|", limit = 2)
        if (parts.size != 2 || parts[0] !in setOf(FORMAT_VERSION_V1, FORMAT_VERSION)) return emptyList()
        return parts[1]
            .split("~")
            .mapNotNull { encoded ->
                val sampleParts = decode(encoded).split("|", limit = 3)
                if (sampleParts.size < 2) return@mapNotNull null
                val phrase = decode(sampleParts[0]).ifBlank { null }
                val embedding = sampleParts[1]
                    .split(",")
                    .mapNotNull { it.toFloatOrNull() }
                    .toFloatArray()
                    .takeIf { it.isValidEmbedding() }
                    ?: return@mapNotNull null
                val model = sampleParts
                    .getOrNull(2)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { deserializeGmmModel(decode(it)) }
                VoiceTemplate(tag, phrase, embedding, model)
            }
            .takeLast(MAX_TEMPLATES_PER_TAG)
    }

    private fun serializeGmmModel(model: GmmVoiceModel): String {
        return listOf(
            formatFloatArray(model.weights),
            formatFrameList(model.means),
            formatFrameList(model.variances),
            String.format(Locale.US, "%.8f", model.enrollmentLogLikelihood),
            String.format(Locale.US, "%.8f", model.enrollmentLogLikelihoodStd)
        ).joinToString(";")
    }

    private fun deserializeGmmModel(raw: String): GmmVoiceModel? {
        val parts = raw.split(";")
        if (parts.size != 5) return null
        val weights = parseFloatArray(parts[0]).takeIf { it.isNotEmpty() } ?: return null
        val means = parseFrameList(parts[1]).takeIf { it.isNotEmpty() } ?: return null
        val variances = parseFrameList(parts[2]).takeIf { it.size == means.size } ?: return null
        val enrollmentLogLikelihood = parts[3].toFloatOrNull() ?: return null
        val enrollmentLogLikelihoodStd = parts[4].toFloatOrNull() ?: return null
        if (weights.size != means.size || variances.any { it.size != means.first().size }) return null
        return GmmVoiceModel(weights, means, variances, enrollmentLogLikelihood, enrollmentLogLikelihoodStd)
    }

    private fun formatFloatArray(values: FloatArray): String {
        return values.joinToString(",") { String.format(Locale.US, "%.8f", it) }
    }

    private fun formatFrameList(frames: List<FloatArray>): String {
        return frames.joinToString("/") { formatFloatArray(it) }
    }

    private fun parseFloatArray(raw: String): FloatArray {
        return raw.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
    }

    private fun parseFrameList(raw: String): List<FloatArray> {
        return raw.split("/")
            .map { parseFloatArray(it) }
            .filter { it.isNotEmpty() }
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
        const val STORAGE_NAME = "voice_templates"
        const val TEMPLATE_PREFIX = "template_"
        const val FORMAT_VERSION_V1 = "v1"
        const val FORMAT_VERSION = "v2"
        const val MAX_TAG_LENGTH = 80
        const val MAX_TEMPLATES_PER_TAG = 5
    }
}

internal fun mergeVoiceTemplates(
    existing: List<VoiceTemplate>,
    incoming: List<VoiceTemplate>,
    maxTemplates: Int
): List<VoiceTemplate> {
    if (incoming.isEmpty()) return existing
    val incomingPhrase = incoming.first().phrase
    val otherPhraseTemplates = existing.filter { it.phrase != incomingPhrase }
    val samePhraseTemplates = (existing.filter { it.phrase == incomingPhrase } + incoming)
        .takeLast(maxTemplates)
    return otherPhraseTemplates + samePhraseTemplates
}

internal fun trainVoiceTemplates(
    tag: String,
    phrase: String?,
    embeddings: List<FloatArray>,
    featureBatches: List<List<FloatArray>> = emptyList()
): List<VoiceTemplate> {
    val normalized = embeddings
        .mapNotNull { it.normalizedCopy() }
    val gmmModel = GmmVoiceTrainer.train(featureBatches)
    if (normalized.size <= 1) {
        return normalized.map { VoiceTemplate(tag, phrase, it, gmmModel) }
    }

    val filtered = normalized.filterIndexed { index, embedding ->
        val averageSimilarity = normalized
            .filterIndexed { otherIndex, _ -> otherIndex != index }
            .map { other -> VoiceScorer.score(embedding, other) }
            .average()
            .takeIf { !it.isNaN() }
            ?: 0.0
        averageSimilarity >= TRAINING_MIN_AVERAGE_SIMILARITY
    }.ifEmpty {
        normalized
    }

    val centroid = centroidEmbedding(filtered)
    val templates = filtered.map { VoiceTemplate(tag, phrase, it) }.toMutableList()
    if (centroid != null && filtered.size > 1) {
        templates.add(VoiceTemplate(tag, phrase, centroid, gmmModel))
    } else if (gmmModel != null && templates.isNotEmpty()) {
        val first = templates.removeAt(0)
        templates.add(0, first.copy(gmmModel = gmmModel))
    }
    return templates
}

private fun FloatArray.normalizedCopy(): FloatArray? {
    if (!isValidEmbedding()) return null
    var sumSquares = 0.0
    for (value in this) {
        sumSquares += value * value
    }
    val norm = sqrt(sumSquares).toFloat()
    if (norm <= 0f || !norm.isFinite()) return null
    return FloatArray(size) { index -> this[index] / norm }
}

private fun centroidEmbedding(embeddings: List<FloatArray>): FloatArray? {
    if (embeddings.isEmpty()) return null
    val size = embeddings.minOf { it.size }
    val centroid = FloatArray(size)
    embeddings.forEach { embedding ->
        for (index in 0 until size) {
            centroid[index] += embedding[index] / embeddings.size
        }
    }
    return centroid.normalizedCopy()
}

private const val TRAINING_MIN_AVERAGE_SIMILARITY = 0.70
