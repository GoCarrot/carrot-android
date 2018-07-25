/* Teak -- Copyright (C) 2018 GoCarrot Inc.
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
package io.teak.sdk.push;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.NotificationBuilder;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.json.JSONArray;
import io.teak.sdk.json.JSONObject;

public class PushState {
    private static final String PUSH_STATE_CHAIN_KEY = "io.teak.sdk.Preferences.PushStateChain";

    public enum State {
        Unknown("unknown"),
        Authorized("authorized"),
        Denied("denied");

        //public static final Integer length = 1 + Denied.ordinal();

        private static final State[][] allowedTransitions = {
            {State.Authorized, State.Denied},
            {State.Denied},
            {State.Authorized}};

        public final String name;

        State(String name) {
            this.name = name;
        }

        public boolean canTransitionTo(State nextState) {
            for (State allowedTransition : allowedTransitions[this.ordinal()]) {
                if (nextState.equals(allowedTransition)) return true;
            }
            return false;
        }
    }

    private class StateChainEntry {
        final State state;
        final Date date;
        final boolean canBypassDnd;
        final boolean canShowOnLockscreen;
        final boolean canShowBadge;

        StateChainEntry(State state, boolean canBypassDnd, boolean canShowOnLockscreen, boolean canShowBadge) {
            this.state = state;
            this.date = new Date();
            this.canBypassDnd = canBypassDnd;
            this.canShowOnLockscreen = canShowOnLockscreen;
            this.canShowBadge = canShowBadge;
        }

        StateChainEntry(JSONObject json) {
            String stateName = json.getString("state");
            if (stateName.equals(State.Authorized.name)) {
                this.state = State.Authorized;
            } else if (stateName.equals(State.Denied.name)) {
                this.state = State.Denied;
            } else {
                this.state = State.Unknown;
            }

            long timestamp = json.getLong("date");
            this.date = new Date(timestamp * 1000L); // Sec -> MS

            this.canBypassDnd = json.getBoolean("canBypassDnd");
            this.canShowOnLockscreen = json.getBoolean("canShowOnLockscreen");
            this.canShowBadge = json.getBoolean("canShowBadge");
        }

        Map<String, Object> toMap() {
            HashMap<String, Object> ret = new HashMap<>();
            ret.put("state", this.state.name);
            ret.put("date", this.date.getTime() / 1000L); // MS -> Sec
            ret.put("canBypassDnd", this.canBypassDnd);
            ret.put("canShowOnLockscreen", this.canShowOnLockscreen);
            ret.put("canShowBadge", this.canShowBadge);
            return ret;
        }

        JSONObject toJson() {
            return new JSONObject(this.toMap());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof StateChainEntry)) {
                return super.equals(obj);
            }

            StateChainEntry other = (StateChainEntry) obj;
            return this.equalsIgnoreDate(other) && this.date.equals(other.date);
        }

        public boolean equalsIgnoreDate(StateChainEntry other) {
            return this.state.equals(other.state) &&
                    this.canBypassDnd == other.canBypassDnd &&
                    this.canShowOnLockscreen == other.canShowOnLockscreen &&
                    this.canShowBadge == other.canShowBadge;
        }
    }

    private List<StateChainEntry> stateChain = new ArrayList<>();
    private final NotificationManagerCompat notificationManagerCompat;
    private final ExecutorService executionQueue = Executors.newSingleThreadExecutor();

    private static PushState Instance;
    public static void init(@NonNull Context context) throws IntegrationChecker.MissingDependencyException {
        if (Instance == null) {
            Instance = new PushState(context);
        }
    }

    public static PushState get() {
        return Instance;
    }

    private PushState(@NonNull Context context) throws IntegrationChecker.MissingDependencyException {
        IntegrationChecker.requireDependency("android.support.v4.app.NotificationManagerCompat");
        this.notificationManagerCompat = NotificationManagerCompat.from(context);

        // Try and load serialized state chain
        final SharedPreferences preferences = context.getSharedPreferences(Teak.PREFERENCES_FILE, Context.MODE_PRIVATE);
        final String pushStateChainJson = preferences.getString(PUSH_STATE_CHAIN_KEY, null);
        try {
            if (pushStateChainJson != null) {
                List<StateChainEntry> tempStateChain = new ArrayList<>();
                JSONArray stateChainJsonArray = new JSONArray(pushStateChainJson);
                for (Object jsonEntry : stateChainJsonArray) {
                    tempStateChain.add(new StateChainEntry((JSONObject) jsonEntry));
                }
                this.stateChain = Collections.unmodifiableList(tempStateChain);
            }
        } catch (Exception ignored) {
        }

        // Event listener - When onResume is called, update the state chain
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                switch (event.eventType) {
                    case LifecycleEvent.Resumed: {
                        LifecycleEvent lifecycleEvent = (LifecycleEvent) event;
                        PushState.this.updateStateChain(lifecycleEvent.context);
                    } break;
                }
            }
        });
    }

    private Future<State> updateStateChain(@NonNull final Context context) {
        return this.executionQueue.submit(new Callable<State>() {
            @Override
            public State call() {
                final State currentState = PushState.this.getCurrentStateFromChain();
                final StateChainEntry currentEntry = PushState.this.stateChain.size() == 0 ? null : PushState.this.stateChain.get(PushState.this.stateChain.size() - 1);
                final StateChainEntry newStateEntry = PushState.this.determineStateFromSystem(context);
                if (currentState.canTransitionTo(newStateEntry.state) ||
                        (currentState.equals(newStateEntry.state) && !newStateEntry.equalsIgnoreDate(currentEntry))) {
                    List<StateChainEntry> newChain = new ArrayList<>(PushState.this.stateChain);
                    newChain.add(newStateEntry);
                    PushState.this.stateChain = Collections.unmodifiableList(newChain);
                    PushState.this.writeSerialzedStateChain(context, PushState.this.stateChain);
                    return newStateEntry.state;
                }
                return PushState.this.getCurrentStateFromChain();
            }
        });
    }

    private State getCurrentStateFromChain() {
        if (this.stateChain.size() == 0) return State.Unknown;

        final StateChainEntry currentEntry = this.stateChain.get(PushState.this.stateChain.size() - 1);
        return currentEntry.state;
    }

    private Future<Boolean> writeSerialzedStateChain(@NonNull final Context context, List<StateChainEntry> stateChain) {
        final JSONArray jsonStateChain = new JSONArray();
        for (StateChainEntry entry : stateChain) {
            jsonStateChain.put(entry.toJson());
        }

        return this.executionQueue.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                synchronized (Teak.PREFERENCES_FILE) {
                    SharedPreferences preferences = context.getSharedPreferences(Teak.PREFERENCES_FILE, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(PUSH_STATE_CHAIN_KEY, jsonStateChain.toString());
                    return editor.commit();
                }
            }
        });
    }

    public boolean areNotificationsEnabled() {
        boolean ret = true;
        boolean notificationManagerCompatHas_areNotificationsEnabled = false;
        try {
            if (NotificationManagerCompat.class.getMethod("areNotificationsEnabled") != null) {
                notificationManagerCompatHas_areNotificationsEnabled = true;
            }
        } catch (Exception ignored) {
        }

        if (notificationManagerCompatHas_areNotificationsEnabled && this.notificationManagerCompat != null) {
            try {
                ret = this.notificationManagerCompat.areNotificationsEnabled();
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }
        return ret;
    }

    private StateChainEntry determineStateFromSystem(@NonNull Context context) {
        final State newState = this.areNotificationsEnabled() ? State.Authorized : State.Denied;
        boolean canBypassDnd = false;
        boolean canShowOnLockscreen = true;
        boolean canShowBadge = true;

        final String notificationChannelId = NotificationBuilder.getNotificationChannelId(context);
        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationChannelId != null && notificationManager != null) {
            final NotificationChannel channel = notificationManager.getNotificationChannel(notificationChannelId);
            canBypassDnd = channel.canBypassDnd();
            canShowOnLockscreen = (channel.getLockscreenVisibility() != Notification.VISIBILITY_SECRET);
            canShowBadge = channel.canShowBadge();
        }
        return new StateChainEntry(newState, canBypassDnd, canShowOnLockscreen, canShowBadge);
    }

    public Map<String, Object> toMap() {
        List<StateChainEntry> currentStateChain = this.stateChain;
        List<Map<String, Object>> genericStateChain = new ArrayList<>();
        for (StateChainEntry entry : currentStateChain) {
            genericStateChain.add(entry.toMap());
        }

        HashMap<String, Object> ret = new HashMap<>();
        ret.put("push_state_chain", genericStateChain);
        return ret;
    }
}
