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

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.json.JSONArray;
import io.teak.sdk.json.JSONObject;

public class PushState {
    private static final String PUSH_STATE_CHAIN_KEY = "io.teak.sdk.Preferences.PushStateChain";

    public enum State {
        Unknown("Unknown"),
        Authorized("Authorized"),
        Denied("Denied");

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
        public final State state;
        public final Date date;

        StateChainEntry(State state) {
            this.state = state;
            this.date = new Date();
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
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("state", this.state);
            json.put("date", this.date.getTime() / 1000L); // MS -> Sec
            return json;
        }
    }

    private List<StateChainEntry> stateChain;
    private final NotificationManagerCompat notificationManagerCompat;
    private final ExecutorService executionQueue = Executors.newSingleThreadExecutor();

    public PushState(@NonNull Context context) throws IntegrationChecker.MissingDependencyException {
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

        // Default
        if (this.stateChain == null) {
            this.stateChain = Collections.singletonList(new StateChainEntry(State.Unknown));
            this.writeSerialzedStateChain(context, this.stateChain);
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
            public State call() throws Exception {
                final State newState = PushState.this.areNotificationsEnabled() ? State.Authorized : State.Denied;
                if (PushState.this.getCurrentStateFromChain().canTransitionTo(newState)) {
                    List<StateChainEntry> newChain = new ArrayList<>(PushState.this.stateChain);
                    newChain.add(new StateChainEntry(newState));
                    PushState.this.stateChain = Collections.unmodifiableList(newChain);
                    PushState.this.writeSerialzedStateChain(context, PushState.this.stateChain);
                    return newState;
                }
                return PushState.this.getCurrentStateFromChain();
            }
        });
    }

    private State getCurrentStateFromChain() {
        StateChainEntry currentEntry = PushState.this.stateChain.get(PushState.this.stateChain.size() - 1);
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
}
