package dev.skomlach.biometric.compat.crypto

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

object AppFlowSessionStore {

    private class Session(
        val secret: CharArray,
        val createdAt: Long
    ) {
        var expiry: ScheduledFuture<*>? = null

        fun clear() {
            expiry?.cancel(false)
            secret.fill('\u0000')
        }
    }

    private val sessions = HashMap<String, Session>()
    private const val SESSION_TTL_SECONDS = 15L
    private val expiryExecutor = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "BiometricCompat-app-flow-expiry").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    @Synchronized
    fun unlock(keyName: String, secret: CharArray) {
        val session = Session(secret.copyOf(), System.nanoTime())
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
    @Synchronized
    fun consumeSecretOrNull(keyName: String): CharArray? {
        val session = sessions.remove(keyName) ?: return null
        return try {
            val expired = System.nanoTime() - session.createdAt >= TimeUnit.SECONDS.toNanos(SESSION_TTL_SECONDS)
            if (expired) null else session.secret.copyOf()
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
