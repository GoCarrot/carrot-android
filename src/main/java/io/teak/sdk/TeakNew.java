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

import android.app.Activity;
import android.app.Application.ActivityLifecycleCallbacks;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;

import android.os.Bundle;
import android.os.Handler;
import android.os.AsyncTask;

import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.lang.reflect.*;

public class TeakNew extends BroadcastReceiver {

    public static void onCreate(Activity activity) {
        activity.getApplication().registerActivityLifecycleCallbacks(mLifecycleCallbacks);
    }

    static int mAppVersion;
    static boolean mIsDebug;
    static AsyncTask<Void, Void, String> mGcmId;
    static String mAPIKey;
    static String mAppId;

    private static final String LOG_TAG = "Teak";
    private static final String TEAK_API_KEY = "TEAK_API_KEY";
    private static final String TEAK_APP_ID = "TEAK_APP_ID";

    private static final String TEAK_PREFERENCES_FILE = "io.teak.sdk.Preferences";
    private static final String TEAK_PREFERENCE_GCM_ID = "io.teak.sdk.Preferences.GcmId";
    private static final String TEAK_PREFERENCE_APP_VERSION = "io.teak.sdk.Preferences.AppVersion";

    protected static boolean startAsyncTask(Context context, AsyncTask<Void, Void, String> task) {
        Handler mainHandler = new Handler(context.getMainLooper());
        return mainHandler.post(new Runnable() {
            @Override 
            public void run() {
                mGcmId.execute(null, null, null);
            }
        });
    }

    protected static Object getBuildConfigValue(Context context, String fieldName) {
        try {
            Class<?> clazz = Class.forName(context.getPackageName() + ".BuildConfig");
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**************************************************************************/
    private static final TeakActivityLifecycleCallbacks mLifecycleCallbacks = new TeakActivityLifecycleCallbacks();

    static class TeakActivityLifecycleCallbacks implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

             // Check for debug build
            Boolean isDebug = (Boolean) getBuildConfigValue(activity, "DEBUG");
            mIsDebug = (isDebug == Boolean.TRUE);

            // Get current app version
            mAppVersion = 0;
            try {
                mAppVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
            } catch(Exception e) {
                Log.e(LOG_TAG, e.toString());
            }

            // Get the API Key
            if (mAPIKey == null) {
                mAPIKey = (String) getBuildConfigValue(activity, TEAK_API_KEY);
                if (mAPIKey == null) {
                    throw new RuntimeException("Failed to find BuildConfig." + TEAK_API_KEY);
                }
            }

            // Get the App Id
            if (mAppId == null) {
                mAppId = (String) getBuildConfigValue(activity, TEAK_APP_ID);
                if (mAppId == null) {
                    throw new RuntimeException("Failed to find BuildConfig." + TEAK_APP_ID);
                }
            }

            if(mIsDebug) {
                Log.d(LOG_TAG, "onActivityCreated");
                Log.d(LOG_TAG, "        App Id: " + mAppId);
                Log.d(LOG_TAG, "       Api Key: " + mAPIKey);
                Log.d(LOG_TAG, "   App Version: " + mAppVersion);
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            Log.d(LOG_TAG, "onActivityDestroyed");
            activity.getApplication().unregisterActivityLifecycleCallbacks(this);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            Log.d(LOG_TAG, "onActivityPaused");
        }

        @Override
        public void onActivityResumed(Activity activity) {
            Log.d(LOG_TAG, "onActivityResumed");

            // Check for valid GCM Id
            SharedPreferences preferences = activity.getSharedPreferences(TEAK_PREFERENCES_FILE, Context.MODE_PRIVATE);
            int storedAppVersion = preferences.getInt(TEAK_PREFERENCE_APP_VERSION, 0);
            final String gcmId = preferences.getString(TEAK_PREFERENCE_GCM_ID, null);

            if(storedAppVersion == mAppVersion && gcmId != null) {
                mGcmId = new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        if(mIsDebug) {
                            Log.d(LOG_TAG, "GCM Id cached: " + gcmId);
                        }
                        return gcmId;
                    }
                };
                startAsyncTask(activity, mGcmId);
            } else {
                mGcmId = new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        Log.e(LOG_TAG, "Placeholder AsyncTask has been executed. That shouldn't happen.");
                        return null;
                    }
                };
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        @Override
        public void onActivityStarted(Activity activity) {}
        @Override
        public void onActivityStopped(Activity activity) {}
    }

    /**************************************************************************/
    private static final String GCM_RECEIVE_INTENT_ACTION = "com.google.android.c2dm.intent.RECEIVE";
    private static final String GCM_REGISTRATION_INTENT_ACTION = "com.google.android.c2dm.intent.REGISTRATION";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String action = intent.getAction();

            // <debug>
            Log.d(LOG_TAG, "Intent received: " + action);
            Bundle dbundle = intent.getExtras();
            if (dbundle != null && !dbundle.isEmpty()) {
                for (String key : dbundle.keySet()) {
                    Object value = dbundle.get(key);
                    Log.d(LOG_TAG, String.format("    %s %s (%s)", key, value.toString(), value.getClass().getName()));
                }
            }
            // </debug>

        if(GCM_REGISTRATION_INTENT_ACTION.equals(action)) {

            // Store off the GCM Id and app version
            try {
                Bundle bundle = intent.getExtras();
                final String registration = bundle.get("registration_id").toString();
                SharedPreferences.Editor editor = context.getSharedPreferences(TEAK_PREFERENCES_FILE, Context.MODE_PRIVATE).edit();
                editor.putInt(TEAK_PREFERENCE_APP_VERSION, mAppVersion);
                editor.putString(TEAK_PREFERENCE_GCM_ID, registration);
                editor.apply();

                mGcmId = new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        if(mIsDebug) {
                            Log.d(LOG_TAG, "GCM Id received from registration intent: " + registration);
                        }
                        return registration;
                    }
                };
                startAsyncTask(context, mGcmId);
            } catch(Exception e) {
                Log.e(LOG_TAG, "Error storing GCM Id from " + GCM_REGISTRATION_INTENT_ACTION + ":\n" + e.toString());
            }
        } else if(GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            // TODO: Check for presence of 'teakNotifId'
        }
    }
}