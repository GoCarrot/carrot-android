package io.teak.sdk.configuration;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import io.teak.sdk.Helpers;
import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.Teak;
import io.teak.sdk.io.AndroidResources;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.json.JSONObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AppConfiguration {
    @SuppressWarnings("WeakerAccess")
    public final String appId;
    @SuppressWarnings("WeakerAccess")
    public final String apiKey;
    @SuppressWarnings("WeakerAccess")
    public final String gcmSenderId;
    @SuppressWarnings("WeakerAccess")
    public final String firebaseAppId;
    @SuppressWarnings("WeakerAccess")
    public final int jobId;
    @SuppressWarnings("WeakerAccess")
    public final int appVersion;
    @SuppressWarnings("WeakerAccess")
    public final String appVersionName;
    @SuppressWarnings("WeakerAccess")
    public final String bundleId;
    @SuppressWarnings("WeakerAccess")
    public final String installerPackage;
    @SuppressWarnings("WeakerAccess")
    public final String storeId;
    @SuppressWarnings("WeakerAccess")
    public final PackageManager packageManager;
    @SuppressWarnings("WeakerAccess")
    public final Context applicationContext;
    @SuppressWarnings("WeakerAccess")
    public final int targetSdkVersion;

    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_API_KEY_RESOURCE = "io_teak_api_key";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_APP_ID_RESOURCE = "io_teak_app_id";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_GCM_SENDER_ID_RESOURCE = "io_teak_gcm_sender_id";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_FIREBASE_APP_ID_RESOURCE = "io_teak_firebase_app_id";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_JOB_ID_RESOURCE = "io_teak_job_id";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_STORE_ID = "io_teak_store_id";

    @SuppressWarnings("WeakerAccess")
    public static final String GooglePlayStoreId = "google_play";
    @SuppressWarnings("WeakerAccess")
    public static final String AmazonStoreId = "amazon";
    @SuppressWarnings("WeakerAccess")
    public static final String SamsungStoreId = "samsung";
    @SuppressWarnings("WeakerAccess")
    public static final Set<String> KnownStoreIds = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        GooglePlayStoreId,
        AmazonStoreId,
        SamsungStoreId)));

    public AppConfiguration(@NonNull Context context, @NonNull IAndroidResources iAndroidResources) throws IntegrationChecker.InvalidConfigurationException {
        this.applicationContext = context.getApplicationContext();

        // Target SDK Version
        {
            int tempTargetSdkVersion = 0;
            try {
                ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                tempTargetSdkVersion = appInfo.targetSdkVersion;
            } catch (Exception ignored) {
            }
            this.targetSdkVersion = tempTargetSdkVersion;
        }

        final AndroidResources androidResources = new AndroidResources(context, iAndroidResources);

        // Teak App Id
        {
            this.appId = androidResources.getTeakStringResource(TEAK_APP_ID_RESOURCE);

            if (this.appId == null) {
                throw new IntegrationChecker.InvalidConfigurationException("Failed to find R.string." + TEAK_APP_ID_RESOURCE);
            } else if (this.appId.trim().length() < 1) {
                throw new IntegrationChecker.InvalidConfigurationException("R.string." + TEAK_APP_ID_RESOURCE + " is empty.");
            }
        }

        // Teak API Key
        {
            this.apiKey = androidResources.getTeakStringResource(TEAK_API_KEY_RESOURCE);

            if (this.apiKey == null) {
                throw new IntegrationChecker.InvalidConfigurationException("Failed to find R.string." + TEAK_API_KEY_RESOURCE);
            } else if (this.apiKey.trim().length() < 1) {
                throw new IntegrationChecker.InvalidConfigurationException("R.string." + TEAK_API_KEY_RESOURCE + " is empty.");
            }
        }

        // Push Sender Id
        {
            String tempPushSenderId = androidResources.getTeakStringResource(TEAK_GCM_SENDER_ID_RESOURCE);

            // If the google-services.json file was included and processed, gcm_defaultSenderId will be present
            // https://developers.google.com/android/guides/google-services-plugin#processing_the_json_file
            if (tempPushSenderId == null) {
                tempPushSenderId = androidResources.getStringResource("gcm_defaultSenderId");
            }

            this.gcmSenderId = tempPushSenderId;
            if (this.gcmSenderId == null || this.gcmSenderId.trim().length() < 1) {
                android.util.Log.e(IntegrationChecker.LOG_TAG, "R.string." + TEAK_GCM_SENDER_ID_RESOURCE + " not present or empty, push notifications disabled");
            }
        }

        // Firebase App Id
        {
            String tempFirebaseAppId = androidResources.getTeakStringResource(TEAK_FIREBASE_APP_ID_RESOURCE);

            // If the google-services.json file was included and processed, google_app_id will be present
            // https://developers.google.com/android/guides/google-services-plugin#processing_the_json_file
            if (tempFirebaseAppId == null) {
                tempFirebaseAppId = androidResources.getStringResource("google_app_id");
            }

            this.firebaseAppId = tempFirebaseAppId;
            if (this.firebaseAppId == null || this.firebaseAppId.trim().length() < 1) {
                android.util.Log.e(IntegrationChecker.LOG_TAG, "R.string." + TEAK_FIREBASE_APP_ID_RESOURCE + " not present or empty, push notifications disabled");
            }
        }

        // Job Id
        {
            String tempJobId = androidResources.getTeakStringResource(TEAK_JOB_ID_RESOURCE);

            int jobIdAsInt = Teak.JOB_ID;
            if (tempJobId != null && tempJobId.trim().length() > 0) {
                jobIdAsInt = Integer.parseInt(tempJobId.trim());
            }

            this.jobId = jobIdAsInt;
        }

        // Store
        {
            String tempStoreId = androidResources.getTeakStringResource(TEAK_STORE_ID);

            // If Amazon store stuff is available, we must be on Amazon
            if (Helpers.isNullOrEmpty(tempStoreId)) {
                tempStoreId = Helpers.isAmazonDevice(context) ? AppConfiguration.AmazonStoreId : AppConfiguration.GooglePlayStoreId;
            }
            this.storeId = tempStoreId;

            // Warn if we haven't seen this
            if (!AppConfiguration.KnownStoreIds.contains(this.storeId)) {
                android.util.Log.w(IntegrationChecker.LOG_TAG,
                    "Store id '" + this.storeId + "' is not a known value for this version of the Teak SDK. "
                        +
                        "Ignore this warning if you are certain the value is correct.");
            }
        }

        // Package Id
        {
            this.bundleId = context.getPackageName();
            if (this.bundleId == null) {
                throw new RuntimeException("Failed to get Bundle Id.");
            }
        }

        this.packageManager = context.getPackageManager();
        if (this.packageManager == null) {
            throw new RuntimeException("Unable to get Package Manager.");
        }

        // App Version
        {
            int tempAppVersion = 0;
            String tempAppVersionName = null;
            try {
                final PackageInfo info = this.packageManager.getPackageInfo(this.bundleId, 0);
                tempAppVersion = info.versionCode;
                tempAppVersionName = info.versionName;
            } catch (Exception e) {
                Teak.log.exception(e);
            } finally {
                this.appVersion = tempAppVersion;
                this.appVersionName = tempAppVersionName;
            }
        }

        // Get the installer package
        {
            this.installerPackage = this.packageManager.getInstallerPackageName(this.bundleId);
        }
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("appId", this.appId);
        ret.put("apiKey", this.apiKey);
        ret.put("gcmSenderId", this.gcmSenderId);
        ret.put("firebaseAppId", this.firebaseAppId);
        ret.put("jobId", this.jobId);
        ret.put("appVersion", this.appVersion);
        ret.put("bundleId", this.bundleId);
        ret.put("installerPackage", this.installerPackage);
        ret.put("storeId", this.storeId);
        ret.put("targetSdkVersion", this.targetSdkVersion);
        return ret;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.toMap())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
