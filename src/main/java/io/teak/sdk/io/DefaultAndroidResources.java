package io.teak.sdk.io;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DefaultAndroidResources implements IAndroidResources {
    private final Context context;

    public DefaultAndroidResources(@NonNull Context context) {
        this.context = context;
    }

    @Nullable
    @Override
    public String getStringResource(@NonNull String name) {
        try {
            String packageName = this.context.getPackageName();
            int resId = this.context.getResources().getIdentifier(name, "string", packageName);
            return this.context.getString(resId);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    @Override
    public Boolean getBooleanResource(@NonNull String name) {
        try {
            String packageName = this.context.getPackageName();
            int resId = this.context.getResources().getIdentifier(name, "bool", packageName);
            return this.context.getResources().getBoolean(resId);
        } catch (Exception ignored) {
        }
        return null;
    }
}
