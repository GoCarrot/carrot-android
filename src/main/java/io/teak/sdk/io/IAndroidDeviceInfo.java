package io.teak.sdk.io;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface IAndroidDeviceInfo {
    @NonNull
    Map<String, String> getDeviceDescription();

    @Nullable
    String getDeviceId();

    @Nullable
    String getSystemProperty(String propName);

    void requestAdvertisingId();

    int getNumCores();
}
