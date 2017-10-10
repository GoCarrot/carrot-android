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


import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.teak.sdk.TeakNotification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class OSListenerIntegrationTests extends TeakIntegrationTests {
    @Test
    public void basicLifecycle() {
        launchActivity();
    }

    @Test
    @SdkSuppress(minSdkVersion = 18)
    public void integratedLifecycle() {
        launchActivity();

        // Reference:
        // http://alexzh.com/tutorials/android-testing-ui-automator-part-4/

        // Background the app, it should now be paused
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
        verify(osListener, times(1)).lifecycle_onActivityPaused(getActivity());

        // Open up the apps list
        UiObject2 allApps = device.findObject(By.text("Apps"));
        allApps.click();

        // Re-open the test app
        device.wait(Until.hasObject(By.desc("Teak SDK Tests")), 3000);
        UiObject2 testAppIcon = device.findObject(By.desc("Teak SDK Tests"));
        testAppIcon.click();

        // Should be resumed
        verify(osListener, timeout(500).times(2)).lifecycle_onActivityResumed(getActivity());
    }

    @Test(timeout = 10000)
    @SdkSuppress(minSdkVersion = 21)
    public void notificationCleared() {
        launchActivity();

        // Make sure app is fully active
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wait(Until.hasObject(By.text("Hello World")), 1000);

        // Background the app
        device.pressHome();

        // Send a notification cleared broadcast
        String event = getActivity().getPackageName() + TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX;
        sendBroadcast(event, null);

        // Should have been called
        verify(osListener, timeout(3000).times(1)).notification_onNotificationCleared(any(Context.class), any(Intent.class));
    }
}
