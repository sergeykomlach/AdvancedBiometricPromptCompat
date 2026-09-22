package dev.skomlach.biometric.compat.custom

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLClassLoader
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

class SoftwareBiometricServicesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    interface Service { val value: String }
    class Broken : Service {
        init { error("optional SDK unavailable") }
        override val value = "broken"
    }
    class Healthy : Service { override val value = "healthy" }

    @Test fun actualServiceLoaderConstructorFailureDoesNotHideNextProvider() {
        val root = temporaryFolder.newFolder()
        val descriptor = root.resolve("META-INF/services/" + Service::class.java.name)
        requireNotNull(descriptor.parentFile).mkdirs()
        descriptor.writeText(Broken::class.java.name + "\n" + Healthy::class.java.name)
        URLClassLoader(arrayOf(root.toURI().toURL()), javaClass.classLoader).use { loader ->
            val errors = mutableListOf<Throwable>()
            val result = SoftwareBiometricServices.collect(
                ServiceLoader.load(Service::class.java, loader), { errors += it }
            ) { it.value }
            assertEquals(listOf("healthy"), result)
            assertEquals(1, errors.size)
            assertTrue(errors.single() is ServiceConfigurationError)
        }
    }

    @Test fun brokenDescriptorDoesNotHideFollowingResource() {
        val bad = temporaryFolder.newFolder()
        val good = temporaryFolder.newFolder()
        for ((root, text) in listOf(bad to "illegal class name", good to Healthy::class.java.name)) {
            val descriptor = root.resolve("META-INF/services/" + Service::class.java.name)
            requireNotNull(descriptor.parentFile).mkdirs()
            descriptor.writeText(text)
        }
        URLClassLoader(arrayOf(bad.toURI().toURL(), good.toURI().toURL()), javaClass.classLoader).use { loader ->
            val result = SoftwareBiometricServices.collect(
                ServiceLoader.load(Service::class.java, loader), {}
            ) { it.value }
            assertEquals(listOf("healthy"), result)
        }
    }

    @Test fun nonAdvancingIteratorDoesNotReturnPartialDiscoveryAsComplete() {
        val providers = Iterable {
            object : Iterator<String> {
                var first = true
                override fun hasNext(): Boolean {
                    if (!first) throw ServiceConfigurationError("stuck descriptor")
                    return true
                }
                override fun next(): String { first = false; return "partial" }
            }
        }
        assertThrows(ServiceConfigurationError::class.java) {
            SoftwareBiometricServices.collect(providers, {}) { it }
        }
    }
}
