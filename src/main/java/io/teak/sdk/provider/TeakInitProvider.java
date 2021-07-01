package io.teak.sdk.provider;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.Teak;
import io.teak.sdk.wrapper.cocos2dx.TeakCocos2dx;
import io.teak.sdk.wrapper.unity.TeakUnity;

public class TeakInitProvider extends ContentProvider {
    private boolean validConfiguration = true;

    public static final String IO_TEAK_INITIALIZE = "io.teak.sdk.initialize";
    public static final String LOG_TAG = "Teak.InitProvider";

    @Override
    public void attachInfo(Context context, ProviderInfo providerInfo) {
        final String initProviderAuthorities = context.getPackageName() + ".TeakInitProvider";
        if (providerInfo == null) {
            this.validConfiguration = false;
            Log.e(LOG_TAG, "ProviderInfo cannot be null.");
        } else if (!initProviderAuthorities.equals(providerInfo.authority)) {
            this.validConfiguration = false;
            Log.e(LOG_TAG, "Missing applicationId during AndroidManifest merge.");
        }

        super.attachInfo(context, providerInfo);
    }

    @Override
    public boolean onCreate() {
        if (this.validConfiguration) {
            // In this case, getContext() is the Application
            final Application application = (Application) this.getContext();
            if (application == null) return false;

            application.registerActivityLifecycleCallbacks(this.lifecycleCallbacks);
            return true;
        }
        return false;
    }

    private boolean shouldAttachToActivity(Activity activity) {
        Bundle activityMetaData = null;
        try {
            ActivityInfo info = activity.getPackageManager().getActivityInfo(activity.getComponentName(), PackageManager.GET_META_DATA);
            activityMetaData = info.metaData;
        } catch (Exception ignored) {
        }

        return activityMetaData != null && activityMetaData.getBoolean(IO_TEAK_INITIALIZE, false);
    }

    ///// ActivityLifecycleCallbacks

    private final Application.ActivityLifecycleCallbacks lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
            if (shouldAttachToActivity(activity)) {
                Log.i(LOG_TAG, activity.getComponentName().flattenToShortString());
                Teak.onCreate(activity);
                if (Teak.Instance != null) {
                    Teak.Instance.lifecycleCallbacks.onActivityCreated(activity, bundle);
                }

                // Initialize wrapper if needed.
                if (TeakUnity.isAvailable()) {
                    TeakUnity.initialize();
                } else if (TeakCocos2dx.isAvailable()) {
                    TeakCocos2dx.initialize();
                }
            }
        }

        @Override
        public void onActivityStarted(Activity activity) {
            // None
        }

        @Override
        public void onActivityResumed(Activity activity) {
            // None
        }

        @Override
        public void onActivityPaused(Activity activity) {
            // None
        }

        @Override
        public void onActivityStopped(Activity activity) {
            // None
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
            // None
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            // None
        }
    };

    ///// Remainder of ContentProvider not needed

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
