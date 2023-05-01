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

import okhttp3.Request
import okio.Buffer
import okio.BufferedSink
import java.io.IOException

/**
 * This request body involves an application thread only. First all bytes are written to the buffer.
 * Only once that is complete are bytes then copied to the network.
 *
 *
 * This body has two special powers. First, it can retransmit the same request body multiple
 * times in order to recover from failures or cope with redirects. Second, it can compute the total
 * length of the request body by measuring it after it has been written to the output stream.
 */
internal class BufferedRequestBody(expectedContentLength: Long) : OutputStreamRequestBody() {
    val buffer = Buffer()
    var contentLength = -1L

    init {
        initOutputStream(buffer, expectedContentLength)
    }

    @Throws(IOException::class)
    override fun contentLength(): Long {
        return contentLength
    }

    /**
     * Now that we've buffered the entire request body, update the request headers and the body
     * itself. This happens late to enable HttpURLConnection users to complete the socket connection
     * before sending request body bytes.
     */
    @Throws(IOException::class)
    override fun prepareToSendRequest(request: Request?): Request? {
        if (request?.header("Content-Length") != null) {
            return request
        }
        outputStream()?.close()
        contentLength = buffer.size
        return request?.newBuilder()
            ?.removeHeader("Transfer-Encoding")
            ?.header("Content-Length", buffer.size.toString())
            ?.build()
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        buffer.copyTo(sink.buffer, 0, buffer.size)
    }
}