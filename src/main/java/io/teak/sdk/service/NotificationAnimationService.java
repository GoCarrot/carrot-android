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
package io.teak.sdk.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.view.Display;

public class NotificationAnimationService extends Service {
    public static final String START_ANIMATING = "NotificationAnimationService.START_ANIMATING";
    public static final String STOP_ANIMATING = "NotificationAnimationService.STOP_ANIMATING";

    private BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                setState(State.ScreenOn);
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                setState(State.ScreenOff);
            } else {
                android.util.Log.i("Teak.Animation", intent.getAction());
            }
        }
    };

    public enum State {
        Unknown("Unknown"),
        ScreenOn("ScreenOn"),
        ScreenOff("ScreenOff");

        //public static final Integer length = 1 + Expired.ordinal();

        private static final State[][] allowedTransitions = {
                {State.ScreenOn, State.ScreenOff},
                {State.ScreenOff},
                {State.ScreenOn}
        };

        public final String name;
        public final int ordinal;

        State(String name) {
            this.name = name;
            this.ordinal = this.ordinal();
        }

        public boolean canTransitionTo(State nextState) {
            for (State allowedTransition : allowedTransitions[this.ordinal()]) {
                if (nextState == allowedTransition) return true;
            }
            return false;
        }
    }

    private State state = State.Unknown;
    private final Object stateMutex = new Object();

    private void setState(State newState) {
        synchronized (this.stateMutex) {
            if (this.state.canTransitionTo(newState)) {
                android.util.Log.i("Teak.Animation", this.state.toString() + " -> " + newState.toString());
                this.state = newState;

                Intent intent = this.state == State.ScreenOn ?
                        new Intent(NotificationAnimationService.START_ANIMATING) :
                        new Intent(NotificationAnimationService.STOP_ANIMATING);
                this.sendBroadcast(intent);
            } else {
                android.util.Log.e("Teak.Animation", this.state.toString() + " xx " + newState.toString());
            }
        }
    }

    public static boolean isScreenOn(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null && dm.getDisplays() != null) {
                for (Display display : dm.getDisplays()) {
                    if (display.getState() == Display.STATE_ON || display.getState() == Display.STATE_UNKNOWN) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
            return powerManager != null && powerManager.isScreenOn();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.i("Teak.Animation", "Service started.");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, screenStateFilter);

        if (isScreenOn(this)) {
            setState(State.ScreenOn);
        } else {
            setState(State.ScreenOff);
        }
    }

    @Override
    public void onDestroy() {
        this.sendBroadcast(new Intent(NotificationAnimationService.STOP_ANIMATING));
        unregisterReceiver(screenStateReceiver);

        android.util.Log.i("Teak.Animation", "Service stopped.");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
