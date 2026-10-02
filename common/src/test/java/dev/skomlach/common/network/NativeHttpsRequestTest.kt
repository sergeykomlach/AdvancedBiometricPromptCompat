package dev.skomlach.common.network

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.SocketFactory
import javax.net.ssl.SSLPeerUnverifiedException

/** Loopback transport tests. TLS is injected; Android's real TLS policy needs device QA. */
class NativeHttpsRequestTest {
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test(timeout = 5_000)
    fun authenticatedHttpErrorsAndRedirectsAreReportedWithoutFollowingOrReadingBodies() {
        for (status in listOf(204, 401, 503, 302)) {
            withServer({ socket ->
                val request = readHeaders(socket)
                socket.outputStream.write(("HTTP/1.1 $status Reply\r\nLocation: https://other.test/\r\n" +
                    "Set-Cookie: secret=value\r\nContent-Length: 999999\r\n\r\n").toByteArray())
                request
            }) { server, received ->
                val request = request(server)
                assertEquals(ProbeResponse.Http(status), request.execute())
                val headers = received.get(1, TimeUnit.SECONDS)
                assertTrue(headers.startsWith("GET /probe HTTP/1.1\r\nHost: example.test:443\r\n"))
                assertTrue(headers.contains("Connection: close\r\n"))
                assertFalse(headers.contains("Cookie:"))
                assertFalse(headers.contains("Authorization:"))
            }
        }
    }

    @Test(timeout = 5_000)
    fun cancellationClosesAStalledResponseSocket() {
        val started = CountDownLatch(1)
        withServer({ socket ->
            readHeaders(socket)
            started.countDown()
            assertEquals(-1, socket.inputStream.read())
            ""
        }) { server, received ->
            val request = request(server)
            val pending = FutureTask { request.execute() }
            Thread(pending).apply { isDaemon = true }.start()
            assertTrue(started.await(1, TimeUnit.SECONDS))
            request.cancel()
            val error = assertThrows(ExecutionException::class.java) { pending.get(1, TimeUnit.SECONDS) }
            assertTrue(error.cause is IOException)
            received.get(1, TimeUnit.SECONDS)
        }
    }

    @Test(timeout = 5_000)
    fun aSlowTrickleCannotExtendTheTotalResponseDeadline() {
        withServer({ socket ->
            readHeaders(socket)
            try {
                for (character in "HTTP/1.1 204 Reply\r\n") {
                    socket.outputStream.write(character.code)
                    Thread.sleep(30)
                }
            } catch (_: IOException) { }
            ""
        }) { server, _ ->
            val request = request(server, timeout = 150)
            assertThrows(SocketTimeoutException::class.java) { request.execute() }
        }
    }

    @Test(timeout = 5_000)
    fun malformedOrOversizedResponsesDoNotProveInternetAccess() {
        for (reply in listOf("HTTP/2 200 Reply\r\n", "HTTP/1.1 999 Reply\r\n",
            "HTTP/1.1 200 Reply\n", "HTTP/1.1 200 " + "x".repeat(4_096) + "\r\n")) {
            withServer({ socket ->
                readHeaders(socket)
                try { socket.outputStream.write(reply.toByteArray()) } catch (_: IOException) { }
                ""
            }) { server, _ -> assertThrows(IOException::class.java) { request(server).execute() } }
        }
    }

    @Test(timeout = 5_000)
    fun tlsFailurePreventsSendingTheHttpRequest() {
        withServer({ socket -> readHeaders(socket) }) { server, received ->
            val request = request(server, tls = { _, host, _, _ ->
                assertEquals("example.test", host)
                throw SSLPeerUnverifiedException("Rejected certificate")
            })
            assertThrows(SSLPeerUnverifiedException::class.java) { request.execute() }
            assertEquals("", received.get(1, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun failedFirstAddressFallsBackWithinTheSameSelectedSocketFactory() {
        val attempts = AtomicInteger()
        withServer({ socket ->
            readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 204 Reply\r\n".toByteArray())
            ""
        }) { server, _ ->
            val factory = sockets(server) {
                if (attempts.incrementAndGet() == 1) throw SocketTimeoutException("First family stalled")
            }
            val dns = dns(listOf(InetAddress.getByAddress(ByteArray(16).apply { this[15] = 1 }), loopback))
            val request = NativeHttpsRequest("https://example.test/probe", factory, dns, { socket, _, _, _ -> socket })
            assertEquals(ProbeResponse.Http(204), request.execute())
            assertEquals(2, attempts.get())
        }
    }

    @Test(timeout = 5_000)
    fun proxyAuthenticationFailureIsNotAnAuthenticatedEndpointResponse() {
        val tlsCalls = AtomicInteger()
        withServer({ socket ->
            val connect = readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 407 Auth required\r\n\r\n".toByteArray())
            connect
        }) { server, received ->
            val request = request(server, proxy = Proxy(Proxy.Type.HTTP,
                InetSocketAddress.createUnresolved("proxy.test", 8080)), tls = { socket, _, _, _ ->
                tlsCalls.incrementAndGet(); socket
            })
            assertThrows(IOException::class.java) { request.execute() }
            assertEquals(0, tlsCalls.get())
            assertEquals("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n\r\n",
                received.get(1, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun successfulHttpProxyUsesTheOriginalHostnameForTls() {
        withServer({ socket ->
            val connect = readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 200 Connected\r\nProxy-Agent: test\r\n\r\n".toByteArray())
            val get = readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 503 Reply\r\n".toByteArray())
            connect + get
        }) { server, received ->
            val request = request(server, proxy = Proxy(Proxy.Type.HTTP,
                InetSocketAddress.createUnresolved("proxy.test", 8080)), tls = { socket, host, port, _ ->
                assertEquals("example.test", host)
                assertEquals(443, port)
                socket
            })
            assertEquals(ProbeResponse.Http(503), request.execute())
            assertTrue(received.get(1, TimeUnit.SECONDS).contains("GET /probe HTTP/1.1"))
        }
    }

    @Test(timeout = 5_000)
    fun resolvedAndNumericProxyAddressesNeverUseDns() {
        val namedAddress = InetAddress.getByAddress("proxy.test", loopback.address)
        for (address in listOf(InetSocketAddress(namedAddress, 8080),
            InetSocketAddress.createUnresolved("127.0.0.1", 8080))) {
            withServer({ socket ->
                val connect = readHeaders(socket)
                socket.outputStream.write("HTTP/1.1 200 Connected\r\n\r\n".toByteArray())
                val get = readHeaders(socket)
                socket.outputStream.write("HTTP/1.1 204 Reply\r\n".toByteArray())
                connect + get
            }) { server, received ->
                val request = NativeHttpsRequest("https://example.test/probe", sockets(server,
                    observeConnection = { target, _ -> assertEquals(loopback, target.address) }),
                    noDns(), { socket, host, _, _ -> assertEquals("example.test", host); socket },
                    Proxy(Proxy.Type.HTTP, address))
                assertEquals(ProbeResponse.Http(204), request.execute())
                assertTrue(received.get(1, TimeUnit.SECONDS).contains("CONNECT example.test:443"))
            }
        }
    }

    @Test(timeout = 5_000)
    fun ipv4AndIpv6EndpointsAreParsedLocallyAndRetainTheirTlsIdentity() {
        for (host in listOf("127.0.0.1", "::1", "2001:db8::1", "2001:db8:0:0:0:0:0:1", "::ffff:127.0.0.1")) {
            withServer({ socket ->
                val get = readHeaders(socket)
                socket.outputStream.write("HTTP/1.1 204 Reply\r\n".toByteArray())
                get
            }) { server, received ->
                val authority = if (host.contains(':')) "[$host]" else host
                val request = NativeHttpsRequest("https://$authority/probe", sockets(server), noDns(),
                    { socket, tlsHost, _, _ -> assertEquals(host, tlsHost); socket })
                assertEquals(ProbeResponse.Http(204), request.execute())
                assertTrue(received.get(1, TimeUnit.SECONDS).contains("Host: $authority:443"))
            }
        }
    }

    @Test(timeout = 5_000)
    fun aLongPreferredFamilyCannotExcludeTheWorkingOtherFamily() {
        val attempts = AtomicInteger()
        val ipv6 = (1..5).map { suffix -> InetAddress.getByAddress(ByteArray(16).apply {
            this[0] = 0x20; this[1] = 0x01; this[15] = suffix.toByte()
        }) }
        withServer({ socket ->
            readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 204 Reply\r\n".toByteArray())
            ""
        }) { server, _ ->
            val factory = sockets(server, observeConnection = { target, _ ->
                attempts.incrementAndGet()
                if (target.address.address.size == 16) throw SocketTimeoutException("IPv6 black hole")
            })
            val request = NativeHttpsRequest("https://example.test/probe", factory, dns(ipv6 + loopback),
                { socket, _, _, _ -> socket })
            assertEquals(ProbeResponse.Http(204), request.execute())
            assertEquals(2, attempts.get())
        }
    }

    @Test(timeout = 5_000)
    fun pacFallbackUsesHealthyProxyAndReportsTheFailedProxy() {
        val dead = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("dead.test", 8080))
        val healthy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("healthy.test", 8081))
        val selector = selector(listOf(dead, healthy))
        val attempts = mutableListOf<Int>()
        withServer({ socket ->
            val connect = readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 200 Connected\r\n\r\n".toByteArray())
            val get = readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 204 Reply\r\n".toByteArray())
            connect + get
        }) { server, received ->
            val request = NativeHttpsRequest("https://example.test/probe", sockets(server,
                observeConnection = { target, _ ->
                    attempts += target.port
                    if (target.port == 8080) throw IOException("Dead proxy")
                }), dns(listOf(loopback)), { socket, host, _, _ ->
                    assertEquals("example.test", host); socket
                }, proxySelector = selector)
            assertEquals(ProbeResponse.Http(204), request.execute())
            assertEquals(listOf(8080, 8081), attempts)
            assertEquals(listOf(dead.address()), selector.failed)
            assertTrue(received.get(1, TimeUnit.SECONDS).startsWith("CONNECT example.test:443"))
        }
    }

    @Test(timeout = 5_000)
    fun directFallbackIsUsedOnlyWhenReturnedByTheSelector() {
        val dead = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("dead.test", 8080))
        for (allowDirect in listOf(false, true)) {
            val attempts = mutableListOf<Int>()
            withServer({ socket ->
                val get = readHeaders(socket)
                socket.outputStream.write("HTTP/1.1 204 Reply\r\n".toByteArray())
                get
            }) { server, received ->
                val request = NativeHttpsRequest("https://example.test/probe", sockets(server,
                    observeConnection = { target, _ ->
                        attempts += target.port
                        if (target.port == 8080) throw IOException("Dead proxy")
                    }), dns(listOf(loopback)), { socket, _, _, _ -> socket },
                    proxySelector = selector(if (allowDirect) listOf(dead, Proxy.NO_PROXY) else listOf(dead)))
                if (allowDirect) {
                    assertEquals(ProbeResponse.Http(204), request.execute())
                    assertEquals(listOf(8080, 443), attempts)
                    assertTrue(received.get(1, TimeUnit.SECONDS).startsWith("GET /probe"))
                } else {
                    assertThrows(IOException::class.java) { request.execute() }
                    assertEquals(listOf(8080), attempts)
                }
            }
        }
    }

    @Test(timeout = 5_000)
    fun pacAlternativesShareOneDeadline() {
        val clock = AtomicLong()
        val budgets = mutableListOf<Int>()
        withServer({ "" }) { server, _ ->
            val request = NativeHttpsRequest("https://example.test/probe", sockets(server,
                observeConnection = { _, timeout ->
                    budgets += timeout
                    clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(timeout.toLong()))
                    throw SocketTimeoutException("Dead proxy")
                }), dns(listOf(loopback)), { socket, _, _, _ -> socket }, timeoutMillis = 300,
                nanoTime = clock::get, proxySelector = selector((1..3).map { port ->
                    Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.test", 8080 + port))
                }))
            assertThrows(IOException::class.java) { request.execute() }
            assertEquals(listOf(100, 100, 100), budgets)
            assertEquals(TimeUnit.MILLISECONDS.toNanos(300), clock.get())
        }
    }

    @Test(timeout = 5_000)
    fun stalledTlsCannotConsumeTheHealthyPacAlternativesDeadline() {
        ServerSocket(0, 2, loopback).use { server ->
            server.soTimeout = 2_000
            val received = FutureTask {
                repeat(2) { index ->
                    server.accept().use { socket ->
                        readHeaders(socket)
                        socket.outputStream.write("HTTP/1.1 200 Connected\r\n\r\n".toByteArray())
                        if (index == 0) assertEquals(-1, socket.inputStream.read())
                        else {
                            assertTrue(readHeaders(socket).startsWith("GET /probe"))
                            socket.outputStream.write("HTTP/1.1 204 Reply\r\n".toByteArray())
                        }
                    }
                }
            }
            val worker = Thread(received).apply { isDaemon = true; start() }
            val tlsCalls = AtomicInteger()
            val selected = selector((1..2).map { port -> Proxy(Proxy.Type.HTTP,
                InetSocketAddress.createUnresolved("proxy.test", 8080 + port)) })
            try {
                val request = NativeHttpsRequest("https://example.test/probe", sockets(server), dns(listOf(loopback)),
                    { socket, _, _, _ ->
                        if (tlsCalls.incrementAndGet() == 1) {
                            while (!socket.isClosed) Thread.sleep(5)
                            throw SocketTimeoutException("TLS stalled until the route socket was closed")
                        }
                        socket
                    }, timeoutMillis = 1_200, proxySelector = selected)
                assertEquals(ProbeResponse.Http(204), request.execute())
                assertEquals(2, tlsCalls.get())
                received.get(1, TimeUnit.SECONDS)
            } finally { server.close(); worker.join(1_000) }
        }
    }

    @Test(timeout = 5_000)
    fun pacDnsTimeoutKeepsTheResolverUsableForTheFallback() {
        val cancelledQueries = AtomicInteger()
        val selected = selector(listOf(
            Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("dead.test", 8080)),
            Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("healthy.test", 8081))))
        val resolver = CancellableDns(AsyncDnsLookup { host, complete ->
            if (host == "healthy.test") complete(listOf(loopback), null)
            ProbeCancellation { cancelledQueries.incrementAndGet() }
        }, timeoutMillis = 1_500)
        withServer({ socket ->
            readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 200 Connected\r\n\r\n".toByteArray())
            readHeaders(socket)
            socket.outputStream.write("HTTP/1.1 204 Reply\r\n".toByteArray())
            ""
        }) { server, received ->
            val request = NativeHttpsRequest("https://example.test/probe", sockets(server), resolver,
                { socket, _, _, _ -> socket }, timeoutMillis = 1_200, proxySelector = selected)
            assertEquals(ProbeResponse.Http(204), request.execute())
            assertEquals(1, cancelledQueries.get())
            assertEquals(1, selected.failed.size)
            received.get(1, TimeUnit.SECONDS)
        }
    }

    private class TestSelector(private val candidates: List<Proxy>) : ProxySelector() {
        val failed = mutableListOf<SocketAddress>()
        override fun select(uri: URI): List<Proxy> = candidates
        override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) { failed += address }
    }

    private fun selector(candidates: List<Proxy>) = TestSelector(candidates)

    private fun noDns() = CancellableDns(AsyncDnsLookup { host, _ ->
        throw AssertionError("Numeric or resolved target must not reach DNS: $host")
    })

    private fun request(server: ServerSocket, timeout: Long = 2_000, proxy: Proxy = Proxy.NO_PROXY,
        tls: (Socket, String, Int, Int) -> Socket = { socket, _, _, _ -> socket }) =
        NativeHttpsRequest("https://example.test/probe", sockets(server), dns(listOf(loopback)), tls, proxy, timeout)

    private fun dns(addresses: List<InetAddress>) = CancellableDns(AsyncDnsLookup { _, complete ->
        complete(addresses, null)
        ProbeCancellation { }
    })

    private fun sockets(server: ServerSocket,
        observeConnection: (InetSocketAddress, Int) -> Unit = { _, _ -> },
        beforeConnect: () -> Unit = { }) = object : SocketFactory() {
        override fun createSocket(): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress, timeout: Int) {
                beforeConnect()
                observeConnection(endpoint as InetSocketAddress, timeout)
                super.connect(InetSocketAddress(loopback, server.localPort), timeout)
            }
        }
        override fun createSocket(host: String, port: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(host: InetAddress, port: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = throw UnsupportedOperationException()
    }

    private fun readHeaders(socket: Socket): String {
        socket.soTimeout = 2_000
        val input = socket.inputStream
        val headers = StringBuilder()
        while (headers.length < 16_384) {
            val value = input.read()
            if (value == -1) break
            headers.append(value.toChar())
            if (headers.endsWith("\r\n\r\n")) break
        }
        return headers.toString()
    }

    private fun withServer(reply: (Socket) -> String, test: (ServerSocket, FutureTask<String>) -> Unit) {
        ServerSocket(0, 1, loopback).use { server ->
            server.soTimeout = 2_000
            val received = FutureTask { server.accept().use(reply) }
            val worker = Thread(received).apply { isDaemon = true; start() }
            try { test(server, received) }
            finally { server.close(); worker.join(2_000) }
        }
    }
}
