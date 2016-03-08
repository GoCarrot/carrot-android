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
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;

import android.support.v4.content.LocalBroadcastManager;

import android.os.Bundle;
import android.os.Handler;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import android.telephony.TelephonyManager;

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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import java.util.UUID;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.TimeZone;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

/**
 * 
 */
public class Teak extends BroadcastReceiver {

    /** Version of the Teak SDK. */
    public static final String SDKVersion = "2.0";

    /**
     * Initialize Teak and tell it to listen to the lifecycle events of {@link Activity}.
     *
     * <p>Call this function from the {@link Activity#onCreate} function of your <code>Activity</code>
     * <b>before</b> the call to <code>super.onCreate()</code></p>
     *
     * @param activity The main <code>Activity</code> of your app.
     */
    public static void onCreate(Activity activity) {
        Teak.mainActivity = activity;
        activity.getApplication().registerActivityLifecycleCallbacks(Teak.lifecycleCallbacks);
    }

    /**
     * Tell Teak about the result of an {@link Activity} started by your app.
     *
     * <p>This allows Teak to automatically get the results of In-App Purchase events.</p>
     *
     * @param requestCode The <code>requestCode</code> parameter received from {@link Activity#onActivityResult}
     * @param resultCode The <code>resultCode</code> parameter received from {@link Activity#onActivityResult}
     * @param data The <code>data</code> parameter received from {@link Activity#onActivityResult}
     */
    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "Lifecycle - onActivityResult");
        }

        if(data != null) {
            checkActivityResultForPurchase(resultCode, data);
        }
    }

    /**
     * Tell Teak how it should identify the current user.
     *
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     */
    public static void identifyUser(String userIdentifier) {
        if(!Teak.userId.isDone()) {
            Teak.userIdQueue.offer(userIdentifier);
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
    static FutureTask<String> facebookAccessToken;
    static Activity mainActivity;
    static ArrayBlockingQueue<String> userIdQueue;
    static ArrayBlockingQueue<String> gcmIdQueue;
    static ArrayBlockingQueue<String> facebookAccessTokenQueue;
    static ExecutorService asyncExecutor;
    static FacebookAccessTokenBroadcast facebookAccessTokenBroadcast;
    static CacheOpenHelper cacheOpenHelper;
    static String launchedFromTeakNotifId;
    static ScheduledExecutorService heartbeatService;
    static boolean userIdentifiedThisSession;
    static Date lastSessionEndedAt;
    static SQLiteDatabase database;
    static String deviceId;

    static final String LOG_TAG = "Teak";

    private static final String TEAK_API_KEY = "TEAK_API_KEY";
    private static final String TEAK_APP_ID = "TEAK_APP_ID";

    private static final String TEAK_PREFERENCES_FILE = "io.teak.sdk.Preferences";
    private static final String TEAK_PREFERENCE_GCM_ID = "io.teak.sdk.Preferences.GcmId";
    private static final String TEAK_PREFERENCE_APP_VERSION = "io.teak.sdk.Preferences.AppVersion";

    private static final String TEAK_SERVICES_HOSTNAME = "services.gocarrot.com";

    private static final long SAME_SESSION_TIME_DELTA = 120000;

    /**************************************************************************/
    private static final TeakActivityLifecycleCallbacks lifecycleCallbacks = new TeakActivityLifecycleCallbacks();

    static class TeakActivityLifecycleCallbacks implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
            if(activity != Teak.mainActivity) return;

            // Unique device id
            final TelephonyManager tm = (TelephonyManager)activity.getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
            final String tmDevice, tmSerial, androidId;
            tmDevice = "" + tm.getDeviceId();
            tmSerial = "" + tm.getSimSerialNumber();
            androidId = "" + android.provider.Settings.Secure.getString(activity.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
            Teak.deviceId = deviceUuid.toString();

             // Check for debug build
            Teak.isDebug = ((Boolean) Helpers.getBuildConfigValue(activity, "DEBUG")) == Boolean.TRUE;

            // Get current app version
            Teak.appVersion = 0;
            try {
                Teak.appVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
            } catch(Exception e) {
                Log.e(LOG_TAG, Log.getStackTraceString(e));
            }

            // Get the API Key
            if (Teak.apiKey == null) {
                Teak.apiKey = (String) Helpers.getBuildConfigValue(activity, TEAK_API_KEY);
                if (Teak.apiKey == null) {
                    throw new RuntimeException("Failed to find BuildConfig." + TEAK_API_KEY);
                }
            }

            // Get the App Id
            if (Teak.appId == null) {
                Teak.appId = (String) Helpers.getBuildConfigValue(activity, TEAK_APP_ID);
                if (Teak.appId == null) {
                    throw new RuntimeException("Failed to find BuildConfig." + TEAK_APP_ID);
                }
            }

            // Facebook Access Token Broadcaster
            Teak.facebookAccessTokenBroadcast = new FacebookAccessTokenBroadcast(activity);

            // Register for local broadcasts
            IntentFilter filter = new IntentFilter();
            filter.addAction(FacebookAccessTokenBroadcast.UPDATED_ACCESS_TOKEN_INTENT_ACTION);
            LocalBroadcastManager.getInstance(activity).registerReceiver(Teak.localBroadcastReceiver, filter);

            // Producer/Consumer Queues
            Teak.asyncExecutor = Executors.newCachedThreadPool();
            Teak.gcmIdQueue = new ArrayBlockingQueue<String>(1);
            Teak.userIdQueue = new ArrayBlockingQueue<String>(1);
            Teak.facebookAccessTokenQueue = new ArrayBlockingQueue<String>(1);

            // User Id
            Teak.userId = new FutureTask<String>(new Callable<String>() {
                public String call() {
                    try {
                        String ret = Teak.userIdQueue.take();
                        if (Teak.isDebug) {
                            Log.d(LOG_TAG, "User Id ready: " + ret);
                        }
                        return ret;
                    } catch(InterruptedException e) {
                        Log.e(LOG_TAG, Log.getStackTraceString(e));
                    }
                    return null;
                }
            });
            Teak.asyncExecutor.submit(Teak.userId);

            // Facebook Access Token
            createFacebookAccessTokenFuture();

            // Check for valid GCM Id
            SharedPreferences preferences = activity.getSharedPreferences(TEAK_PREFERENCES_FILE, Context.MODE_PRIVATE);
            int storedAppVersion = preferences.getInt(TEAK_PREFERENCE_APP_VERSION, 0);
            String storedGcmId = preferences.getString(TEAK_PREFERENCE_GCM_ID, null);
            if(storedAppVersion == Teak.appVersion && storedGcmId != null) {
                // No need to get a new one, so put it on the blocking queue
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "GCM Id found in cache: " + storedGcmId);
                }
                Teak.gcmIdQueue.offer(storedGcmId);
            }

            Teak.gcmId = new FutureTask<String>(new Callable<String>() {
                public String call() {
                    try {
                        String ret = Teak.gcmIdQueue.take();
                        return ret;
                    } catch(InterruptedException e) {
                        Log.e(LOG_TAG, Log.getStackTraceString(e));
                    }
                    return null;
                }
            });
            Teak.asyncExecutor.submit(Teak.gcmId);

            // Google Play Advertising Id
            int googlePlayStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
            if (googlePlayStatus == ConnectionResult.SUCCESS) {
                Teak.adInfo = new FutureTask<AdvertisingInfo>(new Callable<AdvertisingInfo>() {
                    public AdvertisingInfo call() {
                        AdvertisingInfo ret = null;
                        try {
                            ret = new AdvertisingInfo(AdvertisingIdClient.getAdvertisingIdInfo(activity));

                            if (Teak.isDebug) {
                                Log.d(LOG_TAG, "Google Play Advertising Info loaded: " + ret.toString());
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Couldn't get Google Play Advertising Id.");
                            if (Teak.isDebug) {
                                e.printStackTrace();
                            }
                        }
                        return ret;
                    }
                });
            } else {
                Teak.adInfo = new FutureTask<AdvertisingInfo>(new Callable<AdvertisingInfo>() {
                    public AdvertisingInfo call() {
                        Log.e(LOG_TAG, "Google Play Services not available, can't get advertising id.");
                        return null;
                    }
                });
            }
            Teak.asyncExecutor.submit(Teak.adInfo);

            // Cache
            Teak.cacheOpenHelper = new CacheOpenHelper(activity);
            try {
                Teak.database = Teak.cacheOpenHelper.getWritableDatabase();
            } catch (SQLException e) {
                Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
            }

            // Validate the app id/key via "/games/#{@appId}/validate_sig.json"
            if(Teak.isDebug) {
                HashMap<String, Object> payload = new HashMap<String, Object>();
                payload.put("id", Teak.appId);
                Teak.asyncExecutor.execute(new Request("POST", "gocarrot.com", "/games/" + Teak.appId + "/validate_sig.json", payload) {
                    @Override
                    protected void done(int responseCode, String responseBody) {
                        try {
                            JSONObject response = new JSONObject(responseBody);
                            if(response.has("error")) {
                                JSONObject error = response.getJSONObject("error");
                                Log.e(LOG_TAG, "Error in Teak configuration: " + error.getString("message"));
                            } else {
                                Log.d(LOG_TAG, "Teak configuration valid for: " + response.getString("name"));
                            }
                        } catch(Exception ignored) {}
                    }
                });
            }

            if(Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityCreated");
                Log.d(LOG_TAG, "        App Id: " + Teak.appId);
                Log.d(LOG_TAG, "       Api Key: " + Teak.apiKey);
                Log.d(LOG_TAG, "   App Version: " + Teak.appVersion);
                if(Teak.launchedFromTeakNotifId != null) {
                    Log.d(LOG_TAG, " Teak Notif Id: " + Teak.launchedFromTeakNotifId);
                }
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if(activity != Teak.mainActivity) return;

            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityDestroyed");
            }

            Teak.database.close();
            Teak.cacheOpenHelper.close();
            Teak.facebookAccessTokenBroadcast.unregister(activity);
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(Teak.localBroadcastReceiver);
            activity.getApplication().unregisterActivityLifecycleCallbacks(this);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if(activity != Teak.mainActivity) return;

            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityPaused");
            }

            if(Teak.asyncExecutor != null) {
                Teak.asyncExecutor.shutdown();
                Teak.asyncExecutor = null;
            }

            if(Teak.heartbeatService != null) {
                Teak.heartbeatService.shutdown();
                Teak.heartbeatService = null;
            }

            Teak.launchedFromTeakNotifId = null;
            Teak.userIdentifiedThisSession = false;
            Teak.lastSessionEndedAt = new Date();
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if(activity != Teak.mainActivity) return;

            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityResumed");
            }

            if(Teak.asyncExecutor == null) {
                Teak.asyncExecutor = Executors.newCachedThreadPool();
            }

            // Service config
            final ServiceConfig config = new ServiceConfig();
            HashMap<String, Object> payload = new HashMap<String, Object>();
            payload.put("id", Teak.appId);
            Teak.serviceConfig = new FutureTask<ServiceConfig>(new Request("POST", "gocarrot.com", "/games/" + Teak.appId + "/settings.json", payload) {
                @Override
                protected void done(int responseCode, String responseBody) {
                    try {
                        JSONObject response = new JSONObject(responseBody);
                        config.setConfig(response);

                        if(Teak.isDebug) {
                            Log.d(LOG_TAG, "Services response (" + responseCode + "): " + response.toString(2));
                            Log.d(LOG_TAG, "Service Config " + config.toString());
                        }

                        // Heartbeat will block on userId Future, which is fine
                        startHeartbeat();

                        // Submit cached requests
                        CachedRequest.submitCachedRequests();
                    } catch(Exception ignored) {}
                }
            }, config);
            Teak.asyncExecutor.execute(Teak.serviceConfig);

            // Adds executor task that waits on userId and other Futures
            if(Teak.lastSessionEndedAt == null || new Date().getTime() - Teak.lastSessionEndedAt.getTime() > SAME_SESSION_TIME_DELTA) {
                identifyUser();
            }

            // Check for pending inbox messages, and notify app if they exist
            if(TeakNotification.inboxCount() > 0) {
                LocalBroadcastManager.getInstance(Teak.mainActivity).sendBroadcast(new Intent(TeakNotification.TEAK_INBOX_HAS_NOTIFICATIONS_INTENT));
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        @Override
        public void onActivityStarted(Activity activity) {}
        @Override
        public void onActivityStopped(Activity activity) {}
    }

    private static void startHeartbeat() {
        if(Teak.heartbeatService == null) {
            Teak.heartbeatService = Executors.newSingleThreadScheduledExecutor();
        }

        Teak.heartbeatService.scheduleAtFixedRate(new Runnable() {
            public void run() {
                String userId = null;
                try {
                    userId = Teak.userId.get();
                } catch(Exception e) {
                    Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                    return;
                }

                if(Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "Sending heartbeat for user: " + userId);
                }

                HttpsURLConnection connection = null;
                try {
                    String queryString = "game_id=" + URLEncoder.encode(Teak.appId, "UTF-8") +
                            "&api_key=" + URLEncoder.encode(userId, "UTF-8") +
                            "&sdk_version=" + URLEncoder.encode(Teak.SDKVersion, "UTF-8") +
                            "&sdk_platform=" + URLEncoder.encode("android_" + android.os.Build.VERSION.RELEASE, "UTF-8") +
                            "&app_version=" + URLEncoder.encode(String.valueOf(Teak.appVersion), "UTF-8") +
                            "&buster=" + URLEncoder.encode(UUID.randomUUID().toString(), "UTF-8");
                    URL url = new URL("https://iroko.gocarrot.com/ping?" + queryString);
                    connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestProperty("Accept-Charset", "UTF-8");
                    connection.setUseCaches(false);
                } catch (Exception e) {
                } finally {
                    connection.disconnect();
                    connection = null;
                }
            }
        }, 0, 1, TimeUnit.MINUTES); // TODO: If services config specifies a different rate, use that
    }

    private static void identifyUser() {
        final Date dateIssued = new Date();
        final String launchedFromTeakNotifId = Teak.launchedFromTeakNotifId;

        Teak.asyncExecutor.submit(new Runnable() {
            public void run() {
                String userId = null;
                try {
                    userId = Teak.userId.get();
                } catch(Exception e) {
                    Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                    return;
                }

                HashMap<String, Object> payload = new HashMap<String, Object>();

                payload.put("happened_at", dateIssued.getTime() / 1000); // Milliseconds -> Seconds

                payload.put("device_id", Teak.deviceId);

                if(Teak.userIdentifiedThisSession) {
                    payload.put("do_not_track_event", Boolean.TRUE);
                }
                Teak.userIdentifiedThisSession = true;

                TimeZone tz = TimeZone.getDefault();
                long rawTz = tz.getRawOffset();
                if (tz.inDaylightTime(new Date())) {
                    rawTz += tz.getDSTSavings();
                }
                long minutes = TimeUnit.MINUTES.convert(rawTz, TimeUnit.MILLISECONDS);
                String tzOffset = new DecimalFormat("#0.00").format(minutes / 60.0f);
                payload.put("timezone", tzOffset);

                String locale = Locale.getDefault().toString();
                payload.put("locale", locale);

                try {
                    AdvertisingInfo adInfo = Teak.adInfo.get(5L, TimeUnit.SECONDS);
                    if(adInfo != null) {
                        payload.put("android_ad_id", adInfo.adId);
                        payload.put("android_limit_ad_tracking", adInfo.limitAdTracking);
                    }
                } catch(Exception e) {}

                try {
                    String accessToken = Teak.facebookAccessToken.get(5L, TimeUnit.SECONDS);
                    if(accessToken != null) {
                        payload.put("access_token", accessToken);
                    }
                } catch(Exception e) {}

                if (launchedFromTeakNotifId != null) {
                    payload.put("teak_notif_id", new Long(launchedFromTeakNotifId));
                }

                try {
                    String gcmId = Teak.gcmId.get(5L, TimeUnit.SECONDS);
                    if(gcmId != null) {
                        payload.put("gcm_push_key", gcmId);
                    }
                } catch(Exception e) {}

                Log.d(LOG_TAG, "Identifying user: " + userId);
                Log.d(LOG_TAG, "        Timezone: " + tzOffset);
                Log.d(LOG_TAG, "          Locale: " + locale);

                Teak.asyncExecutor.submit(new CachedRequest("/games/" + Teak.appId + "/users.json", payload, dateIssued));
            }
        });
    }

    static BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
            if (FacebookAccessTokenBroadcast.UPDATED_ACCESS_TOKEN_INTENT_ACTION.equals(action)) {
                if(Teak.isDebug) {
                    Log.d(LOG_TAG, "Facebook Access Token updated.");
                }
                createFacebookAccessTokenFuture();
            }
        }
    };

    /**************************************************************************/

    private static final String GCM_RECEIVE_INTENT_ACTION = "com.google.android.c2dm.intent.RECEIVE";
    private static final String GCM_REGISTRATION_INTENT_ACTION = "com.google.android.c2dm.intent.REGISTRATION";

    @Override
    public void onReceive(Context context, Intent intent) {
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
                editor.putInt(TEAK_PREFERENCE_APP_VERSION, Teak.appVersion);
                editor.putString(TEAK_PREFERENCE_GCM_ID, registration);
                editor.apply();

                if(Teak.isDebug) {
                    Log.d(LOG_TAG, "GCM Id received from registration intent: " + registration);
                }
                Teak.gcmIdQueue.offer(registration);
            } catch(Exception e) {
                Log.e(LOG_TAG, "Error storing GCM Id from " + GCM_REGISTRATION_INTENT_ACTION + ":\n" + Log.getStackTraceString(e));
            }
        } else if(GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            TeakNotification notif = TeakNotification.notificationFromIntent(context, intent);

            // Send out inbox broadcast
            LocalBroadcastManager.getInstance(Teak.mainActivity).sendBroadcast(new Intent(TeakNotification.TEAK_INBOX_HAS_NOTIFICATIONS_INTENT));
        } else if(action.endsWith(TeakNotification.TEAK_PUSH_OPENED_INTENT_ACTION_SUFFIX)) {
            Bundle bundle = intent.getExtras();

            // TODO: Send opened metric (remember the app may not be active)

            // Set the notification id
            Teak.launchedFromTeakNotifId = bundle.getString("teakNotifId");

            // Launch the app
            if(!bundle.getBoolean("noAutolaunch")) {
                if(Teak.isDebug) {
                    Log.d(LOG_TAG, "Notification (" + Teak.launchedFromTeakNotifId + ") opened, auto-launching app.");
                }
                Intent launchIntent  = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                launchIntent.addCategory("android.intent.category.LAUNCHER");
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                launchIntent.putExtras(bundle);
                context.startActivity(launchIntent);
            } else {
                if(Teak.isDebug) {
                    Log.d(LOG_TAG, "Notification (" + Teak.launchedFromTeakNotifId + ") opened, NOT auto-launching app (noAutoLaunch flag present, and set to true).");
                }
            }
        }
    }

    static void createFacebookAccessTokenFuture() {
        Teak.facebookAccessToken = new FutureTask<String>(new Callable<String>() {
            public String call() {
                try {
                    String ret = Teak.facebookAccessTokenQueue.take();
                    return ret;
                } catch(InterruptedException e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                }
                return null;
            }
        });
        Teak.asyncExecutor.submit(Teak.facebookAccessToken);
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
                if(Teak.isDebug) {
                    Log.d(LOG_TAG, "Purchase activity has succeeded.");
                }

                // TODO: Tell Teak about the purchase
            } else {
                if(Teak.isDebug) {
                    Log.d(LOG_TAG, "Purchase activity has failed.");
                }
            }

            if(Teak.isDebug) {
                try {
                    Log.d(LOG_TAG, "Purchase data: " + new JSONObject(purchaseData).toString(2));
                } catch(Exception e) {
                    Log.d(LOG_TAG, "Purchase data: " + purchaseData);
                }
                Log.d(LOG_TAG, "Data signature: " + dataSignature);
            }

            // Send request
            /*
            Teak.asyncExecutor.submit(new Runnable() {
                public void run() {
                    HashMap<String, Object> payload = new HashMap<String, Object>();
                    String userId = null;
                    try {
                        userId = Teak.userId.get();
                    } catch(Exception e) {
                        Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                        return;
                    }

                    payload.put("app_id", Teak.appId);
                    payload.put("user_id", userId);
                    payload.put("network_id", new Integer(2));
                    payload.put("happened_at", dateIssued);
                    payload.put("platfom_id", dunno);

                    Teak.asyncExecutor.submit(new CachedRequest("/purchase.json", payload, dateIssued));
                }
            });
            */
        }
    }
}
