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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HttpsURLConnection;

import io.teak.sdk.Helpers._;

class Session {
    private static long SAME_SESSION_TIME_DELTA = 120000;

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
    public final String sessionId;

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
    private Future<Map<String, Object>> launchAttribution = null;

    // For cases where setUserId() is called before a Session has been created
    private static String pendingUserId;

    private static final String PREFERENCE_FIRST_RUN = "io.teak.sdk.Preferences.FirstRun";

    private Session(AppConfiguration appConfiguration, DeviceConfiguration deviceConfiguration) {
        // State: Created
        // Valid data:
        // - startDate
        // - appConfiguration
        // - deviceConfiguration
        this.startDate = new Date();
        this.appConfiguration = appConfiguration;
        this.deviceConfiguration = deviceConfiguration;
        this.sessionId = UUID.randomUUID().toString().replace("-", "");

        DeviceConfiguration.addEventListener(this.deviceConfigurationListener);

        IntentFilter filter = new IntentFilter();
        filter.addAction(FacebookAccessTokenBroadcast.UPDATED_ACCESS_TOKEN_INTENT_ACTION);
        Teak.localBroadcastManager.registerReceiver(this.facebookBroadcastReceiver, filter);

        setState(State.Created);
    }

    private Session(@NonNull Session session) {
        this(session.appConfiguration, session.deviceConfiguration);

        this.userId = session.userId;
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
            Teak.log.e("session", "User Id can not be null or empty.");
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

                        newSession.launchAttribution = currentSession.launchAttribution;

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
                Teak.log.i("session.same_state", _.h("state", this.state));
                return false;
            }

            if (!this.state.canTransitionTo(newState)) {
                Teak.log.e("session.invalid_state", _.h("state", this.state, "new_state", newState));
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
                Map<String, Object> h = new HashMap<>();
                for (Object[] invalidValue : invalidValuesForTransition) {
                    h.put(invalidValue[0].toString(), invalidValue[1]);
                }
                h.put("state", this.state);
                h.put("new_state", newState);
                Teak.log.e("session.invalid_values", h);

                // Invalidate this session
                this.setState(State.Invalid);
                return false;
            }

            this.previousState = this.state;
            this.state = newState;

            Teak.log.i("session.state", _.h("state", this.state.name, "old_state", this.previousState.name));

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
                    connection.getResponseCode();
                } catch (Exception ignored) {
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

                    Map<String, Object> attribution = new HashMap<>();
                    if (_this.launchAttribution != null) {
                        try {
                            attribution = _this.launchAttribution.get(5, TimeUnit.SECONDS);
                        } catch (Exception ignored) {
                        }
                    }
                    for (Map.Entry<String, Object> entry : attribution.entrySet()) {
                        payload.put(entry.getKey(), entry.getValue());
                    }

                    if (_this.deviceConfiguration.gcmId != null) {
                        payload.put("gcm_push_key", _this.deviceConfiguration.gcmId);
                    }

                    if (_this.deviceConfiguration.admId != null) {
                        payload.put("adm_push_key", _this.deviceConfiguration.admId);
                    }

                    Teak.log.i("session.identify_user", _.h("userId", _this.userId, "timezone", tzOffset, "locale", locale));

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
                                    _this.deviceConfiguration.reRegisterPushToken(_this.appConfiguration, "reset_push_key");
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
            synchronized (currentSession.stateMutex) {
                if (currentSession.state == State.Expiring) {
                    currentSession.previousState = State.Configured;
                } else {
                    setState(State.Configured);
                }
            }
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
                Teak.log.i("session.fb_access_token", _.h("access_token", facebookAccessToken));
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
        // Call getCurrentSession() so the null || Expired logic stays in one place
        synchronized (currentSessionMutex) {
            getCurrentSession(appConfiguration, deviceConfiguration);

            // If this intent has already been processed by Teak, just reset the state and we are done.
            // Otherwise, out-of-app deep links can cause a back-stack loop
            if (intent.getBooleanExtra("processedByTeak", false)) {
                // Reset state on current session, if it is expiring
                synchronized (currentSession.stateMutex) {
                    if (currentSession.state == State.Expiring) {
                        currentSession.setState(currentSession.previousState);
                    }
                }
                return;
            }
            intent.putExtra("processedByTeak", true);

            // Check and see if this is (probably) the first time this app has been ever launched
            boolean isFirstLaunch = false;
            if (deviceConfiguration.preferences != null) {
                long firstLaunch = deviceConfiguration.preferences.getLong(PREFERENCE_FIRST_RUN, 0);
                if (firstLaunch == 0) {
                    firstLaunch = new Date().getTime() / 1000;
                    SharedPreferences.Editor editor = deviceConfiguration.preferences.edit();
                    editor.putLong(PREFERENCE_FIRST_RUN, firstLaunch);
                    editor.apply();
                    isFirstLaunch = true;
                }
            }

            // If this is the first launch, see if the InstallReferrerReceiver has anything for us.
            Future<String> deepLinkURL = null;
            if (isFirstLaunch) {
                FutureTask<String> referrerPollTask = new FutureTask<>(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        long startTime = System.nanoTime();
                        String referralString = InstallReferrerReceiver.installReferrerQueue.poll();
                        while(referralString == null) {
                            Thread.sleep(100);
                            referralString = InstallReferrerReceiver.installReferrerQueue.poll();
                            long elapsedTime = System.nanoTime() - startTime;
                            if (elapsedTime / 1000000000 > 10) break;
                        }
                        return referralString;
                    }
                });
                new Thread(referrerPollTask).start();
                deepLinkURL = referrerPollTask;
            } else {
                // Otherwise see if there's a deep link in the intent
                final String intentDataString = intent.getDataString();
                if (intentDataString != null && !intentDataString.isEmpty()) {
                    Teak.log.i("session.attribution", _.h("deep_link", intentDataString));
                    deepLinkURL = new Future<String>() {
                        @Override
                        public boolean cancel(boolean b) {
                            return false;
                        }

                        @Override
                        public boolean isCancelled() {
                            return false;
                        }

                        @Override
                        public boolean isDone() {
                            return true;
                        }

                        @Override
                        public String get() throws InterruptedException, ExecutionException {
                            return intentDataString;
                        }

                        @Override
                        public String get(long l, @NonNull TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                            return get();
                        }
                    };
                }
            }

            //
            final Future<Map<String, Object>> deepLinkAttribution;
            if (deepLinkURL != null) {
                deepLinkAttribution = attributionForDeepLink(deepLinkURL, appConfiguration);
            } else {
                deepLinkAttribution = null;
            }

            // Check for launch via notification
            String nonFinalTeakNotifId = null;
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                String teakNotifId = bundle.getString("teakNotifId");
                if (teakNotifId != null && !teakNotifId.isEmpty()) {
                    nonFinalTeakNotifId = teakNotifId;
                }
            }
            final String teakNotifId = nonFinalTeakNotifId;

            // Get the session attribution
            final Future<Map<String, Object>> sessionAttribution;
            if (teakNotifId != null) {
                Teak.log.i("session.attribution", _.h("teak_notif_id", teakNotifId));
                sessionAttribution = new Future<Map<String, Object>>() {
                    @Override
                    public boolean cancel(boolean b) {
                        return false;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public boolean isDone() {
                        return true;
                    }

                    @Override
                    public Map<String, Object> get() throws InterruptedException, ExecutionException {
                        Map<String, Object> returnValue = new HashMap<>();
                        returnValue.put("teak_notif_id", teakNotifId);
                        return returnValue;
                    }

                    @Override
                    public Map<String, Object> get(long l, @NonNull TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                        return get();
                    }
                };
            } else if (deepLinkAttribution != null) {
                sessionAttribution = deepLinkAttribution;
            } else {
                sessionAttribution = null;
            }

            // If there is any deep link, see if we handle the link
            if (deepLinkAttribution != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // If we need to wait for Unity/Adobe Air, do it here
                            if (Teak.waitForDeepLink != null) {
                                Teak.waitForDeepLink.get();
                            }
                        } catch (Exception ignored){
                        }

                        try {
                            // Resolve the deepLinkAttribution future
                            Map<String, Object> attribution = deepLinkAttribution.get(5, TimeUnit.SECONDS);
                            Uri uri = Uri.parse((String) attribution.get("deep_link"));

                            // See if TeakLinks can do anything with the deep link
                            if (!DeepLink.processUri(uri) && teakNotifId != null) {
                                // If this was a deep link from a Teak Notification, then go ahead and
                                // try to find a different app to launch.
                                Intent uriIntent = new Intent(Intent.ACTION_VIEW, uri);
                                uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                List<ResolveInfo> resolvedActivities = appConfiguration.packageManager.queryIntentActivities(uriIntent, 0);
                                boolean safeToRedirect = true;
                                for (ResolveInfo info : resolvedActivities) {
                                    safeToRedirect &= !appConfiguration.bundleId.equalsIgnoreCase(info.activityInfo.packageName);
                                }
                                if (resolvedActivities.size() > 0 && safeToRedirect) {
                                    appConfiguration.applicationContext.startActivity(uriIntent);
                                }
                            }

                            // Send reward broadcast
                            String teakRewardId = attribution.get("teak_reward_id").toString();
                            if (teakRewardId != null) {
                                final Future<TeakNotification.Reward> rewardFuture = TeakNotification.Reward.rewardFromRewardId(teakRewardId);
                                if (rewardFuture != null) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                TeakNotification.Reward reward = rewardFuture.get();
                                                HashMap<String, Object> rewardMap = Helpers.jsonToMap(reward.json);
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
                        } catch (Exception ignored) {
                        }
                    }
                }).start();
            }

            // If the current session has a launch different attribution, it's a new session
            if (sessionAttribution != null && (currentSession.state != State.Allocated && currentSession.state != State.Created)) {
                Session oldSession = currentSession;
                currentSession = new Session(oldSession);
                currentSession.launchAttribution = sessionAttribution;
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
        }
    }

    private static Future<Map<String, Object>> attributionForDeepLink(final Future<String> urlFuture, final AppConfiguration appConfiguration) {
        FutureTask<Map<String, Object>> returnTask = new FutureTask<>(new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() throws Exception {
                Map<String, Object> returnValue = new HashMap<>();

                // Wait on the incoming Future
                Uri uri = null;
                try {
                    uri = Uri.parse(urlFuture.get(5, TimeUnit.SECONDS));
                } catch (Exception ignored) {
                }

                // If we have a URL, process it if needed
                if (uri != null) {
                    // Try and resolve any Teak links
                    if (uri.getScheme().equals("http") || uri.getScheme().equals("https")) {
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

                            try {
                                JSONObject teakData = new JSONObject(response.toString());
                                if (teakData.getString("AndroidPath") != null) {
                                    uri = Uri.parse(String.format(Locale.US, "teak%s://%s", appConfiguration.appId, teakData.getString("AndroidPath")));
                                }
                            } catch (Exception ignored) {
                            }
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        } finally {
                            if (connection != null) {
                                connection.disconnect();
                            }
                        }
                    }

                    // Put the URI and any query parameters that start with 'teak_'
                    returnValue.put("deep_link", uri.toString());
                    for (String name : uri.getQueryParameterNames()) {
                        if (name.startsWith("teak_")) {
                            List<String> values = uri.getQueryParameters(name);
                            if (values.size() > 1) {
                                returnValue.put(name, values);
                            } else {
                                returnValue.put(name, values.get(0));
                            }
                        }
                    }
                }

                return returnValue;
            }
        });

        // Start it running, and return the Future
        new Thread(returnTask).start();
        return returnTask;
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
                    // If the old session had a user id assigned, it needs to be passed to the newly created
                    // session. When setState(State.Configured) happens, it will call identifyUser()
                    if (oldSession.userId != null) {
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

    public Map<String, Object> to_h() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("appConfiguration", Integer.toHexString(this.appConfiguration.hashCode()));
        map.put("deviceConfiguration", Integer.toHexString(this.deviceConfiguration.hashCode()));
        map.put("startDate", this.startDate.getTime() / 1000);
        return map;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.to_h())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
