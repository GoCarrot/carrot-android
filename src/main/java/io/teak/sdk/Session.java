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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

class Session {
    private static final String LOG_TAG = "Teak:Session";
    private static final long SAME_SESSION_TIME_DELTA = 120000;

    // region State machine
    public enum State {
        Invalid("Invalid"),
        Allocated("Allocated"),
        Created("Created"),
        Configured("Configured"),
        IdentifyingUser("IdentifyingUser"),
        UserIdentified("UserIdentified"),
        Expiring("Expiring"),
        Expired("Expired");

        //public static final Integer length = 1 + Expired.ordinal();

        private static final State[][] allowedTransitions = {
                {},
                {State.Created, State.Expiring},
                {State.Configured, State.Expiring},
                {State.IdentifyingUser, State.Expiring},
                {State.UserIdentified, State.Expiring},
                {State.Expiring},
                {State.Created, State.Configured, State.IdentifyingUser, State.UserIdentified, State.Expired},
                {}
        };

        public final String name;

        State(String name) {
            this.name = name;
        }

        public boolean canTransitionTo(State nextState) {
            if (nextState == State.Invalid) return true;

            for (State allowedTransition : allowedTransitions[this.ordinal()]) {
                if (nextState == allowedTransition) return true;
            }
            return false;
        }
    }

    private State state = State.Allocated;
    private State previousState = null;
    private final Object stateMutex = new Object();
    // endregion

    // region Event Listener
    public interface EventListener {
        void onStateChange(Session session, State oldState, State newState);
    }

    private static final Object eventListenersMutex = new Object();
    private static ArrayList<EventListener> eventListeners = new ArrayList<>();

    public static void addEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            if (!eventListeners.contains(e)) {
                eventListeners.add(e);
            }
        }
    }

    public static void removeEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            eventListeners.remove(e);
        }
    }
    // endregion

    // State: Created
    public final Date startDate;
    public final AppConfiguration appConfiguration;
    public final DeviceConfiguration deviceConfiguration;

    // State: Configured
    public RemoteConfiguration remoteConfiguration;

    // State: UserIdentified
    private String userId;

    private ScheduledExecutorService heartbeatService;
    private String countryCode;
    private String facebookAccessToken;

    // State: Expiring
    private Date endDate;

    // State Independent
    private Map<String, Object> launchAttribution = new HashMap<>();
    private ArrayList<Map<String, Object>> attributionChain = new ArrayList<>();

    // For cases where setUserId() is called before a Session has been created
    private static String pendingUserId;

    private Session(AppConfiguration appConfiguration, DeviceConfiguration deviceConfiguration) {
        // State: Created
        // Valid data:
        // - startDate
        // - appConfiguration
        // - deviceConfiguration
        this.startDate = new Date();
        this.appConfiguration = appConfiguration;
        this.deviceConfiguration = deviceConfiguration;

        DeviceConfiguration.addEventListener(this.deviceConfigurationListener);

        IntentFilter filter = new IntentFilter();
        filter.addAction(FacebookAccessTokenBroadcast.UPDATED_ACCESS_TOKEN_INTENT_ACTION);
        Teak.localBroadcastManager.registerReceiver(this.facebookBroadcastReceiver, filter);

        setState(State.Created);
    }

    private Session(@NonNull Session session) {
        this(session.appConfiguration, session.deviceConfiguration);

        this.userId = session.userId;
        this.attributionChain.addAll(session.attributionChain);
    }

    public boolean hasExpired() {
        synchronized (stateMutex) {
            if (this.state == State.Expiring &&
                    (new Date().getTime() - this.endDate.getTime() > SAME_SESSION_TIME_DELTA)) {
                setState(State.Expired);
            }
            return (this.state == State.Expired);
        }
    }

    public static boolean isExpiringOrExpired() {
        synchronized (currentSessionMutex) {
            return currentSession == null || currentSession.hasExpired() || currentSession.state == State.Expiring;
        }
    }

    public static void setUserId(@NonNull String userId) {
        if (userId.isEmpty()) {
            Log.e(LOG_TAG, "User Id can not be null or empty.");
            return;
        }

        // If the user id has changed, create a new session
        synchronized (currentSessionMutex) {
            if (currentSession == null) {
                Session.pendingUserId = userId;
            } else {
                synchronized (currentSession.stateMutex) {
                    if (currentSession.userId != null && !currentSession.userId.equals(userId)) {
                        Session newSession = new Session(currentSession);

                        currentSession.setState(State.Expiring);
                        currentSession.setState(State.Expired);

                        currentSession = newSession;
                    }

                    currentSession.userId = userId;

                    if (currentSession.state == State.Configured) {
                        currentSession.identifyUser();
                    }
                }
            }
        }
    }

    private boolean setState(@NonNull State newState) {
        synchronized (stateMutex) {
            if (this.state == newState) {
                Log.i(LOG_TAG, String.format("Session State transition to same state (%s). Ignoring.", this.state));
                return false;
            }

            if (!this.state.canTransitionTo(newState)) {
                Log.e(LOG_TAG, String.format("Invalid Session State transition (%s -> %s). Ignoring.", this.state, newState));
                return false;
            }

            ArrayList<Object[]> invalidValuesForTransition = new ArrayList<>();

            // Check the data that should be valid before transitioning to the next state. Perform any
            // logic that should occur on transition.
            switch (newState) {
                case Created: {
                    if (this.startDate == null) {
                        invalidValuesForTransition.add(new Object[]{"startDate", "null"});
                        break;
                    }

                    if (this.appConfiguration == null) {
                        invalidValuesForTransition.add(new Object[]{"appConfiguration", "null"});
                        break;
                    }

                    if (this.deviceConfiguration == null) {
                        invalidValuesForTransition.add(new Object[]{"deviceConfiguration", "null"});
                        break;
                    }

                    RemoteConfiguration.addEventListener(remoteConfigurationListener);
                }
                break;

                case Configured: {
                    if (this.remoteConfiguration == null) {
                        invalidValuesForTransition.add(new Object[]{"remoteConfiguration", "null"});
                        break;
                    }

                    RemoteConfiguration.removeEventListener(remoteConfigurationListener);

                    final Session _this = this;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (_this.userId != null) {
                                _this.identifyUser();
                            }
                        }
                    }).start();
                }
                break;

                case IdentifyingUser: {
                    if (this.userId == null) {
                        invalidValuesForTransition.add(new Object[]{"userId", "null"});
                        break;
                    }
                }
                break;

                case UserIdentified: {

                    // Start heartbeat, heartbeat service should be null right now
                    if (this.heartbeatService != null) {
                        invalidValuesForTransition.add(new Object[]{"heartbeatService", this.heartbeatService});
                        break;
                    }

                    startHeartbeat();

                    synchronized (userIdReadyRunnableQueueMutex) {
                        for (WhenUserIdIsReadyRun runnable : userIdReadyRunnableQueue) {
                            new Thread(runnable).start();
                        }
                        userIdReadyRunnableQueue.clear();
                    }
                }
                break;

                case Expiring: {
                    this.endDate = new Date();

                    // TODO: When expiring, punt to background service and say "Hey check the state of this session in N seconds"

                    // Stop heartbeat, Expiring->Expiring is possible, so no invalid data here
                    if (this.heartbeatService != null) {
                        this.heartbeatService.shutdown();
                        this.heartbeatService = null;
                    }
                }
                break;

                case Expired: {
                    DeviceConfiguration.removeEventListener(this.deviceConfigurationListener);
                    if (Teak.localBroadcastManager != null) {
                        Teak.localBroadcastManager.unregisterReceiver(this.facebookBroadcastReceiver);
                    }

                    // TODO: Report Session to server, once we collect that info.
                }
                break;
            }

            // Print out any invalid values
            if (invalidValuesForTransition.size() > 0) {
                Log.e(LOG_TAG, String.format("Invalid Session value%s while trying to transition from %s -> %s. Invalidating Session.",
                        invalidValuesForTransition.size() > 1 ? "s" : "", this.state, newState));
                for (Object[] invalidValue : invalidValuesForTransition) {
                    Log.e(LOG_TAG, String.format(Locale.US, "\t%s: %s", invalidValue[0], invalidValue[1]));
                }

                // Invalidate this session
                this.setState(State.Invalid);
                return false;
            }

            this.previousState = this.state;
            this.state = newState;

            if (Teak.isDebug) {
                Log.d(LOG_TAG, String.format("Session State transition from %s -> %s.", this.previousState, this.state));
            }

            synchronized (eventListenersMutex) {
                for (EventListener e : eventListeners) {
                    e.onStateChange(this, this.previousState, this.state);
                }
            }
            return true;
        }
    }

    private void startHeartbeat() {
        final Session _this = this;

        this.heartbeatService = Executors.newSingleThreadScheduledExecutor();
        this.heartbeatService.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (Teak.isDebug) {
                    Log.v(LOG_TAG, "Sending heartbeat for user: " + userId);
                }

                HttpsURLConnection connection = null;
                try {
                    String queryString = "game_id=" + URLEncoder.encode(_this.appConfiguration.appId, "UTF-8") +
                            "&api_key=" + URLEncoder.encode(_this.userId, "UTF-8") +
                            "&sdk_version=" + URLEncoder.encode(Teak.SDKVersion, "UTF-8") +
                            "&sdk_platform=" + URLEncoder.encode(_this.deviceConfiguration.platformString, "UTF-8") +
                            "&app_version=" + URLEncoder.encode(String.valueOf(_this.appConfiguration.appVersion), "UTF-8") +
                            (_this.countryCode == null ? "" : "&country_code=" + URLEncoder.encode(String.valueOf(_this.countryCode), "UTF-8")) +
                            "&buster=" + URLEncoder.encode(UUID.randomUUID().toString(), "UTF-8");
                    URL url = new URL("https://iroko.gocarrot.com/ping?" + queryString);
                    connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestProperty("Accept-Charset", "UTF-8");
                    connection.setUseCaches(false);

                    int responseCode = connection.getResponseCode();
                    if (Teak.isDebug) {
                        Log.v(LOG_TAG, "Heartbeat response code: " + responseCode);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }, 0, 1, TimeUnit.MINUTES); // TODO: If RemoteConfiguration specifies a different rate, use that
    }

    private void identifyUser() {
        final Session _this = this;

        new Thread(new Runnable() {
            public void run() {
                synchronized (_this.stateMutex) {
                    if (_this.state != State.UserIdentified && !_this.setState(State.IdentifyingUser)) {
                        return;
                    }

                    HashMap<String, Object> payload = new HashMap<>();

                    if (_this.state == State.UserIdentified) {
                        payload.put("do_not_track_event", Boolean.TRUE);
                    }

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

                    if (_this.deviceConfiguration.advertisingId != null) {
                        payload.put("android_ad_id", _this.deviceConfiguration.advertisingId);
                        payload.put("android_limit_ad_tracking", _this.deviceConfiguration.limitAdTracking);
                    }

                    if (_this.facebookAccessToken != null) {
                        payload.put("access_token", _this.facebookAccessToken);
                    }

                    for (Map.Entry<String, Object> entry : _this.launchAttribution.entrySet()) {
                        payload.put(entry.getKey(), entry.getValue());
                    }

                    if (_this.deviceConfiguration.gcmId != null) {
                        payload.put("gcm_push_key", _this.deviceConfiguration.gcmId);
                    }

                    if (_this.deviceConfiguration.admId != null) {
                        payload.put("adm_push_key", _this.deviceConfiguration.admId);
                    }

                    Log.d(LOG_TAG, "Identifying user: " + _this.userId);
                    Log.d(LOG_TAG, "        Timezone: " + tzOffset);
                    Log.d(LOG_TAG, "          Locale: " + locale);

                    new Request("/games/" + _this.appConfiguration.appId + "/users.json", payload, _this) {
                        @Override
                        protected void done(int responseCode, String responseBody) {
                            try {
                                JSONObject response = new JSONObject(responseBody);

                                // TODO: Grab 'id' and 'game_id' from response and store for Parsnip

                                // Enable verbose logging if flagged
                                boolean enableVerboseLogging = response.optBoolean("verbose_logging");
                                if (Teak.debugConfiguration != null) {
                                    Teak.debugConfiguration.setPreferenceForceDebug(enableVerboseLogging);
                                }

                                // Server requesting new push key.
                                if (response.optBoolean("reset_push_key", false)) {
                                    _this.deviceConfiguration.reRegisterPushToken(_this.appConfiguration);
                                }

                                if (response.has("country_code")) {
                                    _this.countryCode = response.getString("country_code");
                                }

                                // Prevent warning for 'do_not_track_event'
                                if (_this.state == State.Expiring) {
                                    _this.previousState = State.UserIdentified;
                                } else if (_this.state != State.UserIdentified) {
                                    _this.setState(State.UserIdentified);
                                }
                            } catch (Exception ignored) {
                            }

                            super.done(responseCode, responseBody);
                        }
                    }.run();
                }
            }
        }).start();
    }

    public static abstract class SessionRunnable {
        public abstract void run(Session session);
    }

    private static class WhenUserIdIsReadyRun implements Runnable {
        private SessionRunnable runnable;

        public WhenUserIdIsReadyRun(SessionRunnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            synchronized (currentSessionMutex) {
                this.runnable.run(currentSession);
            }
        }
    }

    private static final Object userIdReadyRunnableQueueMutex = new Object();
    private static final ArrayList<WhenUserIdIsReadyRun> userIdReadyRunnableQueue = new ArrayList<>();

    public static void whenUserIdIsReadyRun(@NonNull SessionRunnable runnable) {
        synchronized (currentSessionMutex) {
            if (currentSession == null) {
                synchronized (userIdReadyRunnableQueueMutex) {
                    userIdReadyRunnableQueue.add(new WhenUserIdIsReadyRun(runnable));
                }
            } else {
                synchronized (currentSession.stateMutex) {
                    if (currentSession.state == State.UserIdentified) {
                        new Thread(new WhenUserIdIsReadyRun(runnable)).start();
                    } else {
                        synchronized (userIdReadyRunnableQueueMutex) {
                            userIdReadyRunnableQueue.add(new WhenUserIdIsReadyRun(runnable));
                        }
                    }
                }
            }
        }
    }

    public static void whenUserIdIsOrWasReadyRun(@NonNull SessionRunnable runnable) {
        synchronized (currentSessionMutex) {
            if (currentSession == null) {
                synchronized (userIdReadyRunnableQueueMutex) {
                    userIdReadyRunnableQueue.add(new WhenUserIdIsReadyRun(runnable));
                }
            } else {
                synchronized (currentSession.stateMutex) {
                    if (currentSession.state == State.UserIdentified ||
                            (currentSession.state == State.Expiring && currentSession.previousState == State.UserIdentified)) {
                        new Thread(new WhenUserIdIsReadyRun(runnable)).start();
                    } else {
                        synchronized (userIdReadyRunnableQueueMutex) {
                            userIdReadyRunnableQueue.add(new WhenUserIdIsReadyRun(runnable));
                        }
                    }
                }
            }
        }
    }


    /**
     * Used to listen for when remote configuration is ready
     */
    private RemoteConfiguration.EventListener remoteConfigurationListener = new RemoteConfiguration.EventListener() {
        @Override
        public void onConfigurationReady(RemoteConfiguration configuration) {
            remoteConfiguration = configuration;
            setState(State.Configured);
        }
    };

    /**
     * Used to listen for when a GCM key or Advertising Info is changed.
     */
    private DeviceConfiguration.EventListener deviceConfigurationListener = new DeviceConfiguration.EventListener() {
        @Override
        public void onPushIdChanged(DeviceConfiguration deviceConfiguration) {
            synchronized (stateMutex) {
                if (state == State.UserIdentified) {
                    identifyUser();
                }
            }
        }

        @Override
        public void onAdvertisingInfoChanged(DeviceConfiguration deviceConfiguration) {
            synchronized (stateMutex) {
                if (state == State.UserIdentified) {
                    identifyUser();
                }
            }
        }
    };

    /**
     * Used to listen for Facebook Access Token update
     */
    private BroadcastReceiver facebookBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context unused, Intent intent) {
            String action = intent.getAction();
            if (FacebookAccessTokenBroadcast.UPDATED_ACCESS_TOKEN_INTENT_ACTION.equals(action)) {
                facebookAccessToken = intent.getStringExtra("accessToken");
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Facebook Access Token updated: " + facebookAccessToken);
                }
                synchronized (stateMutex) {
                    if (state == State.UserIdentified) {
                        identifyUser();
                    }
                }
            }
        }
    };

    /**
     * Called by Teak lifecycle when activity is paused, set current session state to Expiring
     */
    public static void onActivityPaused() {
        synchronized (currentSessionMutex) {
            currentSession.setState(State.Expiring);
        }
    }

    /**
     * Called by Teak lifecycle when activity is resumed, reset state on current session if it's 'Expiring'
     */
    public static void onActivityResumed(final Intent intent, final AppConfiguration appConfiguration, final DeviceConfiguration deviceConfiguration) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (currentSessionMutex) {
                    // Call getCurrentSession() so the null || Expired logic stays in one place
                    getCurrentSession(appConfiguration, deviceConfiguration);

                    // Launch attribution contents
                    HashMap<String, Object> launchAttribution = new HashMap<String, Object>();

                    // Check intent for attribution
                    String launchedFromDeepLink = null;
                    if (intent != null) {
                        // Check for an install referrer
                        String installReferrer = null;
                        if (intent.getStringExtra("referrer") != null && !intent.getStringExtra("referrer").isEmpty()) {
                            installReferrer = intent.getStringExtra("referrer");
                            if (Teak.isDebug) {
                                Log.d(LOG_TAG, "Install referrer: " + installReferrer);
                            }
                        }

                        // Check for launch via deep link, process install referrer
                        String intentDataString = intent.getDataString();
                        if (installReferrer != null ||
                                (intentDataString != null && !intentDataString.isEmpty())) {
                            launchedFromDeepLink = intentDataString;
                            if (launchedFromDeepLink != null && Teak.isDebug) {
                                Log.d(LOG_TAG, "Launch from deep link: " + launchedFromDeepLink);
                            }

                            // Try and resolve any Teak links
                            Uri uri = Uri.parse(launchedFromDeepLink);
                            if (uri != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                                HttpsURLConnection connection = null;
                                try {
                                    Uri.Builder httpsUri = uri.buildUpon();
                                    httpsUri.scheme("https");
                                    URL url = new URL(httpsUri.build().toString());
                                    connection = (HttpsURLConnection) url.openConnection();
                                    connection.setRequestProperty("Accept-Charset", "UTF-8");
                                    connection.setRequestProperty("X-Teak-DeviceType", "API");

                                    // Get Response
                                    InputStream is;
                                    if (connection.getResponseCode() < 400) {
                                        is = connection.getInputStream();
                                    } else {
                                        is = connection.getErrorStream();
                                    }
                                    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                                    String line;
                                    StringBuilder response = new StringBuilder();
                                    while ((line = rd.readLine()) != null) {
                                        response.append(line);
                                        response.append('\r');
                                    }
                                    rd.close();

                                    String responseText = response.toString();
                                    try {
                                        JSONObject teakData = new JSONObject(response.toString());
                                        responseText = teakData.toString(2);
                                        if (teakData.getString("AndroidPath") != null) {
                                            launchedFromDeepLink = String.format(Locale.US, "teak%s://%s", appConfiguration.appId, teakData.getString("AndroidPath"));
                                        }
                                    } catch (Exception ignored) {
                                    }

                                    if (Teak.isDebug) {
                                        Log.d(LOG_TAG, responseText);
                                    }
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                                } finally {
                                    if (connection != null) {
                                        connection.disconnect();
                                    }
                                }
                            }
                        }

                        // Check for launch via notification
                        Bundle bundle = intent.getExtras();
                        if (bundle != null) {
                            String teakNotifId = bundle.getString("teakNotifId");
                            if (teakNotifId != null && !teakNotifId.isEmpty()) {
                                // Assign notification launch attribution
                                launchAttribution.put("teak_notif_id", Long.valueOf(teakNotifId));
                                if (Teak.isDebug) {
                                    Log.d(LOG_TAG, "Launch from Teak notification: " + teakNotifId);
                                }
                            }
                        }
                    }

                    // Assign deep link attribution
                    if (launchedFromDeepLink != null && !launchedFromDeepLink.isEmpty()) {
                        launchAttribution.put("deep_link", launchedFromDeepLink);
                        Uri uri = Uri.parse(launchedFromDeepLink);
                        for (String name : uri.getQueryParameterNames()) {
                            if (name.startsWith("teak_")) {
                                List<String> values = uri.getQueryParameters(name);
                                if (values.size() > 1) {
                                    launchAttribution.put(name, values);
                                } else {
                                    launchAttribution.put(name, values.get(0));
                                }
                            }
                        }
                    }

                    // If the current session has a launch different attribution, it's a new session
                    if (!launchAttribution.equals(currentSession.launchAttribution) &&
                            (currentSession.state != State.Allocated && currentSession.state != State.Created)) {
                        Session oldSession = currentSession;
                        currentSession = new Session(oldSession);
                        currentSession.launchAttribution = launchAttribution;
                        currentSession.attributionChain.add(launchAttribution);
                        synchronized (oldSession.stateMutex) {
                            oldSession.setState(State.Expiring);
                            oldSession.setState(State.Expired);
                        }
                    } else {
                        // Reset state on current session, if it is expiring
                        synchronized (currentSession.stateMutex) {
                            if (currentSession.state == State.Expiring) {
                                currentSession.setState(currentSession.previousState);
                            }
                        }
                    }

                    // See if TeakLinks can do anything with the deep link
                    if (launchedFromDeepLink != null) {
                        Uri uri = Uri.parse(launchedFromDeepLink);
                        if (!DeepLink.processUri(uri) && launchAttribution.get("teak_notif_id") != null) {
                            // If this was a deep link from a Teak Notification, then go ahead and
                            // try to find another app to launch.
                            Intent uriIntent = new Intent(Intent.ACTION_VIEW, uri);
                            uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            List<ResolveInfo> resolvedActivities = appConfiguration.packageManager.queryIntentActivities(uriIntent, 0);
                            boolean safeToRedirect = true;
                            for (ResolveInfo info : resolvedActivities) {
                                Log.d(LOG_TAG, "" + info.activityInfo.packageName);
                                safeToRedirect &= !appConfiguration.bundleId.equalsIgnoreCase(info.activityInfo.packageName);
                            }
                            if (resolvedActivities.size() > 0 && safeToRedirect) {
                                appConfiguration.applicationContext.startActivity(uriIntent);
                            }
                        }
                    }
                }
            }
        }).start();
    }

    // region Accessors
    public String userId() {
        return userId;
    }
    // endregion

    // region Current Session
    private static Session currentSession;
    private static final Object currentSessionMutex = new Object();

    private static Session getCurrentSession(AppConfiguration appConfiguration, DeviceConfiguration deviceConfiguration) {
        synchronized (currentSessionMutex) {
            if (currentSession == null || currentSession.hasExpired()) {
                Session oldSession = currentSession;
                currentSession = new Session(appConfiguration, deviceConfiguration);

                if (oldSession != null) {
                    currentSession.attributionChain.addAll(oldSession.attributionChain);

                    // If the old session had a user id assigned, it needs to be passed to the newly created
                    // session. When setState(State.Configured) happens, it will call identifyUser()
                    if (oldSession.userId != null) {
                        if (Teak.isDebug) {
                            Log.d(LOG_TAG, "Previous Session expired, assigning user id '" + oldSession.userId + " to new Session.");
                        }
                        setUserId(oldSession.userId);
                    }
                } else if (Session.pendingUserId != null) {
                    setUserId(Session.pendingUserId);
                    Session.pendingUserId = null;
                }
            }
            return currentSession;
        }
    }

    public static Session getCurrentSessionOrNull() {
        synchronized (currentSessionMutex) {
            return currentSession;
        }
    }
    // endregion

    // region Helpers
    private static boolean attributionStringsAreDifferent(String currentValue, String newValue) {
        return (newValue != null && !newValue.equals(currentValue)) ||
                (currentValue != null && !currentValue.equals(newValue));
    }
    // endregion
}
