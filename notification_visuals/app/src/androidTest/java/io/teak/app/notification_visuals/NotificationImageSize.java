package io.teak.app.notification_visuals;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;


import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.LifecycleEvent;

import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.UserIdEvent;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;


@RunWith(AndroidJUnit4.class)
public class NotificationImageSize {
    @Rule
    public ActivityTestRule<MainActivity> testRule = new ActivityTestRule<MainActivity>(MainActivity.class, false, false) {
        @Override
        protected void beforeActivityLaunched() {
            super.beforeActivityLaunched();

            // Reset things other tests could be mucking up
            Teak.Instance = null;
        }
    };

    @Rule
    public GrantPermissionRule writeExternalStoragePermission = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Rule
    public GrantPermissionRule readExternalStoragePermission = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE);

    @Test
    public void sendNotificationAndGetScreenshot() {
        // Event Listener
        TestTeakEventListener eventListener = spy(TestTeakEventListener.class);
        TeakEvent.addEventListener(eventListener);

        // Init
        Activity activity = testRule.launchActivity(null);

        final String creativeId = "test_deeplink";
        final String searchText = "Teak";
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        // Identify user
        String userId = String.format(Locale.US, "notification-%s-%s", creativeId, "test");
        TestTeakEventListener listener = spy(TestTeakEventListener.class);
        TeakEvent.addEventListener(listener);
        Teak.identifyUser(userId);
        verify(listener, timeout(5000).times(1)).eventRecieved(UserIdEvent.class, UserIdEvent.Type);

        // Background app
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        activity.startActivity(i);
        verify(eventListener, timeout(5000).times(1)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Paused);

        // Open notification tray and clear existing notifications
        UiDevice device = UiDevice.getInstance(instrumentation);
        device.openNotification();
        device.wait(Until.hasObject(By.text("CLEAR ALL")), 5000); // TODO: Make sure text is consistent on versions
        UiObject2 clearAll = device.findObject(By.text("CLEAR ALL"));
        assertNotNull(clearAll);
        clearAll.click();

        // Simulate notification
        simulateNotification(activity);

        // Wait for notification
        device.wait(Until.hasObject(By.text("teak_notif_no_title")), 5000);
        UiObject2 title = device.findObject(By.text("teak_notif_no_title"));
        if (title == null) {
            title = device.findObject(By.text("teak_big_notif_image_text"));
        } else {
            // TODO: Try and expand it?
            //title.getParent(/* LinearLayout */).getParent(/* RelativeLayout */).getParent(/* ? */).swipe(Direction.DOWN, 0.5f);
        }
        assertNotNull(title);

        // Screenshot
        String path = "/sdcard/test-screenshots";
        File file = new File(path);
        if (!file.exists()) {
            assertTrue(file.mkdirs());
        }
        file = new File(file, "teak-" + creativeId + ".png");
        boolean success = UiDevice.getInstance(instrumentation).takeScreenshot(file);
        assertTrue(success);
        android.util.Log.i("Teak.NotificationVisuals", "Wrote screenshot to file: " + file.toString());
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
            teak_notif_no_title.put("notification_background", "assets:///pixelgrid_2000x2000.png");
            //teak_notif_no_title.put("notification_background", "https://s3.amazonaws.com/carrot-images/creative_translations-media/45333/original-base64Default.txt?1510768708");
            teak_notif_no_title.put("left_image", "BUILTIN_APP_ICON");
            //teak_notif_no_title.put("left_image", "NONE");
        } catch (Exception ignored) {
        }

        JSONObject teak_big_notif_image_text = new JSONObject();
        try {
            teak_big_notif_image_text.put("text", "teak_big_notif_image_text");
            teak_big_notif_image_text.put("notification_background", "assets:///pixelgrid_2000x2000.png");
        } catch (Exception ignored) {
        }

        // Display
        JSONObject display = new JSONObject();
        try {
            display.put("contentView", "teak_notif_no_title");
            display.put("bigContentView", "teak_big_notif_image_text");
            display.put("teak_notif_no_title", teak_notif_no_title);
            display.put("teak_big_notif_image_text", teak_big_notif_image_text);
        } catch (Exception ignored) {
        }

        // Add display to intent
        intent.putExtra("display", display.toString());

        TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, context, intent));
    }

    @SuppressWarnings("WeakerAccess") // Must be public in order to mock
    public abstract class TestTeakEventListener implements TeakEvent.EventListener {
        @Override
        public void onNewEvent(@NonNull TeakEvent event) {
            eventRecieved(event.getClass(), event.eventType);
        }

        public abstract void eventRecieved(Class eventClass, String eventType);
    }
}
