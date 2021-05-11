package io.teak.sdk;

import android.util.Log;

import androidx.annotation.NonNull;

class Uncaught implements Thread.UncaughtExceptionHandler {
    private static final String LOG_TAG = "Teak.Uncaught";

    private final Thread.UncaughtExceptionHandler previousUncaughtExceptionHandler;
    private final Thread createdOnThread;

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable ex) {
        if (!createdOnThread.equals(thread)) {
            Log.d(LOG_TAG, "TeakExceptionHandler created on " + createdOnThread.toString() + " getting exception from " + thread.toString());
        }

        if (Teak.Instance != null && Teak.Instance.sdkRaven != null) {
            Teak.Instance.sdkRaven.reportException(ex, null);
        }
    }

    public static synchronized Uncaught begin() {
        return new Uncaught();
    }

    private Uncaught() {
        createdOnThread = Thread.currentThread();
        previousUncaughtExceptionHandler = createdOnThread.getUncaughtExceptionHandler();
        createdOnThread.setUncaughtExceptionHandler(this);
    }

    public synchronized void end() {
        createdOnThread.setUncaughtExceptionHandler(previousUncaughtExceptionHandler);
    }
}
