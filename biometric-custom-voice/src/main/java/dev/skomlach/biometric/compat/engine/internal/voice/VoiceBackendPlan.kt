package dev.skomlach.biometric.compat.engine.internal.voice

internal data class VoiceBackendPlan(
    val primary: VoiceBackendType,
    val optional: Set<VoiceBackendType> = emptySet()
) {
    fun isEnabled(type: VoiceBackendType): Boolean {
        return type == primary || optional.contains(type)
    }

    fun withOptional(type: VoiceBackendType): VoiceBackendPlan {
        if (type == primary || optional.contains(type)) return this
        return copy(optional = optional + type)
    }

    companion object {
        fun default(): VoiceBackendPlan {
            return VoiceBackendPlan(primary = VoiceBackendType.HEURISTIC)
        }
    }
}
