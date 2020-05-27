package io.teak.sdk.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class Executors {
    public static ExecutorService newSingleThreadExecutor() {
        return java.util.concurrent.Executors.newSingleThreadExecutor(ThreadFactory.autonamed(5, "singleThreadExecutor"));
    }

    public static ExecutorService newCachedThreadPool() {
        return java.util.concurrent.Executors.newCachedThreadPool(ThreadFactory.autonamed(5, "cachedThreadPool"));
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
        return java.util.concurrent.Executors.newSingleThreadScheduledExecutor(ThreadFactory.autonamed(5, "singleThreadScheduledExecutor"));
    }
}
