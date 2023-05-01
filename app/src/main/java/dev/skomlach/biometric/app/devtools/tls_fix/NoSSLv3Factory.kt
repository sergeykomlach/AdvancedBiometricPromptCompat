package dev.skomlach.biometric.app.devtools.tls_fix

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * [SSLSocketFactory] that doesn't allow `SSLv3` only connections
 */
internal class NoSSLv3Factory : SSLSocketFactory() {
    private val delegate: SSLSocketFactory

    init {
        delegate = HttpsURLConnection.getDefaultSSLSocketFactory()
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return delegate.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return makeSocketSafe(delegate.createSocket(s, host, port, autoClose))
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int): Socket {
        return makeSocketSafe(delegate.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): Socket {
        return makeSocketSafe(delegate.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        return makeSocketSafe(delegate.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket {
        return makeSocketSafe(delegate.createSocket(address, port, localAddress, localPort))
    }

    companion object {
        private fun makeSocketSafe(socket: Socket): Socket {
            var socket = socket
            if (socket is SSLSocket) {
                socket = NoSSLv3SSLSocket(socket)
            }
            return socket
        }
    }
}