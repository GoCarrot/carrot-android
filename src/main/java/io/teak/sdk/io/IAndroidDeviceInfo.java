package io.teak.sdk.io;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Map;

public interface IAndroidDeviceInfo {
    @NonNull
    Map<String, String> getDeviceDescription();

    @Nullable
    String getDeviceId();

    @Nullable
    String getSystemProperty(String propName);

    void requestAdvertisingId();
}
