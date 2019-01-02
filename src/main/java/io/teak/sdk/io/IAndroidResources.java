package io.teak.sdk.io;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface IAndroidResources {
    @Nullable
    String getStringResource(@NonNull String name);

    @Nullable
    Boolean getBooleanResource(@NonNull String name);
}
