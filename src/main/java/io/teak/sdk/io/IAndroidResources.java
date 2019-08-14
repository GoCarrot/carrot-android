package io.teak.sdk.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface IAndroidResources {
    @Nullable
    String getStringResource(@NonNull String name);

    @Nullable
    Boolean getBooleanResource(@NonNull String name);
}
