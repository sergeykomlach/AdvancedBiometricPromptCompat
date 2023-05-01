/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.skomlach.biometric.app.devtools

import dev.skomlach.biometric.app.devtools.internal.huc.OkHttpURLConnection
import dev.skomlach.biometric.app.devtools.internal.huc.OkHttpsURLConnection
import dev.skomlach.biometric.app.devtools.tls_fix.NoSSLv3Fix
import dev.skomlach.common.logging.LogCat.logError
import dev.skomlach.common.logging.LogCat.logException
import okhttp3.JavaNetAuthenticator
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.ProxySelector
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

class OkUrlFactory(private val client: OkHttpClient) : URLStreamHandlerFactory, Cloneable {

    /**
     * Returns a copy of this stream handler factory that includes a shallow copy of the internal
     * [HTTP client][OkHttpClient].
     */
    public override fun clone(): OkUrlFactory {
        return OkUrlFactory(client)
    }

    @JvmOverloads
    fun open(url: URL, proxy: Proxy? = client.proxy): HttpURLConnection {
        var proxy = proxy
        val protocol = url.protocol
        if (Proxy.NO_PROXY == proxy) {
            var prx = Proxy.NO_PROXY
            try {
                val proxyList = ProxySelector.getDefault().select(url.toURI())
                if (Proxy.NO_PROXY != proxyList[0]) {
                    prx = proxyList[0]
                }
            } catch (e: Throwable) {
                logException(e)
            }
            if (Proxy.NO_PROXY != prx) {
                proxy = prx
            }
        }
        val copy = client.newBuilder()
            .proxy(proxy)
            .build()
        var conn: HttpURLConnection? = null
        conn = when (protocol) {
            "http" -> OkHttpURLConnection(url, copy, null)
            "https" -> OkHttpsURLConnection(url, copy, null)
            else -> throw IllegalArgumentException("Unexpected protocol: $protocol")
        }
        return conn
    }

    /**
     * Creates a URLStreamHandler as a [URL.setURLStreamHandlerFactory].
     *
     *
     *
     * This code configures OkHttp to handle all HTTP and HTTPS connections
     * created with [URL.openConnection]: <pre>   `<p>
     * OkHttpClient okHttpClient = new OkHttpClient();
     * URL.setURLStreamHandlerFactory(new OkUrlFactory(okHttpClient));
    `</pre> *
     */
    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
        return if (protocol != "http" && protocol != "https") {
            null
        } else object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection {
                return open(url)
            }

            override fun openConnection(url: URL, proxy: Proxy): URLConnection {
                return open(url, proxy)
            }

            override fun getDefaultPort(): Int {
                if (protocol == "http") {
                    return 80
                }
                if (protocol == "https") {
                    return 443
                }
                throw AssertionError()
            }
        }
    }

    companion object {
        var factory: OkUrlFactory? = null

        init {
            NoSSLv3Fix.setFix()
            HttpURLConnection.setFollowRedirects(true)
        }

        fun setURLStreamHandlerFactory() {

            //avoid double call
            if (factory == null) {
                run {
                    try {
                        val javaNetAuthenticator = JavaNetAuthenticator()
                        val clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
                            .proxyAuthenticator(javaNetAuthenticator)
                            .authenticator(javaNetAuthenticator)
                            .cookieJar(
                                JavaNetCookieJar(
                                    CookieManager(
                                        null,
                                        CookiePolicy.ACCEPT_ORIGINAL_SERVER
                                    )
                                )
                            )
                        val client: OkHttpClient = clientBuilder.build()
                        factory = OkUrlFactory(client)
                        URL.setURLStreamHandlerFactory(factory)
                        logError("OkHttp3 now is default network handler")
                    } catch (throwable: Throwable) {
                        logException(throwable)
                    }
                }
            }
        }
    }
}