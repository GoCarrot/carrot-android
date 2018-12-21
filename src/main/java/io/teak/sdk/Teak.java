package io.teak.sdk;

import android.app.Activity;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import io.teak.sdk.core.TeakCore;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import io.teak.sdk.json.JSONException;
import io.teak.sdk.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.teak.sdk.core.Session;
import io.teak.sdk.event.DeepLinksReadyEvent;
import io.teak.sdk.event.SessionStateEvent;
import io.teak.sdk.io.IAndroidResources;

/**
 * Teak
 */
public class Teak extends BroadcastReceiver implements Unobfuscable {
    static final String LOG_TAG = "Teak";

    /**
     * The default id Teak uses for {@link android.app.job.JobInfo}.
     */
    public static final int JOB_ID = 1946157056;

    /**
     * Version of the Teak SDK.
     *
     * @deprecated Use the {@link Teak#Version} member instead.
     */
    public static final String SDKVersion = io.teak.sdk.BuildConfig.VERSION_NAME;

    /**
     * Version of the Teak SDK, and Unity/Air SDK if applicable.
     * <p>
     * You must call {@link Teak#onCreate(Activity)} in order to get Unity/Air SDK version info.
     */
    public static final Map<String, Object> Version;

    /**
     * Version of the Teak SDK, as an array [major, minor, revision]
     */
    public static final int[] MajorMinorRevision;

    private static final Map<String, Object> sdkMap = new HashMap<>();

    static {
        sdkMap.put("android", io.teak.sdk.BuildConfig.VERSION_NAME);
        Version = Collections.unmodifiableMap(sdkMap);

        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+).*").matcher(io.teak.sdk.BuildConfig.VERSION_NAME);
        if (m.matches()) {
            MajorMinorRevision = new int[] {
                Integer.parseInt(m.group(1)), // major
                Integer.parseInt(m.group(2)), // minor
                Integer.parseInt(m.group(3))  // revision
            };
        } else {
            MajorMinorRevision = new int[] {0, 0, 0};
        }
    }

    /**
     * Force debug print on/off.
     */
    public static boolean forceDebug;

    /**
     * Is Teak enabled?
     *
     * @return True if Teak is enabled; False if Teak has not been initialized, or is disabled.
     */
    public static boolean isEnabled() {
        return Instance != null && Instance.isEnabled();
    }

    /**
     * Initialize Teak and tell it to listen to the lifecycle events of {@link Activity}.
     * <p/>
     * <p>Call this function from the {@link Activity#onCreate} function of your <code>Activity</code>
     * <b>before</b> the call to <code>super.onCreate()</code></p>
     *
     * @param activity The main <code>Activity</code> of your app.
     */
    @SuppressWarnings("unused")
    public static void onCreate(@NonNull Activity activity) {
        onCreate(activity, null);
    }

    /**
     * Used for internal testing.
     *
     * @param activity      The main <code>Activity</code> of your app.
     * @param objectFactory Teak Object Factory to use, or null for default.
     */
    public static void onCreate(@NonNull Activity activity, @Nullable IObjectFactory objectFactory) {
        // Init integration checks, we decide later if they report or not
        if (!IntegrationChecker.init(activity)) {
            return;
        }

        // Unless something gave us an object factory, use the default one
        if (objectFactory == null) {
            try {
                objectFactory = new DefaultObjectFactory(activity.getApplicationContext());
            } catch (Exception e) {
                return;
            }
        }

        // Add version info for Unity/Air
        IAndroidResources androidResources = objectFactory.getAndroidResources();
        //noinspection ConstantConditions
        if (androidResources != null) {
            String wrapperSDKName = androidResources.getStringResource("io_teak_wrapper_sdk_name");
            String wrapperSDKVersion = androidResources.getStringResource("io_teak_wrapper_sdk_version");
            if (wrapperSDKName != null && wrapperSDKVersion != null) {
                Teak.sdkMap.put(wrapperSDKName, wrapperSDKVersion);
            }
        }

        // Create Instance last
        if (Instance == null) {
            try {
                Instance = new TeakInstance(activity, objectFactory);
            } catch (Exception e) {
                return;
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
    @SuppressWarnings("unused")
    public static void onActivityResult(@SuppressWarnings("unused") int requestCode, int resultCode, Intent data) {
        Teak.log.i("lifecycle", Helpers.mm.h("callback", "onActivityResult"));

        if (data != null) {
            checkActivityResultForPurchase(resultCode, data);
        }
    }

    /**
     * @deprecated call {@link Activity#setIntent(Intent)} inside your {@link Activity#onNewIntent(Intent)}.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static void onNewIntent(Intent intent) {
        if (!Teak.sdkMap.containsKey("adobeAir")) {
            Teak.log.e("deprecation.onNewIntent", "Teak.onNewIntent is deprecated, call Activity.onNewIntent() instead.");
        }
    }

    /**
     * Tell Teak how it should identify the current user.
     * <p/>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     */
    @SuppressWarnings("unused")
    public static void identifyUser(final String userIdentifier) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.identifyUser(userIdentifier, new String[] {});
                }
            });
        }
    }

    /**
     * Value provided to {@link #identifyUser(String, String[])} to opt out of
     * collecting an IDFA for this specific user.
     * <p/>
     * If you prevent Teak from collecting the Identifier For Advertisers (IDFA), Teak will no longer be able to add this user to Facebook Ad Audiences.
     */
    @SuppressWarnings("unused")
    public static final String OPT_OUT_IDFA = "opt_out_idfa";

    /**
     * Value provided to {@link #identifyUser(String, String[])} to opt out of
     * collecting a Facebook Access Token for this specific user.
     * <p/>
     * If you prevent Teak from collecting the Facebook Access Token, Teak will no longer be able to correlate this user across multiple devices.
     */
    @SuppressWarnings("unused")
    public static final String OPT_OUT_FACEBOOK = "opt_out_facebook";

    /**
     * Value provided to {@link #identifyUser(String, String[])} to opt out of
     * collecting a Push Key for this specific user.
     * <p/>
     * If you prevent Teak from collecting the Push Key, Teak will no longer be able to send Local Notifications or Push Notifications for this user.
     */
    @SuppressWarnings("unused")
    public static final String OPT_OUT_PUSH_KEY = "opt_out_push_key";

    /**
     * Tell Teak how it should identify the current user, with data collection opt-out.
     * <p/>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     * @param optOut         A list containing zero or more of:
     *                          {@link #OPT_OUT_IDFA}, {@link #OPT_OUT_FACEBOOK}, {@link #OPT_OUT_PUSH_KEY}
     */
    @SuppressWarnings("unused")
    public static void identifyUser(final String userIdentifier, final String[] optOut) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.identifyUser(userIdentifier, optOut != null ? optOut : new String[] {});
                }
            });
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
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.trackEvent(actionId, objectTypeId, objectInstanceId);
                }
            });
        }
    }

    /**
     * Notifications are enabled.
     */
    public static final int TEAK_NOTIFICATIONS_ENABLED = 0;

    /**
     * Notifications are disabled.
     */
    public static final int TEAK_NOTIFICATIONS_DISABLED = 1;

    /**
     * Notification status is not known. The device could be below API 19, or another issue.
     */
    public static final int TEAK_NOTIFICATIONS_UNKNOWN = -1;

    /**
     * Has the user disabled notifications for this app.
     *
     * This will always return 'false' for any device below API 19.
     *
     * @return 'true' if the device is above API 19 and the user has disabled notifications, 'false' otherwise.
     */
    @SuppressWarnings("unused")
    public static int getNotificationStatus() {
        if (Instance == null) {
            Teak.log.e("error.getNotificationStatus", "getNotificationStatus() should not be called before onCreate()");
            return TEAK_NOTIFICATIONS_UNKNOWN;
        }
        return Instance.getNotificationStatus();
    }

    /**
     * Open the settings app to the settings for this app.
     *
     * Be sure to prompt the user to re-enable notifications for your app before calling this function.
     *
     * This will always return 'false' for any device below API 19.
     *
     * @return 'true' if Teak was (probably) able to open the settings, 'false' if Teak was (probably) not able to open the settings.
     */
    @SuppressWarnings("unused")
    public static boolean openSettingsAppToThisAppsSettings() {
        if (Instance == null) {
            Teak.log.e("error.openSettingsAppToThisAppsSettings", "openSettingsAppToThisAppsSettings() should not be called before onCreate()");
            return false;
        } else {
            return Instance.openSettingsAppToThisAppsSettings();
        }
    }

    /**
     * Set the badge number on the icon of the application.
     *
     * Set the count to 0 to remove the badge.
     *
     * @return 'true' if Teak was able to set the badge number, 'false' otherwise.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue", "SameParameterValue"})
    public static boolean setApplicationBadgeNumber(int count) {
        if (Instance == null) {
            Teak.log.e("error.setApplicationBadgeNumber", "setApplicationBadgeNumber() should not be called before onCreate()");
            return false;
        } else {
            return Instance.setApplicationBadgeNumber(count);
        }
    }

    /**
     * Track a numeric player profile attribute.
     *
     * @param attributeName  The name of the numeric attribute.
     * @param attributeValue The numeric value to assign.
     */
    @SuppressWarnings("unused")
    public static void setNumericAttribute(final String attributeName, final double attributeValue) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.setNumericAttribute(attributeName, attributeValue);
                }
            });
        }
    }

    /**
     * Track a string player profile attribute.
     *
     * @param attributeName  The name of the string attribute.
     * @param attributeValue The string value to assign.
     */
    @SuppressWarnings("unused")
    public static void setStringAttribute(final String attributeName, final String attributeValue) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.setStringAttribute(attributeName, attributeValue);
                }
            });
        }
    }

    /**
     * Get Teak's configuration data about the current device.
     *
     * @return JSON string containing device info, or null if it's not ready
     */
    @SuppressWarnings("unused")
    public static String getDeviceConfiguration() {
        return getConfiguration("deviceConfiguration", new String[] {
                                                           "deviceId",
                                                           "deviceManufacturer",
                                                           "deviceModel",
                                                           "deviceFallback",
                                                           "platformString",
                                                           "memoryClass",
                                                           "advertisingId",
                                                           "limitAdTracking"});
    }

    /**
     * Get Teak's configuration data about the current app.
     *
     * @return JSON string containing device info, or null if it's not ready
     */
    @SuppressWarnings("unused")
    public static String getAppConfiguration() {
        return getConfiguration("appConfiguration", new String[] {
                                                        "appId",
                                                        "apiKey",
                                                        "appVersion",
                                                        "bundleId",
                                                        "installerPackage",
                                                        "targetSdkVersion"});
    }

    private static String getConfiguration(String subConfiguration, String[] configurationElements) {
        try {
            final TeakConfiguration teakConfiguration = TeakConfiguration.get();

            Map<String, Object> configurationMap = null;
            if ("deviceConfiguration".equalsIgnoreCase(subConfiguration)) {
                configurationMap = teakConfiguration.deviceConfiguration.toMap();
            } else if ("appConfiguration".equalsIgnoreCase(subConfiguration)) {
                configurationMap = teakConfiguration.appConfiguration.toMap();
            }
            if (configurationMap == null) return null;

            JSONObject configurationJSON = new JSONObject();
            for (String element : configurationElements) {
                if (configurationMap.containsKey(element)) {
                    configurationJSON.put(element, configurationMap.get(element));
                }
            }
            return configurationJSON.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Interface for running code when a deep link is received
     */
    public static abstract class DeepLink {
        public abstract void call(Map<String, Object> parameters);
    }

    /**
     * Register a deep link route with Teak.
     *
     * @param route       A Sinatra-style route, eg /path/:capture
     * @param name        The name of the deep link, used in the Teak dashboard
     * @param description The description of the deep link, used in the Teak dashboard
     * @param call        The code to invoke when this deep link is received
     */
    @SuppressWarnings("unused")
    public static void registerDeepLink(@NonNull String route, @NonNull String name, @NonNull String description, @NonNull Teak.DeepLink call) {
        io.teak.sdk.core.DeepLink.internalRegisterRoute(route, name, description, call);
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
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    public static final String REWARD_CLAIM_ATTEMPT = "io.teak.sdk.Teak.intent.REWARD_CLAIM_ATTEMPT";

    ///// BroadcastReceiver

    @Override
    public void onReceive(final Context inContext, final Intent intent) {
        final Context context = inContext.getApplicationContext();

        String action = intent.getAction();
        if (action == null) return;

        // If Instance is null, make sure TeakCore is around. If it's not null, make sure it's enabled.
        if ((Instance == null && TeakCore.getWithoutThrow(context) == null) || (Instance != null && !Instance.isEnabled())) {
            return;
        }

        if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX)) {
            TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Interaction, context, intent));
        } else if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX)) {
            TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Cleared, context, intent));
        }
    }

    ///// Purchase Code

    // Called by Unity integration
    @SuppressWarnings("unused")
    public static void pluginPurchaseSucceeded(final String json, final String pluginName) {
        try {
            final JSONObject originalJson = new JSONObject(json);
            Teak.log.i("purchase." + pluginName, originalJson.toMap());

            if (Instance != null) {
                asyncExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        final Map<String, Object> extras = new HashMap<>();
                        extras.put("iap_plugin", pluginName);
                        Instance.purchaseSucceeded(json, extras);
                    }
                });
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    // Called by Unity integration
    @SuppressWarnings("unused")
    public static void pluginPurchaseFailed(final int errorCode, final String pluginName) {
        if (Instance != null) {
            final Map<String, Object> extras = new HashMap<>();
            extras.put("iap_plugin", pluginName);

            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.purchaseFailed(errorCode, extras);
                }
            });
        }
    }

    // Called by onActivityResult, as well as via reflection/directly in external purchase
    // activity code.
    public static void checkActivityResultForPurchase(final int resultCode, final Intent data) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.checkActivityResultForPurchase(resultCode, data);
                }
            });
        }
    }

    ///// Logging

    public static int jsonLogIndentation = 0;
    public static io.teak.sdk.Log log = new io.teak.sdk.Log(Teak.LOG_TAG, Teak.jsonLogIndentation);

    public static String formatJSONForLogging(JSONObject obj) throws JSONException {
        if (Teak.jsonLogIndentation > 0) {
            return obj.toString(Teak.jsonLogIndentation);
        } else {
            return obj.toString();
        }
    }

    ///// Give SDK wrappers the ability to delay deep link resolution

    public static Future<Void> waitForDeepLink;

    static {
        // TODO: This may not (shouldn't?) get called during unit tests, so make sure something else does it.
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(SessionStateEvent.Type) && ((SessionStateEvent) event).state == Session.State.Created) {

                    // Use thread instead of executor here, since this could block for a while
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (Teak.waitForDeepLink != null) {
                                    Teak.waitForDeepLink.get();
                                }
                            } catch (Exception ignored) {
                            }

                            // TODO: Is there a more general event that should be used here?
                            TeakEvent.postEvent(new DeepLinksReadyEvent());
                        }
                    })
                        .start();
                }
            }
        });
    }

    ///// Configuration

    public static final String PREFERENCES_FILE = "io.teak.sdk.Preferences";

    ///// Data Members

    public static TeakInstance Instance;

    private static ExecutorService asyncExecutor = Executors.newCachedThreadPool();
}
