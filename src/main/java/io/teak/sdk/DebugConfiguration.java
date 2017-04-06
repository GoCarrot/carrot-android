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

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;

class DebugConfiguration {
    private static final String LOG_TAG = "Teak.DebugConfig";

    private static final String PREFERENCE_FORCE_DEBUG = "io.teak.sdk.Preferences.ForceDebug";

    @SuppressWarnings("unused")
    public static void addExternalDebugInfo(String key, Object value) {
        if (key == null || key.isEmpty()) {
            Log.e(LOG_TAG, "key can not be null or empty for addExternalDebugInfo(), ignoring.");
            return;
        }

        try {
            if (value == null || value.toString() == null || value.toString().isEmpty()) {
                Log.e(LOG_TAG, "value can not be null or empty for addExternalDebugInfo(), ignoring.");
                return;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error occured while converting value to string in addExternalDebugInfo(), ignoring.");
            return;
        }

        DebugConfiguration.externalDebugInfo.put(key, value);
    }
    private static final HashMap<String, Object> externalDebugInfo = new HashMap<>();

    private final SharedPreferences preferences;

    public final boolean forceDebug;
    private final String bugReportUrl = "https://github.com/GoCarrot/teak-android/issues/new";

    public DebugConfiguration(@NonNull Context context) {
        SharedPreferences tempPreferences = null;
        try {
            tempPreferences = context.getSharedPreferences(Teak.PREFERENCES_FILE, Context.MODE_PRIVATE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error calling getSharedPreferences(). " + Log.getStackTraceString(e));
        } finally {
            this.preferences = tempPreferences;
        }

        if (this.preferences == null) {
            Log.e(LOG_TAG, "getSharedPreferences() returned null. Some debug functionality is disabled.");
            this.forceDebug = false;
        } else {
            this.forceDebug = this.preferences.getBoolean(PREFERENCE_FORCE_DEBUG, false);
        }
    }

    public void setPreferenceForceDebug(boolean forceDebug) {
        if (this.preferences == null) {
            Log.e(LOG_TAG, "getSharedPreferences() returned null. Setting force debug is disabled.");
        } else if (forceDebug != this.forceDebug) {
            try {
                SharedPreferences.Editor editor = this.preferences.edit();
                editor.putBoolean(PREFERENCE_FORCE_DEBUG, forceDebug);
                editor.apply();
                Log.d(LOG_TAG, String.format(Locale.US, "Force debug is now %s, please re-start the app.", forceDebug ? "enabled" : "disabled"));
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error occurred while storing preferences. " + Log.getStackTraceString(e));
            }
        }
    }

    public void printBugReportInfo(@NonNull Context context, @NonNull AppConfiguration appConfiguration, @NonNull DeviceConfiguration deviceConfiguration) {
        if (Teak.isDebug) {
            try {
                HashMap<String, Object> debugInfoMap = new HashMap<>();

                HashMap<String, Object> sdkInfo = new HashMap<>();
                sdkInfo.put("teakAndroidVersion", Teak.SDKVersion);

                String airSdkVersion = Helpers.getStringResourceByName("io_teak_air_sdk_version", context);
                if (airSdkVersion != null) {
                    sdkInfo.put("teakAirVersion", airSdkVersion);
                }

                debugInfoMap.put("sdk", sdkInfo);
                debugInfoMap.put("appConfiguration", appConfiguration.to_h());
                debugInfoMap.put("deviceConfiguration", deviceConfiguration.to_h());
                if (!DebugConfiguration.externalDebugInfo.isEmpty()) {
                    debugInfoMap.put("externalDebugInfo", DebugConfiguration.externalDebugInfo);
                }

                String debugInfo = new JSONObject(debugInfoMap).toString();
                Log.d(LOG_TAG, "Please include the following JSON blob with any bug reports you submit: " + debugInfo);

                if (this.bugReportUrl != null) {
                    Log.d(LOG_TAG, String.format(Locale.US, "Or use this link:\n%s?body=%s", this.bugReportUrl, URLEncoder.encode("DESCRIBE THE ISSUE HERE\n\n" + debugInfo, "UTF-8")));
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error in printBugReportInfo() " + Log.getStackTraceString(e));
            }
        }
    }
}
