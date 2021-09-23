package io.teak.sdk;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.StrictMode;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.core.Executors;
import io.teak.sdk.core.InstrumentableReentrantLock;
import io.teak.sdk.core.TeakCore;
import io.teak.sdk.event.DeepLinksReadyEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.io.AndroidResources;
import io.teak.sdk.json.JSONException;
import io.teak.sdk.json.JSONObject;

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
     * Version of the Teak SDK, and Unity/Cocos2dx SDK if applicable.
     * <p>
     * You must call {@link Teak#onCreate(Activity)} in order to get Unity/Cocos2dx SDK version info.
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
     * <br>
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
        // Provide a way to wait for debugger to connect via a data param
        final Intent intent = activity.getIntent();

        if (intent != null && intent.getData() != null) {
            final Uri intentData = intent.getData();

            if (intentData.getBooleanQueryParameter("teak_log", false)) {
                Teak.forceDebug = true;
            }

            if (intentData.getBooleanQueryParameter("teak_debug", false) &&
                (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                Debug.waitForDebugger();
            }

            if (intentData.getBooleanQueryParameter("teak_mutex_report", false)) {
                InstrumentableReentrantLock.interruptLongLocksAndReport = true;
            }

            if (intentData.getBooleanQueryParameter("teak_strict_mode", false)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                               .detectNonSdkApiUsage()
                                               .penaltyLog()
                                               .build());
                }
            }
        }

        // Init integration checks, we decide later if they report or not
        if (!IntegrationChecker.init(activity)) {
            throw new RuntimeException("Teak integration check failed. Please see the log for details.");
        }

        // Unless something gave us an object factory, use the default one
        if (objectFactory == null) {
            try {
                objectFactory = new DefaultObjectFactory(activity.getApplicationContext());
            } catch (Exception e) {
                return;
            }
        }

        // Add version info for Unity
        try {
            Class<?> clazz = Class.forName("io.teak.sdk.wrapper.Version");
            Method m = clazz.getDeclaredMethod("map");
            @SuppressWarnings("unchecked")
            final Map<String, Object> wrapperVersion = (Map<String, Object>) m.invoke(null);
            if (wrapperVersion != null) {
                Teak.sdkMap.putAll(wrapperVersion);
            }
        } catch (Exception ignored) {
        }

        final AndroidResources androidResources = new AndroidResources(activity.getApplicationContext(), objectFactory.getAndroidResources());
        if (androidResources != null) {
            // Check for 'trace' log mode
            final Boolean traceLog = androidResources.getTeakBoolResource(AppConfiguration.TEAK_TRACE_LOG_RESOURCE, false);
            if (traceLog != null) {
                Teak.log.setLogTrace(traceLog);
            }
        }

        // Create Instance last
        if (Instance == null) {
            try {
                Instance = new TeakInstance(activity, objectFactory);
            } catch (Exception e) {
                android.util.Log.e(LOG_TAG, android.util.Log.getStackTraceString(e));
            }
        }
    }

    /**
     * Tell Teak how it should identify the current user.
     * <br>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static void identifyUser(final String userIdentifier) {
        Teak.identifyUser(userIdentifier, new String[0], null);
    }

    /**
     * Tell Teak how it should identify the current user.
     * <br>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     * @param email          The email address for the user.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static void identifyUser(final String userIdentifier, final String email) {
        Teak.identifyUser(userIdentifier, new String[0], email);
    }

    /**
     * Value provided to {@link #identifyUser(String, String[])} to opt out of
     * collecting an IDFA for this specific user.
     * <br>
     * If you prevent Teak from collecting the Identifier For Advertisers (IDFA), Teak will no longer be able to add this user to Facebook Ad Audiences.
     */
    @SuppressWarnings("unused")
    public static final String OPT_OUT_IDFA = "opt_out_idfa";

    /**
     * Value provided to {@link #identifyUser(String, String[])} to opt out of
     * collecting a Facebook Access Token for this specific user.
     * <br>
     * If you prevent Teak from collecting the Facebook Access Token, Teak will no longer be able to correlate this user across multiple devices.
     */
    @SuppressWarnings("unused")
    public static final String OPT_OUT_FACEBOOK = "opt_out_facebook";

    /**
     * Value provided to {@link #identifyUser(String, String[])} to opt out of
     * collecting a Push Key for this specific user.
     * <br>
     * If you prevent Teak from collecting the Push Key, Teak will no longer be able to send Local Notifications or Push Notifications for this user.
     */
    @SuppressWarnings("unused")
    public static final String OPT_OUT_PUSH_KEY = "opt_out_push_key";

    /**
     * Tell Teak how it should identify the current user, with data collection opt-out.
     * <br>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     * @param optOut         A list containing zero or more of:
     *                          {@link #OPT_OUT_IDFA}, {@link #OPT_OUT_FACEBOOK}, {@link #OPT_OUT_PUSH_KEY}
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static void identifyUser(final String userIdentifier, final String[] optOut) {
        identifyUser(userIdentifier, optOut, null);
    }

    /**
     * Tell Teak how it should identify the current user, with data collection opt-out and email.
     * <br>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     * @param optOut         A list containing zero or more of:
     *                          {@link #OPT_OUT_IDFA}, {@link #OPT_OUT_FACEBOOK}, {@link #OPT_OUT_PUSH_KEY}
     * @param email          The email address for the user.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static void identifyUser(final String userIdentifier, final String[] optOut, final String email) {
        final Set<String> optOutSet = optOut == null ? new HashSet<>() : new HashSet<>(Arrays.asList(optOut));
        final UserConfiguration userConfiguration = new UserConfiguration(email, null,
            optOutSet.contains(OPT_OUT_FACEBOOK),
            optOutSet.contains(OPT_OUT_IDFA),
            optOutSet.contains(OPT_OUT_PUSH_KEY));

        identifyUser(userIdentifier, userConfiguration);
    }

    public static class UserConfiguration implements Unobfuscable {
        public final String email;
        public final String facebookId;

        /**
         * Opt out of collecting a Facebook Access Token for this specific user.
         * <br>
         * If you prevent Teak from collecting the Facebook Access Token, Teak will no longer be able to correlate this user across multiple devices.
         */
        @Deprecated
        public final boolean optOutFacebook;

        /**
         * Opt out of collecting an IDFA for this specific user.
         * <br>
         * If you prevent Teak from collecting the Identifier For Advertisers (IDFA), Teak will no longer be able to add this user to Facebook Ad Audiences.
         */
        public final boolean optOutIDFA;

        /**
         * Opt out of collecting a Push Key for this specific user.
         * <br>
         * If you prevent Teak from collecting the Push Key, Teak will no longer be able to send Local Notifications or Push Notifications for this user.
         */
        public final boolean optOutPushKey;

        public UserConfiguration() {
            this(null, null, false, false, false);
        }

        public UserConfiguration(final String email) {
            this(email, null, false, false, false);
        }

        public UserConfiguration(final String email, final String facebookId) {
            this(email, facebookId, false, false, false);
        }

        public UserConfiguration(final String email, final String facebookId,
            final boolean optOutFacebook, final boolean optOutIDFA,
            final boolean optOutPushKey) {
            this.email = email;
            this.facebookId = facebookId;
            this.optOutFacebook = optOutFacebook;
            this.optOutIDFA = optOutIDFA;
            this.optOutPushKey = optOutPushKey;
        }

        public Map<String, Object> toHash() {
            final Map<String, Object> map = new HashMap<>();
            map.put("email", this.email);
            map.put("facebook_id", this.facebookId);
            map.put("opt_out_facebook", this.optOutFacebook);
            map.put("opt_out_idfa", this.optOutIDFA);
            map.put("opt_out_push_key", this.optOutPushKey);
            return map;
        }
    }

    /**
     * Tell Teak how it should identify the current user, with additional options and configuration.
     * <br>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     * @param userConfiguration A set of configuration keys and value, @see UserConfiguration
     */
    @SuppressWarnings("unused")
    public static void identifyUser(final String userIdentifier, final UserConfiguration userConfiguration) {
        Teak.log.trace("Teak.identifyUser", userIdentifier, userConfiguration);

        // Always process deep links when identifyUser is called
        Teak.processDeepLinks();

        if (Instance != null) {
            asyncExecutor.submit(() -> Instance.identifyUser(userIdentifier, userConfiguration));
        }
    }

    /**
     * Logout the current user.
     */
    @SuppressWarnings("unused")
    public static void logout() {
        Teak.log.trace("Teak.logout");

        if (Instance != null) {
            asyncExecutor.submit(() -> Instance.logout());
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
        Teak.log.trace("Teak.trackEvent", "actionId", actionId, "objectTypeId", objectTypeId, "objectInstanceId", objectInstanceId);

        if (Instance != null) {
            asyncExecutor.submit(() -> Instance.trackEvent(actionId, objectTypeId, objectInstanceId));
        }
    }

    /**
     * Increment the value an arbitrary event in Teak.
     *
     * @param actionId         The identifier for the action, e.g. 'complete'.
     * @param objectTypeId     The type of object that is being posted, e.g. 'quest'.
     * @param objectInstanceId The specific instance of the object, e.g. 'gather-quest-1'
     * @param count            The amount by which to increment.
     */
    @SuppressWarnings("unused")
    public static void incrementEvent(final String actionId, final String objectTypeId, final String objectInstanceId, final long count) {
        Teak.log.trace("Teak.incrementEvent", "actionId", actionId, "objectTypeId", objectTypeId, "objectInstanceId", objectInstanceId);

        if (Instance != null) {
            asyncExecutor.submit(() -> Instance.trackEvent(actionId, objectTypeId, objectInstanceId, count));
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
        Teak.log.trace("Teak.getNotificationStatus");

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
        Teak.log.trace("Teak.openSettingsAppToThisAppsSettings");

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
        Teak.log.trace("Teak.setApplicationBadgeNumber", "count", count);

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
        Teak.log.trace("Teak.setNumericAttribute", "attributeName", attributeName, "attributeValue", attributeValue);

        if (Instance != null) {
            asyncExecutor.submit(() -> Instance.setNumericAttribute(attributeName, attributeValue));
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
        Teak.log.trace("Teak.setStringAttribute", "attributeName", attributeName, "attributeValue", attributeValue);

        if (Instance != null) {
            asyncExecutor.submit(() -> Instance.setStringAttribute(attributeName, attributeValue));
        }
    }

    /**
     * Get Teak's configuration data about the current device.
     *
     * @return JSON string containing device info, or null if it's not ready
     */
    @SuppressWarnings("unused")
    public static String getDeviceConfiguration() {
        Teak.log.trace("Teak.getDeviceConfiguration");

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
        Teak.log.trace("Teak.getAppConfiguration");

        return getConfiguration("appConfiguration", new String[] {
                                                        "appId",
                                                        "apiKey",
                                                        "appVersion",
                                                        "bundleId",
                                                        "installerPackage",
                                                        "targetSdkVersion",
                                                        "traceLog"});
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
    public static abstract class DeepLink implements Unobfuscable {
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
        Teak.log.trace("Teak.registerDeepLink", "route", route, "name", name, "description", description, "call", call.toString());
        io.teak.sdk.core.DeepLink.internalRegisterRoute(route, name, description, call);
    }

    /**
     * Base class for providing data about the launch of the app.
     */
    public static class LaunchData implements Unobfuscable {
        /**
         * If this launch is not attributed to anything, this constant is used instead of
         * a null LaunchData.
         */
        public static final LaunchData Unattributed = new LaunchData();

        /**
         * The link associated with this launch; or null.
         */
        public final Uri launchLink;

        /**
         * Constructor with {@link String}.
         * @param launchLink Link as a String.
         */
        protected LaunchData(@Nullable String launchLink) {
            this(launchLink != null ? Uri.parse(launchLink) : null);
        }

        /**
         * Constructor with {@link Uri}.
         * @param launchLink Link as a Uri.
         */
        public LaunchData(@Nullable Uri launchLink) {
            this.launchLink = launchLink;
        }

        /**
         * For {@link LaunchData#Unattributed}
         */
        private LaunchData() {
            this.launchLink = null;
        }

        /**
         * Used by {@link io.teak.sdk.core.LaunchDataSource#sourceWithUpdatedDeepLink}
         * @param uri Updated deep link
         * @return A merged LaunchData
         */
        public LaunchData mergeDeepLink(@NonNull final Uri uri) {
            return new LaunchData(uri);
        }

        /**
         * For internal use.
         *
         * @return Map used by Teak internally.
         */
        public Map<String, Object> toSessionAttributionMap() {
            final HashMap<String, Object> map = new HashMap<>();

            if (this.launchLink != null) {
                map.put("launch_link", this.launchLink.toString());
            }
            return map;
        }

        /**
         * Convert to a Map, intended to be converted to JSON and
         * consumed by the Teak Unity SDK.
         *
         * @return A Map representation of this object.
         */
        public Map<String, Object> toMap() {
            final HashMap<String, Object> map = new HashMap<>();
            map.put("launch_link", this.launchLink != null ? this.launchLink.toString() : null);
            return map;
        }
    }

    /**
     * Base class for describing a Teak-attributed launch of the app.
     */
    public static class AttributedLaunchData extends LaunchData implements Unobfuscable {
        /**
         * The name of the schedule responsible on the Teak dashboard; or null if this was not a scheduled channel.
         */
        public final String scheduleName;

        /**
         * The id of the schedule responsible on the Teak dashboard; or null if this was not a scheduled channel.
         */
        public final String scheduleId;

        /**
         * The name of the creative on the Teak dashboard.
         */
        public final String creativeName;

        /**
         * The id of the creative on the Teak dashboard.
         */
        public final String creativeId;

        /**
         * The id of the Teak reward associated with this launch; or null.
         */
        public final String rewardId;

        /**
         * The name of the channel responsible for this attribution.
         *
         * One of:
         *  * generic_link
         *  * android_push
         *  * email
         */
        public final String channelName;

        /**
         * Used by {@link NotificationLaunchData}
         * @param bundle Push notification contents.
         */
        protected AttributedLaunchData(@NonNull final Bundle bundle) {
            super(bundle.getString("teakDeepLink"));

            if (!(this instanceof NotificationLaunchData)) {
                throw new RuntimeException("AttributedLaunchData(Bundle) constructor used to construct something other than NotificationLaunchData");
            }

            this.scheduleName = bundle.getString("teakScheduleName");
            this.scheduleId = bundle.getString("teakScheduleId");
            this.creativeName = bundle.getString("teakCreativeName");
            this.creativeId = bundle.getString("teakCreativeId");
            this.rewardId = bundle.getString("teakRewardId");
            this.channelName = bundle.getString("teakChannelName");
        }

        /**
         * Used by both {@link NotificationLaunchData} and {@link RewardlinkLaunchData}
         * @param uri Uri of the email link or generic link used to launch the app.
         */
        protected AttributedLaunchData(@NonNull final Uri uri) {
            super(uri);

            this.scheduleName = uri.getQueryParameter("teak_schedule_name");
            this.scheduleId = uri.getQueryParameter("teak_schedule_id");

            // In the non-mobile world, there is no such thing as "not a link launch" and so the
            // parameter names are different to properly differentiate session source
            final String urlCreativeName = uri.getQueryParameter("teak_creative_name");
            this.creativeName = urlCreativeName != null ? urlCreativeName : uri.getQueryParameter("teak_rewardlink_name");
            final String urlCreativeId = uri.getQueryParameter("teak_creative_id");
            this.creativeId = urlCreativeId != null ? urlCreativeId : uri.getQueryParameter("teak_rewardlink_id");

            this.rewardId = uri.getQueryParameter("teak_reward_id");
            this.channelName = uri.getQueryParameter("teak_channel_name");
        }

        /**
         * For use in {@link AttributedLaunchData#mergeDeepLink(Uri)}
         * @param oldLaunchData The old attribution
         * @param updatedDeepLink The deep link in the reply from the identify user request.
         */
        protected AttributedLaunchData(@NonNull final AttributedLaunchData oldLaunchData, @NonNull Uri updatedDeepLink) {
            super(updatedDeepLink);

            final AttributedLaunchData newLaunchData = new AttributedLaunchData(updatedDeepLink);
            this.scheduleName = Helpers.newIfNotOld(oldLaunchData.scheduleName, newLaunchData.scheduleName);
            this.scheduleId = Helpers.newIfNotOld(oldLaunchData.scheduleId, newLaunchData.scheduleId);
            this.creativeName = Helpers.newIfNotOld(oldLaunchData.creativeName, newLaunchData.creativeName);
            this.creativeId = Helpers.newIfNotOld(oldLaunchData.creativeId, newLaunchData.creativeId);
            this.rewardId = Helpers.newIfNotOld(oldLaunchData.rewardId, newLaunchData.rewardId);
            this.channelName = Helpers.newIfNotOld(oldLaunchData.channelName, newLaunchData.channelName);
        }

        @Override
        public LaunchData mergeDeepLink(@NonNull Uri uri) {
            return new AttributedLaunchData(this, uri);
        }

        /**
         * @return True if this notification had a reward attached to it.
         */
        public boolean isIncentivized() {
            return this.rewardId != null;
        }

        @Override
        public Map<String, Object> toSessionAttributionMap() {
            final Map<String, Object> map = super.toSessionAttributionMap();

            // Put the URI and any query parameters that start with 'teak_' into 'deep_link'
            if (this.launchLink != null) {
                map.put("deep_link", this.launchLink.toString());
                for (final String name : this.launchLink.getQueryParameterNames()) {
                    if (name.startsWith("teak_")) {
                        final List<String> values = this.launchLink.getQueryParameters(name);
                        if (values.size() > 1) {
                            map.put(name, values);
                        } else {
                            map.put(name, values.get(0));
                        }
                    }
                }
            }

            return map;
        }

        /**
         * Convert to a Map, intended to be converted to JSON and
         * consumed by the Teak Unity SDK.
         *
         * @return A Map representation of this object.
         */
        @Override
        public Map<String, Object> toMap() {
            final Map<String, Object> map = super.toMap();
            map.put("teakScheduleName", this.scheduleName);
            map.put("teakScheduleId", this.scheduleId);
            map.put("teakCreativeName", this.creativeName);
            map.put("teakCreativeId", this.creativeId);
            map.put("teakRewardId", this.rewardId);
            map.put("teakChannelName", this.channelName);
            map.put("teakDeepLink", io.teak.sdk.core.DeepLink.willProcessUri(this.launchLink) ? this.launchLink.toString() : null);
            return map;
        }
    }

    /**
     * Launch data for a Teak push notification or email.
     */
    public static class NotificationLaunchData extends AttributedLaunchData implements Unobfuscable {
        /**
         * The send-id of the notification.
         */
        public final String sourceSendId;

        /**
         * Construct NotificationLaunchData for a push notification.
         * @param bundle Push notification contents.
         */
        public NotificationLaunchData(@NonNull final Bundle bundle) {
            super(bundle);
            this.sourceSendId = bundle.getString("teakNotifId");
        }

        /**
         * Construct NotificationLaunchData for an email link.
         * @param uri Email link.
         */
        public NotificationLaunchData(@NonNull final Uri uri) {
            super(uri);
            this.sourceSendId = uri.getQueryParameter("teak_notif_id");
        }

        /**
         * For use in {@link NotificationLaunchData#mergeDeepLink(Uri)}
         * @param oldLaunchData The old attribution
         * @param updatedDeepLink The deep link in the reply from the identify user request.
         */
        protected NotificationLaunchData(@NonNull final NotificationLaunchData oldLaunchData, @NonNull Uri updatedDeepLink) {
            super(oldLaunchData, updatedDeepLink);
            this.sourceSendId = Helpers.newIfNotOld(oldLaunchData.sourceSendId, updatedDeepLink.getQueryParameter("teak_notif_id"));
        }

        @Override
        public LaunchData mergeDeepLink(@NonNull Uri uri) {
            return new NotificationLaunchData(this, uri);
        }

        /**
         * For internal use.
         * @param uri The Uri to test.
         * @return true if the Uri is from a Teak email, in which case it should use NotificationLaunchData.
         */
        public static boolean isTeakEmailUri(@NonNull final Uri uri) {
            return !Helpers.isNullOrEmpty(uri.getQueryParameter("teak_notif_id"));
        }

        @Override
        public Map<String, Object> toMap() {
            final Map<String, Object> map = super.toMap();
            map.put("teakNotifId", this.sourceSendId);
            return map;
        }

        @Override
        public Map<String, Object> toSessionAttributionMap() {
            final Map<String, Object> map = super.toSessionAttributionMap();
            if (this.sourceSendId != null) {
                map.put("teak_notif_id", this.sourceSendId);
            }
            return map;
        }
    }

    /**
     * Launch data for a Teak reward link.
     */
    public static class RewardlinkLaunchData extends AttributedLaunchData implements Unobfuscable {
        /**
         * The Teak short link that resolved to this attribution; or null.
         */
        public final Uri shortLink;

        public RewardlinkLaunchData(@NonNull final Uri uri, @Nullable final Uri shortLink) {
            super(uri);
            this.shortLink = shortLink;
        }

        /**
         * For internal use.
         * @param uri The Uri to test.
         * @return true if the Uri is a Teak reward link.
         */
        public static boolean isTeakRewardLink(@NonNull final Uri uri) {
            return !Helpers.isNullOrEmpty(uri.getQueryParameter("teak_rewardlink_id"));
        }

        /**
         * For use in {@link RewardlinkLaunchData#mergeDeepLink(Uri)}
         * @param oldLaunchData The old attribution
         * @param updatedDeepLink The deep link in the reply from the identify user request.
         */
        protected RewardlinkLaunchData(@NonNull final RewardlinkLaunchData oldLaunchData, @NonNull Uri updatedDeepLink) {
            super(oldLaunchData, updatedDeepLink);
            this.shortLink = oldLaunchData.shortLink;
        }

        @Override
        public LaunchData mergeDeepLink(@NonNull Uri uri) {
            return new RewardlinkLaunchData(this, uri);
        }
    }

    public static class Event implements Unobfuscable {
        /**
         * Data associated with this launch.
         */
        public final LaunchData launchData;

        /**
         * The {@link TeakNotification.Reward} attached, or null.
         */
        public final TeakNotification.Reward reward;

        /**
         * Event base class.
         * @param launchData Attribution data for launch.
         * @param reward Reward, if available.
         */
        public Event(@NonNull final LaunchData launchData, @Nullable final TeakNotification.Reward reward) {
            this.launchData = launchData;
            this.reward = reward;
        }

        /**
         * Used internally for JSON serialization.
         *
         * @return Teak wrapper SDK consumable JSON.
         */
        public JSONObject toJSON() {
            final Map<String, Object> map = this.launchData.toMap();
            if (this.reward != null && this.reward.json != null) {
                map.putAll(this.reward.json.toMap());
            }
            return new JSONObject(map);
        }
    }

    /**
     * Event posted when a foreground notification was received, or the game was launched from a
     * Teak email, or Teak push notification.
     */
    public static class NotificationEvent extends Event implements Unobfuscable {
        /**
         * True if this push notification was received when the app was in the foreground.
         */
        public final boolean isForeground;

        public NotificationEvent(@NonNull final NotificationLaunchData launchData, final boolean isForeground) {
            super(launchData, null);
            this.isForeground = isForeground;
        }

        @Override
        public JSONObject toJSON() {
            final JSONObject json = super.toJSON();
            json.put("isForeground", this.isForeground);
            return json;
        }
    }

    /**
     * Event posted when the app was launched from a link created by the Teak dashboard.
     */
    public static class LaunchFromLinkEvent extends Event implements Unobfuscable {
        /**
         * Constructor
         * @param launchData Launch attribution data.
         */
        public LaunchFromLinkEvent(@NonNull final RewardlinkLaunchData launchData) {
            super(launchData, null);
        }
    }

    /**
     * Event posted whenever the app launches.
     */
    public static class PostLaunchSummaryEvent extends Event implements Unobfuscable {
        public PostLaunchSummaryEvent(@NonNull final LaunchData launchData) {
            super(launchData, null);
        }
    }

    /**
     * Event posted when a reward claim attempt has occurred.
     */
    public static class RewardClaimEvent extends Event implements Unobfuscable {
        /**
         * Constructor
         * @param launchData Launch attribution data.
         * @param reward Reward.
         */
        public RewardClaimEvent(@NonNull final AttributedLaunchData launchData, @NonNull final TeakNotification.Reward reward) {
            super(launchData, reward);
        }
    }

    /**
     * Event sent when "additional data" is available for the user.
     */
    public static class AdditionalDataEvent implements Unobfuscable {
        /**
         * A JSON object containing user-defined data received from the server.
         */
        public final JSONObject additionalData;

        public AdditionalDataEvent(final JSONObject additionalData) {
            this.additionalData = additionalData;
        }
    }

    ///// LogListener

    /**
     *
     */
    public static abstract class LogListener {
        public abstract void logEvent(String logEvent, String logLevel, Map<String, Object> logData);
    }

    /**
     * Listen for Teak SDK log events.
     * <br>
     * @param logListener A {@link LogListener} that will be called each time Teak would log an internal SDK event.
     */
    @SuppressWarnings("unused")
    public static void setLogListener(LogListener logListener) {
        Teak.log.setLogListener(logListener);
    }

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

    ///// Deep Links

    /**
     * Indicate that your app is ready for deep links.
     * <br>
     * Deep links will not be processed sooner than the earliest of:
     * - {@link #identifyUser(String, String[])} is called
     * - This method is called
     */
    @SuppressWarnings("unused")
    public static void processDeepLinks() {
        try {
            synchronized (Teak.waitForDeepLink) {
                if (!Teak.waitForDeepLink.isDone()) {
                    Teak.asyncExecutor.execute(Teak.waitForDeepLink);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static final FutureTask<Void> waitForDeepLink = new FutureTask<>(() -> TeakEvent.postEvent(new DeepLinksReadyEvent()), null);

    /**
     * Block until deep links are ready for processing.
     * <br>
     * For internal use.
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static void waitUntilDeepLinksAreReady() throws ExecutionException, InterruptedException {
        Teak.waitForDeepLink.get();
    }

    ///// Configuration

    public static final String PREFERENCES_FILE = "io.teak.sdk.Preferences";

    ///// Data Members

    public static TeakInstance Instance;

    private static final ExecutorService asyncExecutor = Executors.newCachedThreadPool();
}
