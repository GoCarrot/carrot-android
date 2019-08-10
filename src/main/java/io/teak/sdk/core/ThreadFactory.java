package io.teak.sdk.core;

import androidx.annotation.NonNull;

public class ThreadFactory implements java.util.concurrent.ThreadFactory {
    public final String threadNamePrefix;

    @Override
    public Thread newThread(@NonNull Runnable runnable) {
        return new Thread(runnable, this.threadNamePrefix);
    }

    private ThreadFactory(final String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public static ThreadFactory autonamed() {
        return new ThreadFactory(getNameForThreadOrFactory());
    }

    public static Thread autoStart(@NonNull Runnable runnable) {
        final Thread thread = new Thread(runnable, getNameForThreadOrFactory());
        thread.start();
        return thread;
    }

    private static String getNameForThreadOrFactory() {
        final StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        final StackTraceElement e = stacktrace[4];
        final String methodName = e.getMethodName();
        final String className = e.getClassName();
        return String.format("%s#%s", className, methodName == null ? "" : methodName);
    }
}
