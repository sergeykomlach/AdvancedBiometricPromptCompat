package dev.skomlach.biometric.compat.utils.device

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import javax.net.ssl.HttpsURLConnection

object Network {
    @Throws(Exception::class)
    fun createConnection(link: String?, timeout: Int): HttpURLConnection {
        val url = URL(link).toURI().normalize().toURL()
        val conn = if (url.protocol.equals(
                "https",
                ignoreCase = true
            )
        ) url.openConnection() as HttpsURLConnection else url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        return conn
    }

    @Throws(IOException::class)
    fun fastCopy(src: InputStream?, dest: OutputStream?) {
        val inputChannel = Channels.newChannel(src)
        val outputChannel = Channels.newChannel(dest)
        fastCopy(inputChannel, outputChannel)
        inputChannel.close()
        outputChannel.close()
    }

    @Throws(IOException::class)
    fun fastCopy(src: ReadableByteChannel, dest: WritableByteChannel) {
        val buffer = ByteBuffer.allocateDirect(16 * 1024)
        while (src.read(buffer) != -1) {
            buffer.flip()
            dest.write(buffer)
            buffer.compact()
        }
        buffer.flip()
        while (buffer.hasRemaining()) {
            dest.write(buffer)
        }
    }

    fun resolveUrl(baseUrl: String?, relativeUrl: String): String {
        try {
            return URI(baseUrl).resolve(relativeUrl).toString()
        } catch (ignore: Throwable) {
        }
        return relativeUrl
    }
}