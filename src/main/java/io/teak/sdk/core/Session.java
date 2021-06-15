package io.teak.sdk.core;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.FacebookAccessTokenBroadcast;
import io.teak.sdk.Helpers;
import io.teak.sdk.Helpers.mm;
import io.teak.sdk.Request;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.event.AdvertisingInfoEvent;
import io.teak.sdk.event.ExternalBroadcastEvent;
import io.teak.sdk.event.FacebookAccessTokenEvent;
import io.teak.sdk.event.LaunchedFromLinkEvent;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.LogoutEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.event.SessionStateEvent;
import io.teak.sdk.event.UserAdditionalDataEvent;
import io.teak.sdk.event.UserIdEvent;
import io.teak.sdk.json.JSONObject;
import io.teak.sdk.push.PushState;
import io.teak.sdk.referrer.InstallReferrerFuture;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;

public class Session {

    @SuppressWarnings("FieldCanBeLocal") // This can be changed by tests
    private static long SAME_SESSION_TIME_DELTA = 120000;

    public static final Session NullSession = new Session("Null Session");

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
            {}};

        public final String name;

        State(String name) {
            this.name = name;
        }

        public boolean canTransitionTo(State nextState) {
            if (nextState == State.Invalid) return true;

            for (State allowedTransition : allowedTransitions[this.ordinal()]) {
                if (nextState.equals(allowedTransition)) return true;
            }
            return false;
        }
    }

    private State state = State.Allocated;
    private State previousState = null;
    private final InstrumentableReentrantLock stateLock = new InstrumentableReentrantLock();
    private final ExecutorService executionQueue = Executors.newSingleThreadExecutor();
    // endregion

    // State: Created
    private final Date startDate;
    @SuppressWarnings("UnusedDeclaration")
    private final String sessionId;

    // State: Configured
    // (none)

    // State: UserIdentified
    private String userId;
    private String email;

    private ScheduledExecutorService heartbeatService;
    private String countryCode;
    private String facebookAccessToken;

    public UserProfile userProfile;

    // State: Expiring
    private Date endDate;

    // State Independent
    private Future<Map<String, Object>> launchAttribution = null;
    private boolean launchAttributionProcessed = false;

    // For cases where setUserId() is called before a Session has been created
    private static String pendingUserId;
    private static String pendingEmail;

    // Used specifically for creating the "null session" which is just used for code-intent clarity
    private Session(@NonNull String nullSessionId) {
        this.startDate = new Date();
        this.sessionId = nullSessionId;
    }

    private Session() {
        this(null, null);
    }

    private Session(@Nullable Session session, @Nullable Future<Map<String, Object>> launchAttribution) {
        // State: Created
        // Valid data:
        // - startDate
        // - appConfiguration
        // - deviceConfiguration
        this.startDate = new Date();
        this.sessionId = UUID.randomUUID().toString().replace("-", "");
        this.launchAttribution = launchAttribution;
        if (session != null) {
            this.userId = session.userId;
            this.facebookAccessToken = session.facebookAccessToken;
        }
        TeakEvent.addEventListener(this.teakEventListener);

        setState(State.Created);
    }

    private boolean hasExpired() {
        this.stateLock.lock();
        try {
            if (this.state == State.Expiring &&
                (new Date().getTime() - this.endDate.getTime() > SAME_SESSION_TIME_DELTA)) {
                setState(State.Expired);
            }
            return (this.state == State.Expired);
        } finally {
            this.stateLock.unlock();
        }
    }

    private boolean isCurrentSession() {
        Session.currentSessionLock.lock();
        this.stateLock.lock();
        try {
            return (Session.currentSession == this);
        } finally {
            this.stateLock.unlock();
            Session.currentSessionLock.unlock();
        }
    }

    private boolean setState(@NonNull State newState) {
        this.stateLock.lock();
        try {
            if (this.state == newState) {
                Teak.log.i("session.same_state", Helpers.mm.h("state", this.state, "session_id", this.sessionId));
                return false;
            }

            if (!this.state.canTransitionTo(newState)) {
                Teak.log.e("session.invalid_state", mm.h("state", this.state, "new_state", newState, "session_id", this.sessionId));
                return false;
            }

            ArrayList<Object[]> invalidValuesForTransition = new ArrayList<>();

            // Check the data that should be valid before transitioning to the next state. Perform any
            // logic that should occur on transition.
            switch (newState) {
                case Created: {
                    if (this.startDate == null) {
                        invalidValuesForTransition.add(new Object[] {"startDate", "null"});
                        break;
                    }

                    TeakEvent.addEventListener(this.remoteConfigurationEventListener);
                } break;

                case Configured: {
                    TeakEvent.removeEventListener(this.remoteConfigurationEventListener);

                    this.executionQueue.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (Session.this.userId != null) {
                                Session.this.identifyUser();
                            }
                        }
                    });
                } break;

                case IdentifyingUser: {
                    if (this.userId == null) {
                        invalidValuesForTransition.add(new Object[] {"userId", "null"});
                    }
                } break;

                case UserIdentified: {
                    // Start heartbeat, heartbeat service should be null right now
                    if (this.heartbeatService != null) {
                        invalidValuesForTransition.add(new Object[] {"heartbeatService", this.heartbeatService});
                        break;
                    }

                    startHeartbeat();

                    userIdReadyRunnableQueueLock.lock();
                    try {
                        for (WhenUserIdIsReadyRun runnable : userIdReadyRunnableQueue) {
                            this.executionQueue.execute(runnable);
                        }
                        userIdReadyRunnableQueue.clear();
                    } finally {
                        userIdReadyRunnableQueueLock.unlock();
                    }

                    // Process deep link and/or rewards and send out events
                    processAttributionAndDispatchEvents();
                } break;

                case Expiring: {
                    this.endDate = new Date();

                    // TODO: When expiring, punt to background service and say "Hey check the state of this session in N seconds"

                    // Stop heartbeat, Expiring->Expiring is possible, so no invalid data here
                    if (this.heartbeatService != null) {
                        this.heartbeatService.shutdown();
                        this.heartbeatService = null;
                    }

                    // Send UserProfile to server
                    if (this.userProfile != null) {
                        TeakCore.operationQueue.execute(this.userProfile);
                    }
                } break;

                case Expired: {
                    TeakEvent.removeEventListener(this.teakEventListener);

                    // TODO: Report Session to server, once we collect that info.
                } break;
            }

            // Print out any invalid values
            if (invalidValuesForTransition.size() > 0) {
                Map<String, Object> h = new HashMap<>();
                for (Object[] invalidValue : invalidValuesForTransition) {
                    h.put(invalidValue[0].toString(), invalidValue[1]);
                }
                h.put("state", this.state);
                h.put("new_state", newState);
                h.put("session_id", this.sessionId);
                Teak.log.e("session.invalid_values", h);

                // Invalidate this session
                this.setState(State.Invalid);
                return false;
            }

            this.previousState = this.state;
            this.state = newState;

            Teak.log.i("session.state", Helpers.mm.h("state", this.state.name, "old_state", this.previousState.name, "session_id", this.sessionId));
            TeakEvent.postEvent(new SessionStateEvent(this, this.state, this.previousState));

            TeakConfiguration teakConfiguration = TeakConfiguration.get();
            //noinspection all - Seriously, that is not a simplification
            if (this.state == State.Created && teakConfiguration != null && teakConfiguration.remoteConfiguration != null) {
                return setState(State.Configured);
            } else {
                return true;
            }
        } finally {
            this.stateLock.unlock();
        }
    }

    private void startHeartbeat() {
        final TeakConfiguration teakConfiguration = TeakConfiguration.get();

        // If heartbeatInterval is 0, do not send heartbeats
        final int heartbeatInterval = teakConfiguration.remoteConfiguration != null ? teakConfiguration.remoteConfiguration.heartbeatInterval : 60;
        if (heartbeatInterval == 0) {
            return;
        }

        // TODO: Revist this when we have time, if it is important
        //noinspection deprecation - Alex said "ehhhhhhh" to changing the heartbeat param to a map
        @SuppressWarnings("deprecation")
        final String teakSdkVersion = Teak.SDKVersion;

        this.heartbeatService = Executors.newSingleThreadScheduledExecutor();
        this.heartbeatService.scheduleAtFixedRate(new Runnable() {
            public void run() {
                HttpsURLConnection connection = null;
                try {
                    String buster;
                    {
                        final SecureRandom random = new SecureRandom();
                        byte bytes[] = new byte[4];
                        random.nextBytes(bytes);

                        // When packing signed bytes into an int, each byte needs to be masked off
                        // because it is sign-extended to 32 bits (rather than zero-extended) due to
                        // the arithmetic promotion rule (described in JLS, Conversions and Promotions).
                        // https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa
                        final int asInt = bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
                        buster = String.format("%08x", asInt);
                    }

                    String queryString = "game_id=" + URLEncoder.encode(teakConfiguration.appConfiguration.appId, "UTF-8") +
                                         "&api_key=" + URLEncoder.encode(Session.this.userId, "UTF-8") +
                                         "&sdk_version=" + URLEncoder.encode(teakSdkVersion, "UTF-8") +
                                         "&sdk_platform=" + URLEncoder.encode(teakConfiguration.deviceConfiguration.platformString, "UTF-8") +
                                         "&app_version=" + URLEncoder.encode(String.valueOf(teakConfiguration.appConfiguration.appVersion), "UTF-8") +
                                         "&app_version_name=" + URLEncoder.encode(String.valueOf(teakConfiguration.appConfiguration.appVersionName), "UTF-8") +
                                         (Session.this.countryCode == null ? "" : "&country_code=" + URLEncoder.encode(String.valueOf(Session.this.countryCode), "UTF-8")) +
                                         "&buster=" + URLEncoder.encode(buster, "UTF-8");
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
        }, 0, heartbeatInterval, TimeUnit.SECONDS);
    }

    private void identifyUser() {
        final TeakConfiguration teakConfiguration = TeakConfiguration.get();

        this.executionQueue.execute(new Runnable() {
            public void run() {
                Session.this.stateLock.lock();
                try {
                    if (Session.this.state != State.UserIdentified && !Session.this.setState(State.IdentifyingUser)) {
                        return;
                    }

                    HashMap<String, Object> payload = new HashMap<>();

                    if (Session.this.state == State.UserIdentified) {
                        payload.put("do_not_track_event", Boolean.TRUE);
                    }

                    if (Session.this.email != null) {
                        payload.put("email", Session.this.email);
                    }

                    // "true", "false" or "unknown" (if API < 19)
                    payload.put("notifications_enabled",
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? String.valueOf(Teak.getNotificationStatus() == Teak.TEAK_NOTIFICATIONS_ENABLED) : "unknown");

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

                    payload.put("android_limit_ad_tracking", !teakConfiguration.dataCollectionConfiguration.enableIDFA());
                    if (teakConfiguration.deviceConfiguration.advertisingId != null &&
                        teakConfiguration.dataCollectionConfiguration.enableIDFA()) {
                        payload.put("android_ad_id", teakConfiguration.deviceConfiguration.advertisingId);
                    } else {
                        payload.put("android_ad_id", "");
                    }

                    if (teakConfiguration.dataCollectionConfiguration.enableFacebookAccessToken()) {
                        if (Session.this.facebookAccessToken == null) {
                            Session.this.facebookAccessToken = FacebookAccessTokenBroadcast.getCurrentAccessToken();
                        }

                        if (Session.this.facebookAccessToken != null) {
                            payload.put("access_token", Session.this.facebookAccessToken);
                        }
                    }

                    Map<String, Object> attribution = new HashMap<>();
                    if (Session.this.launchAttribution != null) {
                        try {
                            attribution = Session.this.launchAttribution.get(5, TimeUnit.SECONDS);
                        } catch (Exception ignored) {
                        }
                    }
                    payload.putAll(attribution);

                    if (teakConfiguration.deviceConfiguration.pushRegistration != null &&
                        teakConfiguration.dataCollectionConfiguration.enablePushKey()) {
                        payload.putAll(teakConfiguration.deviceConfiguration.pushRegistration);
                        payload.putAll(PushState.get().toMap());
                    }

                    Teak.log.i("session.identify_user", Helpers.mm.h("userId", Session.this.userId, "timezone", tzOffset, "locale", locale, "session_id", Session.this.sessionId));

                    Request.submit("/games/" + teakConfiguration.appConfiguration.appId + "/users.json", payload, Session.this,
                        new Request.Callback() {
                            @Override
                            public void onRequestCompleted(int responseCode, String responseBody) {
                                Session.this.stateLock.lock();
                                try {
                                    JSONObject response = new JSONObject(responseBody);

                                    // TODO: Grab 'id' and 'game_id' from response and store for Parsnip

                                    // Enable verbose logging if flagged
                                    boolean logLocal = response.optBoolean("verbose_logging");
                                    boolean logRemote = response.optBoolean("log_remote");
                                    teakConfiguration.debugConfiguration.setLogPreferences(logLocal, logRemote);

                                    // Server requesting new push key.
                                    if (response.optBoolean("reset_push_key", false)) {
                                        teakConfiguration.deviceConfiguration.requestNewPushToken();
                                    }

                                    // Assign country code from server if it sends it
                                    if (!response.isNull("country_code")) {
                                        Session.this.countryCode = response.getString("country_code");
                                    }

                                    // Assign deep link to launch, if it is provided
                                    if (!response.isNull("deep_link")) {
                                        final String deepLink = response.getString("deep_link");
                                        Map<String, Object> merge = new HashMap<>();
                                        merge.put("deep_link", deepLink);
                                        Session.this.launchAttribution = Session.attributionFutureMerging(Session.this.launchAttribution, merge);
                                        Teak.log.i("deep_link.processed", deepLink);
                                    }

                                    // Grab user profile
                                    JSONObject profile = response.optJSONObject("user_profile");
                                    if (profile != null) {
                                        try {
                                            Session.this.userProfile = new UserProfile(Session.this, profile.toMap());
                                        } catch (Exception ignored) {
                                        }
                                    }

                                    // Grab additional data
                                    final JSONObject additionalData = response.optJSONObject("additional_data");
                                    if (additionalData != null) {
                                        TeakEvent.postEvent(new UserAdditionalDataEvent(additionalData));
                                    }

                                    // Assign new state
                                    // Prevent warning for 'do_not_track_event'
                                    if (Session.this.state == State.Expiring) {
                                        Session.this.previousState = State.UserIdentified;
                                    } else if (Session.this.state != State.UserIdentified) {
                                        Session.this.setState(State.UserIdentified);
                                    }

                                } catch (Exception e) {
                                    Teak.log.exception(e);
                                } finally {
                                    Session.this.stateLock.unlock();
                                }
                            }
                        });
                } finally {
                    Session.this.stateLock.unlock();
                }
            }
        });
    }

    private final TeakEvent.EventListener teakEventListener = new TeakEvent.EventListener() {
        @Override
        public void onNewEvent(@NonNull TeakEvent event) {
            switch (event.eventType) {
                case FacebookAccessTokenEvent.Type: {
                    String newAccessToken = ((FacebookAccessTokenEvent) event).accessToken;
                    if (newAccessToken != null && !newAccessToken.equals(Session.this.facebookAccessToken)) {
                        Session.this.facebookAccessToken = newAccessToken;
                        Teak.log.i("session.fb_access_token", Helpers.mm.h("access_token", Session.this.facebookAccessToken, "session_id", Session.this.sessionId));
                        userInfoWasUpdated();
                    }
                } break;
                case AdvertisingInfoEvent.Type: {
                    userInfoWasUpdated();
                } break;
                case PushRegistrationEvent.Registered: {
                    userInfoWasUpdated();
                } break;
            }
        }
    };

    private void userInfoWasUpdated() {
        // TODO: Revisit/double-check this logic
        this.executionQueue.execute(new Runnable() {
            @Override
            public void run() {
                Session.currentSessionLock.lock();
                Session.this.stateLock.lock();
                try {
                    if (Session.this.state == State.UserIdentified) {
                        identifyUser();
                    } else if (Session.this.state == State.IdentifyingUser) {
                        Session.whenUserIdIsReadyRun(new SessionRunnable() {
                            @Override
                            public void run(Session session) {
                                identifyUser();
                            }
                        });
                    }
                } finally {
                    Session.this.stateLock.unlock();
                    Session.currentSessionLock.unlock();
                }
            }
        });
    }

    private synchronized void processAttributionAndDispatchEvents() {
        // If there is no launch attribution, bail.
        if (this.launchAttribution == null || this.launchAttributionProcessed) return;
        this.launchAttributionProcessed = true;

        this.executionQueue.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Teak.waitUntilDeepLinksAreReady();
                } catch (Exception ignored) {
                }

                // If this is no longer the current session, do not continue processing
                if (!Session.this.isCurrentSession()) {
                    return;
                }

                try {
                    // Resolve attribution Future
                    final Map<String, Object> attribution = Session.this.launchAttribution.get(15, TimeUnit.SECONDS);

                    // Process any deep links
                    Session.this.checkAttributionForDeepLinkAndPostEvents(attribution);

                    // Process any rewards
                    Session.this.checkAttributionForRewardAndPostEvents(attribution);
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            }
        });
    }

    // This is separate so it can be removed/added independently
    private final TeakEvent.EventListener remoteConfigurationEventListener = new TeakEvent.EventListener() {
        @Override
        public void onNewEvent(@NonNull TeakEvent event) {
            if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                executionQueue.execute(new Runnable() {
                    @Override
                    public void run() {
                        stateLock.lock();
                        try {
                            if (state == State.Expiring) {
                                previousState = State.Configured;
                            } else {
                                setState(State.Configured);
                            }
                        } finally {
                            stateLock.unlock();
                        }
                    }
                });
            }
        }
    };

    // Static event listener
    private static final TeakEvent.EventListener staticTeakEventListener = new TeakEvent.EventListener() {
        @Override
        public void onNewEvent(@NonNull TeakEvent event) {
            switch (event.eventType) {
                case LogoutEvent.Type:
                    logout(false);
                    break;
                case UserIdEvent.Type:
                    final UserIdEvent userIdEvent = (UserIdEvent) event;
                    final TeakConfiguration teakConfiguration = TeakConfiguration.get();

                    // Data-collection opt-out
                    //noinspection ConstantConditions
                    if (teakConfiguration != null) {
                        teakConfiguration.dataCollectionConfiguration.addConfigurationFromDeveloper(userIdEvent.optOut);
                    }

                    // Assign user id
                    setUserId(userIdEvent.userId, userIdEvent.email);
                    break;
                case LifecycleEvent.Paused:
                    // Set state to 'Expiring'
                    currentSessionLock.lock();
                    try {
                        if (currentSession != null) {
                            currentSession.setState(State.Expiring);
                        }
                    } finally {
                        currentSessionLock.unlock();
                    }
                    break;
                case LifecycleEvent.Resumed:
                    Intent resumeIntent = ((LifecycleEvent) event).intent;
                    onActivityResumed(resumeIntent);
                    break;
            }
        }
    };

    // TODO: I'd love to make this Annotation based
    static void registerStaticEventListeners() {
        TeakEvent.addEventListener(Session.staticTeakEventListener);
    }

    static boolean isExpiringOrExpired() {
        currentSessionLock.lock();
        try {
            return currentSession == null || currentSession.hasExpired() || currentSession.state == State.Expiring;
        } finally {
            currentSessionLock.unlock();
        }
    }

    private static void setUserId(@NonNull String userId, @Nullable String email) {
        // If the user id has changed, create a new session
        currentSessionLock.lock();
        try {
            if (currentSession == null) {
                Session.pendingUserId = userId;
                Session.pendingEmail = email;
            } else {
                if (currentSession.userId != null && !currentSession.userId.equals(userId)) {
                    Session.logout(true);
                }

                currentSession.stateLock.lock();

                boolean needsIdentifyUser = (currentSession.state == State.Configured);
                if (!Helpers.stringsAreEqual(currentSession.email, email) &&
                    (currentSession.state == State.UserIdentified || currentSession.state == State.IdentifyingUser)) {
                    needsIdentifyUser = true;
                }

                currentSession.stateLock.unlock();

                currentSession.userId = userId;
                currentSession.email = email;

                if (needsIdentifyUser) {
                    currentSession.identifyUser();
                }
            }
        } finally {
            currentSessionLock.unlock();
        }
    }

    private static void logout(boolean copyCurrentSession) {
        currentSessionLock.lock();
        try {
            final Session _lockedSession = currentSession;
            _lockedSession.stateLock.lock();
            try {
                // Do *not* copy the launch attribution. Prevent the server from
                // double-counting attributions, and prevent the client from
                // double-processing deep links and rewards.
                Session newSession = new Session(copyCurrentSession ? currentSession : null, null);

                currentSession.setState(State.Expiring);
                currentSession.setState(State.Expired);

                currentSession = newSession;
            } finally {
                _lockedSession.stateLock.unlock();
            }
        } finally {
            currentSessionLock.unlock();
        }
    }

    public static abstract class SessionRunnable {
        public abstract void run(Session session);
    }

    private static class WhenUserIdIsReadyRun implements Runnable {
        private SessionRunnable runnable;

        WhenUserIdIsReadyRun(SessionRunnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            currentSessionLock.lock();
            try {
                this.runnable.run(currentSession);
            } finally {
                currentSessionLock.unlock();
            }
        }
    }

    private static final InstrumentableReentrantLock userIdReadyRunnableQueueLock = new InstrumentableReentrantLock();
    private static final ArrayList<WhenUserIdIsReadyRun> userIdReadyRunnableQueue = new ArrayList<>();

    public static void whenUserIdIsReadyRun(@NonNull SessionRunnable runnable) {
        currentSessionLock.lock();
        try {
            if (currentSession == null) {
                userIdReadyRunnableQueueLock.lock();
                try {
                    userIdReadyRunnableQueue.add(new WhenUserIdIsReadyRun(runnable));
                } finally {
                    userIdReadyRunnableQueueLock.unlock();
                }
            } else {
                final Session _lockedSession = currentSession;
                _lockedSession.stateLock.lock();
                try {
                    if (currentSession.state == State.UserIdentified) {
                        currentSession.executionQueue.execute(new WhenUserIdIsReadyRun(runnable));
                    } else {
                        userIdReadyRunnableQueueLock.lock();
                        try {
                            userIdReadyRunnableQueue.add(new WhenUserIdIsReadyRun(runnable));
                        } finally {
                            userIdReadyRunnableQueueLock.unlock();
                        }
                    }
                } finally {
                    _lockedSession.stateLock.unlock();
                }
            }
        } finally {
            currentSessionLock.unlock();
        }
    }

    public static void whenUserIdIsOrWasReadyRun(@NonNull SessionRunnable runnable) {
        currentSessionLock.lock();
        try {
            if (currentSession == null) {
                userIdReadyRunnableQueueLock.lock();
                try {
                    userIdReadyRunnableQueue.add(new WhenUserIdIsReadyRun(runnable));
                } finally {
                    userIdReadyRunnableQueueLock.unlock();
                }
            } else {
                final Session _lockedSession = currentSession;
                _lockedSession.stateLock.lock();
                try {
                    if (currentSession.state == State.UserIdentified ||
                        (currentSession.state == State.Expiring && currentSession.previousState == State.UserIdentified)) {
                        currentSession.executionQueue.execute(new WhenUserIdIsReadyRun(runnable));
                    } else {
                        userIdReadyRunnableQueueLock.lock();
                        try {
                            userIdReadyRunnableQueue.add(new WhenUserIdIsReadyRun(runnable));
                        } finally {
                            userIdReadyRunnableQueueLock.unlock();
                        }
                    }
                } finally {
                    _lockedSession.stateLock.unlock();
                }
            }
        } finally {
            currentSessionLock.unlock();
        }
    }

    private static void onActivityResumed(final Intent intent) {
        // Call getCurrentSession() so the null || Expired logic stays in one place
        currentSessionLock.lock();
        try {
            getCurrentSession();

            // If this intent has already been processed by Teak, just reset the state and we are done.
            // Otherwise, out-of-app deep links can cause a back-stack loop
            if (intent.getBooleanExtra("teakSessionProcessed", false)) {
                // Reset state on current session, if it is expiring
                final Session _lockedSession = currentSession;
                _lockedSession.stateLock.lock();
                try {
                    if (currentSession.state == State.Expiring) {
                        currentSession.setState(currentSession.previousState);
                    }
                } finally {
                    _lockedSession.stateLock.unlock();
                }
                return;
            }
            intent.putExtra("teakSessionProcessed", true);

            final TeakConfiguration teakConfiguration = TeakConfiguration.get();
            final boolean isFirstLaunch = intent.getBooleanExtra("teakIsFirstLaunch", false);

            // Check for launch via notification
            final String teakNotifId = Helpers.getStringOrNullFromIntentExtra(intent, "teakNotifId");

            // See if there's a deep link in the intent
            Future<String> deepLinkURL = null;
            final String intentDataString = intent.getDataString();
            if (intentDataString != null && !intentDataString.isEmpty()) {
                Teak.log.i("session.attribution", Helpers.mm.h("deep_link", intentDataString));
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
                    public String get() {
                        return intentDataString;
                    }

                    @Override
                    public String get(long l, @NonNull TimeUnit timeUnit) {
                        return get();
                    }
                };
            } else if (isFirstLaunch) {
                deepLinkURL = InstallReferrerFuture.get(teakConfiguration.appConfiguration.applicationContext);
            }

            // Get an attribution future for a deep link, and include teak_notif_id if it exists.
            final Future<Map<String, Object>> deepLinkAttribution;
            if (deepLinkURL != null) {
                deepLinkAttribution = attributionWithDeepLink(deepLinkURL, teakNotifId);
            } else {
                deepLinkAttribution = null;
            }

            // Get the session attribution
            final Future<Map<String, Object>> sessionAttribution;
            if (deepLinkAttribution != null) {
                // deepLinkAttribution contains teak_notif_id
                sessionAttribution = deepLinkAttribution;
            } else if (teakNotifId != null) {
                Teak.log.i("session.attribution", Helpers.mm.h("teak_notif_id", teakNotifId));
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
                    public Map<String, Object> get() {
                        Map<String, Object> returnValue = new HashMap<>();
                        returnValue.put("teak_notif_id", teakNotifId);
                        returnValue.put("notification_placement", Helpers.getStringOrNullFromIntentExtra(intent, "teakNotificationPlacement"));
                        return returnValue;
                    }

                    @Override
                    public Map<String, Object> get(long l, @NonNull TimeUnit timeUnit) {
                        return get();
                    }
                };
            } else {
                sessionAttribution = null;
            }

            // If the current session has a launch different attribution, it's a new session
            if (currentSession.state == State.Allocated || currentSession.state == State.Created) {
                currentSession.launchAttribution = sessionAttribution;
            } else if (sessionAttribution != null) {
                Session oldSession = currentSession;
                currentSession = new Session(oldSession, sessionAttribution);
                oldSession.stateLock.lock();
                try {
                    oldSession.setState(State.Expiring);
                    oldSession.setState(State.Expired);
                } finally {
                    oldSession.stateLock.unlock();
                }
            } else {
                // Reset state on current session, if it is expiring
                final Session _lockedSession = currentSession;
                _lockedSession.stateLock.lock();
                try {
                    if (currentSession.state == State.Expiring) {
                        currentSession.setState(currentSession.previousState);
                    }
                } finally {
                    _lockedSession.stateLock.unlock();
                }
            }
        } finally {
            currentSessionLock.unlock();
        }
    }

    private void checkAttributionForDeepLinkAndPostEvents(final Map<String, Object> attribution) {
        try {
            final TeakConfiguration teakConfiguration = TeakConfiguration.get();
            final String deep_link = (String) attribution.get("deep_link");
            final URI uri = deep_link == null ? null : new URI(deep_link);

            if (uri != null) {
                // See if TeakLinks can do anything with the deep link
                final boolean deepLinkWasProcessedByTeak = DeepLink.processUri(uri);

                // Otherwise, if this was a deep link (not a launch_link) then it was either a universal
                // link or came in from a Teak Notification
                if (!deepLinkWasProcessedByTeak) {
                    Intent uriIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(deep_link));
                    uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    List<ResolveInfo> resolvedActivities = teakConfiguration.appConfiguration.packageManager.queryIntentActivities(uriIntent, 0);
                    boolean safeToRedirect = true;
                    for (ResolveInfo info : resolvedActivities) {
                        safeToRedirect &= !teakConfiguration.appConfiguration.bundleId.equalsIgnoreCase(info.activityInfo.packageName);
                    }
                    if (resolvedActivities.size() > 0 && safeToRedirect) {
                        teakConfiguration.appConfiguration.applicationContext.startActivity(uriIntent);
                    }
                }
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    private void checkAttributionForRewardAndPostEvents(final Map<String, Object> attribution) {
        try {
            final String teakNotifId = (String) attribution.get("teak_notif_id");
            final String teakRewardId = attribution.containsKey("teak_reward_id") ? attribution.get("teak_reward_id").toString() : null;
            final String teakRewardLinkName = attribution.containsKey("teak_rewardlink_name") ? attribution.get("teak_rewardlink_name").toString() : null;
            final String teakChannelName = attribution.containsKey("teak_channel_name") ? attribution.get("teak_channel_name").toString() : null;
            // Future-Pat: Attribution can also contain 'teak_rewardlink_id' if we ever need it

            if (teakRewardId != null) {
                final Future<TeakNotification.Reward> rewardFuture = TeakNotification.Reward.rewardFromRewardId(teakRewardId);
                if (rewardFuture != null) {
                    Session.this.executionQueue.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final TeakNotification.Reward reward = rewardFuture.get();

                                // Future-Pat, we can do this cast because we have vendored the JSONObject code
                                // and it uses a HashMap.
                                final HashMap<String, Object> rewardMap = (HashMap<String, Object>) reward.json.toMap();

                                // This is to make sure the payloads match from a notification claim
                                rewardMap.put("teakNotifId", teakNotifId);
                                rewardMap.put("incentivized", true);
                                rewardMap.put("teakRewardId", teakRewardId);
                                rewardMap.put("teakScheduleName", null);
                                rewardMap.put("teakCreativeName", teakRewardLinkName);
                                rewardMap.put("teakChannelName", teakChannelName);

                                final Intent rewardIntent = new Intent(Teak.REWARD_CLAIM_ATTEMPT);
                                rewardIntent.putExtra("reward", rewardMap);
                                TeakEvent.postEvent(new ExternalBroadcastEvent(rewardIntent));
                            } catch (Exception e) {
                                Teak.log.exception(e);
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    private static Future<Map<String, Object>> attributionWithDeepLink(final Future<String> urlFuture, final String teakNotifId) {
        final TeakConfiguration teakConfiguration = TeakConfiguration.get();

        FutureTask<Map<String, Object>> returnTask = new FutureTask<>(new Callable<Map<String, Object>>() {
            @Override
            public Map<String, Object> call() {
                Map<String, Object> returnValue = new HashMap<>();
                boolean wasTeakDeepLink = false;

                // Make sure teak_notif_id is in the return value if provided
                if (teakNotifId != null) {
                    returnValue.put("teak_notif_id", teakNotifId);
                }

                // Wait on the incoming Future
                Uri uri = null;
                try {
                    uri = Uri.parse(urlFuture.get(5, TimeUnit.SECONDS));
                } catch (Exception ignored) {
                }

                // If we have a URL, process it if needed
                if (uri != null) {
                    // Try and resolve any Teak links
                    if (uri.getScheme() != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                        HttpsURLConnection connection = null;
                        try {
                            Uri.Builder httpsUri = uri.buildUpon();
                            httpsUri.scheme("https");
                            URL url = new URL(httpsUri.build().toString());

                            Teak.log.i("deep_link.request.send", url.toString());

                            connection = (HttpsURLConnection) url.openConnection();
                            connection.setUseCaches(false);
                            connection.setRequestProperty("Accept-Charset", "UTF-8");
                            connection.setRequestProperty("X-Teak-DeviceType", "API");
                            connection.setRequestProperty("X-Teak-Supports-Templates", "TRUE");

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

                            Teak.log.i("deep_link.request.reply", response.toString());

                            try {
                                JSONObject teakData = new JSONObject(response.toString());
                                if (teakData.getString("AndroidPath") != null) {
                                    uri = Uri.parse(String.format(Locale.US, "teak%s://%s", teakConfiguration.appConfiguration.appId, teakData.getString("AndroidPath")));
                                    wasTeakDeepLink = true;
                                }

                                Teak.log.i("deep_link.request.resolve", uri.toString());
                                TeakEvent.postEvent(new LaunchedFromLinkEvent(teakData));
                            } catch (Exception e) {
                                Teak.log.exception(e);
                            }
                        } catch (SSLProtocolException ssl_e) {
                            // Ignored, Sentry issue 'TEAK-SDK-Z'
                        } catch (SSLException ssl_e) {
                            // Ignored
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        } finally {
                            if (connection != null) {
                                connection.disconnect();
                            }
                        }
                    } else if (DeepLink.willProcessUri(uri)) {
                        wasTeakDeepLink = true;
                    }

                    // Always assign 'launch_link'
                    returnValue.put("launch_link", uri.toString());

                    // Put the URI and any query parameters that start with 'teak_' into 'deep_link'
                    // but only if this was a Teak deep link
                    if (wasTeakDeepLink) {
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
                }

                return returnValue;
            }
        });

        // Start it running, and return the Future
        ThreadFactory.autoStart(returnTask);
        return returnTask;
    }

    private static Future<Map<String, Object>> attributionFutureMerging(@Nullable final Future<Map<String, Object>> previousAttribution, @NonNull final Map<String, Object> merging) {
        return new Future<Map<String, Object>>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return previousAttribution == null || previousAttribution.cancel(mayInterruptIfRunning);
            }

            @Override
            public boolean isCancelled() {
                return previousAttribution != null && previousAttribution.isCancelled();
            }

            @Override
            public boolean isDone() {
                return previousAttribution == null || previousAttribution.isDone();
            }

            @Override
            public Map<String, Object> get() throws InterruptedException, ExecutionException {
                Map<String, Object> ret = previousAttribution == null ? new HashMap<String, Object>() : previousAttribution.get();
                ret.putAll(merging);
                return ret;
            }

            @Override
            public Map<String, Object> get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                Map<String, Object> ret = previousAttribution == null ? new HashMap<String, Object>() : previousAttribution.get(timeout, unit);
                ret.putAll(merging);
                return ret;
            }
        };
    }

    // region Accessors
    public String userId() {
        return userId;
    }
    // endregion

    // region Current Session
    private static Session currentSession;
    private static final InstrumentableReentrantLock currentSessionLock = new InstrumentableReentrantLock();

    private static void getCurrentSession() {
        currentSessionLock.lock();
        try {
            if (currentSession == null || currentSession.hasExpired()) {
                Session oldSession = currentSession;
                currentSession = new Session();

                if (oldSession != null) {
                    // If the old session had a user id assigned, it needs to be passed to the newly created
                    // session. When setState(State.Configured) happens, it will call identifyUser()
                    if (oldSession.userId != null) {
                        setUserId(oldSession.userId, oldSession.email);
                    }
                } else if (Session.pendingUserId != null) {
                    setUserId(Session.pendingUserId, Session.pendingEmail);
                    Session.pendingUserId = null;
                    Session.pendingEmail = null;
                }
            }
        } finally {
            currentSessionLock.unlock();
        }
    }

    static Session getCurrentSessionOrNull() {
        currentSessionLock.lock();
        try {
            return currentSession;
        } finally {
            currentSessionLock.unlock();
        }
    }
    // endregion

    private Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("startDate", this.startDate.getTime() / 1000);
        return map;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.toMap())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
