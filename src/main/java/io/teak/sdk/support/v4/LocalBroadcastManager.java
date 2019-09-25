package io.teak.sdk.support.v4;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.NonNull;
import io.teak.sdk.support.ILocalBroadcastManager;

public class LocalBroadcastManager implements ILocalBroadcastManager {
    private final android.support.v4.content.LocalBroadcastManager localBroadcastManager;

    public LocalBroadcastManager(@NonNull Context context) {
        this.localBroadcastManager = android.support.v4.content.LocalBroadcastManager.getInstance(context);
    }

    @Override
    public void registerReceiver(@NonNull BroadcastReceiver receiver, @NonNull IntentFilter filter) {
        this.localBroadcastManager.registerReceiver(receiver, filter);
    }

    @Override
    public boolean sendBroadcast(@NonNull Intent intent) {
        return this.localBroadcastManager.sendBroadcast(intent);
    }

    @Override
    public void unregisterReceiver(@NonNull BroadcastReceiver receiver) {
        this.localBroadcastManager.unregisterReceiver(receiver);
    }
}
