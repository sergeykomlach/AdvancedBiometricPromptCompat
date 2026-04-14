package dev.skomlach.biometric.compat.crypto

import java.util.concurrent.ConcurrentHashMap

object AppFlowSessionStore {

    private data class Session(
        val secret: CharArray,
        val createdAt: Long
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private const val SESSION_TTL_MS = 15_000L

    fun unlock(keyName: String, secret: CharArray) {
        close(keyName)
        sessions[keyName] = Session(secret.copyOf(), System.currentTimeMillis())
    }

    /**
     * One-shot consumption for production safety.
     */
    fun consumeSecretOrNull(keyName: String): CharArray? {
        val session = sessions.remove(keyName) ?: return null
        val expired = System.currentTimeMillis() - session.createdAt > SESSION_TTL_MS
        return if (expired) {
            session.secret.fill('\u0000')
            null
        } else {
            session.secret.copyOf().also { session.secret.fill('\u0000') }
        }
    }

    fun close(keyName: String) {
        val removed = sessions.remove(keyName) ?: return
        removed.secret.fill('\u0000')
    }

    fun closeAll() {
        val keys = sessions.keys().toList()
        keys.forEach(::close)
    }
}
