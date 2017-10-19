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
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.support.annotation.NonNull;

import java.util.HashMap;

import io.teak.sdk.Teak;

public class DebugConfiguration {
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

    private boolean forceDebug;
    final boolean isDevelopmentBuild;

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

        // TODO: This should be listener based

        // Set up Logs
        Teak.log.setLoggingEnabled(this.forceDebug || this.isDevelopmentBuild);
        Teak.log.useRapidIngestionEndpoint(this.isDevelopmentBuild);
    }

    public void setPreferenceForceDebug(boolean forceDebug) {
        if (forceDebug != this.forceDebug) {
            try {
                SharedPreferences.Editor editor = this.preferences.edit();
                editor.putBoolean(PREFERENCE_FORCE_DEBUG, forceDebug);
                editor.apply();
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }
        this.forceDebug = forceDebug;

        // TODO: This should be listener based
        Teak.log.setLoggingEnabled(this.forceDebug || this.isDevelopmentBuild);
    }

    public boolean isDebug() {
        return this.forceDebug || this.isDevelopmentBuild;
    }
}
