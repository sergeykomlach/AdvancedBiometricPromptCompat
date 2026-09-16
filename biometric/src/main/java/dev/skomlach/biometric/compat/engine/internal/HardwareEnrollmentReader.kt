package dev.skomlach.biometric.compat.engine.internal

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Copies the entire list before publishing success, including validation of every identifier. */
internal fun <T : Any> readHardwareEnrollments(
    read: () -> List<T?>?,
    identity: (T) -> String
): EnrollmentSnapshot = try {
    val enrollments = checkNotNull(read()) { "Enrollment list unavailable" }
    val ids = LinkedHashSet<String>()
    for (entry in enrollments) {
        val id = identity(checkNotNull(entry) { "Null enrollment entry" })
        check(ids.add(id)) { "Duplicate enrollment identifier" }
    }
    EnrollmentSnapshot.Available(ids)
} catch (_: NoSuchMethodException) {
    EnrollmentSnapshot.Unsupported
} catch (_: NoSuchMethodError) {
    EnrollmentSnapshot.Unsupported
} catch (_: NoClassDefFoundError) {
    EnrollmentSnapshot.Unsupported
} catch (error: Exception) {
    EnrollmentSnapshot.Unavailable(error)
} catch (error: LinkageError) {
    EnrollmentSnapshot.Unavailable(error)
}

// Plain integer IDs retain compatibility with legacy Huawei/Honor hashes.
internal fun readIntegerHardwareEnrollments(read: () -> IntArray?): EnrollmentSnapshot =
    readHardwareEnrollments({ read()?.toList() }) { it.toString() }

// Explicit numeric components; names, concrete class names and reflection order are irrelevant.
internal fun hardwareEnrollmentId(groupId: Int, deviceId: Long, biometricId: Int): String =
    "group=$groupId;device=$deviceId;id=$biometricId"

/** The SDK hides these methods. Resolve only known public framework signatures, including inheritance. */
internal object FingerprintEnrollmentReader {
    private val listMethods = ConcurrentHashMap<Class<*>, Method>()
    private val idMethods = ConcurrentHashMap<Class<*>, IdentifierMethods>()

    fun read(manager: Any?): EnrollmentSnapshot = readHardwareEnrollments(
        read = {
            val target = checkNotNull(manager) { "Fingerprint manager unavailable" }
            val method = listMethods.getOrPut(target.javaClass) {
                target.javaClass.getMethod("getEnrolledFingerprints").also {
                    check(List::class.java.isAssignableFrom(it.returnType)) { "Invalid enrollment list signature" }
                }
            }
            invoke(method, target) as? List<*>
        },
        identity = { entry ->
            val methods = idMethods.getOrPut(entry.javaClass) { IdentifierMethods(entry.javaClass) }
            hardwareEnrollmentId(
                invoke(methods.group, entry) as Int,
                invoke(methods.device, entry) as Long,
                invoke(methods.id, entry) as Int
            )
        }
    )

    private class IdentifierMethods(type: Class<*>) {
        val group = type.getMethod("getGroupId").requireReturnType(Int::class.javaPrimitiveType!!)
        val device = type.getMethod("getDeviceId").requireReturnType(Long::class.javaPrimitiveType!!)
        val id = try {
            type.getMethod("getBiometricId")
        } catch (_: NoSuchMethodException) {
            // Android 6-8 used getFingerId; invocation failures must not trigger this fallback.
            type.getMethod("getFingerId")
        }.requireReturnType(Int::class.javaPrimitiveType!!)
    }

    private fun Method.requireReturnType(expected: Class<*>): Method = apply {
        check(returnType == expected) { "Invalid enrollment identifier signature" }
    }

    private fun invoke(method: Method, target: Any): Any? = try {
        method.invoke(target)
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }
}
