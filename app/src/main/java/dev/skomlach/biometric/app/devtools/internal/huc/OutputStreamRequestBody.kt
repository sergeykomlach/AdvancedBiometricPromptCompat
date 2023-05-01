/*
 * Copyright (C) 2016 Square, Inc.
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
package dev.skomlach.biometric.app.devtools.internal.huc

import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Timeout
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.ProtocolException
import java.net.SocketTimeoutException

/**
 * A request body that's populated by blocking writes to an output stream. The output data is either
 * fully buffered (with [BufferedRequestBody]) or streamed (with [StreamedRequestBody]).
 * In either case the bytes of the body aren't known until the caller writes them to the output
 * stream.
 */
internal abstract class OutputStreamRequestBody : RequestBody() {
    var isClosed = false
    private var timeout: Timeout? = null
    private var expectedContentLength: Long = 0
    private var outputStream: OutputStream? = null
    protected fun initOutputStream(sink: BufferedSink, expectedContentLength: Long) {
        timeout = sink.timeout()
        this.expectedContentLength = expectedContentLength

        // An output stream that writes to sink. If expectedContentLength is not -1, then this expects
        // exactly that many bytes to be written.
        outputStream = object : OutputStream() {
            private var bytesReceived: Long = 0

            @Throws(IOException::class)
            override fun write(b: Int) {
                write(byteArrayOf(b.toByte()), 0, 1)
            }

            @Throws(IOException::class)
            override fun write(source: ByteArray, offset: Int, byteCount: Int) {
                if (isClosed) {
                    throw IOException("closed") // Not IllegalStateException!
                }
                if (expectedContentLength != -1L && bytesReceived + byteCount > expectedContentLength) {
                    throw ProtocolException(
                        "expected " + expectedContentLength
                                + " bytes but received " + bytesReceived + byteCount
                    )
                }
                bytesReceived += byteCount.toLong()
                try {
                    sink.write(source, offset, byteCount)
                } catch (e: InterruptedIOException) {
                    throw SocketTimeoutException(e.message)
                }
            }

            @Throws(IOException::class)
            override fun flush() {
                if (isClosed) {
                    return  // Weird, but consistent with historical behavior.
                }
                sink.flush()
            }

            @Throws(IOException::class)
            override fun close() {
                isClosed = true
                if (expectedContentLength != -1L && bytesReceived < expectedContentLength) {
                    throw ProtocolException(
                        "expected " + expectedContentLength
                                + " bytes but received " + bytesReceived
                    )
                }
                sink.close()
            }
        }
    }

    fun outputStream(): OutputStream? {
        return outputStream
    }

    fun timeout(): Timeout? {
        return timeout
    }

    @Throws(IOException::class)
    override fun contentLength(): Long {
        return expectedContentLength
    }

    override fun contentType(): MediaType? {
        return null // Let the caller provide this in a regular header.
    }

    @Throws(IOException::class)
    open fun prepareToSendRequest(request: Request?): Request? {
        return request
    }
}