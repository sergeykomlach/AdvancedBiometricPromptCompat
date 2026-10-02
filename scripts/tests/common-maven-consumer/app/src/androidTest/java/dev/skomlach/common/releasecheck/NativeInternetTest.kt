@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")
package dev.skomlach.common.releasecheck

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.SystemClock
import android.test.InstrumentationTestCase
import dev.skomlach.common.network.AndroidInternetProbe
import dev.skomlach.common.network.InternetProbe
import dev.skomlach.common.network.InternetAccess
import dev.skomlach.common.network.NetworkAvailability
import dev.skomlach.common.network.NetworkApi
import dev.skomlach.common.network.NetworkReachability
import dev.skomlach.common.network.NetworkState
import dev.skomlach.common.network.ProbeResponse
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Real Android socket routing, DNS, certificate trust and hostname verification; no injected TLS. */
class NativeInternetTest : InstrumentationTestCase() {
    override fun setUp() {
        super.setUp()
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("skipNetworkInit", true))
        instrumentation.waitForIdleSync()
    }

    fun testTrustedNumericHttpsUsesRealAndroidTls() = withTlsServer("trusted.p12") { server ->
        assertEquals(ProbeResponse.Http(204), check("https://127.0.0.1:${server.localPort}/"))
    }

    fun testWrongHostnameFailsClosed() = withTlsServer("trusted.p12") { server ->
        assertEquals(ProbeResponse.TlsError, check("https://127.0.0.2:${server.localPort}/"))
    }

    fun testUntrustedCertificateFailsClosed() = withTlsServer("untrusted.p12") { server ->
        assertEquals(ProbeResponse.TlsError, check("https://127.0.0.1:${server.localPort}/"))
    }

    fun testStalledHttpsIsCancelledAtTheTotalDeadline() = withTlsServer("trusted.p12", stall = true) { server ->
        val start = SystemClock.elapsedRealtime()
        assertEquals(ProbeResponse.Unreachable, check("https://127.0.0.1:${server.localPort}/"))
        assertTrue("Stalled request exceeded bounded deadline", SystemClock.elapsedRealtime() - start < 3_000)
    }

    fun testTwoStalledEndpointsCannotStarveTheFourthHealthyEndpoint() {
        withTlsServer("trusted.p12", stall = true) { first ->
            withTlsServer("trusted.p12", stall = true) { second ->
                val deadPort = ServerSocket(0).use { it.localPort }
                withTlsServer("trusted.p12") { healthy ->
                    val cm = instrumentation.targetContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val selected = cm.activeNetwork ?: throw AssertionError("QA device has no default Network")
                    val raw = MutableStateFlow(NetworkState(NetworkAvailability.VALIDATED, selected.networkHandle))
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                    val probe = AndroidInternetProbe { selected }
                    try {
                        val group = InternetProbe(NetworkReachability(raw, { raw.value }, scope), probe,
                            listOf(first.localPort, second.localPort, deadPort, healthy.localPort)
                                .map { "https://127.0.0.1:$it/" })
                        assertTrue(runBlocking {
                            withTimeout(InternetProbe.ROUND_TIMEOUT_MILLIS) { group.check(raw.value) }
                        })
                    } finally { probe.close(); scope.cancel() }
                }
            }
        }
    }

    fun testNumericPacFallsBackToHealthyProxyWithOriginalTlsHostname() {
        val context = tlsContext("trusted.p12")
        ServerSocket(0).use { server ->
            server.soTimeout = 3_000
            val deadPort = ServerSocket(0).use { it.localPort }
            val dead = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", deadPort))
            val healthy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", server.localPort))
            val failed = AtomicInteger()
            val old = ProxySelector.getDefault()
            val worker = Thread {
                try {
                    server.accept().use { raw ->
                        raw.soTimeout = 3_000
                        val connect = readHeaders(raw.inputStream)
                        if (!connect.startsWith("CONNECT probe.test:443")) throw IOException("Wrong CONNECT identity")
                        raw.outputStream.write("HTTP/1.1 200 Connected\r\n\r\n".toByteArray())
                        (context.socketFactory.createSocket(raw, "127.0.0.1", server.localPort, true) as SSLSocket).use { tls ->
                            tls.useClientMode = false
                            tls.startHandshake()
                            readHeaders(tls.inputStream)
                            tls.outputStream.write("HTTP/1.1 204 No Content\r\n\r\n".toByteArray())
                        }
                    }
                } catch (_: IOException) { }
            }.apply { isDaemon = true; start() }
            try {
                ProxySelector.setDefault(object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> = listOf(dead, healthy)
                    override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) { failed.incrementAndGet() }
                })
                assertEquals(ProbeResponse.Http(204), check("https://probe.test/"))
                assertEquals(1, failed.get())
            } finally { ProxySelector.setDefault(old); server.close(); worker.join(1_000) }
        }
    }

    fun testStalledVpnBecomesOfflineAndRecoversWithoutRestart() {
        val context = instrumentation.targetContext
        assertNull("Grant ACTIVATE_VPN on the isolated QA device before this test", VpnService.prepare(context))
        NetworkApi.setInternetCheckEndpoints(listOf("https://www.google.com/generate_204"))
        val states = NetworkApi.networkState
        waitUntil(15_000) { states.value.internetAccess == InternetAccess.AVAILABLE }
        val intent = Intent(context, BlackholeVpnService::class.java)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun vpnIsActive() = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        var recoveryStart = 0L
        try {
            context.startService(intent)
            waitUntil(3_000) { vpnIsActive() }
            val start = SystemClock.elapsedRealtime()
            waitUntil(12_000) { states.value.internetAccess == InternetAccess.UNAVAILABLE }
            assertTrue(NetworkApi.hasNetworkTransport())
            assertFalse(NetworkApi.hasInternet())
            android.util.Log.i("CommonReleaseQA", "BLACKHOLE_VPN_OFFLINE_MS=${SystemClock.elapsedRealtime() - start}")
        } finally {
            // Android keeps VpnService bound; stopService alone does not close its tunnel.
            recoveryStart = SystemClock.elapsedRealtime()
            context.startService(Intent(context, BlackholeVpnService::class.java).setAction(BlackholeVpnService.ACTION_STOP))
            waitUntil(5_000) { !vpnIsActive() }
        }
        waitUntil(12_000) { states.value.internetAccess == InternetAccess.AVAILABLE }
        assertTrue(NetworkApi.hasInternet())
        android.util.Log.i("CommonReleaseQA", "BLACKHOLE_VPN_RECOVERED_MS=${SystemClock.elapsedRealtime() - recoveryStart}")
    }

    private fun check(endpoint: String): ProbeResponse {
        val cm = instrumentation.targetContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: throw AssertionError("QA device has no default Network")
        val probe = AndroidInternetProbe { network }
        val ready = CountDownLatch(1)
        val result = AtomicReference<ProbeResponse>()
        val completions = AtomicInteger()
        try {
            probe.start(endpoint, NetworkState()) { completions.incrementAndGet(); result.set(it); ready.countDown() }
            assertTrue("Probe did not complete", ready.await(3, TimeUnit.SECONDS))
            SystemClock.sleep(50)
            assertEquals("Deadline/result must complete only once", 1, completions.get())
            return result.get()
        } finally { probe.close() }
    }

    private fun tlsContext(asset: String): SSLContext {
        val keys = KeyStore.getInstance("PKCS12")
        instrumentation.context.assets.open(asset).use { keys.load(it, "probe-only".toCharArray()) }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        managers.init(keys, "probe-only".toCharArray())
        return SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
    }

    private fun withTlsServer(asset: String, stall: Boolean = false, test: (SSLServerSocket) -> Unit) {
        val context = tlsContext(asset)
        (context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("0.0.0.0")) as SSLServerSocket).use { server ->
            server.soTimeout = 4_000
            val accepted = AtomicReference<SSLSocket?>()
            val worker = Thread {
                try {
                    (server.accept() as SSLSocket).use { socket ->
                        accepted.set(socket)
                        socket.soTimeout = 4_000
                        socket.startHandshake()
                        val input = socket.inputStream
                        val headers = StringBuilder()
                        while (!headers.endsWith("\r\n\r\n") && headers.length < 4_096) {
                            val value = input.read()
                            if (value < 0) return@Thread
                            headers.append(value.toChar())
                        }
                        if (stall) input.read()
                        else socket.outputStream.write("HTTP/1.1 204 No Content\r\n\r\n".toByteArray())
                    }
                } catch (_: IOException) { }
            }.apply { isDaemon = true; start() }
            try { test(server) }
            finally { accepted.get()?.close(); server.close(); worker.join(1_000) }
        }
    }

    private fun readHeaders(input: java.io.InputStream): String {
        val headers = StringBuilder()
        while (!headers.endsWith("\r\n\r\n") && headers.length < 4_096) {
            val value = input.read()
            if (value < 0) break
            headers.append(value.toChar())
        }
        return headers.toString()
    }

    private fun waitUntil(timeout: Long, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!predicate()) {
            if (SystemClock.elapsedRealtime() >= end) throw AssertionError("State deadline exceeded: $timeout ms")
            SystemClock.sleep(50)
        }
    }
}
