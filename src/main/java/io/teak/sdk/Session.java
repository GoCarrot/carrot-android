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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
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
        UserIdentified("UserIdentified"),
        Expiring("Expiring"),
        Expired("Expired");

        //public static final Integer length = 1 + Expired.ordinal();

        private static final State[][] allowedTransitions = {
                {},
                {State.Created, State.Expiring},
                {State.Configured, State.Expiring},
                {State.UserIdentified, State.Expiring},
                {State.Expiring},
                {State.Created, State.Configured, State.UserIdentified, State.Expired},
                {}
        };

        public final String name;

        State(String name) {
            this.name = name;
        }

        public boolean canTransitionTo(State nextState) {
            if (nextState == State.Invalid) return true;

            boolean ret = false;
            for (State allowedTransition : allowedTransitions[this.ordinal()]) {
                if (nextState == allowedTransition) {
                    ret = true;
                    break;
                }
            }
            return ret;
        }
    }

    private State state = State.Allocated; // Assign using setState() during copy constructor
    private State previousState = null;    // Copy during copy constructor
    private final Object stateMutex = new Object(); // Do not copy during copy constructor
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
    public final Date startDate; // Do not copy during copy constructor
    public final AppConfiguration appConfiguration; // Copy during copy constructor
    public final DeviceConfiguration deviceConfiguration; // Copy during copy constructor

    // State: Configured
    public RemoteConfiguration remoteConfiguration; // Copy during copy constructor

    // State: UserIdentified
    private String userId; // Copy during copy constructor

    private ScheduledExecutorService heartbeatService; // Do not copy during copy constructor
    private String countryCode; // Copy during copy constructor

    // State: Expiring
    private Date endDate; // Do not copy during copy constructor

    // State Independent
    private String launchedFromTeakNotifId; // Do not copy during copy constructor
    private String launchedFromDeepLink;    // Do not copy during copy constructor
    private ArrayList<String> attributionChain = new ArrayList<>(); // Append during copy constructor

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

        setState(State.Created);
    }

    private Session(@NonNull Session otherSession) {
        this.startDate = new Date();

        // Add the old session's attribution chain to ours
        this.attributionChain.addAll(otherSession.attributionChain);

        // Copy over relevant data
        this.appConfiguration = otherSession.appConfiguration;
        this.deviceConfiguration = otherSession.deviceConfiguration;
        this.remoteConfiguration = otherSession.remoteConfiguration;
        this.previousState = otherSession.previousState;
        this.userId = otherSession.userId;
        this.countryCode = otherSession.countryCode;

        DeviceConfiguration.addEventListener(this.deviceConfigurationListener);

        // Assign state
        setState(otherSession.state);
    }

    public boolean hasExpired() {
        synchronized (stateMutex) {
            if (this.state == State.Expiring &&
                    (new Date().getTime() - currentSession.endDate.getTime() > SAME_SESSION_TIME_DELTA)) {
                setState(State.Expired);
            }
            return (this.state == State.Expired);
        }
    }

    public static void setUserId(@NonNull String userId) {
        if (userId.isEmpty()) {
            Log.e(LOG_TAG, "User Id can not be null or empty.");
            return;
        }

        // If the user id has changed, create a new session
        synchronized (currentSessionMutex) {
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

    private boolean setState(@NonNull State newState) {
        synchronized (stateMutex) {
            if (this.state == newState) {
                Log.i(LOG_TAG, String.format("Session State transition to same state (%s). Ignoring.", this.state));
                return true;
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

                    if (this.userId != null) {
                        currentSession.identifyUser();
                    }
                }
                break;

                case UserIdentified: {
                    if (this.userId == null) {
                        invalidValuesForTransition.add(new Object[]{"userId", "null"});
                        break;
                    }

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
        final Date dateIssued = new Date();
        final Session _this = this;

        new Thread(new Runnable() {
            public void run() {
                synchronized (_this.stateMutex) {
                    HashMap<String, Object> payload = new HashMap<>();

                    payload.put("api_key", _this.userId);
                    payload.put("happened_at", dateIssued.getTime() / 1000); // Milliseconds -> Seconds

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

                    if (_this.deviceConfiguration.advertsingInfo != null) {
                        payload.put("android_ad_id", _this.deviceConfiguration.advertsingInfo.getId());
                        payload.put("android_limit_ad_tracking", _this.deviceConfiguration.advertsingInfo.isLimitAdTrackingEnabled());
                    }

                    try {
                        String accessToken = Teak.facebookAccessToken.get(5L, TimeUnit.SECONDS);
                        if (accessToken != null) {
                            payload.put("access_token", accessToken);
                        }
                    } catch (Exception ignored) {
                    }

                    if (_this.launchedFromTeakNotifId != null) {
                        payload.put("teak_notif_id", Long.valueOf(_this.launchedFromTeakNotifId));
                    }

                    if (_this.launchedFromDeepLink != null) {
                        payload.put("deep_link", _this.launchedFromDeepLink);
                    }

                    if (_this.deviceConfiguration.gcmId != null) {
                        payload.put("gcm_push_key", _this.deviceConfiguration.gcmId);
                    } else {
                        payload.put("gcm_push_key", "");
                    }

                    Log.d(LOG_TAG, "Identifying user: " + _this.userId);
                    Log.d(LOG_TAG, "        Timezone: " + tzOffset);
                    Log.d(LOG_TAG, "          Locale: " + locale);

                    new CachedRequest.IdentifyUserRequest("/games/" + _this.appConfiguration.appId + "/users.json", payload, dateIssued, _this, _this.userId) {
                        @Override
                        protected void done(int responseCode, String responseBody) {
                            try {
                                JSONObject response = new JSONObject(responseBody);

                                // TODO: Grab 'id' and 'game_id' from response and store for Parsnip

                                // Enable verbose logging if flagged
                                boolean enableVerboseLogging = response.optBoolean("verbose_logging");
                                Teak.debugConfiguration.setPreferenceForceDebug(enableVerboseLogging);

                                // Server requesting new push key.
                                if (response.optBoolean("reset_push_key", false)) {
                                    _this.deviceConfiguration.reRegisterPushToken(_this.appConfiguration);
                                }

                                if (response.has("country_code")) {
                                    _this.countryCode = response.getString("country_code");
                                }

                                _this.setState(State.UserIdentified);
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

    /**
     * Process an Intent and assign new values for launching from a deep link or Teak notification.
     * <p/>
     * If currentSession was launched via a deep link or notification, and the incoming intent has
     * a new (non null/empty) value. Create a new Session, cloning state from the old one.
     *
     * @param intent Incoming Intent to process.
     */
    public static void processIntent(Intent intent, @NonNull AppConfiguration appConfiguration, @NonNull DeviceConfiguration deviceConfiguration) {
        if (intent == null) return;

        synchronized (currentSessionMutex) {
            // Call getCurrentSession() so the null || Expired logic stays in one place
            getCurrentSession(appConfiguration, deviceConfiguration);

            // Check for launch via deep link
            String intentDataString = intent.getDataString();
            String launchedFromDeepLink = null;
            if (intentDataString != null && !intentDataString.isEmpty()) {
                launchedFromDeepLink = intentDataString;
            }

            // Check for launch via notification
            Bundle bundle = intent.getExtras();
            String launchedFromTeakNotifId = null;
            if (bundle != null) {
                String teakNotifId = bundle.getString("teakNotifId");
                if (teakNotifId != null && !teakNotifId.isEmpty()) {
                    launchedFromTeakNotifId = teakNotifId;
                }
            }

            // If the current session has a launch from deep link/notification, and there is a new
            // deep link/notification, it's a new session
            if (stringsAreNotNullOrEmptyAndAreDifferent(currentSession.launchedFromDeepLink, launchedFromDeepLink) ||
                    stringsAreNotNullOrEmptyAndAreDifferent(currentSession.launchedFromTeakNotifId, launchedFromTeakNotifId)) {
                Session oldSession = currentSession;
                currentSession = new Session(currentSession); // Copy constructor will append oldSession's attribution chain
                oldSession.setState(State.Expired); // Expire after copy constructor so Expired state isn't copied
            }

            // Assign attribution
            if (launchedFromDeepLink != null && !launchedFromDeepLink.isEmpty()) {
                currentSession.launchedFromDeepLink = launchedFromDeepLink;
                currentSession.attributionChain.add(launchedFromDeepLink);
            } else if (launchedFromTeakNotifId != null && !launchedFromTeakNotifId.isEmpty()) {
                currentSession.launchedFromTeakNotifId = launchedFromTeakNotifId;
                currentSession.attributionChain.add(launchedFromTeakNotifId);

                // Send broadcast
                // TODO: Update Unity SDK to read teakNotifId from the broadcast intent
                if (Teak.localBroadcastManager != null) {
                    Intent broadcastEvent = new Intent(TeakNotification.LAUNCHED_FROM_NOTIFICATION_INTENT);
                    broadcastEvent.putExtra("teakNotifId", currentSession.launchedFromTeakNotifId);
                    Teak.localBroadcastManager.sendBroadcast(broadcastEvent);
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
        public void onGCMIdChanged(DeviceConfiguration deviceConfiguration) {
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
    public static void onActivityResumed(AppConfiguration appConfiguration, DeviceConfiguration deviceConfiguration) {
        synchronized (currentSessionMutex) {
            // Call getCurrentSession() so the null || Expired logic stays in one place
            getCurrentSession(appConfiguration, deviceConfiguration);

            // Reset state on current session, if it is expiring
            synchronized (currentSession.stateMutex) {
                if (currentSession.state == State.Expiring) {
                    currentSession.setState(currentSession.previousState);
                }
            }
        }
    }

    // region Accessors
    public String userId() {
        if (this.state != State.UserIdentified) {
            Log.e(LOG_TAG, "Called userId() without checking for state == State.UserIdentified");
        }
        return userId;
    }
    // endregion

    // region Current Session
    private static Session currentSession;
    private static final Object currentSessionMutex = new Object();

    public static Session getCurrentSession(AppConfiguration appConfiguration, DeviceConfiguration deviceConfiguration) {
        synchronized (currentSessionMutex) {
            if (currentSession == null || currentSession.hasExpired()) {
                currentSession = new Session(appConfiguration, deviceConfiguration);
            }
            return currentSession;
        }
    }
    // endregion

    // region Helpers
    private static boolean stringsAreNotNullOrEmptyAndAreDifferent(String currentValue, String newValue) {
        return (currentValue != null && !currentValue.isEmpty() &&
                newValue != null && !newValue.isEmpty() &&
                !currentValue.equals(newValue));
    }
    // endregion
}
