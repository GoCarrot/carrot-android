package io.teak.app.test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.support.v4.content.LocalBroadcastManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;

import io.teak.sdk.Teak2;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.event.OSListener;
import io.teak.sdk.ObjectFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by pat on 10/6/17.
 */
@RunWith(AndroidJUnit4.class)
public class OSListenerIntegrationTests {
    private OSListener osListener;

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

    @Test
    public void basicLifecycle() {
        testRule.launchActivity(null);
    }

    @Test
    @SdkSuppress(minSdkVersion = 18)
    public void integratedLifecycle() {
        // Reference:
        // http://alexzh.com/tutorials/android-testing-ui-automator-part-4/
        testRule.launchActivity(null);

        // Background the app, it should now be paused
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressHome();
        verify(osListener, times(1)).lifecycle_onActivityPaused(testRule.getActivity());

        // Open up the apps list
        UiObject2 allApps = device.findObject(By.text("Apps"));
        allApps.click();

        // Re-open the test app
        device.wait(Until.hasObject(By.desc("Teak SDK Tests")), 3000);
        UiObject2 testAppIcon = device.findObject(By.desc("Teak SDK Tests"));
        testAppIcon.click();

        // Should be resumed
        verify(osListener, timeout(500).times(2)).lifecycle_onActivityResumed(testRule.getActivity());
    }

    @Test(timeout = 10000)
    @SdkSuppress(minSdkVersion = 21)
    public void notificationCleared() {
        testRule.launchActivity(null);

        // Make sure app is fully active
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.wait(Until.hasObject(By.text("Hello World")), 1000);

        // Background the app
        device.pressHome();

        // Send a notification cleared broadcast
        String event = testRule.getActivity().getPackageName() + TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX;
        String receiver = testRule.getActivity().getPackageName() + "/io.teak.sdk.Teak2";
        String adbString = "am broadcast -a " + event + " -n " + receiver + " --es \"platformId\" \"foo\"";
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(adbString);

        // Should have been called
        verify(osListener, timeout(3000).times(1)).notification_onNotificationCleared(any(Context.class), any(Intent.class));
    }
}
