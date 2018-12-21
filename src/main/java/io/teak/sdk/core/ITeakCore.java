package io.teak.sdk.core;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public interface ITeakCore {
    // This is a bit hacky, but so is Adobe AIR
    void registerLocalBroadcastReceiver(BroadcastReceiver broadcastReceiver, IntentFilter filter);
}
