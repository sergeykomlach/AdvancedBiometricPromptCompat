package dev.skomlach.biometric.compat.utils.activityView

import android.annotation.SuppressLint
import android.view.View
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.lang.reflect.Field
import java.lang.reflect.Method

/** WindowInspector replacement only for old or incompatible frameworks. Loaded on demand. */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
internal object LegacyWindowRoots {
    private class Access(
        val manager: Any,
        val roots: Field,
        val getView: Method,
        val stopped: Field?
    )

    private val access: Access? by lazy {
        runCatching {
            val rootClass = Class.forName("android.view.ViewRootImpl")
            val managerClass = Class.forName("android.view.WindowManagerGlobal")
            Access(
                manager = requireNotNull(managerClass.getMethod("getInstance").invoke(null)),
                roots = managerClass.getDeclaredField("mRoots").apply { isAccessible = true },
                getView = rootClass.getMethod("getView"),
                // A missing optional field must not discard all otherwise readable windows.
                stopped = runCatching {
                    rootClass.getDeclaredField("mStopped").apply { isAccessible = true }
                }.getOrNull()
            )
        }.onFailure { BiometricLoggerImpl.e(it, "LegacyWindowRoots") }.getOrNull()
    }

    fun getViews(): List<View> {
        val reader = access ?: return emptyList()
        val roots = runCatching {
            when (val value = reader.roots.get(reader.manager)) {
                is Collection<*> -> value.toList()
                is Array<*> -> value.toList()
                else -> emptyList()
            }
        }.onFailure { BiometricLoggerImpl.e(it, "LegacyWindowRoots") }
            .getOrDefault(emptyList())

        return roots.mapNotNull { root ->
            runCatching {
                if (root == null || reader.stopped?.getBoolean(root) == true) null
                else reader.getView.invoke(root) as? View
            }.onFailure { BiometricLoggerImpl.e(it, "LegacyWindowRoots") }.getOrNull()
        }
    }
}
