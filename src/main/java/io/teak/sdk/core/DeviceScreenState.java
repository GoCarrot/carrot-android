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
package io.teak.sdk.core;

import android.support.annotation.NonNull;

public class DeviceScreenState {
    public enum State {
        Unknown("Unknown"),
        ScreenOn("ScreenOn"),
        ScreenOff("ScreenOff");

        //public static final Integer length = 1 + Expired.ordinal();

        private static final State[][] allowedTransitions = {
                {State.ScreenOn, State.ScreenOff},
                {State.ScreenOff},
                {State.ScreenOn}};

        public final String name;
        public final int ordinal;

        State(String name) {
            this.name = name;
            this.ordinal = this.ordinal();
        }

        public boolean canTransitionTo(State nextState) {
            for (State allowedTransition : allowedTransitions[this.ordinal()]) {
                if (nextState.equals(allowedTransition)) return true;
            }
            return false;
        }
    }

    public interface Callbacks {
        void onStateChanged(State oldState, State newState);
    }

    private State state = State.Unknown;
    private final Object stateMutex = new Object();
    private final Callbacks callbacks;

    public DeviceScreenState(@NonNull Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void setState(State newState) {
        synchronized (this.stateMutex) {
            if (this.state.canTransitionTo(newState)) {
                State oldState = this.state;
                this.state = newState;
                this.callbacks.onStateChanged(oldState, newState);
                android.util.Log.i("Teak.Animation", String.format("State %s -> %s", oldState, newState));
            }
        }
    }
}
