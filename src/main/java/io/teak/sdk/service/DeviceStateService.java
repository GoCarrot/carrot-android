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

import io.teak.sdk.core.DeviceScreenState;

public class DeviceStateService extends Service {
    public static final String SCREEN_STATE = "DeviceStateService.SCREEN_STATE";

    public static final String SCREEN_ON = "DeviceStateService.SCREEN_ON";
    public static final String SCREEN_OFF = "DeviceStateService.SCREEN_OFF";

    private final DeviceScreenState deviceScreenState = new DeviceScreenState();

    private BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                final Intent stateIntent = new Intent(context, DeviceStateService.class);
                stateIntent.setAction(SCREEN_STATE);
                stateIntent.putExtra("state", DeviceScreenState.State.ScreenOn.toString());
                context.startService(stateIntent);
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                final Intent stateIntent = new Intent(context, DeviceStateService.class);
                stateIntent.setAction(SCREEN_STATE);
                stateIntent.putExtra("state", DeviceScreenState.State.ScreenOff.toString());
                context.startService(stateIntent);
            }
        }
    };

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
            final PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
            @SuppressWarnings("deprecation")
            final boolean isScreenOn = powerManager != null && powerManager.isScreenOn();
            return isScreenOn;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction() != null) {
                android.util.Log.i("Teak.Animation", "Start: " + intent.getAction());
            }

            if (SCREEN_STATE.equals(intent.getAction())) {
                DeviceScreenState.State screenState = DeviceScreenState.State.ScreenOff;
                if (DeviceScreenState.State.ScreenOn.toString().equals(intent.getStringExtra("state"))) {
                    screenState = DeviceScreenState.State.ScreenOn;
                }

                deviceScreenState.setState(screenState, new DeviceScreenState.Callbacks() {
                    @Override
                    public void onStateChanged(DeviceScreenState.State oldState, DeviceScreenState.State newState) {
                        Intent intent = newState == DeviceScreenState.State.ScreenOn ? new Intent(DeviceStateService.SCREEN_ON) : new Intent(DeviceStateService.SCREEN_OFF);
                        DeviceStateService.this.sendBroadcast(intent);
                    }
                });
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, screenStateFilter);

        android.util.Log.i("Teak.Animation", "Service created");

        final DeviceScreenState.State screenState = isScreenOn(this) ? DeviceScreenState.State.ScreenOn : DeviceScreenState.State.ScreenOff;
        this.deviceScreenState.setState(screenState, new DeviceScreenState.Callbacks() {
            @Override
            public void onStateChanged(DeviceScreenState.State oldState, DeviceScreenState.State newState) {
                Intent intent = newState == DeviceScreenState.State.ScreenOn ? new Intent(DeviceStateService.SCREEN_ON) : new Intent(DeviceStateService.SCREEN_OFF);
                DeviceStateService.this.sendBroadcast(intent);
            }
        });
    }

    @Override
    public void onDestroy() {
        this.deviceScreenState.setState(DeviceScreenState.State.Unknown, null);
        unregisterReceiver(screenStateReceiver);

        android.util.Log.i("Teak.Animation", "Service stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
