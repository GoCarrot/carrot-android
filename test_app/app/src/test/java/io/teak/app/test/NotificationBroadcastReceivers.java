package io.teak.app.test;

import android.content.Context;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

import io.teak.sdk.Log;
import io.teak.sdk.Request;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.json.JSONObject;
import io.teak.sdk.push.FCMPushProvider;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NotificationBroadcastReceivers {
    @Test
    public void FcmMessageReceieved() throws PackageManager.NameNotFoundException, NoSuchFieldException, IllegalAccessException {
        final Log teakLog = mock(Log.class); // withSettings().verboseLogging()
        final Field teakLogField = Teak.class.getDeclaredField("log");
        teakLogField.setAccessible(true);
        teakLogField.set(null, teakLog);

        final TestTeakEventListener eventListener = spy(TestTeakEventListener.class);
        TeakEvent.addEventListener(eventListener);

        final FCMPushProvider fcmPushProvider = new FCMPushProvider();
        assertNotNull(fcmPushProvider);

        final ApplicationInfo applicationInfo = mock(ApplicationInfo.class);

        final PackageManager packageManager = mock(PackageManager.class);
        when(packageManager.getApplicationInfo(any(String.class), any(int.class))).thenReturn(applicationInfo);

        final Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getPackageManager()).thenReturn(packageManager);
        when(context.getPackageName()).thenReturn("test_package_name");

        final Bundle extras = mock(Bundle.class);
        when(extras.containsKey("teakNotifId")).thenReturn(true);
        when(extras.getString("version")).thenReturn("1");
        when(extras.getString("teakUserId", null)).thenReturn("test_teak_user_id");
        when(extras.getString("teakAppId", null)).thenReturn("test_teak_app_id");

        final JSONObject display = new JSONObject();
        when(extras.getString("display")).thenReturn(display.toString());
        when(extras.getBoolean("teakUnitTest")).thenReturn(true);

        final Intent intent = mock(Intent.class);
        when(intent.getExtras()).thenReturn(extras);

        Request.setTeakApiKey("test_teak_api_key");

        fcmPushProvider.postEvent(context, intent);

        verify(eventListener, timeout(500).times(1)).eventRecieved(PushNotificationEvent.class, PushNotificationEvent.Received);
        verify(eventListener, timeout(500).times(1)).eventRecieved(NotificationDisplayEvent.class, NotificationDisplayEvent.Type);

        ArgumentCaptor<Throwable> thrown = ArgumentCaptor.forClass(Throwable.class);
        verify(teakLog, timeout(1000).times(0)).exception(thrown.capture());
        for (Throwable t : thrown.getAllValues()) {
            t.printStackTrace();
        }
    }
}
