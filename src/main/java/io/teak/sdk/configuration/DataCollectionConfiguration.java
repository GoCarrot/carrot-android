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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.AdvertisingInfoEvent;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.json.JSONObject;

public class DataCollectionConfiguration {
    private boolean enableIDFA;
    private boolean enableFacebookAccessToken;
    private boolean enablePushKey;

    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_ENABLE_IDFA_RESOURCE = "io_teak_enable_idfa";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_ENABLE_FACEBOOK_RESOURCE = "io_teak_enable_facebook";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_ENABLE_PUSH_KEY_RESOURCE = "io_teak_enable_push_key";

    public DataCollectionConfiguration(@NonNull Context context, @NonNull IAndroidResources androidResources) {
        Bundle metaData = null;
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            metaData = appInfo.metaData;
        } catch (Exception ignored) {
        }

        // IDFA
        this.enableIDFA = checkFeatureConfiguration(TEAK_ENABLE_IDFA_RESOURCE, androidResources, metaData);

        // Facebook Access Token
        this.enableFacebookAccessToken = checkFeatureConfiguration(TEAK_ENABLE_FACEBOOK_RESOURCE, androidResources, metaData);

        // Push key
        this.enablePushKey = checkFeatureConfiguration(TEAK_ENABLE_PUSH_KEY_RESOURCE, androidResources, metaData);

        // Listen for Ad Info event and update enableIDFA
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                switch (event.eventType) {
                    case AdvertisingInfoEvent.Type: {
                        DataCollectionConfiguration.this.enableIDFA &= !((AdvertisingInfoEvent) event).limitAdTracking;
                    } break;
                }
            }
        });
    }

    private static boolean checkFeatureConfiguration(@NonNull String featureName, @NonNull IAndroidResources androidResources, @Nullable Bundle metaData) {
        Boolean enableFeature = androidResources.getBooleanResource(featureName);
        if (enableFeature == null && metaData != null) {
            enableFeature = metaData.getBoolean(featureName, true);
        }
        return (enableFeature == null || enableFeature);
    }

    public void extend(JSONObject json) {
        this.enableIDFA &= json.optBoolean("enable_idfa", true);
        this.enableFacebookAccessToken &= json.optBoolean("enable_facebook", true);
        this.enablePushKey &= json.optBoolean("enable_push_key", true);
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("enableIDFA", this.enableIDFA);
        ret.put("enableFacebookAccessToken", this.enableFacebookAccessToken);
        ret.put("enablePushKey", this.enablePushKey);
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

    public boolean enableIDFA() {
        return enableIDFA;
    }

    public boolean enableFacebookAccessToken() {
        return enableFacebookAccessToken;
    }

    public boolean enablePushKey() {
        return enablePushKey;
    }
}
