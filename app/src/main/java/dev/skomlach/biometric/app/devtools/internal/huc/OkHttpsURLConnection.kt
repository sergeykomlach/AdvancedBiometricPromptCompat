/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dev.skomlach.biometric.app.devtools.internal.huc

import dev.skomlach.biometric.app.devtools.internal.URLFilter
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.internal.platform.Platform
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory

class OkHttpsURLConnection(private val delegate: OkHttpURLConnection) :
    DelegatingHttpsURLConnection(
        delegate
    ) {
    constructor(url: URL?, client: OkHttpClient?) : this(OkHttpURLConnection(url, client))
    constructor(
        url: URL?,
        client: OkHttpClient?,
        filter: URLFilter?
    ) : this(OkHttpURLConnection(url, client, filter))

    override fun handshake(): Handshake? {
        checkNotNull(delegate.call) { "Connection has not yet been established" }
        return delegate.handshake
    }

    override fun getHostnameVerifier(): HostnameVerifier {
        return delegate.client.hostnameVerifier
    }

    override fun setHostnameVerifier(hostnameVerifier: HostnameVerifier) {
        delegate.client = delegate.client.newBuilder()
            .hostnameVerifier(hostnameVerifier)
            .build()
    }

    override fun getSSLSocketFactory(): SSLSocketFactory {
        return delegate.client.sslSocketFactory
    }

    override fun setSSLSocketFactory(sslSocketFactory: SSLSocketFactory) {
        try {
            // This fails in JDK 9 because OkHttp is unable to extract the trust manager.
            delegate.client = delegate.client.newBuilder()
                .sslSocketFactory(
                    sslSocketFactory = sslSocketFactory,
                    trustManager = Platform.get().trustManager(sslSocketFactory)
                        ?: throw IllegalStateException(
                            "Unable to extract the trust manager on ${Platform.get()}, " +
                                    "sslSocketFactory is ${sslSocketFactory.javaClass}"
                        )
                )
                .build()
        } catch (e: Throwable) {
            delegate.client = delegate.client.newBuilder()
                .sslSocketFactory(
                    sslSocketFactory = sslSocketFactory,
                    trustManager = delegate.client.x509TrustManager ?: return
                )
                .build()
        }
    }
}