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

import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.event.PushSenderIdChangedEvent;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.service.RavenService;

public class AppConfiguration {
    @SuppressWarnings("WeakerAccess")
    public final String appId;
    @SuppressWarnings("WeakerAccess")
    public final String apiKey;
    @SuppressWarnings("WeakerAccess")
    public String pushSenderId;
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

    // These are public for unit tests, can we clean that up?
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_API_KEY = "io_teak_api_key";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_APP_ID = "io_teak_app_id";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_GCM_SENDER_ID = "io_teak_gcm_sender_id";

    public AppConfiguration(@NonNull Context context, @NonNull IAndroidResources androidResources) {
        this.applicationContext = context.getApplicationContext();

        this.packageManager = context.getPackageManager();
        if (this.packageManager == null) {
            throw new RuntimeException("Unable to get Package Manager.");
        }

        this.bundleId = context.getPackageName();
        if (this.bundleId == null) {
            throw new RuntimeException("Failed to get Bundle Id.");
        }

        this.installerPackage = this.packageManager.getInstallerPackageName(this.bundleId);

        int tmpAppVersion = 0;
        try {
            tmpAppVersion = this.packageManager.getPackageInfo(this.bundleId, 0).versionCode;
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        this.appVersion = tmpAppVersion;

        Bundle metaData = null;
        try {
            metaData = this.packageManager.getApplicationInfo(this.bundleId, PackageManager.GET_META_DATA).metaData;
        } catch (Exception ignored) {}


        String[] standardVariables = {TEAK_APP_ID, TEAK_API_KEY, TEAK_GCM_SENDER_ID};
        String[] configurationValues = new String[standardVariables.length];
        for(int i = 0; i < standardVariables.length; i++) {
            String variable = standardVariables[i];
            String tempVariable = androidResources.getStringResource(variable);
            if (tempVariable == null && metaData != null) {
                String variableFromMetaData = metaData.getString(variable);
                if (variableFromMetaData != null && variableFromMetaData.startsWith("teak")) {
                    tempVariable = variableFromMetaData.substring(4);
                }
            }
            configurationValues[i] = tempVariable;
        }

        this.appId = configurationValues[0];
        this.apiKey = configurationValues[1];
        this.pushSenderId = configurationValues[2];

        if (this.appId == null) {
            throw new RuntimeException("Failed to find R.string." + TEAK_APP_ID);
        }

        if (this.apiKey == null) {
            throw new RuntimeException("Failed to find R.string." + TEAK_API_KEY);
        }

        if (this.pushSenderId == null) {
            Teak.log.e("app_configuration", "R.string." + TEAK_GCM_SENDER_ID + " not present, push notifications disabled.");
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

        // Listen for remote configuration events
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                    final RemoteConfiguration remoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;

                    // Override the provided GCM Sender Id with one from Teak, if applicable
                    if (remoteConfiguration.gcmSenderId != null && pushSenderId != remoteConfiguration.gcmSenderId) {
                        pushSenderId = remoteConfiguration.gcmSenderId;
                        TeakEvent.postEvent(new PushSenderIdChangedEvent());
                        // TODO: Future-Pat, when you add another push provider re-visit the RemoteConfiguration provided sender id
                    }
                }
            }
        });
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
