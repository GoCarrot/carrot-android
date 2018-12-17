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
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Display;

import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.Trigger;

import java.util.Date;
import java.util.concurrent.Callable;

import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.io.IAndroidNotification;
import io.teak.sdk.service.JobService;

public class DeviceScreenState {
    //    public static final String SCREEN_STATE = "DeviceScreenState.SCREEN_STATE";

    public static final String SCREEN_STATE_JOB_TAG = "DeviceScreenState.Job";
    public static final String DELAY_INDEX_KEY = "DeviceScreenState.DelayIndex";
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
                Teak.log.i("teak.animation", Helpers.mm.h("old_state", oldState, "new_state", newState));
            }
        }
    }

    private static final int[][] ExecutionWindow = new int[][] {
        {15, 16},
        {5, 10},  // Not an ordering error, this is to prevent the device re-checking if the screen
        {10, 20}, // gets turned on from re-issuing the notification in the case of a state change to OFF
        {20, 30},
        {30, 50},
        {50, 75},
        {75, 150},
        {150, 300},
        {600, 900}};

    public static void scheduleScreenStateJob(@Nullable JobParameters jobParameters) {
        final Bundle bundle = new Bundle();
        bundle.putString(JobService.JOB_TYPE_KEY, DeviceScreenState.SCREEN_STATE_JOB_TAG);

        int delayIndex = 0;
        if (jobParameters != null && jobParameters.getExtras() != null) {
            final Bundle extras = jobParameters.getExtras();

            // Do not schedule if there is no remaining
            if (extras.containsKey(IAndroidNotification.ANIMATED_NOTIFICATION_COUNT_KEY) &&
                extras.getInt(IAndroidNotification.ANIMATED_NOTIFICATION_COUNT_KEY) < 1) {
                return;
            }
            delayIndex = extras.getInt(DeviceScreenState.DELAY_INDEX_KEY, -1) + 1;
        }
        delayIndex = Math.max(0, Math.min(delayIndex, ExecutionWindow.length - 1));
        bundle.putInt(DeviceScreenState.DELAY_INDEX_KEY, delayIndex);

        Teak.log.i("teak.animation", Helpers.mm.h("delay_index", delayIndex, "timestamp", new Date().getTime() / 1000));

        int[] executionWindow = DeviceScreenState.ExecutionWindow[delayIndex];
        final Job job = Teak.Instance.jobBuilder(DeviceScreenState.SCREEN_STATE_JOB_TAG, bundle)
                            .setTrigger(Trigger.executionWindow(executionWindow[0], executionWindow[1]))
                            .build();
        Teak.Instance.dispatcher.mustSchedule(job);
    }

    public static Callable<Boolean> isDeviceScreenOn(@NonNull final Context context, @NonNull final JobParameters jobParameters) {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() {
                boolean isScreenOn = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    final DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                    if (dm != null && dm.getDisplays() != null) {
                        for (Display display : dm.getDisplays()) {
                            final int displayState = display.getState();
                            isScreenOn |= (displayState == Display.STATE_ON);
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

                // Re-schedule ourselves
                DeviceScreenState.scheduleScreenStateJob(jobParameters);

                // Do not need re-schedule
                return true;
            }
        };
    }
}
