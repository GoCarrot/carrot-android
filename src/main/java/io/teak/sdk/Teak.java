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
import android.content.BroadcastReceiver;
import android.content.pm.ApplicationInfo;

import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import android.os.Bundle;

import android.util.Log;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.HashMap;
import java.util.concurrent.Future;

/**
 * Teak
 */
public class Teak extends BroadcastReceiver {
    private static final String LOG_TAG = "Teak";

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
            Log.e(LOG_TAG, "null Activity passed to onCreate, Teak is disabled.");
            Teak.setState(State.Disabled);
            return;
        }

        {
            String airSdkVersion = Helpers.getStringResourceByName("io_teak_air_sdk_version", activity.getApplicationContext());
            if (airSdkVersion != null) {
                Log.d(LOG_TAG, "Adobe AIR SDK Version: " + airSdkVersion);
            }
        }

        // Set up debug logging ASAP
        try {
            final Context context = activity.getApplicationContext();
            final ApplicationInfo applicationInfo = context.getApplicationInfo();
            Teak.debugConfiguration = new DebugConfiguration(context);
            Teak.isDebug = Teak.forceDebug || Teak.debugConfiguration.forceDebug || (applicationInfo != null && (0 != (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE)));
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error creating DebugConfiguration. " + Log.getStackTraceString(e));
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.e(LOG_TAG, "Teak requires API level 14 to operate. Teak is disabled.");
            Teak.setState(State.Disabled);
        } else {
            try {
                Application application = activity.getApplication();
                synchronized (Teak.stateMutex) {
                    if (Teak.state == State.Allocated) {
                        application.registerActivityLifecycleCallbacks(Teak.lifecycleCallbacks);
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to register Activity lifecycle callbacks. Teak is disabled. " + Log.getStackTraceString(e));
                Teak.setState(State.Disabled);
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

        if (Teak.isEnabled()) {
            if (data != null) {
                checkActivityResultForPurchase(resultCode, data);
            }
        } else {
            Log.e(LOG_TAG, "Teak is disabled, ignoring onActivityResult().");
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

        if (Teak.isEnabled()) {
            if (Teak.appConfiguration != null && Teak.deviceConfiguration != null) {
                Session.processIntent(intent, Teak.appConfiguration, Teak.deviceConfiguration);
            } else {
                Log.e(LOG_TAG, "App Configuration and/or Device Configuration are null, cannot process onNewIntent().");
            }
        } else {
            Log.e(LOG_TAG, "Teak is disabled, ignoring onNewIntent().");
        }
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

        if (Teak.isEnabled()) {
            // Add userId to the Ravens
            Teak.sdkRaven.addUserData("id", userIdentifier);
            Teak.appRaven.addUserData("id", userIdentifier);

            // Send to Session
            Session.setUserId(userIdentifier);
        } else {
            Log.e(LOG_TAG, "Teak is disabled, ignoring identifyUser().");
        }
    }

    /**
     * Track an arbitrary event in Teak.
     *
     * @param actionId         The identifier for the action, e.g. 'complete'.
     * @param objectTypeId     The type of object that is being posted, e.g. 'quest'.
     * @param objectInstanceId The specific instance of the object, e.g. 'gather-quest-1'
     */
    @SuppressWarnings("unused")
    public static void trackEvent(final String actionId, final String objectTypeId, final String objectInstanceId) {
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "Tracking Event: " + actionId + " - " + objectTypeId + " - " + objectInstanceId);
        }

        if (actionId == null || actionId.isEmpty()) {
            Log.e(LOG_TAG, "actionId can not be null or empty for trackEvent(), ignoring.");
            return;
        }

        if ((objectInstanceId == null || objectInstanceId.isEmpty()) &&
                (objectTypeId == null || objectTypeId.isEmpty())) {
            Log.e(LOG_TAG, "objectTypeId can not be null or empty if objectInstanceId is present for trackEvent(), ignoring.");
            return;
        }

        if (Teak.isEnabled()) {
            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                @Override
                public void run(Session session) {
                    HashMap<String, Object> payload = new HashMap<>();
                    payload.put("action_type", actionId);
                    payload.put("object_type", objectTypeId);
                    payload.put("object_instance_id", objectInstanceId);

                    new Request("/me/events", payload, session).run();
                }
            });
        } else {
            Log.e(LOG_TAG, "Teak is disabled, ignoring trackEvent().");
        }
    }

    /**************************************************************************/

    // region State machine
    public enum State {
        Disabled("Disabled"),
        Allocated("Allocated"),
        Created("Created"),
        Active("Active"),
        Paused("Paused"),
        Destroyed("Destroyed");

        //public static final Integer length = 1 + Destroyed.ordinal();

        private static final State[][] allowedTransitions = {
                {},
                {State.Created},
                {State.Active},
                {State.Paused},
                {State.Destroyed, State.Active},
                {}
        };

        public final String name;

        State(String name) {
            this.name = name;
        }

        public boolean canTransitionTo(State nextState) {
            if (nextState == State.Disabled) return true;

            for (State allowedTransition : allowedTransitions[this.ordinal()]) {
                if (nextState == allowedTransition) return true;
            }
            return false;
        }
    }

    private static State state = State.Allocated;
    private static final Object stateMutex = new Object();

    static boolean isEnabled() {
        synchronized (Teak.stateMutex) {
            return (Teak.state != State.Disabled);
        }
    }

    private static boolean setState(@NonNull State newState) {
        synchronized (Teak.stateMutex) {
            if (Teak.state == newState) {
                Log.i(LOG_TAG, String.format("Teak State transition to same state (%s). Ignoring.", Teak.state));
                return false;
            }

            if (!Teak.state.canTransitionTo(newState)) {
                Log.e(LOG_TAG, String.format("Invalid Teak State transition (%s -> %s). Ignoring.", Teak.state, newState));
                return false;
            }

            if (Teak.isDebug) {
                Log.d(LOG_TAG, String.format("Teak State transition from %s -> %s.", Teak.state, newState));
            }

            // TODO: Event listeners

            Teak.state = newState;

            return true;
        }
    }
    // endregion

    static final String PREFERENCES_FILE = "io.teak.sdk.Preferences";

    public static boolean forceDebug;

    static boolean isDebug;
    static DebugConfiguration debugConfiguration;

    static Raven sdkRaven;
    static Raven appRaven;

    static LocalBroadcastManager localBroadcastManager;

    private static IStore appStore;
    private static AppConfiguration appConfiguration;
    private static DeviceConfiguration deviceConfiguration;

    private static FacebookAccessTokenBroadcast facebookAccessTokenBroadcast;

    private static ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    /**************************************************************************/

    private static final ActivityLifecycleCallbacks lifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity inActivity, Bundle savedInstanceState) {
            if (!Teak.setState(State.Created)) {
                // Still process launch event
                Session.processIntent(inActivity.getIntent(), Teak.appConfiguration, Teak.deviceConfiguration);
                return;
            }

            final Context context = inActivity.getApplicationContext();

            // App Configuration
            Teak.appConfiguration = new AppConfiguration(context);

            if (Teak.isDebug) {
                Log.d(LOG_TAG, Teak.appConfiguration.toString());
            }

            // Device configuration
            Teak.deviceConfiguration = new DeviceConfiguration(context, Teak.appConfiguration);

            // If deviceId is null, we can't operate
            if (Teak.deviceConfiguration.deviceId == null) {
                Teak.setState(State.Disabled);
                cleanup(inActivity);
                return;
            }

            if (Teak.isDebug) {
                Log.d(LOG_TAG, Teak.deviceConfiguration.toString());
            }

            // Display a clickable "report bug" link, as well as a copy/paste block for bug reporting
            if (Teak.debugConfiguration != null) {
                Teak.debugConfiguration.printBugReportInfo(context, Teak.appConfiguration, Teak.deviceConfiguration);
            }

            // Facebook Access Token Broadcaster
            Teak.facebookAccessTokenBroadcast = new FacebookAccessTokenBroadcast(context);

            // Hook in to Session state change events
            Session.addEventListener(Teak.sessionEventListener);
            RemoteConfiguration.addEventListener(Teak.remoteConfigurationEventListener);

            // Ravens
            Teak.sdkRaven = new Raven(context, "sdk", Teak.appConfiguration, Teak.deviceConfiguration);
            Teak.appRaven = new Raven(context, Teak.appConfiguration.bundleId, Teak.appConfiguration, Teak.deviceConfiguration);

            // Broadcast manager
            Teak.localBroadcastManager = LocalBroadcastManager.getInstance(context);

            // Process launch event
            Session.processIntent(inActivity.getIntent(), Teak.appConfiguration, Teak.deviceConfiguration);

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
                    IStore store = (IStore) (clazz != null ? clazz.newInstance() : null);
                    if (store != null) {
                        store.init(context);
                    }
                    Teak.appStore = store;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Unable to create app store interface. " + Log.getStackTraceString(e));
                    Teak.sdkRaven.reportException(e);
                }
            }

            // Validate the app id/key via "/games/#{@appId}/validate_sig.json"
            if (Teak.isDebug) {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("id", Teak.appConfiguration.appId);
                new Thread(new Request("gocarrot.com", "/games/" + Teak.appConfiguration.appId + "/validate_sig.json", payload, Session.getCurrentSession(Teak.appConfiguration, Teak.deviceConfiguration)) {
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
        public void onActivityPaused(Activity unused) {
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityPaused");
            }
            if (Teak.setState(State.Paused)){
                Session.onActivityPaused();
            }
        }

        @Override
        public void onActivityResumed(Activity unused) {
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Lifecycle - onActivityResumed");
            }

            if (Teak.setState(State.Active)) {
                if (Teak.appStore != null) {
                    Teak.appStore.onActivityResumed();
                }

                Session.onActivityResumed(Teak.appConfiguration, Teak.deviceConfiguration);
            }
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
            } else if (activity.getClass().getName().equals("com.prime31.GoogleIABProxyActivity")) {
                Bundle bundle = activity.getIntent().getExtras();
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Unity Prime31 purchase launched: " + bundle.toString());
                }
            }
        }

        @Override
        public void onActivityDestroyed(Activity unused) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity unused, Bundle outState) {
        }

        @Override
        public void onActivityStopped(Activity unused) {
        }
    };

    private static final Session.EventListener sessionEventListener = new Session.EventListener() {
        @Override
        public void onStateChange(Session session, Session.State oldState, Session.State newState) {
            if (newState == Session.State.Created) {
                // If Session state is now 'Created', we need the configuration from the Teak server
                RemoteConfiguration.requestConfigurationForApp(session);
            }
        }
    };

    private static final RemoteConfiguration.EventListener remoteConfigurationEventListener = new RemoteConfiguration.EventListener() {
        @Override
        public void onConfigurationReady(RemoteConfiguration configuration) {
            // Begin exception reporting, if enabled
            if (configuration.sdkSentryDSN() != null) {
                Teak.sdkRaven.setDsn(configuration.sdkSentryDSN());
            }

            if (configuration.appSentryDSN() != null) {
                Teak.appRaven.setDsn(configuration.appSentryDSN());

                if (!android.os.Debug.isDebuggerConnected()) {
                    Teak.appRaven.setAsUncaughtExceptionHandler();
                }
            }

            if (Teak.isDebug) {
                Log.d(LOG_TAG, configuration.toString());
            }
        }
    };

    private static void cleanup(Activity activity) {
        if (Teak.appStore != null) {
            Teak.appStore.dispose();
        }

        RemoteConfiguration.removeEventListener(Teak.remoteConfigurationEventListener);
        Session.removeEventListener(Teak.sessionEventListener);

        if (Teak.facebookAccessTokenBroadcast != null) {
            Teak.facebookAccessTokenBroadcast.unregister(activity.getApplicationContext());
        }

        activity.getApplication().unregisterActivityLifecycleCallbacks(Teak.lifecycleCallbacks);
    }

    /**************************************************************************/

    private static final String GCM_RECEIVE_INTENT_ACTION = "com.google.android.c2dm.intent.RECEIVE";

    @Override
    public void onReceive(Context inContext, Intent intent) {
        final Context context = inContext.getApplicationContext();

        if (!Teak.isEnabled()) {
            Log.e(LOG_TAG, "Teak is disabled, ignoring onReceive().");
            return;
        }

        String action = intent.getAction();

        if (GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            Bundle bundle = intent.getExtras();
            TeakNotification notif = null;
            if (bundle.getBoolean("teakShowInForeground", false) || Session.isExpiringOrExpired()) {
                notif = TeakNotification.remoteNotificationFromIntent(context, intent);
                if (notif == null) {
                    return;
                }
            }
            final long teakNotifId =  notif == null ? 0 : notif.teakNotifId;

            // Send Notification Received Metric
            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                @Override
                public void run(Session session) {
                    HashMap<String, Object> payload = new HashMap<>();
                    payload.put("app_id", session.appConfiguration.appId);
                    payload.put("user_id", session.userId());
                    payload.put("platform_id", teakNotifId);

                    if (teakNotifId == 0) {
                        payload.put("impression", false);
                    }

                    new Request("/notification_received", payload, session).run();
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
                if (bundle.getString("teakDeepLink") != null) {
                    Uri teakDeepLink = Uri.parse(bundle.getString("teakDeepLink"));
                    launchIntent.setData(teakDeepLink);
                }
                context.startActivity(launchIntent);
            } else {
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Notification (" + bundle.getString("teakNotifId") + ") opened, NOT auto-launching app (noAutoLaunch flag present, and set to true).");
                }
            }

            // Send broadcast
            if (Teak.localBroadcastManager != null) {
                final Intent broadcastEvent = new Intent(TeakNotification.LAUNCHED_FROM_NOTIFICATION_INTENT);
                broadcastEvent.putExtras(bundle);
                if (bundle.getString("teakDeepLink") != null) {
                    Uri teakDeepLink = Uri.parse(bundle.getString("teakDeepLink"));
                    HashMap<String, List<String>> teakDeepLinkQueryParameters = new HashMap<>();
                    for (String key : teakDeepLink.getQueryParameterNames()) {
                        teakDeepLinkQueryParameters.put(key, teakDeepLink.getQueryParameters(key));
                    }
                    broadcastEvent.putExtra("teakDeepLinkQueryParameters", teakDeepLinkQueryParameters);
                    broadcastEvent.putExtra("teakDeepLinkQueryParametersJson", new JSONObject(teakDeepLinkQueryParameters).toString());
                    broadcastEvent.putExtra("teakDeepLinkPath", teakDeepLink.getPath());
                }

                String teakRewardId = bundle.getString("teakRewardId");
                if (teakRewardId != null) {
                    final Future<TeakNotification.Reward> rewardFuture = TeakNotification.Reward.rewardFromRewardId(teakRewardId);
                    if(rewardFuture != null) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    TeakNotification.Reward reward = rewardFuture.get();
                                    broadcastEvent.putExtra("teakRewardJson", reward.originalJson.toString());
                                    broadcastEvent.putExtra("teakReward", Helpers.jsonToMap(reward.originalJson));
                                } catch(Exception e) {
                                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                                } finally {
                                    Teak.localBroadcastManager.sendBroadcast(broadcastEvent);
                                }
                            }
                        }).start();
                    } else {
                        Teak.localBroadcastManager.sendBroadcast(broadcastEvent);
                    }
                } else {
                    Teak.localBroadcastManager.sendBroadcast(broadcastEvent);
                }
            }
        } else if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX)) {
            Bundle bundle = intent.getExtras();
            TeakNotification.cancel(context, bundle.getInt("platformId"));
        }

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
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "OpenIAB/Prime31 purchase failed (" + errorCode + ")");
        }
        purchaseFailed(errorCode);
    }

    static void purchaseSucceeded(final JSONObject purchaseData) {
        Teak.asyncExecutor.submit(new Runnable() {
            public void run() {
                try {
                    if (Teak.isDebug) {
                        Log.d(LOG_TAG, "Purchase succeeded: " + purchaseData.toString(2));
                    }

                    final HashMap<String, Object> payload = new HashMap<>();

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

                    Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                        @Override
                        public void run(Session session) {
                            new Request("/me/purchase", payload, session).run();
                        }
                    });
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error reporting purchase: " + Log.getStackTraceString(e));
                    Teak.sdkRaven.reportException(e);
                }
            }
        });
    }

    static void purchaseFailed(int errorCode) {
        if (Teak.isDebug) {
            Log.d(LOG_TAG, "Purchase failed (" + errorCode + ")");
        }

        final HashMap<String, Object> payload = new HashMap<>();
        payload.put("error_code", errorCode);

        Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
            @Override
            public void run(Session session) {
                new Request("/me/purchase", payload, session).run();
            }
        });
    }

    public static void checkActivityResultForPurchase(int resultCode, Intent data) {
        if (Teak.isEnabled()) {
            if (Teak.appStore != null) {
                Teak.appStore.checkActivityResultForPurchase(resultCode, data);
            } else {
                Log.e(LOG_TAG, "Unable to checkActivityResultForPurchase, no active app store.");
            }
        } else {
            Log.e(LOG_TAG, "Teak is disabled, ignoring checkActivityResultForPurchase().");
        }
    }
}
