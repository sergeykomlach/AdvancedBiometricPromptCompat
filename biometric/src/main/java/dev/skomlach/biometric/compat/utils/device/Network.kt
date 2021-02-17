package dev.skomlach.biometric.compat.utils.device;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import javax.net.ssl.HttpsURLConnection;

public class Network {

    public static HttpURLConnection createConnection(String link, int timeout) throws Exception {

        URL url = new URL(link).toURI().normalize().toURL();
        HttpURLConnection conn = null;

        if (url.getProtocol().equalsIgnoreCase("https"))
            conn = (HttpsURLConnection) url.openConnection();
        else
            conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        return conn;
    }

    public static void fastCopy(final InputStream src, final OutputStream dest) throws IOException {
        final ReadableByteChannel inputChannel = Channels.newChannel(src);
        final WritableByteChannel outputChannel = Channels.newChannel(dest);
        fastCopy(inputChannel, outputChannel);
        inputChannel.close();
        outputChannel.close();
    }

    public static void fastCopy(final ReadableByteChannel src, final WritableByteChannel dest) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);

        while (src.read(buffer) != -1) {
            buffer.flip();
            dest.write(buffer);
            buffer.compact();
        }

        buffer.flip();

        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }

    public static String resolveUrl(String baseUrl, String relativeUrl) {
        try {
            return new URI(baseUrl).resolve(relativeUrl).toString();
        } catch (Throwable ignore) {}
        return relativeUrl;
    }
}
