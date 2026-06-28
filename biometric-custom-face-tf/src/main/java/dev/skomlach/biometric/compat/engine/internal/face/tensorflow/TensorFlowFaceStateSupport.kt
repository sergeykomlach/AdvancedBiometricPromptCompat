package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.content.res.AssetManager
import org.json.JSONObject

internal const val REGISTERED_TEMPLATES_PREF_KEY = "registered"

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
