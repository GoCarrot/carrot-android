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
import android.content.pm.ApplicationInfo;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import android.os.Bundle;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.InvalidParameterException;
import java.util.Map;
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
            String airSdkVersion = Helpers.getStringResourceByName("io_teak_air_sdk_version", Teak.mainActivity.getApplicationContext());
            if (airSdkVersion != null) {
                map.put("adobeAir", airSdkVersion);
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
        try {
            final Context context = Teak.mainActivity.getApplicationContext();
            final ApplicationInfo applicationInfo = context.getApplicationInfo();
            Teak.debugConfiguration = new DebugConfiguration(context);
            Teak.isDebug = Teak.forceDebug || Teak.debugConfiguration.forceDebug || (applicationInfo != null && (0 != (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE)));
        } catch (Exception e) {
            Teak.log.exception(e);
        }

        // Check the launch mode of the activity
        try {
            ComponentName cn = new ComponentName(activity, activity.getClass());
            ActivityInfo ai = activity.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
            // (LAUNCH_SINGLE_INSTANCE == LAUNCH_SINGLE_TASK | LAUNCH_SINGLE_TOP) but let's not
            // assume that those values will stay the same
            if ((ai.launchMode & ActivityInfo.LAUNCH_SINGLE_INSTANCE) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TASK) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TOP) == 0) {
                if (Teak.isDebug) {
                    Log.w(LOG_TAG, "The android:launchMode of this activity is not set to 'singleTask', 'singleTop' or 'singleInstance'. This could cause undesired behavior.");
                }
            }
        } catch (Exception e) {
            Teak.log.exception(e);
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
        if (Teak.isDebug) {
            Log.d(LOG_TAG, String.format("Lifecycle@%s: {\"callback\": \"onActivityResult\"}", Integer.toHexString(Teak.stateMutex.hashCode())));
        }

        if (Teak.isEnabled()) {
            if (data != null) {
                checkActivityResultForPurchase(resultCode, data);
            }
        } else {
            Log.e(LOG_TAG, "Teak is disabled, ignoring onActivityResult().");
        }
    }

    /**
     * @deprecated call {@link Activity#setIntent(Intent)} inside your {@link Activity#onNewIntent(Intent)}.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static void onNewIntent(Intent intent) {
        if (Teak.isDebug) {
            Log.d(LOG_TAG, String.format("Lifecycle@%s: {\"callback\": \"onNewIntent\"}", Integer.toHexString(Teak.stateMutex.hashCode())));
        }

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
        // Always show this debug output.
        Log.d(LOG_TAG, String.format("IdentifyUser@%s: {\"userId\": \"%s\"}", Integer.toHexString(Teak.stateMutex.hashCode()), userIdentifier));

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
                Log.d(LOG_TAG, String.format("State@%s: {\"previousState\": \"%s\", \"state\": \"%s\"}",
                        Integer.toHexString(Teak.stateMutex.hashCode()), Teak.state, newState));
            }

            // TODO: Event listeners

            Teak.state = newState;

            return true;
        }
    }
    // endregion

    static final String PREFERENCES_FILE = "io.teak.sdk.Preferences";

    static boolean isDebug;
    static DebugConfiguration debugConfiguration;

    public static int jsonLogIndentation = 0;
    static io.teak.sdk.Log log = new io.teak.sdk.Log(Teak.LOG_TAG, Teak.jsonLogIndentation);

    static Raven sdkRaven;
    static Raven appRaven;

    static LocalBroadcastManager localBroadcastManager;

    private static Activity mainActivity;
    private static IStore appStore;
    private static AppConfiguration appConfiguration;
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

    private static final ActivityLifecycleCallbacks lifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity inActivity, Bundle savedInstanceState) {
            if (inActivity != Teak.mainActivity) return;
            if (!Teak.setState(State.Created)) return;

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
                Teak.cleanup(inActivity);
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
                        Log.e(LOG_TAG, "Amazon store detected, but app does not include in-app purchasing JAR.");
                    }

                    if (clazz != null) {
                        try {
                            clazz = Class.forName("io.teak.sdk.Amazon");
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Couldn't find Teak's Amazon app store handler. " + Log.getStackTraceString(e));
                            Teak.sdkRaven.reportException(e);
                        }
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

            if (Teak.isDebug) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("callback", "onActivityCreated");
                map.put("appConfiguration", Integer.toHexString(Teak.appConfiguration.hashCode()));
                map.put("deviceConfiguration", Integer.toHexString(Teak.deviceConfiguration.hashCode()));

                // Duplicate these, for ease of manual debugging
                map.put("appId", Teak.appConfiguration.appId);
                map.put("apiKey", Teak.appConfiguration.apiKey);
                map.put("appVersion", Teak.appConfiguration.appVersion);
                map.put("appStore", Teak.appConfiguration.installerPackage);

                String debugOutput = "{}";
                try {
                    debugOutput = Teak.formatJSONForLogging(new JSONObject(map));
                } catch (Exception ignored){
                }
                Log.d(LOG_TAG, String.format("Lifecycle@%s: %s", Integer.toHexString(Teak.stateMutex.hashCode()), debugOutput));
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (activity != Teak.mainActivity) return;

            if (Teak.isDebug) {
                Log.d(LOG_TAG, String.format("Lifecycle@%s: {\"callback\": \"onActivityPaused\"}", Integer.toHexString(Teak.stateMutex.hashCode())));
            }
            if (Teak.setState(State.Paused)) {
                Session.onActivityPaused();
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (activity != Teak.mainActivity) return;

            if (Teak.isDebug) {
                Log.d(LOG_TAG, String.format("Lifecycle@%s: {\"callback\": \"onActivityResumed\"}", Integer.toHexString(Teak.stateMutex.hashCode())));
            }

            if (Teak.setState(State.Active)) {
                if (Teak.appStore != null) {
                    Teak.appStore.onActivityResumed();
                }

                Intent intent = activity.getIntent();

                Session.onActivityResumed(intent, Teak.appConfiguration, Teak.deviceConfiguration);

                if (intent != null && intent.getExtras() != null && intent.hasExtra("teakNotifId")) {
                    Bundle bundle = intent.getExtras();

                    // Send broadcast
                    if (Teak.localBroadcastManager != null) {
                        final Intent broadcastEvent = new Intent(TeakNotification.LAUNCHED_FROM_NOTIFICATION_INTENT);
                        broadcastEvent.putExtras(bundle);

                        String teakRewardId = bundle.getString("teakRewardId");
                        if (teakRewardId != null) {
                            final Future<TeakNotification.Reward> rewardFuture = TeakNotification.Reward.rewardFromRewardId(teakRewardId);
                            if (rewardFuture != null) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            TeakNotification.Reward reward = rewardFuture.get();
                                            broadcastEvent.putExtra("teakReward", Helpers.jsonToMap(reward.json));
                                        } catch (Exception e) {
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
            Log.e(LOG_TAG, "Teak is disabled, ignoring onReceive().");
            return;
        }

        String action = intent.getAction();

        if (GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            Teak.handlePushNotificationReceived(context, intent);
        } else if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX)) {
            Bundle bundle = intent.getExtras();

            // Cancel any updates pending
            TeakNotification.cancel(context, bundle.getInt("platformId"));

            // Launch the app
            boolean autoLaunch = !Helpers.getBooleanFromBundle(bundle, "noAutolaunch");
            if (Teak.isDebug) {
                Log.d(LOG_TAG, String.format("Notification@%s: {\"teakNotifId\": \"%s\", \"autoLaunch\"=%b}", Integer.toHexString(Teak.stateMutex.hashCode()), bundle.getString("teakNotifId"), autoLaunch));
            }
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
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "OpenIAB purchase succeeded: " + Teak.formatJSONForLogging(purchase));
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
                Log.d(LOG_TAG, "Prime31 purchase succeeded: " + Teak.formatJSONForLogging(originalJson));
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
                        Log.d(LOG_TAG, "Purchase succeeded: " + Teak.formatJSONForLogging(purchaseData));
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
