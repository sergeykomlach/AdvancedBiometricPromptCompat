package dev.skomlach.biometric.compat.engine.internal

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Compatibility boundary for external providers and opaque OEM managers without a typed contract.
 * Keep the old identifier encoding, but never publish null, partial or failed reads as empty.
 * New built-in providers must use explicit readers instead of adding names to this heuristic.
 */
internal object LegacyEnrollmentReader {
    private val enrollmentMethods = ConcurrentHashMap<Class<*>, List<Method>>()
    private val identifierMethods = ConcurrentHashMap<Class<*>, Set<Method>>()

    fun read(managers: Set<Any>): EnrollmentSnapshot = try {
        val ids = ArrayList<String>()
        var supported = false
        for (manager in managers) {
            readIds(manager)?.let { ids.addAll(it); supported = true }
        }
        if (!supported) EnrollmentSnapshot.Unsupported else {
            val counts = ids.groupingBy { it }.eachCount()
            val encoded = ArrayList<String>()
            for ((id, count) in counts) {
                if (count == 1) encoded.add(id)
                else repeat(count) { index -> encoded.add("$id; index=$index") }
            }
            EnrollmentSnapshot.Available(encoded)
        }
    } catch (error: Exception) {
        EnrollmentSnapshot.Unavailable(error)
    } catch (error: LinkageError) {
        EnrollmentSnapshot.Unavailable(error)
    }

    private fun readIds(manager: Any): List<String>? {
        val methods = enrollmentMethods.getOrPut(manager.javaClass) {
            manager.javaClass.declaredMethods.filter {
                (it.name.contains("enrolled", true) || it.name.contains("registered", true)) &&
                    it.parameterTypes.isEmpty() && it.returnType != Void.TYPE &&
                    it.returnType != Boolean::class.javaPrimitiveType && it.returnType != Boolean::class.javaObjectType
            }.onEach { it.isAccessible = true }
        }
        var completeEmpty = false
        for (method in methods) {
            val value = checkNotNull(invoke(method, manager)) { "Legacy enrollment list unavailable" }
            val entries: Collection<*> = when (value) {
                is Collection<*> -> value
                is IntArray -> value.toList()
                is LongArray -> value.toList()
                is Array<*> -> value.toList()
                is Boolean -> continue
                else -> listOf(value)
            }
            val ids = entries.map { uniqueId(checkNotNull(it) { "Null legacy enrollment entry" }) }
            if (ids.isNotEmpty()) return ids
            completeEmpty = true
        }
        return if (completeEmpty) emptyList() else null
    }

    private fun uniqueId(value: Any): String {
        if (value is String || value is Int || value is Long) return value.toString()
        val methods = identifierMethods.getOrPut(value.javaClass) {
            // Preserve historical order/format for existing snapshots and third-party providers.
            val type = value.javaClass
            (type.declaredMethods.toList() + type.superclass?.declaredMethods.orEmpty()).filter {
                (it.name.endsWith("id", true) || it.name.endsWith("name", true)) &&
                    it.returnType != Void.TYPE && it.parameterTypes.isEmpty()
            }.onEach { it.isAccessible = true }.toSet()
        }
        val result = StringBuilder()
        for (method in methods) {
            val part = invoke(method, value) ?: continue
            if (result.isEmpty()) result.append(value.javaClass.simpleName).append("; ")
            result.append(method.name).append("=").append(part).append("; ")
        }
        return result.toString().trim().also {
            check(it.isNotEmpty()) { "Legacy enrollment identifier unavailable" }
        }
    }

    private fun invoke(method: Method, target: Any): Any? = try {
        method.invoke(target)
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }
}
