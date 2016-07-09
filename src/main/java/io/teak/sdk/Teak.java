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
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;

import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.ApplicationInfo;

import android.net.Uri;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;

import android.os.Bundle;

import android.util.Log;

import org.json.JSONObject;

import java.lang.InterruptedException;

import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;

import java.util.Stack;
import java.util.Date;
import java.util.HashMap;

import io.teak.sdk.service.RavenService;

/**
 * Working with Teak on Android.
 * <p/>
 * Firstly, add a <code>teak.xml</code> file into your res/values folder.
 * <pre>{@code
 * <?xml version="1.0" encoding="utf-8"?>
 * <resources>
 *   <string name="io_teak_app_id">YOUR TEAK APP ID</string>
 *   <string name="io_teak_api_key">YOUR TEAK API KEY</string>
 * </resources>
 * }</pre>
 * Your Teak App Id and API Key can be found in the Settings for your app on the Teak dashboard.
 * <p/>
 * You may also provide a GCM sender id to Teak, in which case Teak will take care of
 * registering for a GCM key.
 * <pre>{@code
 * <string name="io_teak_gcm_sender_id">YOUR GCM SENDER ID</string>
 * }</pre>
 * <p/>
 * Next, add Teak to your gradle build.
 * <p/>
 * For Unity, open settings.gradle and add the line:
 * <pre>{@code project(':teak').projectDir=new File('teak-android/sdk') }</pre>
 *
 * And then open app/build.gradle and add the following line to dependencies:
 * <pre>{@code compile project(':teak') }</pre>
 *
 * Add the following as the <i>first line</i> of onCreate function of UnityPlayerNativeActivity:
 * <pre>{@code Teak.onCreate(this); }</pre>
 *
 * Add the following as the <i>first line</i> of onActivityResult function of UnityPlayerNativeActivity:
 * <pre>{@code Teak.onActivityResult(requestCode, resultCode, data); }</pre>
 */
public class Teak extends BroadcastReceiver {

    /**
     * Version of the Teak SDK.
     */
    public static final String SDKVersion = io.teak.sdk.BuildConfig.VERSION_NAME;

    /**
     * Initialize Teak and tell it to listen to the lifecycle events of {@link Activity}.
     * <p/>
     * <p>Call this function from the {@link Activity#onCreate} function of your <code>Activity</code>
     * <b>before</b> the call to <code>super.onCreate()</code></p>
     *
     * @param activity The main <code>Activity</code> of your app.
     */
    public static void onCreate(Activity activity) {
        Log.d(LOG_TAG, "Android SDK Version: " + Teak.SDKVersion);

        if (activity == null) {
            Log.e(LOG_TAG, "null Activity passed to onCreate, Teak is unavailable.");
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.e(LOG_TAG, "Teak requires API level 14 to operate. Teak is unavailable.");
        } else {
            try {
                Application application = activity.getApplication();
                if (!Teak.lifecycleCallbacksRegistered.containsKey(application.hashCode())) {
                    Teak.lifecycleCallbacksRegistered.put(application.hashCode(), Boolean.TRUE);
                    application.registerActivityLifecycleCallbacks(Teak.lifecycleCallbacks);
                } else {
                    Log.d(LOG_TAG, "Duplicate onCreate call for Application " + application.toString() + ", ignoring.");
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to register Activity lifecycle callbacks. Teak is unavailable. " + Log.getStackTraceString(e));
            }
        }
    }

    /**
     * Tell Teak about the result of an {@link Activity} started by your app.
     * <p/>
     * <p>This allows Teak to automatically get the results of In-App Purchase events.</p>
     *
     * @param requestCode The <code>requestCode</code> parameter received from {@link Activity#onActivityResult}
     * @param resultCode  The <code>resultCode</code> parameter received from {@link Activity#onActivityResult}
     * @param data        The <code>data</code> parameter received from {@link Activity#onActivityResult}
     */
    public static void onActivityResult(@SuppressWarnings("unused") int requestCode, int resultCode, Intent data) {
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "Lifecycle - onActivityResult");
        }

        if (data != null) {
            checkActivityResultForPurchase(resultCode, data);
        }
    }

    public static void onNewIntent(Intent intent) {
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "Lifecycle - onNewIntent");
        }

        if (intent == null) {
            Log.e(LOG_TAG, "null Intent passed to onNewIntent, ignoring.");
            return;
        }
        Session.processIntent(intent, Teak.appConfiguration, Teak.deviceConfiguration);
    }

    /**
     * Tell Teak how it should identify the current user.
     * <p/>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     */
    public static void identifyUser(String userIdentifier) {
        // Always show this debug output.
        Log.d(LOG_TAG, "identifyUser(): " + userIdentifier);

        if (userIdentifier == null || userIdentifier.isEmpty()) {
            Log.e(LOG_TAG, "User identifier can not be null or empty.");
            return;
        }

        // Add userId to the Ravens
        Teak.sdkRaven.addUserData("id", userIdentifier);
        Teak.appRaven.addUserData("id", userIdentifier);

        // Send to Session
        Session.setUserId(userIdentifier);
    }

    /**
     * Track an arbitrary event in Teak.
     *
     * @param actionId         The identifier for the action, e.g. 'complete'.
     * @param objectTypeId     The type of object that is being posted, e.g. 'quest'.
     * @param objectInstanceId The specific instance of the object, e.g. 'gather-quest-1'
     */
    @SuppressWarnings("unused")
    public static void trackEvent(String actionId, String objectTypeId, String objectInstanceId) {
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "Tracking Event: " + actionId + " - " + objectTypeId + " - " + objectInstanceId);
        }

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("action_type", actionId);
        payload.put("object_type", objectTypeId);
        payload.put("object_instance_id", objectInstanceId);

        CachedRequest.submitCachedRequest("/me/events", payload, new Date());
    }

    /**************************************************************************/

    static boolean isDebug;
    static boolean forceDebug = false;
    static HashMap<Integer, Boolean> lifecycleCallbacksRegistered = new HashMap<>();

    static IStore appStore;
    static AppConfiguration appConfiguration;
    static DeviceConfiguration deviceConfiguration;

    static FutureTask<String> facebookAccessToken;
    static ArrayBlockingQueue<String> facebookAccessTokenQueue;
    static ExecutorService asyncExecutor = Executors.newCachedThreadPool();
    static FacebookAccessTokenBroadcast facebookAccessTokenBroadcast;

    static Stack<String> skuStack = new Stack<>();

    static Raven sdkRaven;
    static Raven appRaven;

    static LocalBroadcastManager localBroadcastManager;

    static final String LOG_TAG = "Teak";

    /**************************************************************************/

    static final ActivityLifecycleCallbacks lifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
            // Check for debug build
            Teak.isDebug = Teak.forceDebug || (0 != (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));

            // App Configuration
            Teak.appConfiguration = new AppConfiguration(activity);

            // Device configuration
            Teak.deviceConfiguration = new DeviceConfiguration(activity, Teak.appConfiguration);

            // Ravens
            Teak.sdkRaven = new Raven(activity, "sdk", Teak.appConfiguration, Teak.deviceConfiguration);
            Teak.appRaven = new Raven(activity, Teak.appConfiguration.bundleId, Teak.appConfiguration, Teak.deviceConfiguration);

            // Request cache manager
            CacheManager.initialize(activity);

            // Broadcast manager
            Teak.localBroadcastManager = LocalBroadcastManager.getInstance(activity);

            // Hook in to Session state change events
            Session.addEventListener(Teak.sessionEventListener);
            RemoteConfiguration.addEventListener(Teak.remoteConfigurationEventListener);

            // Process launch event
            Session.processIntent(activity.getIntent(), Teak.appConfiguration, Teak.deviceConfiguration);

            // Applicable store
            if (Teak.appConfiguration.installerPackage != null) {
                Class<?> clazz = null;
                if (Teak.appConfiguration.installerPackage.equals("com.amazon.venezia")) {
                    try {
                        clazz = Class.forName("io.teak.sdk.Amazon");
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Couldn't find Teak's Amazon app store handler. " + Log.getStackTraceString(e));
                        Teak.sdkRaven.reportException(e);
                    }
                } else {
                    // Default to Google Play
                    try {
                        clazz = Class.forName("io.teak.sdk.GooglePlay");
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Couldn't find Teak's Google Play app store handler. " + Log.getStackTraceString(e));
                        Teak.sdkRaven.reportException(e);
                    }
                }
                try {
                    Teak.appStore = (IStore) (clazz != null ? clazz.newInstance() : null);
                    if (Teak.appStore != null) {
                        Teak.appStore.init(activity);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Unable to create app store interface. " + Log.getStackTraceString(e));
                    Teak.sdkRaven.reportException(e);
                }
            }

            // Facebook Access Token Broadcaster
            Teak.facebookAccessTokenBroadcast = new FacebookAccessTokenBroadcast(activity);

            // Register for local broadcasts
            IntentFilter filter = new IntentFilter();
            filter.addAction(FacebookAccessTokenBroadcast.UPDATED_ACCESS_TOKEN_INTENT_ACTION);
            LocalBroadcastManager.getInstance(activity).registerReceiver(Teak.localBroadcastReceiver, filter);

            // Producer/Consumer Queues
            Teak.facebookAccessTokenQueue = new ArrayBlockingQueue<>(1);

            // Facebook Access Token
            createFacebookAccessTokenFuture();

            // Validate the app id/key via "/games/#{@appId}/validate_sig.json"
            if (Teak.isDebug) {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("id", Teak.appConfiguration.appId);
                new Thread(new Request("POST", "gocarrot.com", "/games/" + Teak.appConfiguration.appId + "/validate_sig.json", payload, Session.getCurrentSession(Teak.appConfiguration, Teak.deviceConfiguration)) {
                    @Override
                    protected void done(int responseCode, String responseBody) {
                        try {
                            JSONObject response = new JSONObject(responseBody);
                            if (response.has("error")) {
                                JSONObject error = response.getJSONObject("error");
                                Log.e(LOG_TAG, "Error in Teak configuration: " + error.getString("message"));
                            } else {
                                Log.d(LOG_TAG, "Teak configuration valid for: " + response.getString("name"));
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Error during app validation: " + Log.getStackTraceString(e));
                        }
                        super.done(responseCode, responseBody);
                    }
                }).start();
            }

            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityCreated");
                Log.d(LOG_TAG, "        App Id: " + Teak.appConfiguration.appId);
                Log.d(LOG_TAG, "       Api Key: " + Teak.appConfiguration.apiKey);
                Log.d(LOG_TAG, "   App Version: " + Teak.appConfiguration.appVersion);
                if (Teak.appConfiguration.installerPackage != null) {
                    Log.d(LOG_TAG, "     App Store: " + Teak.appConfiguration.installerPackage);
                }
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityDestroyed");
            }

            if (Teak.appStore != null) {
                Teak.appStore.dispose();
            }

            RemoteConfiguration.removeEventListener(Teak.remoteConfigurationEventListener);
            Session.removeEventListener(Teak.sessionEventListener);
            Teak.facebookAccessTokenBroadcast.unregister(activity);
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(Teak.localBroadcastReceiver);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                activity.getApplication().unregisterActivityLifecycleCallbacks(Teak.lifecycleCallbacks);
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityPaused");
            }

            Session.onActivityPaused();
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityResumed");
            }

            // Stores can do work if needed
            if (Teak.appStore != null) {
                Teak.appStore.onActivityResumed();
            }

            Session.onActivityResumed(Teak.appConfiguration, Teak.deviceConfiguration);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityStarted: " + activity.toString());
            }

            // OpenIAB & Prime31, need to store off the SKU for the purchase failed case
            if (activity.getClass().getName().equals("org.onepf.openiab.UnityProxyActivity")) {
                Bundle bundle = activity.getIntent().getExtras();
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Unity OpenIAB purchase launched: " + bundle.toString());
                }
                skuStack.push(bundle.getString("sku"));
            } else if (activity.getClass().getName().equals("com.prime31.GoogleIABProxyActivity")) {
                Bundle bundle = activity.getIntent().getExtras();
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Unity Prime31 purchase launched: " + bundle.toString());
                }
                skuStack.push(bundle.getString("sku"));
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }
    };

    static final Session.EventListener sessionEventListener = new Session.EventListener() {
        @Override
        public void onStateChange(Session session, Session.State oldState, Session.State newState) {
            if (newState == Session.State.Created) {
                // If Session state is now 'Created', we need the configuration from the Teak server
                RemoteConfiguration.requestConfigurationForApp(session);
            } else if (newState == Session.State.Configured) {
                // Submit cached requests
                CachedRequest.submitCachedRequests(session);
            }
        }
    };

    static final RemoteConfiguration.EventListener remoteConfigurationEventListener = new RemoteConfiguration.EventListener() {
        @Override
        public void onConfigurationReady(RemoteConfiguration configuration) {
            // Begin exception reporting, if enabled
            if (configuration.sdkSentryDSN() != null) {
                Teak.sdkRaven.setDsn(configuration.sdkSentryDSN());
            }

            if (configuration.appSentryDSN() != null) {
                Teak.appRaven.setDsn(configuration.appSentryDSN());
                Teak.appRaven.setAsUncaughtExceptionHandler();
            }
        }
    };

    static BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (FacebookAccessTokenBroadcast.UPDATED_ACCESS_TOKEN_INTENT_ACTION.equals(action)) {
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Facebook Access Token updated.");
                }
                createFacebookAccessTokenFuture();
            }
        }
    };

    /**************************************************************************/

    private static final String GCM_RECEIVE_INTENT_ACTION = "com.google.android.c2dm.intent.RECEIVE";

    @Override
    public void onReceive(Context context, Intent intent) {
        // In case a push comes in
        CacheManager.initialize(context);

        String action = intent.getAction();

        if (GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            final TeakNotification notif = TeakNotification.remoteNotificationFromIntent(context, intent);
            if (notif == null) {
                return;
            }

            // Send Notification Received Metric
            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                @Override
                public void run(Session session) {
                    HashMap<String, Object> payload = new HashMap<>();
                    payload.put("app_id", Teak.appConfiguration.appId);
                    payload.put("user_id", session.userId());
                    payload.put("platform_id", notif.teakNotifId);

                    CachedRequest.submitCachedRequest("/notification_received", payload, new Date());
                }
            });
        } else if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX)) {
            Bundle bundle = intent.getExtras();

            // Cancel any updates pending
            TeakNotification.cancel(context, bundle.getInt("platformId"));

            // Launch the app
            if (!bundle.getBoolean("noAutolaunch")) {
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Notification (" + bundle.getString("teakNotifId") + ") opened, auto-launching app.");
                }
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                launchIntent.addCategory("android.intent.category.LAUNCHER");
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                launchIntent.putExtras(bundle);
                if (bundle.getString("deepLink") != null) {
                    launchIntent.setData(Uri.parse(bundle.getString("deepLink")));
                }
                context.startActivity(launchIntent);
            } else {
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Notification (" + bundle.getString("teakNotifId") + ") opened, NOT auto-launching app (noAutoLaunch flag present, and set to true).");
                }
            }
        } else if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX)) {
            Bundle bundle = intent.getExtras();
            TeakNotification.cancel(context, bundle.getInt("platformId"));
        }
    }

    static void createFacebookAccessTokenFuture() {
        Teak.facebookAccessToken = new FutureTask<>(new Callable<String>() {
            public String call() {
                try {
                    return Teak.facebookAccessTokenQueue.take();
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                }
                return null;
            }
        });
        Teak.asyncExecutor.submit(Teak.facebookAccessToken);
    }

    /**************************************************************************/

    @SuppressWarnings("unused")
    private static void openIABPurchaseSucceeded(String json) {
        try {
            JSONObject purchase = new JSONObject(json);
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "OpenIAB purchase succeeded: " + purchase.toString(2));
            }

            if (Teak.appStore != null && Teak.appStore.ignorePluginPurchaseEvents()) {
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "OpenIAB callback ignored, store purchase reporting is auto-magical.");
                }
            } else {
                JSONObject originalJson = new JSONObject(purchase.getString("originalJson"));
                purchaseSucceeded(originalJson);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));
            Teak.sdkRaven.reportException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void prime31PurchaseSucceeded(String json) {
        try {
            JSONObject originalJson = new JSONObject(json);
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Prime31 purchase succeeded: " + originalJson.toString(2));
            }

            if (Teak.appStore != null && Teak.appStore.ignorePluginPurchaseEvents()) {
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Prime31 callback ignored, store purchase reporting is auto-magical.");
                }
            } else {
                purchaseSucceeded(originalJson);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));
            Teak.sdkRaven.reportException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void pluginPurchaseFailed(int errorCode) {
        String sku = skuStack.pop();
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "OpenIAB/Prime31 purchase failed (" + errorCode + ") for sku: " + sku);
        }
        purchaseFailed(errorCode, sku);
    }

    static void purchaseSucceeded(final JSONObject purchaseData) {
        Teak.asyncExecutor.submit(new Runnable() {
            public void run() {
                try {
                    if (Teak.isDebug) {
                        Log.d(LOG_TAG, "Purchase succeeded: " + purchaseData.toString(2));
                    }

                    HashMap<String, Object> payload = new HashMap<>();

                    if (Teak.appConfiguration.installerPackage == null) {
                        Log.e(LOG_TAG, "Purchase succeded from unknown app store.");
                    } else if (Teak.appConfiguration.installerPackage.equals("com.amazon.venezia")) {
                        JSONObject receipt = purchaseData.getJSONObject("receipt");
                        JSONObject userData = purchaseData.getJSONObject("userData");

                        payload.put("purchase_token", receipt.get("receiptId"));
                        payload.put("purchase_time_string", receipt.get("purchaseDate"));
                        payload.put("product_id", receipt.get("sku"));
                        payload.put("store_user_id", userData.get("userId"));
                        payload.put("store_marketplace", userData.get("marketplace"));

                        Log.d(LOG_TAG, "Purchase of " + receipt.get("sku") + " detected.");
                    } else {
                        payload.put("purchase_token", purchaseData.get("purchaseToken"));
                        payload.put("purchase_time", purchaseData.get("purchaseTime"));
                        payload.put("product_id", purchaseData.get("productId"));
                        if (purchaseData.has("orderId")) {
                            payload.put("order_id", purchaseData.get("orderId"));
                        }

                        Log.d(LOG_TAG, "Purchase of " + purchaseData.get("productId") + " detected.");
                    }

                    if (Teak.appStore != null) {
                        JSONObject skuDetails = Teak.appStore.querySkuDetails((String) payload.get("product_id"));
                        if (skuDetails != null) {
                            if (skuDetails.has("price_amount_micros")) {
                                payload.put("price_currency_code", skuDetails.getString("price_currency_code"));
                                payload.put("price_amount_micros", skuDetails.getString("price_amount_micros"));
                            } else if (skuDetails.has("price_string")) {
                                payload.put("price_string", skuDetails.getString("price_string"));
                            }
                        }
                    }

                    CachedRequest.submitCachedRequest("/me/purchase", payload, new Date());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error reporting purchase: " + Log.getStackTraceString(e));
                    Teak.sdkRaven.reportException(e);
                }
            }
        });
    }

    static void purchaseFailed(int errorCode, String sku) {
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "Purchase failed (" + errorCode + ") for sku: " + sku);
        }

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("error_code", errorCode);
        payload.put("product_id", sku == null ? "" : sku);

        CachedRequest.submitCachedRequest("/me/purchase", payload, new Date());
    }

    public static void checkActivityResultForPurchase(int resultCode, Intent data) {
        if (Teak.appStore != null) {
            Teak.appStore.checkActivityResultForPurchase(resultCode, data);
        } else {
            Log.e(LOG_TAG, "Unable to checkActivityResultForPurchase, no active app store.");
        }
    }
}
