package io.teak.sdk.core;

import androidx.annotation.NonNull;
import io.teak.sdk.BuildConfig;

public class ThreadFactory implements java.util.concurrent.ThreadFactory {
    public final String threadNamePrefix;

    private static int totalThreadsCreated = 0;

    @Override
    public Thread newThread(@NonNull Runnable runnable) {
        return ThreadFactory.createThreadWithName(runnable, this.threadNamePrefix);
    }

    private static Thread createThreadWithName(@NonNull Runnable runnable, String name) {
        final Thread createdThread = new Thread(runnable, name);

        final int currentThreadsCreated = ++ThreadFactory.totalThreadsCreated;

        if (BuildConfig.DEBUG) {
            try {
                final String logOut = String.format("%s :: Total Created %d",
                        name,
                        currentThreadsCreated);
                android.util.Log.e("TeakThreadFactory", logOut);
            } catch (Exception ignored) {
            }
        }

        return createdThread;
    }

    private ThreadFactory(final String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public static ThreadFactory autonamed() {
        return autonamed(4);
    }

    public static ThreadFactory autonamed(int offset) {
        return autonamed(offset, null);
    }

    public static ThreadFactory autonamed(int offset, String prefix) {
        final String name = String.format("%s#%s", prefix == null ? "" : prefix, getNameForThreadOrFactory(offset));
        return new ThreadFactory(name);
    }

    public static Thread autoStart(@NonNull Runnable runnable) {
        final String name = getNameForThreadOrFactory(4);
        final Thread thread = ThreadFactory.createThreadWithName(runnable, name);
        thread.start();
        return thread;
    }

    private static String getNameForThreadOrFactory(int offset) {
        final StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        final StackTraceElement e = stacktrace[offset];
        final String methodName = e.getMethodName();
        final String className = e.getClassName();
        return String.format("%s#%s", className, methodName == null ? "" : methodName);
    }
}
