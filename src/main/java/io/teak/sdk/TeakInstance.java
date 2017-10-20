/* Teak -- Copyright (C) 2017 GoCarrot Inc.
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
import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.security.InvalidParameterException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.PurchaseFailedEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.event.TrackEventEvent;
import io.teak.sdk.event.UserIdEvent;
import io.teak.sdk.store.IStore;

class TeakInstance {
    private final IObjectFactory objectFactory;

    private static final String PREFERENCE_FIRST_RUN = "io.teak.sdk.Preferences.FirstRun";

    @SuppressLint("ObsoleteSdkInt")
    TeakInstance(@NonNull Activity activity, @NonNull IObjectFactory objectFactory) {
        //noinspection all -- Disable warning on the null check
        if (activity == null) {
            throw new InvalidParameterException("null Activity passed to Teak.onCreate");
        }

        this.activityHashCode = activity.hashCode();
        this.objectFactory = objectFactory;

        final Context context = activity.getApplicationContext();

        // Ravens
        TeakConfiguration.addEventListener(new TeakConfiguration.EventListener() {
            @Override
            public void onConfigurationReady(@NonNull TeakConfiguration configuration) {
                sdkRaven = new Raven(context, "sdk", configuration);
                appRaven = new Raven(context, configuration.appConfiguration.bundleId, configuration);
            }
        });

        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                    RemoteConfiguration remoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;

                    if (remoteConfiguration.sdkSentryDsn != null && sdkRaven != null) {
                        sdkRaven.setDsn(remoteConfiguration.sdkSentryDsn);
                    }

                    if (remoteConfiguration.appSentryDsn != null && appRaven != null) {
                        appRaven.setDsn(remoteConfiguration.appSentryDsn);
                        if (!android.os.Debug.isDebuggerConnected()) {
                            appRaven.setAsUncaughtExceptionHandler();
                        }
                    }
                }
            }
        });

        // Get Teak Configuration ready
        if (!TeakConfiguration.initialize(context, this.objectFactory)) {
            this.setState(State.Disabled);
            return;
        }

        // Check the launch mode of the activity for debugging purposes
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
            this.setState(State.Disabled);
        } else {
            try {
                Application application = activity.getApplication();
                application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
            } catch (Exception e) {
                Teak.log.exception(e);
                this.setState(State.Disabled);
            }
        }
    }

    private void cleanup(Activity activity) {
        if (this.appStore != null) {
            this.appStore.dispose();
            this.appStore = null;
        }

        if (this.facebookAccessTokenBroadcast != null) {
            this.facebookAccessTokenBroadcast.unregister(activity.getApplicationContext());
        }

        activity.getApplication().unregisterActivityLifecycleCallbacks(this.lifecycleCallbacks);
    }

    ///// identifyUser

    void identifyUser(String userIdentifier) {
        if (userIdentifier == null || userIdentifier.isEmpty()) {
            Teak.log.e("identify_user.error", "User identifier can not be null or empty.");
            return;
        }

        Teak.log.i("identify_user", Helpers.mm.h("userId", userIdentifier));

        if (this.isEnabled()) {
            TeakEvent.postEvent(new UserIdEvent(userIdentifier));
        }
    }

    ///// trackEvent

    void trackEvent(final String actionId, final String objectTypeId, final String objectInstanceId) {
        if (actionId == null || actionId.isEmpty()) {
            Teak.log.e("track_event.error", "actionId can not be null or empty for trackEvent(), ignoring.");
            return;
        }

        if ((objectInstanceId != null && !objectInstanceId.isEmpty()) &&
                (objectTypeId == null || objectTypeId.isEmpty())) {
            Teak.log.e("track_event.error", "objectTypeId can not be null or empty if objectInstanceId is present for trackEvent(), ignoring.");
            return;
        }

        Teak.log.i("track_event", Helpers.mm.h("actionId", actionId, "objectTypeId", objectTypeId, "objectInstanceId", objectInstanceId));

        if (this.isEnabled()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action_type", actionId);
            payload.put("object_type", objectTypeId);
            payload.put("object_instance_id", objectInstanceId);
            TeakEvent.postEvent(new TrackEventEvent(payload));
        }
    }

    ///// Exception Handling

    Raven sdkRaven;
    private Raven appRaven;

    ///// Broadcast Receiver

    private static final String GCM_RECEIVE_INTENT_ACTION = "com.google.android.c2dm.intent.RECEIVE";
    private static final String GCM_REGISTRATION_INTENT_ACTION = "com.google.android.c2dm.intent.REGISTRATION";

    void onReceive(Context inContext, Intent intent) {
        final Context context = inContext.getApplicationContext();

        if (!this.isEnabled()) {
            return;
        }

        String action = intent.getAction();
        if (GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, context, intent));
        } else if (GCM_REGISTRATION_INTENT_ACTION.equals(action)) {
            final String registrationId = intent.getStringExtra("registration_id");
            TeakEvent.postEvent(new PushRegistrationEvent("gcm_push_key", registrationId));
        } else if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX)) {
            TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Interaction, context, intent));
        } else if (action.endsWith(TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX)) {
            TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Cleared, context, intent));
        }
    }

    ///// State Machine

    private enum State {
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

    private State state = State.Allocated;
    private final Object stateMutex = new Object();

    boolean isEnabled() {
        synchronized (this.stateMutex) {
            return (this.state != State.Disabled);
        }
    }

    private boolean setState(@NonNull State newState) {
        synchronized (this.stateMutex) {
            if (this.state == newState) {
                Teak.log.i("teak.state_duplicate", String.format("Teak State transition to same state (%s). Ignoring.", this.state));
                return false;
            }

            if (!this.state.canTransitionTo(newState)) {
                Teak.log.e("teak.state_invalid", String.format("Invalid Teak State transition (%s -> %s). Ignoring.", this.state, newState));
                return false;
            }

            Teak.log.i("teak.state", Helpers.mm.h("old_state", this.state.name, "state", newState.name));

            this.state = newState;

            return true;
        }
    }

    ///// Purchase

    private IStore appStore;

    void purchaseSucceeded(JSONObject originalJson) {
        if (this.appStore != null) {
            this.appStore.processPurchaseJson(originalJson);
        } else {
            Teak.log.e("purchase.succeeded.error", "Unable to process purchaseSucceeded, no active app store.");
        }
    }

    void purchaseFailed(int errorCode) {
        TeakEvent.postEvent(new PurchaseFailedEvent(errorCode));
    }

    void checkActivityResultForPurchase(int resultCode, Intent data) {
        if (this.isEnabled()) {
            if (this.appStore != null) {
                this.appStore.checkActivityResultForPurchase(resultCode, data);
            } else {
                Teak.log.e("purchase.failed.error", "Unable to checkActivityResultForPurchase, no active app store.");
            }
        }
    }

    ///// Activity Lifecycle

    private final int activityHashCode;
    private FacebookAccessTokenBroadcast facebookAccessTokenBroadcast;

    // Needs to be package-accessable for Adobe Air Application workaround
    @SuppressWarnings("WeakerAccess")
    final Application.ActivityLifecycleCallbacks lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
            if (activity.hashCode() == activityHashCode && setState(State.Created)) {
                Teak.log.i("lifecycle", Helpers.mm.h("callback", "onActivityCreated"));

                final Context context = activity.getApplicationContext();

                // Create IStore
                appStore = objectFactory.getIStore();
                if (appStore != null) {
                    appStore.init(context);
                }

                // Facebook Access Token Broadcaster
                facebookAccessTokenBroadcast = new FacebookAccessTokenBroadcast(context);

                Intent intent = activity.getIntent();
                if (intent == null) {
                    intent = new Intent();
                }

                if (!TeakEvent.postEvent(new LifecycleEvent(LifecycleEvent.Created, intent))) {
                    cleanup(activity);
                    setState(State.Disabled);
                } else {
                    // Set up the /teak_internal/* routes
                    registerTeakInternalDeepLinks();
                }
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (activity.hashCode() == activityHashCode && setState(State.Active)) {
                Teak.log.i("lifecycle", Helpers.mm.h("callback", "onActivityResumed"));
                Intent intent = activity.getIntent();
                if (intent == null) {
                    intent = new Intent();
                }

                // Check and see if this is (probably) the first time this app has been ever launched
                boolean isFirstLaunch = false;
                SharedPreferences preferences;
                try {
                    preferences = activity.getSharedPreferences(Teak.PREFERENCES_FILE, Context.MODE_PRIVATE);
                    if (preferences != null) {
                        long firstLaunch = preferences.getLong(PREFERENCE_FIRST_RUN, 0);
                        if (firstLaunch == 0) {
                            firstLaunch = new Date().getTime() / 1000;
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putLong(PREFERENCE_FIRST_RUN, firstLaunch);
                            editor.apply();
                            isFirstLaunch = true;
                        }
                    }
                } catch (Exception ignored) {
                }
                intent.putExtra("teakIsFirstLaunch", isFirstLaunch);

                TeakEvent.postEvent(new LifecycleEvent(LifecycleEvent.Resumed, intent));
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (activity.hashCode() == activityHashCode && setState(State.Paused)) {
                Teak.log.i("lifecycle", Helpers.mm.h("callback", "onActivityPaused"));
                Intent intent = activity.getIntent();
                if (intent == null) {
                    intent = new Intent();
                }
                TeakEvent.postEvent(new LifecycleEvent(LifecycleEvent.Paused, intent));
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (activity.hashCode() == activityHashCode && setState(State.Destroyed)) {
                Teak.log.i("lifecycle", Helpers.mm.h("callback", "onActivityDestroyed"));
            }
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        }
    };

    ///// Built-in deep links

    private void registerTeakInternalDeepLinks() {
        Teak.registerDeepLink("/teak_internal/store/:sku", "", "", new Teak.DeepLink() {
            @Override
            public void call(Map<String, Object> params) {
                if (appStore != null) {
                    appStore.launchPurchaseFlowForSKU((String) params.get("sku"));
                }
            }
        });
    }
}
