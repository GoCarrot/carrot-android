/* Teak -- Copyright (C) 2016 GoCarrot Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.teak.sdk.configuration;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;

import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.teak.sdk.Teak;
import io.teak.sdk.io.IAndroidResources;

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
    public final String bundleId;
    @SuppressWarnings("WeakerAccess")
    public final String installerPackage;
    @SuppressWarnings("WeakerAccess")
    public final PackageManager packageManager;
    @SuppressWarnings("WeakerAccess")
    public final Context applicationContext;
    @SuppressWarnings("WeakerAccess")
    public final int targetSdkVersion;
    @SuppressWarnings("WeakerAccess")
    public final boolean enableCaching;

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
    public static final String TEAK_ENABLE_CACHING_RESOURCE = "io_teak_enable_caching";

    public AppConfiguration(@NonNull Context context, @NonNull IAndroidResources androidResources) throws IntegrationChecker.InvalidConfigurationException {
        this.applicationContext = context.getApplicationContext();

        Bundle metaData = null;
        int tempTargetSdkVersion = 0;
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            metaData = appInfo.metaData;
            tempTargetSdkVersion = appInfo.targetSdkVersion;
        } catch (Exception ignored) {
        }
        this.targetSdkVersion = tempTargetSdkVersion;

        // Teak App Id
        {
            String tempAppId = androidResources.getStringResource(TEAK_APP_ID_RESOURCE);
            if (tempAppId == null && metaData != null) {
                String appIdFromMetaData = metaData.getString(TEAK_APP_ID_RESOURCE);
                if (appIdFromMetaData != null && appIdFromMetaData.startsWith("teak")) {
                    tempAppId = appIdFromMetaData.substring(4);
                }
            }

            this.appId = tempAppId;
            if (this.appId == null) {
                throw new IntegrationChecker.InvalidConfigurationException("Failed to find R.string." + TEAK_APP_ID_RESOURCE);
            } else if (this.appId.trim().length() < 1) {
                throw new IntegrationChecker.InvalidConfigurationException("R.string." + TEAK_APP_ID_RESOURCE + " is empty.");
            }
        }

        // Teak API Key
        {
            String tempApiKey = androidResources.getStringResource(TEAK_API_KEY_RESOURCE);
            if (tempApiKey == null && metaData != null) {
                String apiKeyFromMetaData = metaData.getString(TEAK_API_KEY_RESOURCE);
                if (apiKeyFromMetaData != null && apiKeyFromMetaData.startsWith("teak")) {
                    tempApiKey = apiKeyFromMetaData.substring(4);
                }
            }

            this.apiKey = tempApiKey;
            if (this.apiKey == null) {
                throw new IntegrationChecker.InvalidConfigurationException("Failed to find R.string." + TEAK_API_KEY_RESOURCE);
            } else if (this.apiKey.trim().length() < 1) {
                throw new IntegrationChecker.InvalidConfigurationException("R.string." + TEAK_API_KEY_RESOURCE + " is empty.");
            }
        }

        // Push Sender Id
        {
            String tempPushSenderId = androidResources.getStringResource(TEAK_GCM_SENDER_ID_RESOURCE);
            if (tempPushSenderId == null && metaData != null) {
                String pushSenderIdFromMetaData = metaData.getString(TEAK_GCM_SENDER_ID_RESOURCE);
                if (pushSenderIdFromMetaData != null && pushSenderIdFromMetaData.startsWith("teak")) {
                    tempPushSenderId = pushSenderIdFromMetaData.substring(4);
                }
            }

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
        // Push Sender Id
        {
            String tempFirebaseAppId = androidResources.getStringResource(TEAK_FIREBASE_APP_ID_RESOURCE);
            if (tempFirebaseAppId == null && metaData != null) {
                String firebaseAppIdFromMetaData = metaData.getString(TEAK_FIREBASE_APP_ID_RESOURCE);
                if (firebaseAppIdFromMetaData != null && firebaseAppIdFromMetaData.startsWith("teak")) {
                    tempFirebaseAppId = firebaseAppIdFromMetaData.substring(4);
                }
            }

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
            String tempJobId = androidResources.getStringResource(TEAK_JOB_ID_RESOURCE);
            if (tempJobId == null && metaData != null) {
                String jobIdFromMetaData = metaData.getString(TEAK_JOB_ID_RESOURCE);
                if (jobIdFromMetaData != null && jobIdFromMetaData.startsWith("teak")) {
                    tempJobId = jobIdFromMetaData.substring(4);
                }
            }

            int jobIdAsInt = Teak.JOB_ID;
            if (tempJobId != null && tempJobId.trim().length() > 0) {
                jobIdAsInt = Integer.parseInt(tempJobId.trim());
            }

            this.jobId = jobIdAsInt;
        }

        // Disable Cache?
        {
            Boolean enableCaching = androidResources.getBooleanResource(TEAK_ENABLE_CACHING_RESOURCE);
            if (enableCaching == null && metaData != null) {
                enableCaching = metaData.getBoolean(TEAK_ENABLE_CACHING_RESOURCE, true);
            }
            this.enableCaching = enableCaching == null ? false : enableCaching;
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
            try {
                tempAppVersion = this.packageManager.getPackageInfo(this.bundleId, 0).versionCode;
            } catch (Exception e) {
                Teak.log.exception(e);
            } finally {
                this.appVersion = tempAppVersion;
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
