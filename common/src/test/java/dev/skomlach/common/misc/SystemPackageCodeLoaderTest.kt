package dev.skomlach.common.misc

import org.junit.Assert.*
import org.junit.Test

class SystemPackageCodeLoaderTest {
    private val pkg = "com.android.facelock"
    private fun identity(system: Boolean = true, updated: Boolean = false, path: String? = "/system/app/Face/base.apk") =
        SystemPackageCodeIdentity(pkg, path, 10045, system, updated)

    @Test fun thirdPartyNameCollisionNeverCreatesOrInitializesCode() {
        var loads = 0
        val loader = SystemPackageCodeLoader({ identity(system = false) }, { _: String -> ++loads })
        assertThrows(SecurityException::class.java) { loader.get(pkg) }
        assertEquals(0, loads)
    }
    @Test fun installedSystemApplicationIsAllowedAndCached() {
        var lookups = 0
        var loads = 0
        val loader = SystemPackageCodeLoader({ lookups++; identity() }, { _: String -> ++loads })
        assertEquals(1, loader.get(pkg))
        assertEquals(1, loader.get(pkg))
        assertEquals(2, lookups)
        assertEquals(1, loads)
    }
    @Test fun legitimateSystemUpdateMayLiveUnderDataAppWithItsOwnUid() {
        val loader = SystemPackageCodeLoader({ identity(false, true, "/data/app/Face/base.apk") }, { path: String -> path })
        assertEquals("/data/app/Face/base.apk", loader.get(pkg))
    }
    @Test fun cachedTrustCannotSurviveReplacementByThirdParty() {
        var current = identity()
        var loads = 0
        val loader = SystemPackageCodeLoader({ current }, { _: String -> ++loads })
        loader.get(pkg)
        current = identity(false, false, "/data/app/fake/base.apk")
        assertThrows(SecurityException::class.java) { loader.get(pkg) }
        assertEquals(1, loads)
    }
    @Test fun changedSystemCodePathCreatesNewLoader() {
        var current = identity()
        val loader = SystemPackageCodeLoader({ current }, { path: String -> path })
        assertEquals("/system/app/Face/base.apk", loader.get(pkg))
        current = identity(true, true, "/data/app/update/base.apk")
        assertEquals("/data/app/update/base.apk", loader.get(pkg))
    }
    @Test fun missingPackageOrBlankApkCannotReachLoader() {
        var loads = 0
        val absent = SystemPackageCodeLoader<Int>({ throw IllegalArgumentException("missing") }, { _: String -> ++loads })
        assertThrows(IllegalArgumentException::class.java) { absent.get(pkg) }
        val blank = SystemPackageCodeLoader({ identity(path = " ") }, { _: String -> ++loads })
        assertThrows(SecurityException::class.java) { blank.get(pkg) }
        assertEquals(0, loads)
    }
    @Test fun lookupForAnotherPackageIsRejected() {
        val loader = SystemPackageCodeLoader({ identity().copy(packageName = "other") }, { _: String -> 1 })
        assertThrows(SecurityException::class.java) { loader.get(pkg) }
    }
}
