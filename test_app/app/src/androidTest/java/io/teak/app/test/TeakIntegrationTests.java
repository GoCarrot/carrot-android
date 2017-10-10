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

package io.teak.app.test;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.rule.ActivityTestRule;

import org.junit.Rule;

import java.util.Map;

import io.teak.sdk.ObjectFactory;
import io.teak.sdk.Teak2;
import io.teak.sdk.event.OSListener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeakIntegrationTests {
    OSListener osListener;

    @Rule
    public ActivityTestRule<MainActivity> testRule = new ActivityTestRule<MainActivity>(MainActivity.class, false, false) {
        @Override
        protected void beforeActivityLaunched() {
            super.beforeActivityLaunched();

            Teak2.Instance = null;

            osListener = mock(io.teak.sdk.event.OSListener.class);
            when(osListener.lifecycle_onActivityCreated(any(Activity.class))).thenReturn(true);

            MainActivity.whateverFactory = new ObjectFactory() {
                @NonNull
                @Override
                public io.teak.sdk.event.OSListener getOSListener() {
                    return osListener;
                }
            };
        }

        @Override
        protected void afterActivityLaunched() {
            super.afterActivityLaunched();
            verify(osListener, times(1)).lifecycle_onActivityCreated(getActivity());
            verify(osListener, times(1)).lifecycle_onActivityResumed(getActivity());
        }

        @Override
        protected void afterActivityFinished() {
            super.afterActivityFinished();
            verify(osListener, timeout(500).atLeastOnce()).lifecycle_onActivityPaused(getActivity());
        }
    };

    ///// TestRule helpers

    Activity getActivity() {
        return testRule.getActivity();
    }

    Activity launchActivity() {
        return testRule.launchActivity(null);
    }

    Activity launchActivity(@Nullable Intent intent) {
        return testRule.launchActivity(intent);
    }

    ///// BroadcastReceiver test helpers

    @SdkSuppress(minSdkVersion = 21)
    void sendBroadcast(@NonNull String event) {
        sendBroadcast(event, null);
    }

    @SdkSuppress(minSdkVersion = 21)
    void sendBroadcast(@NonNull String event, @Nullable Map<String, Object> extras) {
        String adbString = "am broadcast -a " + event;
        if (extras != null) {
            for (Map.Entry<String, Object> entry : extras.entrySet()) {
                if (entry.getValue().getClass() == String.class) {
                    adbString += " --es \"" + entry.getKey() + "\" \"" + entry.getValue() + "\"";
                } else if (entry.getValue().getClass() == boolean.class) {
                    adbString += " --ez \"" + entry.getKey() + "\" " + (((boolean)entry.getValue()) ? "true" : "false");
                } else if (entry.getValue().getClass() == int.class) {
                    adbString += " --ei \"" + entry.getKey() + "\" " + entry.getValue();
                } else if (entry.getValue().getClass() == long.class) {
                    adbString += " --el \"" + entry.getKey() + "\" " + entry.getValue();
                } else if (entry.getValue().getClass() == float.class) {
                    adbString += " --ef \"" + entry.getKey() + "\" " + entry.getValue();
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(adbString);
    }
}
