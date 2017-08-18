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
import android.content.pm.ApplicationInfo;
import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;

class DebugConfiguration {
    private static final String PREFERENCE_FORCE_DEBUG = "io.teak.sdk.Preferences.ForceDebug";

    @SuppressWarnings("unused")
    public static void addExternalDebugInfo(String key, Object value) {
        if (key == null || key.isEmpty()) {
            Teak.log.e("debug_configuration", "key can not be null or empty for addExternalDebugInfo(), ignoring.");
            return;
        }

        try {
            if (value == null || value.toString() == null || value.toString().isEmpty()) {
                Teak.log.e("debug_configuration", "value can not be null or empty for addExternalDebugInfo(), ignoring.");
                return;
            }
        } catch (Exception e) {
            Teak.log.e("debug_configuration", "Error occured while converting value to string in addExternalDebugInfo(), ignoring.");
            return;
        }

        DebugConfiguration.externalDebugInfo.put(key, value);
    }
    private static final HashMap<String, Object> externalDebugInfo = new HashMap<>();

    private final SharedPreferences preferences;

    public final boolean forceDebug;
    public final boolean isDevelopmentBuild;

    public DebugConfiguration(@NonNull Context context) {
        SharedPreferences tempPreferences = null;
        try {
            tempPreferences = context.getSharedPreferences(Teak.PREFERENCES_FILE, Context.MODE_PRIVATE);
        } catch (Exception e) {
            Teak.log.exception(e);
        } finally {
            this.preferences = tempPreferences;
        }

        if (this.preferences == null) {
            Teak.log.e("debug_configuration", "getSharedPreferences() returned null. Some debug functionality is disabled.");
            this.forceDebug = Teak.forceDebug;
        } else {
            this.forceDebug = Teak.forceDebug || this.preferences.getBoolean(PREFERENCE_FORCE_DEBUG, false);
        }

        boolean tempDevelopmentBuild = false;
        try {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            tempDevelopmentBuild = (applicationInfo != null && (0 != (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE)));
        } catch (Exception ignored) {
        }
        this.isDevelopmentBuild = tempDevelopmentBuild;
    }

    public void setPreferenceForceDebug(boolean forceDebug) {
        if (this.preferences == null) {
            Teak.log.e("debug_configuration", "getSharedPreferences() returned null. Setting force debug is disabled.");
        } else if (forceDebug != this.forceDebug) {
            try {
                SharedPreferences.Editor editor = this.preferences.edit();
                editor.putBoolean(PREFERENCE_FORCE_DEBUG, forceDebug);
                editor.apply();
                Teak.log.i("debug_configuration",  String.format(Locale.US, "Force debug is now %s, please re-start the app.", forceDebug ? "enabled" : "disabled"));
            } catch (Exception e) {
                Teak.log.exception(e);
            }
            Teak.forceDebug = true;
        }
    }
}
