package io.teak.sdk;

import android.util.Log;

class Uncaught implements Thread.UncaughtExceptionHandler {
    private static final String LOG_TAG = "Teak.Uncaught";

    private Thread.UncaughtExceptionHandler previousUncaughtExceptionHandler;
    private Thread createdOnThread;

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
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
