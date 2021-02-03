package dev.skomlach.biometric.compat.utils.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;
import dev.skomlach.common.cryptostorage.SharedPreferenceProvider;

import static dev.skomlach.biometric.compat.utils.device.Network.createConnection;
import static dev.skomlach.biometric.compat.utils.device.Network.fastCopy;
import static dev.skomlach.biometric.compat.utils.device.Network.resolveUrl;

public class DeviceInfoManager {

    public static DeviceInfoManager INSTANCE = new DeviceInfoManager();

    private Pattern pattern = Pattern.compile("\\((.*?)\\)+");
    static final String[] agents = new String[]{"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36",
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

    public boolean hasFingerprint(DeviceInfo deviceInfo) {
        if (deviceInfo == null || deviceInfo.getSensors() == null)
            return false;

        for (String s : deviceInfo.getSensors()) {
            s = s.toLowerCase();
            if (s.contains("fingerprint")) {
                return true;
            }
        }
        return false;
    }

    public boolean hasUnderDisplayFingerprint(DeviceInfo deviceInfo) {
        if (deviceInfo == null || deviceInfo.getSensors() == null)
            return false;
        for (String s : deviceInfo.getSensors()) {
            s = s.toLowerCase();

            if (s.contains("fingerprint") && s.contains("under display")) {
                return true;
            }
        }
        return false;
    }

    public boolean hasIrisScanner(DeviceInfo deviceInfo) {
        if (deviceInfo == null || deviceInfo.getSensors() == null)
            return false;
        for (String s : deviceInfo.getSensors()) {
            s = s.toLowerCase();
            if (s.contains(" id") || s.contains(" recognition") || s.contains(" unlock") || s.contains(" auth")) {
                if (s.contains("iris")) {
                    return true;
                }
            }
        }
        return false;
    }
    public boolean hasFaceID(DeviceInfo deviceInfo){
        if(deviceInfo == null || deviceInfo.getSensors() == null)
            return false;
        for (String s : deviceInfo.getSensors()) {
            s = s.toLowerCase();
            if (s.contains(" id") || s.contains(" recognition") || s.contains(" unlock") || s.contains(" auth")) {
                if (s.contains("face")) {
                    return true;
                }
            }
        }
        return false;
    }

    @WorkerThread
    public void getDeviceInfo(OnDeviceInfoListener onDeviceInfoListener) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread())
            throw new IllegalThreadStateException("Worker thread required");

        DeviceInfo deviceInfo = getCachedDeviceInfo();
        if (deviceInfo != null) {
            onDeviceInfoListener.onReady(deviceInfo);
            return;
        }

        Set<String> strings = DeviceModel.INSTANCE.getNames();
        for (String m : strings) {
            deviceInfo = loadDeviceInfo(m);
            if (deviceInfo != null && deviceInfo.getSensors()!=null) {
                BiometricLoggerImpl.e("DeviceInfoManager: " + deviceInfo.getModel() + " -> " + deviceInfo);
                setCachedDeviceInfo(deviceInfo);
                onDeviceInfoListener.onReady(deviceInfo);
                return;
            }
        }

        if (deviceInfo != null) {
            BiometricLoggerImpl.e("DeviceInfoManager: " + deviceInfo.getModel() + " -> " + deviceInfo);
            setCachedDeviceInfo(deviceInfo);
        }
        onDeviceInfoListener.onReady(deviceInfo);
    }

    @Nullable
    private DeviceInfo getCachedDeviceInfo() {
        SharedPreferences sharedPreferences = SharedPreferenceProvider.getCryptoPreferences("StoredDeviceInfo");
        if (sharedPreferences.getBoolean("checked", false)) {
            String model = sharedPreferences.getString("model", null);
            Set<String> sensors = sharedPreferences.getStringSet("sensors", null);
            return new DeviceInfo(model, sensors);
        }
        return null;
    }

    private void setCachedDeviceInfo(@NonNull DeviceInfo deviceInfo) {
        SharedPreferences.Editor sharedPreferences = SharedPreferenceProvider.getCryptoPreferences("StoredDeviceInfo").edit();
        sharedPreferences
                .putStringSet("sensors", deviceInfo.getSensors())
                .putString("model", deviceInfo.getModel())
                .putBoolean("checked", true)
                .apply();
    }

    @Nullable
    private DeviceInfo loadDeviceInfo(String model) {
        BiometricLoggerImpl.e("DeviceInfoManager: loadDeviceInfo for " + model);
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
                return new DeviceInfo(model, null);
            }

            BiometricLoggerImpl.e("DeviceInfoManager: Link: " + detailsLink);

            html = getHtml(detailsLink);

            if (html == null)
                return null;

            Set<String> l = getSensorDetails(html);

            BiometricLoggerImpl.e("DeviceInfoManager: Sensors: " + l);

            return new DeviceInfo(model, l);
        } catch (Throwable e) {
            return null;
        }
    }

    //parser
    private Set<String> getSensorDetails(String html) {

        Set<String> list = new HashSet<>();
        if (html != null) {
            Document doc = Jsoup.parse(html);
            Element body = doc.body().getElementById("content");
            Elements rElements = body.getElementsByAttribute("data-spec");
            for (int i = 0; i < rElements.size(); i++) {
                Element element = rElements.get(i);
                if (element.attr("data-spec").equals("sensors")) {
                    String name = element.text();
                    if (!TextUtils.isEmpty(name)) {

                        Matcher matcher = pattern.matcher(name);
                        while (matcher.find()) {
                            String s = matcher.group();
                            name = name.replace(s, s.replace(",", ";"));
                        }

                        String[] split = name.split(",");
                        for (String s : split) {
                            list.add(capitalize(s.trim()));
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

                    return new String(data);
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

    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
    public interface OnDeviceInfoListener {
        void onReady(@Nullable DeviceInfo deviceInfo);
    }
}
