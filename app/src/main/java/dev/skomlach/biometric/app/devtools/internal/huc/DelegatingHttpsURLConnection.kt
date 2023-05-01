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

import android.os.Build
import androidx.annotation.RequiresApi
import okhttp3.Handshake
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.security.Permission
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocketFactory

/**
 * Implement an HTTPS connection by delegating to an HTTP connection for everything but the
 * HTTPS-specific stuff.
 */
abstract class DelegatingHttpsURLConnection(private val delegate: HttpURLConnection) :
    HttpsURLConnection(
        delegate.url
    ) {
    protected abstract fun handshake(): Handshake?
    abstract override fun getHostnameVerifier(): HostnameVerifier
    abstract override fun setHostnameVerifier(hostnameVerifier: HostnameVerifier)
    abstract override fun getSSLSocketFactory(): SSLSocketFactory
    abstract override fun setSSLSocketFactory(sslSocketFactory: SSLSocketFactory)
    override fun getCipherSuite(): String? {
        val handshake = handshake()
        return handshake?.cipherSuite?.javaName
    }

    override fun getLocalCertificates(): Array<Certificate>? {
        val handshake = handshake() ?: return null
        val result = handshake.localCertificates
        return if (!result.isEmpty()) result.toTypedArray() else null
    }

    @Throws(SSLPeerUnverifiedException::class)
    override fun getServerCertificates(): Array<Certificate>? {
        val handshake = handshake() ?: return null
        val result = handshake.peerCertificates
        return if (!result.isEmpty()) result.toTypedArray() else null
    }

    @Throws(SSLPeerUnverifiedException::class)
    override fun getPeerPrincipal(): Principal? {
        return handshake()?.peerPrincipal
    }

    override fun getLocalPrincipal(): Principal? {
        return handshake()?.localPrincipal
    }

    @Throws(IOException::class)
    override fun connect() {
        connected = true
        delegate.connect()
    }

    override fun disconnect() {
        delegate.disconnect()
    }

    override fun getErrorStream(): InputStream {
        return delegate.errorStream
    }

    override fun getRequestMethod(): String {
        return delegate.requestMethod
    }

    @Throws(ProtocolException::class)
    override fun setRequestMethod(method: String) {
        delegate.requestMethod = method
    }

    @Throws(IOException::class)
    override fun getResponseCode(): Int {
        return delegate.responseCode
    }

    @Throws(IOException::class)
    override fun getResponseMessage(): String {
        return delegate.responseMessage
    }

    override fun usingProxy(): Boolean {
        return delegate.usingProxy()
    }

    override fun getInstanceFollowRedirects(): Boolean {
        return delegate.instanceFollowRedirects
    }

    override fun setInstanceFollowRedirects(followRedirects: Boolean) {
        delegate.instanceFollowRedirects = followRedirects
    }

    override fun getAllowUserInteraction(): Boolean {
        return delegate.allowUserInteraction
    }

    override fun setAllowUserInteraction(newValue: Boolean) {
        delegate.allowUserInteraction = newValue
    }

    @Throws(IOException::class)
    override fun getContent(): Any {
        return delegate.content
    }

    @Throws(IOException::class)
    override fun getContent(types: Array<Class<*>?>?): Any {
        return delegate.getContent(types)
    }

    override fun getContentEncoding(): String {
        return delegate.contentEncoding
    }

    override fun getContentLength(): Int {
        return delegate.contentLength
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun getContentLengthLong(): Long {
        return delegate.contentLengthLong
    }

    override fun getContentType(): String {
        return delegate.contentType
    }

    override fun getDate(): Long {
        return delegate.date
    }

    override fun getDefaultUseCaches(): Boolean {
        return delegate.defaultUseCaches
    }

    override fun setDefaultUseCaches(newValue: Boolean) {
        delegate.defaultUseCaches = newValue
    }

    override fun getDoInput(): Boolean {
        return delegate.doInput
    }

    override fun setDoInput(newValue: Boolean) {
        delegate.doInput = newValue
    }

    override fun getDoOutput(): Boolean {
        return delegate.doOutput
    }

    override fun setDoOutput(newValue: Boolean) {
        delegate.doOutput = newValue
    }

    override fun getExpiration(): Long {
        return delegate.expiration
    }

    override fun getHeaderField(pos: Int): String {
        return delegate.getHeaderField(pos)
    }

    override fun getHeaderFields(): Map<String, List<String>> {
        return delegate.headerFields
    }

    override fun getRequestProperties(): Map<String, List<String>> {
        return delegate.requestProperties
    }

    override fun addRequestProperty(field: String, newValue: String) {
        delegate.addRequestProperty(field, newValue)
    }

    override fun getHeaderField(key: String): String {
        return delegate.getHeaderField(key)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun getHeaderFieldLong(field: String, defaultValue: Long): Long {
        return delegate.getHeaderFieldLong(field, defaultValue)
    }

    override fun getHeaderFieldDate(field: String, defaultValue: Long): Long {
        return delegate.getHeaderFieldDate(field, defaultValue)
    }

    override fun getHeaderFieldInt(field: String, defaultValue: Int): Int {
        return delegate.getHeaderFieldInt(field, defaultValue)
    }

    override fun getHeaderFieldKey(position: Int): String {
        return delegate.getHeaderFieldKey(position)
    }

    override fun getIfModifiedSince(): Long {
        return delegate.ifModifiedSince
    }

    override fun setIfModifiedSince(newValue: Long) {
        delegate.ifModifiedSince = newValue
    }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        return delegate.inputStream
    }

    override fun getLastModified(): Long {
        return delegate.lastModified
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        return delegate.outputStream
    }

    @Throws(IOException::class)
    override fun getPermission(): Permission {
        return delegate.permission
    }

    override fun getRequestProperty(field: String): String {
        return delegate.getRequestProperty(field)
    }

    override fun getURL(): URL {
        return delegate.url
    }

    override fun getUseCaches(): Boolean {
        return delegate.useCaches
    }

    override fun setUseCaches(newValue: Boolean) {
        delegate.useCaches = newValue
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    override fun setFixedLengthStreamingMode(contentLength: Long) {
        delegate.setFixedLengthStreamingMode(contentLength)
    }

    override fun setRequestProperty(field: String, newValue: String) {
        delegate.setRequestProperty(field, newValue)
    }

    override fun getConnectTimeout(): Int {
        return delegate.connectTimeout
    }

    override fun setConnectTimeout(timeoutMillis: Int) {
        delegate.connectTimeout = timeoutMillis
    }

    override fun getReadTimeout(): Int {
        return delegate.readTimeout
    }

    override fun setReadTimeout(timeoutMillis: Int) {
        delegate.readTimeout = timeoutMillis
    }

    override fun toString(): String {
        return delegate.toString()
    }

    override fun setFixedLengthStreamingMode(contentLength: Int) {
        delegate.setFixedLengthStreamingMode(contentLength)
    }

    override fun setChunkedStreamingMode(chunkLength: Int) {
        delegate.setChunkedStreamingMode(chunkLength)
    }
}