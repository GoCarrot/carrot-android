package io.teak.app.test;

import android.app.Instrumentation;
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

import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.json.JSONObject;

import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class NotificationIntents extends TeakIntegrationTest {
    public NotificationIntents() {
        super(true);
    }

    @Test
    @SdkSuppress(minSdkVersion = 16)
    public void clearIntent() {
        launchActivity();

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiDevice device = UiDevice.getInstance(instrumentation);

        // Some devices just don't have a clear all button, so skip those
        final String[] skipDeviceModels = new String[]{
                "BLU Advance 5.0"   // BLU BLU Advance 5.0  Android 5.1     (API 22)
        };
        final String thisDeviceModel = android.os.Build.MODEL;
        assumeThat(thisDeviceModel + " does not have a 'CLEAR' or 'CLEAR ALL' button in the notification tray",
                skipDeviceModels, not(hasItemInArray(thisDeviceModel)));

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
        String[] clearAllResourceIds = new String[] {
                "com.android.systemui:id/dismiss_text",
                "com.android.systemui:id/clear_all",
                "com.android.systemui:id/clear_button"
        };

        UiObject2 clearAll = null;
        for (String resourceId : clearAllResourceIds) {
            clearAll = device.findObject(By.res(resourceId));
            if (clearAll != null) break;
        }

        if (clearAll == null) {
            final String foo = dumpWindowHierarchyToString();
            android.util.Log.d("wtf", foo);
        }
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
