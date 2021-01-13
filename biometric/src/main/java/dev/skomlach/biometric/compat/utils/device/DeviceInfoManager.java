package dev.skomlach.biometric.compat.utils.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider;

public class DeviceInfoManager {

    public static DeviceInfoManager INSTANCE = new DeviceInfoManager();
    final String model = Build.BRAND + " " + Build.MODEL;
    private final String[] agents = new String[]{"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/602.2.14 (KHTML, like Gecko) Version/10.0.1 Safari/602.2.14",
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36",
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36",
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:50.0) Gecko/20100101 Firefox/50.0"};

    private DeviceInfoManager() {
        BiometricLoggerImpl.e("DevicesWithKnownBugs: Device Mode: " + model);
    }

    @WorkerThread
    public void getDeviceInfo(OnDeviceInfoListener onDeviceInfoListener) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread())
            throw new IllegalThreadStateException("Worker thread required");

        DeviceInfo deviceInfo = getCachedDeviceInfo();
        if (deviceInfo == null) {
            deviceInfo = loadDeviceInfo(model);
        }
        BiometricLoggerImpl.e("DevicesWithKnownBugs: " + deviceInfo);
        onDeviceInfoListener.onReady(deviceInfo);
    }

    @Nullable
    private DeviceInfo getCachedDeviceInfo() {
        SharedPreferences sharedPreferences = SharedPreferenceProvider.getCryptoPreferences("DeviceInfo-"+model);
        if (sharedPreferences.getBoolean("checked", false)) {
            boolean hasIris = sharedPreferences.getBoolean("hasIris", false);
            boolean hasFace = sharedPreferences.getBoolean("hasFace", false);
            boolean hasFingerprint = sharedPreferences.getBoolean("hasFingerprint", false);
            boolean hasUnderDisplayFingerprint = sharedPreferences.getBoolean("hasUnderDisplayFingerprint", false);
            return new DeviceInfo(hasIris, hasFace, hasFingerprint, hasUnderDisplayFingerprint);
        }
        return null;
    }

    private void setCachedDeviceInfo(@NonNull DeviceInfo deviceInfo) {
        SharedPreferences.Editor sharedPreferences = SharedPreferenceProvider.getCryptoPreferences("DeviceInfo-"+model).edit();
        sharedPreferences.putBoolean("hasIris", deviceInfo.getHasIris())
                .putBoolean("hasFace", deviceInfo.getHasFace())
                .putBoolean("hasFingerprint", deviceInfo.getHasFingerprint())
                .putBoolean("hasUnderDisplayFingerprint", deviceInfo.getHasUnderDisplayFingerprint())
                .putBoolean("checked", true).apply();
    }

    @Nullable
    private DeviceInfo loadDeviceInfo(String model) {

        try {

            String url = "https://m.gsmarena.com/res.php3?sSearch=" + URLEncoder.encode(model);

            String html = getHtml(url);
            if (html == null)
                return null;

            String detailsLink = getDetailsLink(url, html, model);

            //not found
            if (detailsLink == null) {
                final DeviceInfo deviceInfo = new DeviceInfo();
                setCachedDeviceInfo(deviceInfo);
                return deviceInfo;
            }

            BiometricLoggerImpl.e("DevicesWithKnownBugs: Link: " + detailsLink);

            html = getHtml(detailsLink);

            if (html == null)
                return null;

            List<String> l = getSensorDetails(html);

            BiometricLoggerImpl.e("DevicesWithKnownBugs: Sensors: " + l);

            boolean hasIris = false;
            boolean hasFace = false;
            boolean hasFingerprint = false;
            boolean hasUnderDisplayFingerprint = false;

            for (String s : l) {
                s = s.toLowerCase();

                if (s.contains("fingerprint")) {
                    hasFingerprint = true;
                    if (s.contains("display")) {
                        hasUnderDisplayFingerprint = true;
                    }
                } else if (s.contains(" id") || s.contains(" recognition") || s.contains(" unlock") || s.contains(" auth")) {
                    if (s.contains("iris")) {
                        hasIris = true;
                    }
                    if (s.contains("face")) {
                        hasFace = true;
                    }
                }
            }
            final DeviceInfo deviceInfo = new DeviceInfo(hasIris, hasFace, hasFingerprint, hasUnderDisplayFingerprint);
            setCachedDeviceInfo(deviceInfo);

            return deviceInfo;
        } catch (Throwable e) {
            return null;
        }
    }

    //parser
    private List<String> getSensorDetails(String html) {

        List<String> list = new ArrayList<>();
        if (html != null) {
            Document doc = Jsoup.parse(html);
            Element body = doc.body().getElementById("content");
            Elements rElements = body.getElementsByAttribute("data-spec");
            for (int i = 0; i < rElements.size(); i++) {
                Element element = rElements.get(i);
                if (element.attr("data-spec").equals("sensors")) {
                    String name = element.text();
                    if (!TextUtils.isEmpty(name)) {
                        String[] split = name.split(",");
                        for (String s : split) {
                            list.add(s.trim());
                        }
                    }
                }
            }
        }

        return list;
    }

    private String getDetailsLink(String url, String html, String model) {

        if (html != null) {
            Document doc = Jsoup.parse(html);
            Element body = doc.body().getElementById("content");
            Elements rElements = body.getElementsByTag("a");
            for (int i = 0; i < rElements.size(); i++) {
                Element element = rElements.get(i);

                String name = element.text();

                if (TextUtils.isEmpty(name)) {
                    continue;
                }
                if (name.equalsIgnoreCase(model)) {
                    return resolveUrl(url, element.attr("href"));
                }
            }
        }

        return null;
    }

    //tools
    private String getHtml(String url) {
        try {
            HttpURLConnection urlConnection = null;
            ConnectivityManager connectivityManager = (ConnectivityManager) AndroidContext.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager.getActiveNetworkInfo().isConnectedOrConnecting()) {
                try {
                    urlConnection = createConnection(url, (int) TimeUnit.SECONDS.toMillis(30));

                    urlConnection.setRequestMethod("GET");

                    urlConnection.setRequestProperty("Content-Language", "en-US");
                    urlConnection.setRequestProperty("Accept-Language", "en-US");
                    urlConnection.setRequestProperty("User-Agent", agents[new SecureRandom().nextInt(agents.length)]);

                    urlConnection.connect();

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    InputStream inputStream = null;

                    inputStream = urlConnection.getInputStream();
                    if (inputStream == null)
                        inputStream = urlConnection.getErrorStream();

                    fastCopy(inputStream, byteArrayOutputStream);

                    inputStream.close();

                    byte[] data = byteArrayOutputStream.toByteArray();
                    byteArrayOutputStream.close();

                    return new String(data, "UTF-8");
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                        urlConnection = null;
                    }
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        return null;
    }

    public HttpURLConnection createConnection(String link, int timeout) throws Exception {

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

    public void fastCopy(final InputStream src, final OutputStream dest) throws IOException {
        final ReadableByteChannel inputChannel = Channels.newChannel(src);
        final WritableByteChannel outputChannel = Channels.newChannel(dest);
        fastCopy(inputChannel, outputChannel);
        inputChannel.close();
        outputChannel.close();
    }

    public void fastCopy(final ReadableByteChannel src, final WritableByteChannel dest) throws IOException {
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

    private String resolveUrl(String baseUrl, String relativeUrl) {
        try {
            return new URI(baseUrl).resolve(relativeUrl).toString();
        } catch (Throwable ignore) {}
        return relativeUrl;
    }

    public interface OnDeviceInfoListener {
        void onReady(@Nullable DeviceInfo deviceInfo);
    }
}
