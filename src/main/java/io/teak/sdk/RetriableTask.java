package io.teak.sdk;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

public class RetriableTask<T> implements Callable<T> {
    private final Callable<T> wrappedTask;
    private final int tries;
    private final long retryDelay;
    private final long retryMultiplier;

    public RetriableTask(final int tries, final long retryDelay, final Callable<T> taskToWrap) {
        this(tries, retryDelay, 1, taskToWrap);
    }

    public RetriableTask(final int tries, final long retryDelay, final long retryMultiplier, final Callable<T> taskToWrap) {
        this.wrappedTask = taskToWrap;
        this.tries = tries;
        this.retryDelay = retryDelay;
        this.retryMultiplier = retryMultiplier;
    }

    public T call() throws Exception {
        int triesLeft = this.tries;
        long nextRetryDelay = this.retryDelay;
        while (true) {
            try {
                return this.wrappedTask.call();
            } catch (final ClassNotFoundException | CancellationException | InterruptedException e) {
                throw e;
            } catch (final Exception e) {
                triesLeft--;
                if (triesLeft == 0) throw e;
                if (nextRetryDelay > 0) Thread.sleep(nextRetryDelay);
                nextRetryDelay *= this.retryMultiplier;
            }
        }
    }
}
