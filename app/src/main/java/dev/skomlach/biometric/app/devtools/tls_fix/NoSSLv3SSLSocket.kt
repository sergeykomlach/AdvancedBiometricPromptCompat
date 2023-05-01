package dev.skomlach.biometric.app.devtools.tls_fix

import dev.skomlach.common.logging.LogCat.log
import java.lang.reflect.InvocationTargetException
import java.util.Arrays
import javax.net.ssl.SSLSocket

/**
 * An [SSLSocket] that doesn't allow `SSLv3` only connections
 *
 * fixes https://github.com/koush/ion/issues/386
 */
internal class NoSSLv3SSLSocket(delegate: SSLSocket) : DelegateSSLSocket(delegate) {
    init {
        val canonicalName = delegate.javaClass.canonicalName
        if (!canonicalName.endsWith(".OpenSSLSocketImpl")) {
            // try replicate the code from HttpConnection.setupSecureSocket()
            try {
                val msetUseSessionTickets = delegate.javaClass.getMethod(
                    "setUseSessionTickets",
                    Boolean::class.javaPrimitiveType
                )
                if (null != msetUseSessionTickets) {
                    msetUseSessionTickets.invoke(delegate, true)
                }
            } catch (ignored: NoSuchMethodException) {
            } catch (ignored: InvocationTargetException) {
            } catch (ignored: IllegalAccessException) {
            }
        }
    }

    override fun setEnabledProtocols(protocols: Array<String>) {
        var protocols = protocols
        if (protocols != null && protocols.size == 1 && "SSLv3" == protocols[0]) {
            // no way jose
            // see issue https://code.google.com/p/android/issues/detail?id=78187
            val enabledProtocols: MutableList<String> =
                ArrayList(Arrays.asList(*delegate.enabledProtocols))
            if (enabledProtocols.size > 1) {
                enabledProtocols.remove("SSLv3")
            } else {
                log("SSL stuck with protocol available for $enabledProtocols")
            }
            protocols = enabledProtocols.toTypedArray()
        }
        super.setEnabledProtocols(protocols)
    }
}