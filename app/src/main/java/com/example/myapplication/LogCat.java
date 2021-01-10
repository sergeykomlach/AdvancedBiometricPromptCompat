package com.example.myapplication;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogCat {
    private static LogCat INSTANCE = null;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> cache = new ArrayList<>();
    private Log2ViewCallback log2ViewCallback;
    private String FILTER = "";

    private LogCat() {
    }

    public static LogCat getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LogCat();
        }
        return INSTANCE;
    }

    public void setFilter(String filter) {
        this.FILTER = filter;
    }

    public void setLog2ViewCallback(Log2ViewCallback log2ViewCallback) {
        this.log2ViewCallback = log2ViewCallback;
        if (log2ViewCallback != null) {
            log2ViewCallback.log(getLog());
        }
    }

    public void start() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            started.set(true);
            try {
                Runtime.getRuntime().exec("logcat -c");
            } catch (Throwable ignore) {}
            try {
                Process pq = Runtime.getRuntime().exec("logcat v main");
                BufferedReader stream = new BufferedReader(new InputStreamReader(pq.getInputStream()));
                String log = "";
                while (started.get()) {
                    if ((log = stream.readLine()) != null) {
                        final String temp = truncate(log);
                        cache.add(temp);
                        if (log2ViewCallback != null && (TextUtils.isEmpty(FILTER) || temp.contains(FILTER))) {
                            handler.post(() -> log2ViewCallback.log(temp));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String getLog() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < cache.size(); i++) {
            if (TextUtils.isEmpty(FILTER) || cache.get(i).contains(FILTER)) {
                stringBuilder.append(cache.get(i));
                if (i < cache.size() - 1) {
                    stringBuilder.append("\n");
                }
            }
        }
        return stringBuilder.toString();
    }

    private String truncate(String log) {
        return log.replaceFirst("([\\d-\\s:.]*)", "");
    }

    public void stop() {
        started.set(false);
    }

    public interface Log2ViewCallback {
        void log(String string);
    }
}
