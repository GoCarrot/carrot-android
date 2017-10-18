package io.teak.sdk.io;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Created by pat on 10/17/17.
 */

public interface IAndroidResources {
    @Nullable String getStringResource(@NonNull String name);
}
