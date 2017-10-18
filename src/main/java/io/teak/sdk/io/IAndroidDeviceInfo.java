package io.teak.sdk.io;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Map;

public interface IAndroidDeviceInfo {
    boolean hasGooglePlay();
    boolean hasADM();

    // MANUFACTURER, MODEL, FALLBACK
    @NonNull Map<String, String> getDeviceDescription();

    @Nullable String getDeviceId();
}
