package io.teak.sdk;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.teak.sdk.core.ITeakCore;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.io.IAndroidNotification;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.push.IPushProvider;
import io.teak.sdk.store.IStore;

public interface IObjectFactory {
    @Nullable
    IStore getIStore();

    @NonNull
    IAndroidResources getAndroidResources();

    @NonNull
    IAndroidDeviceInfo getAndroidDeviceInfo();

    @Nullable
    IPushProvider getPushProvider();

    @NonNull
    IAndroidNotification getAndroidNotification();

    @NonNull
    ITeakCore getTeakCore();
}
