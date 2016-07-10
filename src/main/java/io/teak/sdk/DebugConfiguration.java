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

import java.util.Locale;

class DebugConfiguration {
    private static final String LOG_TAG = "Teak:DebugConfig";

    private static final String PREFERENCE_FORCE_DEBUG = "io.teak.sdk.Preferences.ForceDebug";

    private final SharedPreferences preferences;

    public final boolean forceDebug;

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
                Log.e(LOG_TAG, "Error occured while storing preferences. " + Log.getStackTraceString(e));
            }
        }
    }
}
