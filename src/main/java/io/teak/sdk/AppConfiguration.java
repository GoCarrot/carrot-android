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
package io.teak.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.teak.sdk.service.RavenService;

class AppConfiguration {
    public final String appId;
    public final String apiKey;
    public final String pushSenderId;
    public final int appVersion;
    public final String bundleId;
    public final String installerPackage;
    public final PackageManager packageManager;
    public final Context applicationContext;

    private static final String TEAK_API_KEY = "io_teak_api_key";
    private static final String TEAK_APP_ID = "io_teak_app_id";
    private static final String TEAK_GCM_SENDER_ID = "io_teak_gcm_sender_id";

    public AppConfiguration(@NonNull Context context) {
        this.applicationContext = context.getApplicationContext();

        Bundle metaData = null;
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            metaData = appInfo.metaData;
        } catch (Exception ignored) {
        }

        // Teak App Id
        {
            String tempAppId = Helpers.getStringResourceByName(TEAK_APP_ID, context);
            if (tempAppId == null && metaData != null && metaData.getString(TEAK_APP_ID) != null) {
                String temp = metaData.getString(TEAK_APP_ID);
                if (temp.startsWith("teak")) {
                    tempAppId = temp.substring(4);
                }
            }

            this.appId = tempAppId;
            if (this.appId == null) {
                throw new RuntimeException("Failed to find R.string." + TEAK_APP_ID);
            }
        }

        // Teak API Key
        {
            String tempApiKey = Helpers.getStringResourceByName(TEAK_API_KEY, context);
            if (tempApiKey == null && metaData != null && metaData.getString(TEAK_API_KEY) != null) {
                String temp = metaData.getString(TEAK_API_KEY);
                if (temp.startsWith("teak")) {
                    tempApiKey = temp.substring(4);
                }
            }

            this.apiKey = tempApiKey;
            if (this.apiKey == null) {
                throw new RuntimeException("Failed to find R.string." + TEAK_API_KEY);
            }
        }

        // Push Sender Id
        {
            String tempPushSenderId = Helpers.getStringResourceByName(TEAK_GCM_SENDER_ID, context);
            if (tempPushSenderId == null && metaData != null && metaData.getString(TEAK_GCM_SENDER_ID) != null) {
                String temp = metaData.getString(TEAK_GCM_SENDER_ID);
                if (temp.startsWith("teak")) {
                    tempPushSenderId = temp.substring(4);
                }
            }

            this.pushSenderId = tempPushSenderId;
            if (this.pushSenderId == null) {
                Teak.log.e("app_configuration", "R.string." + TEAK_GCM_SENDER_ID + " not present, push notifications disabled.");
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

        // Tell the Raven service about the app id
        try {
            Intent intent = new Intent(context, RavenService.class);
            intent.putExtra("appId", this.appId);
            ComponentName componentName = context.startService(intent);
            if (componentName == null) {
                Teak.log.w("app_configuration", "Unable to communicate with exception reporting service. Please add:\n\t<service android:name=\"io.teak.sdk.service.RavenService\" android:process=\":teak.raven\" android:exported=\"false\"/>\nTo the <application> section of your AndroidManifest.xml");
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    public Map<String, Object> to_h() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("appId", this.appId);
        ret.put("apiKey", this.apiKey);
        ret.put("pushSenderId", this.pushSenderId);
        ret.put("appVersion", this.appVersion);
        ret.put("bundleId", this.bundleId);
        ret.put("installerPackage", this.installerPackage);
        return ret;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.to_h())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
