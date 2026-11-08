package dev.skomlach.biometric.compat.crypto

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

object AppFlowSessionStore {

    private class Session(
        val secret: CharArray,
        val createdAt: Long,
        val allowLegacyDecryption: Boolean
    ) {
        var expiry: ScheduledFuture<*>? = null

        fun clear() {
            expiry?.cancel(false)
            secret.fill('\u0000')
        }
    }

    internal data class Unlock(val secret: CharArray, val allowLegacyDecryption: Boolean)

    private val sessions = HashMap<String, Session>()
    private const val SESSION_TTL_SECONDS = 15L
    private val expiryExecutor = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "BiometricCompat-app-flow-expiry").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    fun unlock(keyName: String, secret: CharArray) {
        unlock(keyName, secret, false)
    }

    @Synchronized
    internal fun unlock(keyName: String, secret: CharArray, allowLegacyDecryption: Boolean) {
        val session = Session(secret.copyOf(), System.nanoTime(), allowLegacyDecryption)
        sessions.put(keyName, session)?.clear()
        session.expiry = expiryExecutor.schedule({
            synchronized(this) {
                if (sessions[keyName] === session) close(keyName)
            }
        }, SESSION_TTL_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * One-shot consumption for production safety.
     */
    fun consumeSecretOrNull(keyName: String): CharArray? = consumeUnlockOrNull(keyName)?.secret

    @Synchronized
    internal fun consumeUnlockOrNull(keyName: String): Unlock? {
        val session = sessions.remove(keyName) ?: return null
        return try {
            val expired = System.nanoTime() - session.createdAt >= TimeUnit.SECONDS.toNanos(SESSION_TTL_SECONDS)
            if (expired) null else Unlock(session.secret.copyOf(), session.allowLegacyDecryption)
        } finally {
            session.clear()
        }
    }

    @Synchronized
    fun close(keyName: String) {
        sessions.remove(keyName)?.clear()
    }

    @Synchronized
    fun closeAll() {
        sessions.values.forEach(Session::clear)
        sessions.clear()
    }
}
