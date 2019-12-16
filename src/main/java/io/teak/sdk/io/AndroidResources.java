package io.teak.sdk.io;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AndroidResources implements IAndroidResources {
    public AndroidResources(@NonNull Context context, @NonNull IAndroidResources androidResources) {
        this.androidResources = androidResources;

        Bundle tempMetaData = null;
        try {
            final ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            tempMetaData = appInfo.metaData;
        } catch (Exception ignored) {
        }
        this.metaData = tempMetaData;
    }

    private final IAndroidResources androidResources;
    private final Bundle metaData;

    @Nullable
    public String getTeakStringResource(@NonNull String name) {
        String ret = this.androidResources.getStringResource(name);
        if (ret == null && this.metaData != null) {
            String appIdFromMetaData = metaData.getString(name);
            if (appIdFromMetaData != null && appIdFromMetaData.startsWith("teak")) {
                ret = appIdFromMetaData.substring(4);
            }
        }
        return ret;
    }

    public Boolean getTeakBoolResource(@NonNull String name) {
        Boolean ret = androidResources.getBooleanResource(name);
        if (ret == null && metaData != null) {
            ret = metaData.getBoolean(name, true);
        }
        return ret;
    }

    @Nullable
    @Override
    public String getStringResource(@NonNull String name) {
        return this.androidResources.getStringResource(name);
    }

    @Nullable
    @Override
    public Boolean getBooleanResource(@NonNull String name) {
        return this.androidResources.getBooleanResource(name);
    }
}
