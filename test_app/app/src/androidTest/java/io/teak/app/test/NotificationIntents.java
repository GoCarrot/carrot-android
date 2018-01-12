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

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.json.JSONObject;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class NotificationIntents extends TeakIntegrationTest {
    public NotificationIntents() {
        super(true);
    }

    @Test
    public void clearIntent() {
        launchActivity();

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiDevice device = UiDevice.getInstance(instrumentation);

        // Background the app, it should now be paused
        backgroundApp();
        verify(eventListener, timeout(500).times(1)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Paused);

        // Open notification tray
        device.openNotification();

        // Simulate notification
        simulateNotification(getActivity());

        // Wait for notification
        device.wait(Until.hasObject(By.text("teak_notif_no_title")), 5000);
        final UiObject2 title = device.findObject(By.text("teak_notif_no_title"));
        assertNotNull(title);

        // Clear all notifications
        device.wait(Until.hasObject(By.text("CLEAR ALL")), 5000); // TODO: Make sure text is consistent on versions
        UiObject2 clearAll = device.findObject(By.text("CLEAR ALL"));
        assertNotNull(clearAll);
        clearAll.click();

        // Clear Notification Event should have occurred
        verify(eventListener, timeout(5000).times(1)).eventRecieved(PushNotificationEvent.class, PushNotificationEvent.Cleared);

        // Make sure the notification got cleared
        final UiObject2 titleAgain = device.findObject(By.text("teak_notif_no_title"));
        assertNull(titleAgain);
    }

    private void simulateNotification(Context context) {
        Intent intent = new Intent();
        intent.putExtra("teakNotifId", "fake-notif-id");
        intent.putExtra("version", "1");
        intent.putExtra("message", "Teak");

        // UI template
        JSONObject teak_notif_no_title = new JSONObject();
        try {
            teak_notif_no_title.put("text", "teak_notif_no_title");
            teak_notif_no_title.put("left_image", "BUILTIN_APP_ICON");
        } catch (Exception ignored) {
        }

        // Display
        JSONObject display = new JSONObject();
        try {
            display.put("contentView", "teak_notif_no_title");
            display.put("teak_notif_no_title", teak_notif_no_title);
        } catch (Exception ignored) {
        }

        // Add display to intent
        intent.putExtra("display", display.toString());

        TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, context, intent));
    }
}
