package io.teak.sdk;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.concurrent.ConcurrentLinkedQueue;

import io.teak.sdk.Helpers.mm;

public class InstallReferrerReceiver extends BroadcastReceiver implements Unobfuscable {

    public static final ConcurrentLinkedQueue<String> installReferrerQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        String installReferrer = intent.getStringExtra("referrer");
        if (installReferrer != null && !installReferrer.isEmpty()) {
            Teak.log.i("install_referrer", mm.h("referrer", installReferrer));
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
}
