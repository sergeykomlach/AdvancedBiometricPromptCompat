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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider;

import static dev.skomlach.biometric.compat.utils.device.Network.createConnection;
import static dev.skomlach.biometric.compat.utils.device.Network.fastCopy;
import static dev.skomlach.biometric.compat.utils.device.Network.resolveUrl;

public class DeviceInfoManager {

    public static DeviceInfoManager INSTANCE = new DeviceInfoManager();
    private final String deviceModel = AndroidModel.INSTANCE.capitalize(Build.BRAND) + " " + Build.MODEL;

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
    }

    @WorkerThread
    public void getDeviceInfo(OnDeviceInfoListener onDeviceInfoListener) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread())
            throw new IllegalThreadStateException("Worker thread required");

        DeviceInfo deviceInfo = getCachedDeviceInfo();
        if (deviceInfo == null) {
            deviceInfo = loadDeviceInfo(deviceModel);
        }

        if (deviceInfo == null || !deviceInfo.getExistsInDatabase()) {
            AndroidModel.INSTANCE.getAsync(AndroidContext.getAppContext(), m -> {
                DeviceInfo info = loadDeviceInfo(m);
                BiometricLoggerImpl.e("DeviceInfoManager: " + m +" -> "+ info);
                if(info!=null) {
                    setCachedDeviceInfo(info);
                }
                onDeviceInfoListener.onReady(info);
            });
        } else {
            BiometricLoggerImpl.e("DeviceInfoManager: " + deviceModel +" -> "+ deviceInfo);
            setCachedDeviceInfo(deviceInfo);
            onDeviceInfoListener.onReady(deviceInfo);
        }
    }

    @Nullable
    private DeviceInfo getCachedDeviceInfo() {
        SharedPreferences sharedPreferences = SharedPreferenceProvider.getCryptoPreferences("DeviceInfo");
        if (sharedPreferences.getBoolean("checked", false)) {
            boolean hasIris = sharedPreferences.getBoolean("hasIris", false);
            boolean hasFace = sharedPreferences.getBoolean("hasFace", false);
            boolean hasFingerprint = sharedPreferences.getBoolean("hasFingerprint", false);
            boolean hasUnderDisplayFingerprint = sharedPreferences.getBoolean("hasUnderDisplayFingerprint", false);
            boolean existsInDatabase = sharedPreferences.getBoolean("existsInDatabase", false);
            return new DeviceInfo(existsInDatabase, hasIris, hasFace, hasFingerprint, hasUnderDisplayFingerprint);
        }
        return null;
    }

    private void setCachedDeviceInfo(@NonNull DeviceInfo deviceInfo) {
        SharedPreferences.Editor sharedPreferences = SharedPreferenceProvider.getCryptoPreferences("DeviceInfo").edit();
        sharedPreferences.putBoolean("hasIris", deviceInfo.getHasIris())
                .putBoolean("existsInDatabase", deviceInfo.getExistsInDatabase())
                .putBoolean("hasFace", deviceInfo.getHasFace())
                .putBoolean("hasFingerprint", deviceInfo.getHasFingerprint())
                .putBoolean("hasUnderDisplayFingerprint", deviceInfo.getHasUnderDisplayFingerprint())
                .putBoolean("checked", true).apply();
    }

    @Nullable
    private DeviceInfo loadDeviceInfo(String model) {
        if(TextUtils.isEmpty(model))
            return null;
        try {

            String url = "https://m.gsmarena.com/res.php3?sSearch=" + URLEncoder.encode(model);

            String html = getHtml(url);
            if (html == null)
                return null;

            String detailsLink = getDetailsLink(url, html, model);

            //not found
            if (detailsLink == null) {
                return new DeviceInfo();
            }

            BiometricLoggerImpl.e("DeviceInfoManager: Link: " + detailsLink);

            html = getHtml(detailsLink);

            if (html == null)
                return null;

            List<String> l = getSensorDetails(html);

            BiometricLoggerImpl.e("DeviceInfoManager: Sensors: " + l);

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
            return new DeviceInfo(true, hasIris, hasFace, hasFingerprint, hasUnderDisplayFingerprint);
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
            if (connectivityManager.getActiveNetworkInfo()!=null && connectivityManager.getActiveNetworkInfo().isConnectedOrConnecting()) {
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
            //ignore - old device cannt resolve SSL connection
            if(e instanceof SSLHandshakeException){
                return "<html></html>";
            }
            BiometricLoggerImpl.e(e);
        }
        return null;
    }

    public interface OnDeviceInfoListener {
        void onReady(@Nullable DeviceInfo deviceInfo);
    }
}
