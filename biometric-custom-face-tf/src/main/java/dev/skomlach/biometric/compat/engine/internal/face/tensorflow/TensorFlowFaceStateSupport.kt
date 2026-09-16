package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.content.res.AssetManager
import org.json.JSONObject

internal const val REGISTERED_TEMPLATES_PREF_KEY = "registered"

/** Strictly read the persisted membership without requiring a camera or a loaded detector. */
internal fun readStoredFaceEnrollmentIds(jsonString: String?): Set<String> {
    if (jsonString == null) return emptySet()
    val templates = JSONObject(jsonString)
    val ids = sortedSetOf<String>()
    val keys = templates.keys()
    while (keys.hasNext()) {
        val id = keys.next()
        // Do not accept partially corrupt entries as a complete enrollment snapshot.
        require(templates.opt(id) is JSONObject) { "Invalid stored face enrollment entry" }
        ids.add(id)
    }
    return ids
}

internal fun hasUsableFaceEnrollment(jsonString: String?, cameraPermissionGranted: Boolean): Boolean =
    cameraPermissionGranted && hasRegisteredTemplates(jsonString)

internal fun hasRegisteredTemplates(jsonString: String?): Boolean {
    return countRegisteredTemplates(jsonString) > 0
}

internal fun countRegisteredTemplates(jsonString: String?): Int {
    if (jsonString.isNullOrBlank()) {
        return 0
    }
    return try {
        JSONObject(jsonString).length()
    } catch (_: Throwable) {
        0
    }
}

internal fun hasAssetFile(assetManager: AssetManager, assetPath: String): Boolean {
    return try {
        assetManager.openFd(assetPath).close()
        true
    } catch (_: Throwable) {
        false
    }
}
