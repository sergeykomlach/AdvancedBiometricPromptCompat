package dev.skomlach.common.misc;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

public class ExecutorHelper {
    public static ExecutorHelper INSTANCE = new ExecutorHelper();
    private final Executor executor;
    private final Handler handler;

    //hide
    private ExecutorHelper() {
        handler = new Handler(Looper.getMainLooper());
        executor = new HandlerExecutor(handler);
    }

    public Handler getHandler() {
        return handler;
    }

    public Executor getExecutor() {
        return executor;
    }

    /**
     * An {@link Executor} which posts to a {@link Handler}.
     */
    public static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        public HandlerExecutor(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void execute(Runnable runnable) {
            mHandler.post(runnable);
        }
    }
}
