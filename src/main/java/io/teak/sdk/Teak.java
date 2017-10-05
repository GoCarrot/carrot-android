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

import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.pm.ActivityInfo;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.InvalidParameterException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.HashMap;
import java.util.concurrent.Future;

import io.teak.sdk.Helpers._;

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
     * Force debug print on/off.
     */
    public static boolean forceDebug;

    /**
     * @return Description of the Teak SDK as Hash
     */
    public static Map<String, Object> to_h() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("android", Teak.SDKVersion);
        if (Teak.mainActivity != null) {
            String wrapperSDKName = Helpers.getStringResourceByName("io_teak_wrapper_sdk_name", Teak.mainActivity.getApplicationContext());
            String wrapperSDKVersion = Helpers.getStringResourceByName("io_teak_wrapper_sdk_version", Teak.mainActivity.getApplicationContext());
            if (wrapperSDKName != null && wrapperSDKVersion != null) {
                map.put(wrapperSDKName, wrapperSDKVersion);
            }
        }
        return map;
    }

    /**
     * Initialize Teak and tell it to listen to the lifecycle events of {@link Activity}.
     * <p/>
     * <p>Call this function from the {@link Activity#onCreate} function of your <code>Activity</code>
     * <b>before</b> the call to <code>super.onCreate()</code></p>
     *
     * @param activity The main <code>Activity</code> of your app.
     */
    public static void onCreate(Activity activity) {
        Teak.mainActivity = activity;
        Teak.log.useSdk(Teak.to_h());

        if (activity == null) {
            throw new InvalidParameterException("null Activity passed to Teak.onCreate");
        }

        // Set up debug logging ASAP
        Teak.debugConfiguration = new DebugConfiguration(Teak.mainActivity.getApplicationContext());

        // Check the launch mode of the activity
        try {
            ComponentName cn = new ComponentName(activity, activity.getClass());
            ActivityInfo ai = activity.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
            // (LAUNCH_SINGLE_INSTANCE == LAUNCH_SINGLE_TASK | LAUNCH_SINGLE_TOP) but let's not
            // assume that those values will stay the same
            if ((ai.launchMode & ActivityInfo.LAUNCH_SINGLE_INSTANCE) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TASK) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TOP) == 0) {
                Teak.log.w("launch_mode", "The android:launchMode of this activity is not set to 'singleTask', 'singleTop' or 'singleInstance'. This could cause undesired behavior.");
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Teak.log.e("api_level", "Teak requires API level 14 to operate. Teak is disabled.");
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
                Teak.log.exception(e);
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
        // Integer.toHexString(Teak.stateMutex.hashCode()) ?
        Teak.log.i("lifecycle", _.h("callback", "onActivityResult"));

        if (Teak.isEnabled()) {
            if (data != null) {
                checkActivityResultForPurchase(resultCode, data);
            }
        }
    }

    /**
     * @deprecated call {@link Activity#setIntent(Intent)} inside your {@link Activity#onNewIntent(Intent)}.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static void onNewIntent(Intent intent) {
        // Integer.toHexString(Teak.stateMutex.hashCode()) ?
        Teak.log.i("lifecycle", _.h("callback", "onNewIntent"));

        Teak.mainActivity.setIntent(intent);
    }

    /**
     * Tell Teak how it should identify the current user.
     * <p/>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     */
    public static void identifyUser(String userIdentifier) {
        if (userIdentifier == null || userIdentifier.isEmpty()) {
            Teak.log.e("identify_user.error", "User identifier can not be null or empty.");
            return;
        }

        Teak.log.i("identify_user", _.h("userId", userIdentifier));

        if (Teak.isEnabled()) {
            // Add userId to the Ravens
            Teak.sdkRaven.addUserData("id", userIdentifier);
            Teak.appRaven.addUserData("id", userIdentifier);

            // Send to Session
            Session.setUserId(userIdentifier);
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
        if (actionId == null || actionId.isEmpty()) {
            Teak.log.e("track_event.error", "actionId can not be null or empty for trackEvent(), ignoring.");
            return;
        }

        if ((objectInstanceId != null && !objectInstanceId.isEmpty()) &&
                (objectTypeId == null || objectTypeId.isEmpty())) {
            Teak.log.e("track_event.error", "objectTypeId can not be null or empty if objectInstanceId is present for trackEvent(), ignoring.");
            return;
        }

        Teak.log.i("track_event", _.h("actionId", actionId, "objectTypeId", objectTypeId, "objectInstanceId", objectInstanceId));

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
        }
    }

    /**
     * Intent action used by Teak to notify you that the app was launched from a notification.
     * <p/>
     * You can listen for this using a {@link BroadcastReceiver} and the {@link LocalBroadcastManager}.
     * <pre>
     * {@code
     *     IntentFilter filter = new IntentFilter();
     *     filter.addAction(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);
     *     LocalBroadcastManager.getInstance(context).registerReceiver(yourBroadcastListener, filter);
     * }
     * </pre>
     */
    public static final String LAUNCHED_FROM_NOTIFICATION_INTENT = "io.teak.sdk.Teak.intent.LAUNCHED_FROM_NOTIFICATION";

    /**
     * Intent action used by Teak to notify you that the a reward claim attempt has occured.
     * <p/>
     * You can listen for this using a {@link BroadcastReceiver} and the {@link LocalBroadcastManager}.
     * <pre>
     * {@code
     *     IntentFilter filter = new IntentFilter();
     *     filter.addAction(Teak.REWARD_CLAIM_ATTEMPT);
     *     LocalBroadcastManager.getInstance(context).registerReceiver(yourBroadcastListener, filter);
     * }
     * </pre>
     */
    public static final String REWARD_CLAIM_ATTEMPT = "io.teak.sdk.Teak.intent.REWARD_CLAIM_ATTEMPT";

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
                Teak.log.i("teak.state_duplicate", String.format("Teak State transition to same state (%s). Ignoring.", Teak.state));
                return false;
            }

            if (!Teak.state.canTransitionTo(newState)) {
                Teak.log.e("teak.state_invalid", String.format("Invalid Teak State transition (%s -> %s). Ignoring.", Teak.state, newState));
                return false;
            }

            Teak.log.i("teak.state", _.h("old_state", Teak.state.name, "state", newState.name));

            // TODO: Event listeners?

            Teak.state = newState;

            return true;
        }
    }
    // endregion

    static final String PREFERENCES_FILE = "io.teak.sdk.Preferences";

    public static Future<Void> waitForDeepLink;

    static DebugConfiguration debugConfiguration;

    public static int jsonLogIndentation = 0;
    static io.teak.sdk.Log log = new io.teak.sdk.Log(Teak.LOG_TAG, Teak.jsonLogIndentation);

    static Raven sdkRaven;
    static Raven appRaven;

    static LocalBroadcastManager localBroadcastManager;

    private static Activity mainActivity;
    private static IStore appStore;
    static AppConfiguration appConfiguration;
    static DeviceConfiguration deviceConfiguration;

    private static FacebookAccessTokenBroadcast facebookAccessTokenBroadcast;

    private static ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    // region Debug Output Formatter
    static String formatJSONForLogging(JSONObject obj) throws JSONException {
        if (Teak.jsonLogIndentation > 0) {
            return obj.toString(Teak.jsonLogIndentation);
        } else {
            return obj.toString();
        }
    }
    // endregion

    /**************************************************************************/

    static final ActivityLifecycleCallbacks lifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity inActivity, Bundle savedInstanceState) {
            if (inActivity != Teak.mainActivity) return;
            if (!Teak.setState(State.Created)) return;

            final Context context = inActivity.getApplicationContext();

            // App Configuration
            Teak.appConfiguration = new AppConfiguration(context);
            Teak.log.useAppConfiguration(Teak.appConfiguration);

            // Device configuration
            Teak.deviceConfiguration = new DeviceConfiguration(context, Teak.appConfiguration);

            // If deviceId is null, we can't operate
            if (Teak.deviceConfiguration.deviceId == null) {
                Teak.setState(State.Disabled);
                Teak.cleanup(inActivity);
                return;
            }

            Teak.log.useDeviceConfiguration(Teak.deviceConfiguration);

            // Facebook Access Token Broadcaster
            Teak.facebookAccessTokenBroadcast = new FacebookAccessTokenBroadcast(context);

            // Hook in to Session state change events
            Session.addEventListener(Teak.sessionEventListener);
            RemoteConfiguration.addEventListener(Teak.remoteConfigurationEventListener);

            // Ravens
            Teak.sdkRaven = new Raven(context, "sdk", Teak.appConfiguration, Teak.deviceConfiguration);
            Teak.appRaven = new Raven(context, Teak.appConfiguration.bundleId, Teak.appConfiguration, Teak.deviceConfiguration);

            // Add the log runId to the Ravens so we can line up logs and exception reports
            Teak.sdkRaven.addUserData("log_run_id", Teak.log.runId);
            Teak.appRaven.addUserData("log_run_id", Teak.log.runId);

            // Broadcast manager
            Teak.localBroadcastManager = LocalBroadcastManager.getInstance(context);

            // Register teak_internal routes
            DeepLink.registerRoute("/teak_internal/store/:sku", "", "", new DeepLink.Call() {
                @Override
                public void call(Map<String, Object> params) {
                    if (Teak.appStore != null) {
                        Teak.appStore.launchPurchaseFlowForSKU((String)params.get("sku"));
                    }
                }
            });

            // Applicable store
            if (Teak.appConfiguration.installerPackage != null) {
                Class<?> clazz = null;
                if (Teak.appConfiguration.installerPackage.equals("com.amazon.venezia")) {
                    try {
                        clazz = Class.forName("com.amazon.device.iap.PurchasingListener");
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }

                    if (clazz != null) {
                        try {
                            clazz = Class.forName("io.teak.sdk.Amazon");
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                } else {
                    // Default to Google Play
                    try {
                        clazz = Class.forName("io.teak.sdk.GooglePlay");
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                }
                try {
                    IStore store = (IStore) (clazz != null ? clazz.newInstance() : null);
                    if (store != null) {
                        store.init(context);
                    }
                    Teak.appStore = store;
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            }

            Teak.log.i("lifecycle", _.h("callback", "onActivityCreated"));
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (activity != Teak.mainActivity) return;

            Teak.log.i("lifecycle", _.h("callback", "onActivityPaused"));
            if (Teak.setState(State.Paused)) {
                Session.onActivityPaused();
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (activity != Teak.mainActivity) return;

            Teak.log.i("lifecycle", _.h("callback", "onActivityResumed"));

            if (Teak.setState(State.Active)) {
                if (Teak.appStore != null) {
                    Teak.appStore.onActivityResumed();
                }

                Intent intent = activity.getIntent();
                if (intent == null) {
                    intent = new Intent();
                }

                Session.onActivityResumed(intent, Teak.appConfiguration, Teak.deviceConfiguration);

                if (intent.getBooleanExtra("processedByTeak", false)) {
                    return;
                } else {
                    intent.putExtra("processedByTeak", true);
                }

                if (intent.hasExtra("teakNotifId")) {
                    Bundle bundle = intent.getExtras();

                    // Send broadcast
                    if (Teak.localBroadcastManager != null) {
                        final HashMap<String, Object> eventDataDict = new HashMap<String, Object>();
                        if (bundle.getString("teakRewardId") != null) {
                            eventDataDict.put("incentivized", true);
                            eventDataDict.put("teakRewardId", bundle.getString("teakRewardId"));
                        } else {
                            eventDataDict.put("incentivized", false);
                        }
                        if (bundle.getString("teakScheduleName") != null) eventDataDict.put("teakScheduleName", bundle.getString("teakScheduleName"));
                        if (bundle.getString("teakCreativeName") != null) eventDataDict.put("teakCreativeName", bundle.getString("teakCreativeName"));


                        final Intent broadcastEvent = new Intent(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);
                        broadcastEvent.putExtras(bundle);
                        broadcastEvent.putExtra("eventData", eventDataDict);
                        Teak.localBroadcastManager.sendBroadcast(broadcastEvent);

                        String teakRewardId = bundle.getString("teakRewardId");
                        if (teakRewardId != null) {
                            final Future<TeakNotification.Reward> rewardFuture = TeakNotification.Reward.rewardFromRewardId(teakRewardId);
                            if (rewardFuture != null) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            TeakNotification.Reward reward = rewardFuture.get();
                                            HashMap<String, Object> rewardMap = Helpers.jsonToMap(reward.json);
                                            rewardMap.putAll(eventDataDict);

                                            // Broadcast reward only if everything goes well
                                            final Intent rewardIntent = new Intent(Teak.REWARD_CLAIM_ATTEMPT);
                                            rewardIntent.putExtra("reward", rewardMap);
                                            Teak.localBroadcastManager.sendBroadcast(rewardIntent);
                                        } catch (Exception e) {
                                            Teak.log.exception(e);
                                        }
                                    }
                                }).start();
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onActivityStarted(Activity unused) {
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
            if (configuration.sdkSentryDsn != null) {
                Teak.sdkRaven.setDsn(configuration.sdkSentryDsn);
            }

            if (configuration.appSentryDsn != null) {
                Teak.appRaven.setDsn(configuration.appSentryDsn);

                if (!android.os.Debug.isDebuggerConnected()) {
                    Teak.appRaven.setAsUncaughtExceptionHandler();
                }
            }

            Teak.log.useRemoteConfiguration(configuration);
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
    private static final String GCM_REGISTRATION_INTENT_ACTION  = "com.google.android.c2dm.intent.REGISTRATION";

    static void handlePushNotificationReceived(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        TeakNotification notif = null;
        boolean showInForeground = Helpers.getBooleanFromBundle(bundle, "teakShowInForeground");
        if (showInForeground || Session.isExpiringOrExpired()) {
            notif = TeakNotification.remoteNotificationFromIntent(context, intent);
            if (notif == null) {
                return;
            }
        }
        final long teakNotifId = notif == null ? 0 : notif.teakNotifId;
        final String teakUserId = bundle.getString("teakUserId", null);

        if (teakUserId == null) {
            return;
        }

        HashMap<String, Object> debugHash = new HashMap<>();
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            Object o = bundle.get(key);
            if (o instanceof String) {
                try {
                    JSONObject jsonObject = new JSONObject(o.toString());
                    o = Helpers.jsonToMap(jsonObject);
                } catch (Exception ignored) {
                }
            }
            debugHash.put(key, o);
        }
        Teak.log.i("notification.received", debugHash);

        // Send Notification Received Metric
        Session session = Session.getCurrentSessionOrNull();
        if (session != null) {
            HashMap<String, Object> payload = new HashMap<>();
            payload.put("app_id", Teak.appConfiguration.appId);
            payload.put("user_id", teakUserId);
            payload.put("platform_id", teakNotifId);

            if (teakNotifId == 0) {
                payload.put("impression", false);
            }

            new Thread(new Request("parsnip.gocarrot.com", "/notification_received", payload, session)).start();
        }
    }

    @Override
    public void onReceive(Context inContext, Intent intent) {
        final Context context = inContext.getApplicationContext();

        if (!Teak.isEnabled()) {
            return;
        }

        String action = intent.getAction();

        if (GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            Teak.handlePushNotificationReceived(context, intent);
        } else if (GCM_REGISTRATION_INTENT_ACTION.equals(action)) {
            final String registrationId = intent.getStringExtra("registration_id");
            if (registrationId != null && Teak.deviceConfiguration != null) {
                Teak.deviceConfiguration.assignGcmRegistration(registrationId);
            }
        } else if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX)) {
            Bundle bundle = intent.getExtras();

            // Cancel any updates pending
            TeakNotification.cancel(context, bundle.getInt("platformId"));

            // Launch the app
            boolean autoLaunch = !Helpers.getBooleanFromBundle(bundle, "noAutolaunch");
            Teak.log.i("notification.opened", _.h("teakNotifId", bundle.getString("teakNotifId"), "autoLaunch", autoLaunch));

            if (autoLaunch) {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                launchIntent.addCategory("android.intent.category.LAUNCHER");
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                launchIntent.putExtras(bundle);
                if (bundle.getString("teakDeepLink") != null) {
                    Uri teakDeepLink = Uri.parse(bundle.getString("teakDeepLink"));
                    launchIntent.setData(teakDeepLink);
                }
                context.startActivity(launchIntent);
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
            Teak.log.i("open_iab", Helpers.jsonToMap(purchase));

            if (Teak.appStore == null || !Teak.appStore.ignorePluginPurchaseEvents()) {
                JSONObject originalJson = new JSONObject(purchase.getString("originalJson"));
                purchaseSucceeded(originalJson);
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    @SuppressWarnings("unused")
    private static void prime31PurchaseSucceeded(String json) {
        try {
            JSONObject originalJson = new JSONObject(json);
            Teak.log.i("prime_31", Helpers.jsonToMap(originalJson));

            if (Teak.appStore == null || !Teak.appStore.ignorePluginPurchaseEvents()) {
                purchaseSucceeded(originalJson);
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    @SuppressWarnings("unused")
    private static void pluginPurchaseFailed(int errorCode) {
        Teak.log.i("plugin_purchase.failed", _.h("errorCode", errorCode));
        purchaseFailed(errorCode);
    }

    static void purchaseSucceeded(final JSONObject purchaseData) {
        Teak.asyncExecutor.submit(new Runnable() {
            public void run() {
                try {
                    Teak.log.i("puchase.succeeded", Helpers.jsonToMap(purchaseData));

                    final HashMap<String, Object> payload = new HashMap<>();

                    if (Teak.appConfiguration.installerPackage != null && Teak.appConfiguration.installerPackage.equals("com.amazon.venezia")) {
                        JSONObject receipt = purchaseData.getJSONObject("receipt");
                        JSONObject userData = purchaseData.getJSONObject("userData");

                        payload.put("purchase_token", receipt.get("receiptId"));
                        payload.put("purchase_time_string", receipt.get("purchaseDate"));
                        payload.put("product_id", receipt.get("sku"));
                        payload.put("store_user_id", userData.get("userId"));
                        payload.put("store_marketplace", userData.get("marketplace"));
                    } else {
                        payload.put("purchase_token", purchaseData.get("purchaseToken"));
                        payload.put("purchase_time", purchaseData.get("purchaseTime"));
                        payload.put("product_id", purchaseData.get("productId"));
                        if (purchaseData.has("orderId")) {
                            payload.put("order_id", purchaseData.get("orderId"));
                        }
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
                    Teak.log.exception(e);
                }
            }
        });
    }

    static void purchaseFailed(int errorCode) {
        Teak.log.i("puchase.failed", _.h("errorCode", errorCode));

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
                Teak.log.e("puchase.failed", "Unable to checkActivityResultForPurchase, no active app store.");
            }
        }
    }
}
