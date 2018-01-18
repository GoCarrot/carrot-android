package io.teak.app.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.TrackEventEvent;
import io.teak.sdk.event.UserIdEvent;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class PublicAPI extends TeakIntegrationTest {
    // Teak#onCreate is already in use by the runner

    @Test
    public void onActivityResult() {
        launchActivity();

        Intent intent = spy(Intent.class);
        Teak.onActivityResult(0, 42, intent);
        verify(store, timeout(5000).times(1)).checkActivityResultForPurchase(42, intent);
    }

    @Test
    public void identifyUser() {
        launchActivity();

        TestTeakEventListener listener = spy(TestTeakEventListener.class);
        TeakEvent.addEventListener(listener);

        String userId = "test user id";
        Teak.identifyUser(userId);

        verify(listener, timeout(5000).times(1)).eventRecieved(UserIdEvent.class, UserIdEvent.Type);
    }

    @Test
    public void trackEvent() {
        launchActivity();

        TestTeakEventListener listener = spy(TestTeakEventListener.class);
        TeakEvent.addEventListener(listener);

        Teak.trackEvent("foo", "bar", "baz");

        verify(listener, timeout(5000).times(1)).eventRecieved(TrackEventEvent.class, TrackEventEvent.Type);
    }

    @Test
    @SdkSuppress(minSdkVersion = 19)
    public void openSettingsAppToThisAppsSettings() {
        final Activity activity = launchActivity();

        // Open settings
        assertTrue(Teak.openSettingsAppToThisAppsSettings());

        // App should now be paused
        verify(eventListener, timeout(500).times(1)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Paused);

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiDevice device = UiDevice.getInstance(instrumentation);

        // Get app name
        ApplicationInfo applicationInfo = activity.getApplicationInfo();
        final int stringId = applicationInfo.labelRes;
        final String appName = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : activity.getString(stringId);

        // Make sure we are on the settings page for the correct app
        device.wait(Until.hasObject(By.text(appName)), 5000);
        final UiObject2 title = device.findObject(By.text(appName));
        assertNotNull(title);
    }

    @Test
    @SdkSuppress(minSdkVersion = 19)
    public void userHasDisabledNotifications() {
        final Activity activity = launchActivity();

        // Notifications should be enabled at the start
        assertFalse(Teak.userHasDisabledNotifications());

        // Open settings
        assertTrue(Teak.openSettingsAppToThisAppsSettings());

        // App should now be paused
        verify(eventListener, timeout(500).times(1)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Paused);

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiDevice device = UiDevice.getInstance(instrumentation);

        // Get app name
        ApplicationInfo applicationInfo = activity.getApplicationInfo();
        final int stringId = applicationInfo.labelRes;
        final String appName = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : activity.getString(stringId);

        // Make sure we are on the settings page for the correct app
        device.wait(Until.hasObject(By.text(appName)), 5000);
        final UiObject2 title = device.findObject(By.text(appName));
        assertNotNull(title);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Get the 'Allow notifications' label
            final UiObject2 allowNotificationsText = device.findObject(By.text("Allow notifications"));
            assertNotNull(allowNotificationsText);

            // Get the check box
            final UiObject2 allowNotificationsCheckbox = allowNotificationsText.getParent().getParent().findObject(By.checkable(true));
            assertNotNull(allowNotificationsCheckbox);
            assumeTrue(android.os.Build.MODEL + " has invalid starting settings, notifications for " + activity.getPackageName() + " are not allowed.", allowNotificationsCheckbox.isChecked());

            // Disable notifications
            allowNotificationsCheckbox.click();
            allowNotificationsCheckbox.wait(Until.checked(false), 500L);
            assertFalse(allowNotificationsCheckbox.isChecked());

            // Notifications should be disabled
            final boolean shouldBeTrue = Teak.userHasDisabledNotifications();

            // Re-enable notifications
            allowNotificationsCheckbox.click();
            allowNotificationsCheckbox.wait(Until.checked(true), 500L);
            assertTrue(allowNotificationsCheckbox.isChecked());

            // Test here (so notifications are re-enabled for certain)
            assertTrue(shouldBeTrue);
        } else {
            // Get the 'Block' label
            final UiObject2 blockText = device.findObject(By.text("Block"));
            assertNotNull(blockText);

            // Get the check box
            final UiObject2 blockCheckbox = blockText.getParent().getParent().findObject(By.checkable(true));
            assertNotNull(blockCheckbox);
            assumeFalse(android.os.Build.MODEL + " has invalid starting settings, notifications for " + activity.getPackageName() + " are blocked.", blockCheckbox.isChecked());

            // Block notifications
            blockCheckbox.click();
            blockCheckbox.wait(Until.checked(true), 500L);
            assertTrue(blockCheckbox.isChecked());

            // Notifications should be disabled
            final boolean shouldBeTrue = Teak.userHasDisabledNotifications();

            // Un-block notifications
            blockCheckbox.click();
            blockCheckbox.wait(Until.checked(false), 500L);
            assertFalse(blockCheckbox.isChecked());

            // Test here (so notifications are re-enabled for certain)
            assertTrue(shouldBeTrue);
        }

    }

    @Test
    public void setApplicationBadgeNumber() {
        // ShortcutBadger doesn't support all devices, skip those
        final String[] skipDeviceModels = new String[]{
                "C5170",            // Kyocera C5170        Android 4.0.4 (API 15)
                "BLU Advance 5.0"   // BLU BLU Advance 5.0  Android 5.1    (API 22)
        };
        final String thisDeviceModel = android.os.Build.MODEL;
        assumeThat("Teak.setApplicationBadgeNumber() does not support " + thisDeviceModel,
                skipDeviceModels, not(hasItemInArray(thisDeviceModel)));

        launchActivity();
        assertTrue(Teak.setApplicationBadgeNumber(42));
    }
}
