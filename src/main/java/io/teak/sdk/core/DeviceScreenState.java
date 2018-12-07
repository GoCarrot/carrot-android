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

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.view.Display;

import java.util.concurrent.Callable;

public class DeviceScreenState {
    //    public static final String SCREEN_STATE = "DeviceScreenState.SCREEN_STATE";

    public static final String SCREEN_ON = "DeviceScreenState.SCREEN_ON";
    public static final String SCREEN_OFF = "DeviceScreenState.SCREEN_OFF";

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

    public void setState(State newState, Callbacks callbacks) {
        synchronized (this.stateMutex) {
            if (this.state.canTransitionTo(newState)) {
                State oldState = this.state;
                this.state = newState;
                if (callbacks != null) {
                    callbacks.onStateChanged(oldState, newState);
                }
                android.util.Log.i("Teak.Animation", String.format("State %s -> %s", oldState, newState));
            }
        }
    }

    public static Callable<Boolean> isDeviceScreenOn(@NonNull final Context context) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() {
                boolean isScreenOn = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                    if (dm != null && dm.getDisplays() != null) {
                        for (Display display : dm.getDisplays()) {
                            if (display.getState() == Display.STATE_ON || display.getState() == Display.STATE_UNKNOWN) {
                                isScreenOn = true;
                            }
                        }
                    }
                } else {
                    final PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    @SuppressWarnings("deprecation")
                    final boolean suppressDeprecation = powerManager != null && powerManager.isScreenOn();
                    isScreenOn = suppressDeprecation;
                }

                // Broadcast to DefaultAndroidNotification
                final Intent intent = isScreenOn ? new Intent(DeviceScreenState.SCREEN_ON) : new Intent(DeviceScreenState.SCREEN_OFF);
                context.sendBroadcast(intent);

                // Always return false, and get re-scheduled
                return false;
            }
        };
    }
}
