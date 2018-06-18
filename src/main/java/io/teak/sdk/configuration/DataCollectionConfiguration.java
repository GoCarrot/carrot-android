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

import java.util.Map;

import io.teak.sdk.io.IAndroidResources;

public class DataCollectionConfiguration {
    @SuppressWarnings("WeakerAccess")
    public final boolean enableIDFA;

    @SuppressWarnings("WeakerAccess")
    public final boolean enableFacebookAccessToken;

    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_ENABLE_IDFA_RESOURCE = "io_teak_enable_idfa";
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_ENABLE_FACEBOOK_RESOURCE = "io_teak_enable_facebook";

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
    }

    private DataCollectionConfiguration(boolean enableIDFA, boolean enableFacebookAccessToken) {
        this.enableIDFA = enableIDFA;
        this.enableFacebookAccessToken = enableFacebookAccessToken;
    }

    public DataCollectionConfiguration extend(@Nullable Map<String, Object> dataCollectionConfiguration) {
        if (dataCollectionConfiguration == null) return this;

        return new DataCollectionConfiguration(
            this.enableIDFA && objToBoolean(dataCollectionConfiguration.get("idfa")),
            this.enableFacebookAccessToken && objToBoolean(dataCollectionConfiguration.get("facebook_access_token")));
    }

    private static boolean objToBoolean(Object obj) {
        try {
            return (obj == null || Boolean.parseBoolean(obj.toString()));
        } catch (Exception ignored) {
        }
        return true;
    }

    private static boolean checkFeatureConfiguration(@NonNull String featureName, @NonNull IAndroidResources androidResources, @Nullable Bundle metaData) {
        Boolean enableFeature = androidResources.getBooleanResource(featureName);
        if (enableFeature == null && metaData != null) {
            enableFeature = metaData.getBoolean(featureName, true);
        }
        return (enableFeature == null || enableFeature);
    }
}
