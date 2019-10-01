package io.teak.sdk.push;

import androidx.annotation.NonNull;
import java.util.Map;

public interface IPushProvider {
    void requestPushKey(@NonNull Map<String, Object> pushConfiguration);
}
