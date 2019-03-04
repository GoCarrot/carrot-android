package io.teak.sdk.referrer;

import android.content.Context;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.teak.sdk.Helpers;
import io.teak.sdk.InstallReferrerReceiver;
import io.teak.sdk.Teak;

public class GooglePlayInstallReferrer implements InstallReferrerStateListener, Future<String> {
    GooglePlayInstallReferrer(@NonNull final Context context) {
        this.referrerClient = InstallReferrerClient.newBuilder(context).build();
        this.referrerClient.startConnection(this);
    }

    private final CountDownLatch latch = new CountDownLatch(1);
    private final InstallReferrerClient referrerClient;
    private boolean isCanceled = false;
    private String result = null;
    private boolean useInstallReferrerReceiverFallback = false;

    ///// InstallReferrerStateListener

    @Override
    public void onInstallReferrerSetupFinished(int responseCode) {
        boolean retry = false;
        switch (responseCode) {
            case InstallReferrerClient.InstallReferrerResponse.OK: {
                try {
                    final ReferrerDetails response = this.referrerClient.getInstallReferrer();
                    this.result = response.getInstallReferrer();
                    if (this.result != null) {
                        Teak.log.i("google_play.install_referrer", Helpers.mm.h("referrer", this.result));
                    }
                } catch (RemoteException e) {
                    this.useInstallReferrerReceiverFallback = true;
                    this.latch.countDown();
                }
                this.latch.countDown();
            }
            break;

            case InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED: {
                /*
                 * Play Store service is not connected now - potentially transient state.
                 *
                 * E.g. Play Store could have been updated in the background while your app was still
                 * running. So feel free to introduce your retry policy for such use case. It should lead to a
                 * call to {@link #startConnection(InstallReferrerStateListener)} right after or in some time
                 * after you received this code.
                 */
                retry = true;
            }
            break;

            case InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR:
            case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
            case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE: {
                // Fall back to InstallReferrerReceiver
                this.useInstallReferrerReceiverFallback = true;
                this.latch.countDown();
            }
            break;
        }

        // Retry if SERVICE_DISCONNECTED and not canceled
        if (retry && !this.isDone()) {
            this.referrerClient.startConnection(this);
        } else {
            // Cleanup
            try {
                this.referrerClient.endConnection();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onInstallReferrerServiceDisconnected() {
        // Nothing
    }

    ///// Future

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (!this.isDone()) {
                this.isCanceled = true;
                this.latch.countDown();
            }
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        synchronized (this) {
            return this.isCanceled;
        }
    }

    @Override
    public boolean isDone() {
        return this.latch.getCount() < 1;
    }

    @Override
    public String get() throws InterruptedException {
        this.latch.await();

        if (this.useInstallReferrerReceiverFallback) {
            this.result = InstallReferrerReceiver.installReferrerQueue.take();
        }

        return this.result;
    }

    @Override
    public String get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, TimeoutException {
        if (!this.latch.await(timeout, unit)) {
            throw new TimeoutException();
        }

        if (this.useInstallReferrerReceiverFallback) {
            this.result = InstallReferrerReceiver.installReferrerQueue.poll(timeout, unit);
        }

        return this.result;
    }
}
