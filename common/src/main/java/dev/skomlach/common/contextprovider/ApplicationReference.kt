package dev.skomlach.common.contextprovider

/** Caches the process application and installs callbacks without a main-thread handoff. */
internal class ApplicationReference<T : Any>(
    private val resolve: () -> T?,
    private val onAvailable: (T) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    @Volatile private var application: T? = null
    @Volatile private var ready = false
    private var initializing = false

    fun getOrNull(): T? {
        if (ready) return application
        return synchronized(this) {
            val resolved = application ?: try {
                resolve()?.also { application = it }
            } catch (error: Throwable) {
                onError(error)
                null
            } ?: return@synchronized null

            // Callbacks may access the application again while they are being installed.
            if (!ready && !initializing) {
                initializing = true
                try {
                    onAvailable(resolved)
                    ready = true
                } catch (error: Throwable) {
                    onError(error)
                } finally {
                    initializing = false
                }
            }
            resolved
        }
    }
}
