package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.common.storage.editProtected
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkScope
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

class VoiceTemplateStore internal constructor(
    preferences: () -> SharedPreferences,
    private val enrollmentEngine: VoiceEngine?
) {
    /** Read/maintenance compatibility constructor. Public writes require an engine-bound store. */
    constructor() : this({ getProtectedPreferences(STORAGE_NAME) }, null)

    /**
     * Bind public enrollment writes to the engine that produced their embeddings.
     * Prepare that engine off the UI thread first; this store never initializes a native runtime.
     */
    constructor(engine: VoiceEngine) : this({ getProtectedPreferences(STORAGE_NAME) }, engine)

    private val prefs: SharedPreferences by lazy(preferences)

    internal fun newWorkSession() = operationScope.newSession()

    fun hasTemplate(): Boolean = templateNames().isNotEmpty()

    fun enrollmentStatus(identity: String?): VoiceEnrollmentStatus = synchronized(storageLock) {
        voiceEnrollmentStatus(templateNames().map { prefs.getString(IDENTITY_PREFIX + it, null) }, identity)
    }

    fun templateNames(): Collection<String> = synchronized(storageLock) {
        prefs.all.keys
            .filter { it.startsWith(TEMPLATE_PREFIX) }
            .map { it.removePrefix(TEMPLATE_PREFIX) }
            .sorted()
    }

    fun loadTemplates(): List<VoiceTemplate> = synchronized(storageLock) {
        templateNames().flatMap { tag ->
            prefs.getString(TEMPLATE_PREFIX + tag, null)
                ?.let { deserializeTemplates(tag, it) }
                .orEmpty()
        }
    }

    fun templateNames(identity: String?): Collection<String> = synchronized(storageLock) {
        templateNames().filter { voiceTemplateIdentityMatches(prefs.getString(IDENTITY_PREFIX + it, null), identity) }
    }

    fun loadTemplates(identity: String?): List<VoiceTemplate> = synchronized(storageLock) {
        loadTemplates().filter { voiceTemplateIdentityMatches(prefs.getString(IDENTITY_PREFIX + it.tag, null), identity) }
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
        val session = newWorkSession()
        try {
            return session.runIfActive {
                // Validation is cheap; model preparation is the caller's responsibility.
                checkNotNull(enrollmentEngine) { "Use VoiceTemplateStore(engine) for enrollment writes" }
            }?.let { engine ->
                check(engine.isAvailable()) { "Prepare the voice engine before saving enrollment" }
                val identity = checkNotNull(voiceEnrollmentIdentity(engine)) {
                    "Voice enrollment requires a stable VoiceTemplateIdentityProvider identity"
                }
                val normalizedTag = sanitizeTag(tag) ?: UUID.randomUUID().toString()
                val incoming = trainVoiceTemplates(normalizedTag, phrase, embeddings, featureBatches)
                require(incoming.isNotEmpty()) { "Voice enrollment requires a consistent set of recordings and features" }
                session.runIfActive { saveTrained(normalizedTag, incoming, identity) }
            } ?: throw java.util.concurrent.CancellationException("Voice enrollment was removed")
        } finally {
            session.complete()
        }
    }

    /** Training is performed outside the cancellation/commit monitor. */
    internal fun saveTrained(tag: String, incoming: List<VoiceTemplate>, identity: String?): String = synchronized(storageLock) {
        require(incoming.isNotEmpty())
        val storageKey = TEMPLATE_PREFIX + tag
        val sameIdentity = prefs.getString(IDENTITY_PREFIX + tag, null) == identity
        val existing = if (sameIdentity) prefs.getString(storageKey, null)
            ?.let { deserializeTemplates(tag, it) }.orEmpty() else emptyList()
        val templates = mergeVoiceTemplates(existing, incoming, MAX_TEMPLATES_PER_TAG)
        prefs.editProtected {
            putString(storageKey, serializeTemplates(templates))
            if (identity == null) remove(IDENTITY_PREFIX + tag)
            else putString(IDENTITY_PREFIX + tag, identity)
        }
        tag
    }

    fun remove(tag: String?) {
        operationScope.revoke {
            prefs.editProtected {
                if (tag.isNullOrBlank()) {
                    templateNames().forEach {
                        remove(TEMPLATE_PREFIX + it)
                        remove(IDENTITY_PREFIX + it)
                    }
                } else {
                    sanitizeTag(tag)?.let {
                        remove(TEMPLATE_PREFIX + it)
                        remove(IDENTITY_PREFIX + it)
                    }
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
        if (raw.length > MAX_SERIALIZED_TEMPLATE_CHARS) return emptyList()
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
        if (raw.length > MAX_SERIALIZED_GMM_CHARS) return null
        val parts = raw.split(";")
        if (parts.size != 5) return null
        val weights = parseFloatArray(parts[0]).takeIf { it.isNotEmpty() } ?: return null
        val means = parseFrameList(parts[1]).takeIf { it.isNotEmpty() } ?: return null
        val variances = parseFrameList(parts[2]).takeIf { it.size == means.size } ?: return null
        val enrollmentLogLikelihood = parts[3].toFloatOrNull() ?: return null
        val enrollmentLogLikelihoodStd = parts[4].toFloatOrNull() ?: return null
        if (!enrollmentLogLikelihood.isFinite() || !enrollmentLogLikelihoodStd.isFinite()) return null
        if (weights.size != means.size ||
            weights.size > MAX_GMM_COMPONENTS ||
            means.first().size > MAX_GMM_FRAME_SIZE ||
            variances.any { it.size != means.first().size }
        ) {
            return null
        }
        return GmmVoiceModel(weights, means, variances, enrollmentLogLikelihood, enrollmentLogLikelihoodStd)
    }

    private fun formatFloatArray(values: FloatArray): String {
        return values.joinToString(",") { String.format(Locale.US, "%.8f", it) }
    }

    private fun formatFrameList(frames: List<FloatArray>): String {
        return frames.joinToString("/") { formatFloatArray(it) }
    }

    private fun parseFloatArray(raw: String): FloatArray {
        return raw.split(",")
            .take(MAX_FLOAT_ARRAY_VALUES)
            .mapNotNull { it.toFloatOrNull()?.takeIf { value -> value.isFinite() } }
            .toFloatArray()
    }

    private fun parseFrameList(raw: String): List<FloatArray> {
        return raw.split("/")
            .take(MAX_GMM_COMPONENTS)
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
        // Stores share one protected namespace. Per-instance locks cannot prevent mixed
        // payload/identity reads or lost read-modify-write updates across public stores.
        // All persistence stays inside this lock; inference/training remains outside.
        val operationScope = SoftwareBiometricWorkScope()
        val storageLock = operationScope.lock
        const val STORAGE_NAME = "voice_templates"
        const val TEMPLATE_PREFIX = "template_"
        const val IDENTITY_PREFIX = "identity_"
        const val FORMAT_VERSION_V1 = "v1"
        const val FORMAT_VERSION = "v2"
        const val MAX_TAG_LENGTH = 80
        const val MAX_TEMPLATES_PER_TAG = 5
        const val MAX_SERIALIZED_TEMPLATE_CHARS = 262_144
        const val MAX_SERIALIZED_GMM_CHARS = 131_072
        const val MAX_FLOAT_ARRAY_VALUES = 1024
        const val MAX_GMM_COMPONENTS = 16
        const val MAX_GMM_FRAME_SIZE = 128
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
    if (embeddings.map { it.size }.distinct().size != 1) return emptyList()
    if (featureBatches.isNotEmpty() && featureBatches.size != embeddings.size) return emptyList()
    // Preserve recording indices: filtering invalid embeddings would misalign GMM frames.
    val normalized = embeddings.map { it.normalizedCopy() ?: return emptyList() }
    val acceptedIndices = consistentMajorityIndices(normalized)
    if (acceptedIndices.isEmpty()) return emptyList()
    val filtered = acceptedIndices.map { normalized[it] }
    val acceptedBatches = if (featureBatches.isEmpty()) emptyList()
        else acceptedIndices.map { featureBatches[it] }
    val hasFeatures = acceptedBatches.any { it.isNotEmpty() }
    val gmmModel = if (hasFeatures) {
        if (acceptedBatches.any { it.isEmpty() }) return emptyList()
        val frameSize = acceptedBatches.first().first().size
        if (frameSize == 0 || acceptedBatches.any { batch ->
                batch.any { frame -> frame.size != frameSize || frame.any { !it.isFinite() } }
            }) return emptyList()
        GmmVoiceTrainer.train(acceptedBatches) ?: return emptyList()
    } else null

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

/**
 * A strict majority must form an isolated, pairwise-consistent group. A bridging recording
 * makes the entire group ambiguous; ties, minorities and disconnected singletons fail closed.
 * Connected components keep the decision deterministic and quadratic, without clique search.
 */
private fun consistentMajorityIndices(embeddings: List<FloatArray>): List<Int> {
    val compatible = Array(embeddings.size) { left ->
        BooleanArray(embeddings.size) { right ->
            left == right || VoiceScorer.score(embeddings[left], embeddings[right]) >=
                TRAINING_MIN_PAIR_SIMILARITY
        }
    }
    val visited = BooleanArray(embeddings.size)
    for (start in embeddings.indices) {
        if (visited[start]) continue
        val component = mutableListOf(start)
        visited[start] = true
        var cursor = 0
        while (cursor < component.size) {
            val current = component[cursor++]
            for (other in embeddings.indices) {
                if (!visited[other] && compatible[current][other]) {
                    visited[other] = true
                    component.add(other)
                }
            }
        }
        if (component.size > embeddings.size / 2 &&
            component.all { left -> component.all { right -> compatible[left][right] } }) {
            return component.sorted()
        }
    }
    return emptyList()
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
    val size = embeddings.first().size
    if (embeddings.any { it.size != size }) return null
    val centroid = FloatArray(size)
    embeddings.forEach { embedding ->
        for (index in 0 until size) {
            centroid[index] += embedding[index] / embeddings.size
        }
    }
    return centroid.normalizedCopy()
}

private const val TRAINING_MIN_PAIR_SIMILARITY = 0.70
