package io.teak.sdk;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

public class RetriableTask<T> implements Callable<T> {
    private final Callable<T> wrappedTask;
    private final int tries;
    private final long retryDelay;

    public RetriableTask(final int tries, final long retryDelay, final Callable<T> taskToWrap) {
        this.wrappedTask = taskToWrap;
        this.tries = tries;
        this.retryDelay = retryDelay;
    }

    public T call() throws Exception {
        int triesLeft = this.tries;
        while (true) {
            try {
                return this.wrappedTask.call();
            } catch (final CancellationException | InterruptedException e) {
                throw e;
            } catch (final Exception e) {
                triesLeft--;
                if (triesLeft == 0) throw e;
                if (this.retryDelay > 0) Thread.sleep(this.retryDelay);
            }
        }
    }
}
