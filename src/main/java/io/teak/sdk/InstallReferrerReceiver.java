package io.teak.sdk;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class InstallReferrerReceiver extends BroadcastReceiver implements Unobfuscable {

    public static final LinkedBlockingQueue<String> installReferrerQueue = new LinkedBlockingQueue<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        String installReferrer = intent.getStringExtra("referrer");
        if (installReferrer != null && !installReferrer.isEmpty()) {
            Teak.log.i("install_referrer", Collections.singletonMap("referrer", installReferrer));
            installReferrerQueue.offer(installReferrer);
        }

        try {
            ActivityInfo ai = context.getPackageManager().getReceiverInfo(new ComponentName(context, "io.teak.sdk.InstallReferrerReceiver"), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            for (String key : bundle.keySet()) {
                try {
                    String receiverClassName = (String) bundle.get(key);
                    Class<?> clazz = Class.forName(receiverClassName);
                    BroadcastReceiver receiver = (BroadcastReceiver) clazz.newInstance();
                    receiver.onReceive(context, intent);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    public static class Future implements java.util.concurrent.Future<String> {
        private boolean isCancelled = false;
        private boolean isDone = false;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (this) {
                if (!this.isDone()) {
                    this.isCancelled = true;
                    this.isDone = true;
                }
            }
            return false;
        }

        @Override
        public boolean isCancelled() {
            return this.isCancelled;
        }

        @Override
        public boolean isDone() {
            return this.isDone;
        }

        @Override
        public String get() throws InterruptedException {
            return InstallReferrerReceiver.installReferrerQueue.take();
        }

        @Override
        public String get(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            return InstallReferrerReceiver.installReferrerQueue.poll(timeout, unit);
        }
    }
}
