package dev.skomlach.biometric.compat.engine.internal

import com.samsung.android.bio.face.SemBioFaceManager
import com.samsung.android.camera.iris.SemIrisManager

/** Compile-only OEM contracts. Missing OEM classes/methods are handled by the guarded reader. */
internal object SamsungEnrollmentReader {
    fun face(manager: SemBioFaceManager?): EnrollmentSnapshot = readHardwareEnrollments(
        read = { manager?.getEnrolledFaces() },
        identity = { hardwareEnrollmentId(it.getGroupId(), it.getDeviceId(), it.getFaceId()) }
    )

    fun iris(manager: SemIrisManager?): EnrollmentSnapshot = readHardwareEnrollments(
        read = { manager?.getEnrolledIrises() },
        identity = { hardwareEnrollmentId(it.getGroupId(), it.getDeviceId(), it.getIrisId()) }
    )
}
