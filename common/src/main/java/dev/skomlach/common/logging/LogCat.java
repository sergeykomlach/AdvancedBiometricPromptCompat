package dev.skomlach.common.logging;

import android.util.Log;

import androidx.annotation.NonNull;

import dev.skomlach.common.BuildConfig;
import timber.log.Timber;

public class LogCat {
    private static final boolean DEBUG = BuildConfig.DEBUG;

    static {
        if (DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new CrashReportingTree());
        }
    }

    private LogCat() {

    }

    public static void log(String msg) {
        if (DEBUG) {
            Timber.d(msg);
        }
    }

    public static void logError(String msg) {
        if (DEBUG) {
            Timber.e(msg);
        }
    }

    public static void logException(Throwable e) {
        Timber.e(e);
    }

    public static void logException(String msg, Throwable e) {
        Timber.e(e, msg);
    }

    /** A tree which logs important information for crash reporting. */
    private static class CrashReportingTree extends Timber.Tree {
        @Override
        protected void log(int priority, String tag, @NonNull String message, Throwable t) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return;
            }

//            FirebaseCrashlytics.getInstance().log(priority+" - "+ tag+" - "+ message);
//
//            if (t != null) {
//                if (priority == Log.ERROR) {
//                    FirebaseCrashlytics.getInstance().recordException(t);
//                } else if (priority == Log.WARN) {
//                    FirebaseCrashlytics.getInstance().recordException(t);
//                }
//            }
        }
    }
}