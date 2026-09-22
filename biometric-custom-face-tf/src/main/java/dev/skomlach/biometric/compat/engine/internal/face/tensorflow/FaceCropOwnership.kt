package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

/** Crop/scale APIs may alias either their input or their output. Release only owned temporaries. */
internal inline fun <T : Any> transformFaceCrop(
    source: T,
    crop: T,
    release: (T) -> Unit,
    transform: (T) -> T?
): T? {
    var result: T? = null
    return try {
        transform(crop).also { result = it }
    } finally {
        if (crop !== source && crop !== result) release(crop)
    }
}
