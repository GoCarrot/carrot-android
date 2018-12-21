package io.teak.sdk.push;

import android.support.annotation.NonNull;

import java.util.Map;

public interface IPushProvider {
    void requestPushKey(@NonNull Map<String, Object> pushConfiguration);
}
