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

import android.util.Log;

import org.json.JSONObject;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.lang.InterruptedException;

import javax.net.ssl.HttpsURLConnection;

import java.net.URL;
import java.net.URLEncoder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;

public class Teak extends BroadcastReceiver {

    public static final String SDKVersion = "2.0";

    public static void onCreate(Activity activity) {
        mainActivity = activity;
        activity.getApplication().registerActivityLifecycleCallbacks(mLifecycleCallbacks);
    }

    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(data != null) {
            // <debug>
            Log.d(LOG_TAG, "onActivityResult");

            checkActivityResultForPurchase(resultCode, data);
        }
    }

    public static void identifyUser(String userIdentifier) {
        if(!userId.isDone()) {
            userIdQueue.offer(userIdentifier);
        }
    }

    /**************************************************************************/
    static int appVersion;
    static boolean isDebug;
    static FutureTask<String> gcmId;
    static String apiKey;
    static String appId;
    static FutureTask<AdvertisingInfo> adInfo;
    static FutureTask<ServiceConfig> serviceConfig;
    static FutureTask<String> userId;
    static Activity mainActivity;
    static ArrayBlockingQueue<String> userIdQueue;
    static ArrayBlockingQueue<String> gcmIdQueue;
    static ExecutorService asyncExecutor;
    static FacebookAccessTokenBroadcast facebookAccessTokenBroadcast;

    static final String LOG_TAG = "Teak";

    private static final String TEAK_API_KEY = "TEAK_API_KEY";
    private static final String TEAK_APP_ID = "TEAK_APP_ID";

    private static final String TEAK_PREFERENCES_FILE = "io.teak.sdk.Preferences";
    private static final String TEAK_PREFERENCE_GCM_ID = "io.teak.sdk.Preferences.GcmId";
    private static final String TEAK_PREFERENCE_APP_VERSION = "io.teak.sdk.Preferences.AppVersion";

    private static final String TEAK_SERVICES_HOSTNAME = "services.gocarrot.com";

    /**************************************************************************/
    private static final TeakActivityLifecycleCallbacks mLifecycleCallbacks = new TeakActivityLifecycleCallbacks();

    static class TeakActivityLifecycleCallbacks implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            if(activity != mainActivity) return;

             // Check for debug build
            isDebug = ((Boolean) Helpers.getBuildConfigValue(activity, "DEBUG")) == Boolean.TRUE;

            // Get current app version
            appVersion = 0;
            try {
                appVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
            } catch(Exception e) {
                Log.e(LOG_TAG, e.toString());
            }

            // Get the API Key
            if (apiKey == null) {
                apiKey = (String) Helpers.getBuildConfigValue(activity, TEAK_API_KEY);
                if (apiKey == null) {
                    throw new RuntimeException("Failed to find BuildConfig." + TEAK_API_KEY);
                }
            }

            // Get the App Id
            if (appId == null) {
                appId = (String) Helpers.getBuildConfigValue(activity, TEAK_APP_ID);
                if (appId == null) {
                    throw new RuntimeException("Failed to find BuildConfig." + TEAK_APP_ID);
                }
            }

            // Facebook Access Token Broadcaster
            facebookAccessTokenBroadcast = new FacebookAccessTokenBroadcast(activity);

            // Producer/Consumer Queues
            asyncExecutor = Executors.newCachedThreadPool();
            gcmIdQueue = new ArrayBlockingQueue<String>(1);
            userIdQueue = new ArrayBlockingQueue<String>(1);

            // User Id
            userId = new FutureTask<String>(new Callable<String>() {
                public String call() {
                    try {
                        String ret = userIdQueue.take();
                        if (isDebug) {
                            Log.d(LOG_TAG, "User Id ready: " + ret);
                        }
                        return ret;
                    } catch(InterruptedException e) {
                        Log.e(LOG_TAG, e.toString());
                    }
                    return null;
                }
            });
            asyncExecutor.submit(userId);

            // TODO: ConnectivityManager listener?

            if(isDebug) {
                Log.d(LOG_TAG, "onActivityCreated");
                Log.d(LOG_TAG, "        App Id: " + appId);
                Log.d(LOG_TAG, "       Api Key: " + apiKey);
                Log.d(LOG_TAG, "   App Version: " + appVersion);
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if(activity != mainActivity) return;

            Log.d(LOG_TAG, "onActivityDestroyed");
            facebookAccessTokenBroadcast.unregister(activity);
            activity.getApplication().unregisterActivityLifecycleCallbacks(this);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if(activity != mainActivity) return;
            Log.d(LOG_TAG, "onActivityPaused");

            if(asyncExecutor != null) {
                asyncExecutor.shutdownNow();
                asyncExecutor = null;
            }
        }

        @Override
        public void onActivityResumed(final Activity activity) {
            if(activity != mainActivity) return;
            Log.d(LOG_TAG, "onActivityResumed");

            if(asyncExecutor == null) {
                asyncExecutor = Executors.newCachedThreadPool();
            }

            // Services config
            serviceConfig = new FutureTask<ServiceConfig>(new Callable<ServiceConfig>() {
                public ServiceConfig call() {
                    ServiceConfig ret = null;

                    HttpsURLConnection connection = null;
                    try {
                        String queryString = "?sdk_version=" + URLEncoder.encode(SDKVersion, "UTF-8") +
                                "&sdk_platform=" + URLEncoder.encode("android_" + android.os.Build.VERSION.RELEASE, "UTF-8") +
                                "&game_id=" + URLEncoder.encode(appId, "UTF-8") +
                                "&app_version=" + appVersion;
                        URL url = new URL("https", TEAK_SERVICES_HOSTNAME, "/services.json" + queryString);
                        connection = (HttpsURLConnection) url.openConnection();
                        connection.setRequestProperty("Accept-Charset", "UTF-8");
                        connection.setUseCaches(false);

                        // Get Response
                        InputStream is = null;
                        if (connection.getResponseCode() < 400) {
                            is = connection.getInputStream();
                        } else {
                            is = connection.getErrorStream();
                        }
                        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                        String line;
                        StringBuffer response = new StringBuffer();
                        while ((line = rd.readLine()) != null) {
                            response.append(line);
                            response.append('\r');
                        }
                        rd.close();

                        // Read the JSON
                        if (connection.getResponseCode() < 400) {
                            JSONObject json = new JSONObject(response.toString());
                            if (isDebug) {
                                Log.d(LOG_TAG, "Services configuration returned: \n" + json);
                            }

                            ret = new ServiceConfig(json);

                            if (isDebug) {
                                Log.d(LOG_TAG, "Service configuration complete: " + ret.toString());
                            }
                        } else {
                            Log.e(LOG_TAG, "Error performing service configuration: " + connection.getResponseCode());
                        }
                    } catch (Exception e) {

                    } finally {
                        connection.disconnect();
                        connection = null;
                    }

                    return ret;
                }
            });
            asyncExecutor.execute(serviceConfig);

            // Check for valid GCM Id
            SharedPreferences preferences = activity.getSharedPreferences(TEAK_PREFERENCES_FILE, Context.MODE_PRIVATE);
            int storedAppVersion = preferences.getInt(TEAK_PREFERENCE_APP_VERSION, 0);
            String storedGcmId = preferences.getString(TEAK_PREFERENCE_GCM_ID, null);

            gcmId = new FutureTask<String>(new Callable<String>() {
                public String call() {
                    try {
                        String ret = gcmIdQueue.take();
                        return ret;
                    } catch(InterruptedException e) {
                        Log.e(LOG_TAG, e.toString());
                    }
                    return null;
                }
            });
            asyncExecutor.submit(gcmId);

            // No need to get a new one, so put it on the blocking queue
            if(storedAppVersion == appVersion && storedGcmId != null) {
                if (isDebug) {
                    Log.d(LOG_TAG, "GCM Id found in cache: " + storedGcmId);
                }
                gcmIdQueue.offer(storedGcmId);
            }

            // Google Play Advertising Id
            int googlePlayStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
            if (googlePlayStatus == ConnectionResult.SUCCESS) {
                adInfo = new FutureTask<AdvertisingInfo>(new Callable<AdvertisingInfo>() {
                    public AdvertisingInfo call() {
                        AdvertisingInfo ret = null;
                        try {
                            ret = new AdvertisingInfo(AdvertisingIdClient.getAdvertisingIdInfo(activity));

                            if (isDebug) {
                                Log.d(LOG_TAG, "Google Play Advertising Info loaded: " + ret.toString());
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Couldn't get Google Play Advertising Id.");
                            if (isDebug) {
                                e.printStackTrace();
                            }
                        }
                        return ret;
                    }
                });
            } else {
                adInfo = new FutureTask<AdvertisingInfo>(new Callable<AdvertisingInfo>() {
                    public AdvertisingInfo call() {
                        Log.e(LOG_TAG, "Google Play Services not available, can't get advertising id.");
                        return null;
                    }
                });
            }
            asyncExecutor.submit(adInfo);
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        @Override
        public void onActivityStarted(Activity activity) {}
        @Override
        public void onActivityStopped(Activity activity) {}
    }

    static String getHostname(String foo) { return "gocarrot.com"; } // TODO: Properly do this

    private static void identifyUser() {
        asyncExecutor.submit(new Runnable() {
            public void run() {
                /*
                HashMap<String, Object> payload = new HashMap<String, Object>();

                TimeZone tz = TimeZone.getDefault();
                long rawTz = tz.getRawOffset();
                if (tz.inDaylightTime(new Date())) {
                    rawTz += tz.getDSTSavings();
                }
                long minutes = TimeUnit.MINUTES.convert(rawTz, TimeUnit.MILLISECONDS);
                String tzOffset = String.format("%f", minutes / 60.0f);
                payload.put("timezone", tzOffset);

                // TODO: NEED LOCALE HERE
                //       (What Facebook does)

                Log.d(LOG_TAG, "Identifying user: " + getUserId());
                Log.d(LOG_TAG, "        Timezone: " + tzOffset);

                if (mFbAttributionId != null) {
                    payload.put("fb_attribution_id", mFbAttributionId);
                }

                if (mGooglePlayAdId != null) {
                    payload.put("android_ad_id", mGooglePlayAdId);
                    payload.put("android_limit_ad_tracking", mLimitAdTracking);
                }

                if (mFbAccessToken != null) {
                    payload.put("access_token", mFbAccessToken);
                }

                if (mLaunchedFromTeakNotifId > 0) {
                    payload.put("teak_notif_id", new Long(mLaunchedFromTeakNotifId));
                    Log.d(LOG_TAG, "   Teak Notif Id: " + mLaunchedFromTeakNotifId);
                    mLaunchedFromTeakNotifId = 0;
                }

                if (mGcmId != null) {
                    payload.put("gcm_push_key", mGcmId);
                    Log.d(LOG_TAG, "          GCM Id: " + mGcmId);
                }

                if (mUserIdentified) {
                    payload.put("do_not_track_event", Boolean.TRUE);
                }
                mUserIdentified = true;

                mTeakCache.addRequest("/games/" + mAppId + "/users.json", payload);
                */
            }
        });
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
                String registration = bundle.get("registration_id").toString();
                SharedPreferences.Editor editor = context.getSharedPreferences(TEAK_PREFERENCES_FILE, Context.MODE_PRIVATE).edit();
                editor.putInt(TEAK_PREFERENCE_APP_VERSION, appVersion);
                editor.putString(TEAK_PREFERENCE_GCM_ID, registration);
                editor.apply();

                if(isDebug) {
                    Log.d(LOG_TAG, "GCM Id received from registration intent: " + registration);
                }
                gcmIdQueue.offer(registration);

                // TODO: runAndReset() the future?
            } catch(Exception e) {
                Log.e(LOG_TAG, "Error storing GCM Id from " + GCM_REGISTRATION_INTENT_ACTION + ":\n" + e.toString());
            }
        } else if(GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            // TODO: Check for presence of 'teakNotifId'
        }
    }

    /**************************************************************************/
    // Billing response codes
    private static final int BILLING_RESPONSE_RESULT_OK = 0;
    private static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
    private static final int BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2;
    private static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
    private static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
    private static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
    private static final int BILLING_RESPONSE_RESULT_ERROR = 6;
    private static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
    private static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

    private static final String RESPONSE_CODE = "RESPONSE_CODE";
    private static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    private static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";

    private static int getResponseCodeFromIntent(Intent i) {
        Object o = i.getExtras().get(RESPONSE_CODE);
        if (o == null) {
            Log.e(LOG_TAG, "Intent with no response code, assuming OK (known Google issue)");
            return BILLING_RESPONSE_RESULT_OK;
        }
        else if (o instanceof Integer) return ((Integer)o).intValue();
        else if (o instanceof Long) return (int)((Long)o).longValue();
        else {
            Log.e(LOG_TAG, "Unexpected type for intent response code.");
            Log.e(LOG_TAG, o.getClass().getName());
            throw new RuntimeException("Unexpected type for intent response code: " + o.getClass().getName());
        }
    }

    private static void checkActivityResultForPurchase(int resultCode, Intent data) {
        String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
        String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);

        // Check for purchase activity result
        if(purchaseData != null && dataSignature != null) {
            int responseCode = getResponseCodeFromIntent(data);

            if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
                // Successful purchase
                if(isDebug) {
                    Log.d(LOG_TAG, "Purchase activity has succeeded.");
                    Log.d(LOG_TAG, "Purchase data: " + purchaseData);
                    Log.d(LOG_TAG, "Data signature: " + dataSignature);
                    Log.d(LOG_TAG, "Extras: " + data.getExtras());
                }

                // TODO: Tell Teak about the purchase
            } else {
                // TODO: Tell Teak about the error
            }
        }
    }
}
