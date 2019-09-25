package io.teak.sdk.support;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.NonNull;

public interface ILocalBroadcastManager {
    void registerReceiver(@NonNull BroadcastReceiver receiver, @NonNull IntentFilter filter);
    boolean sendBroadcast(@NonNull Intent intent);
    void unregisterReceiver(@NonNull BroadcastReceiver receiver);
}
