package io.teak.sdk.io;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.SparseArray;

import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.PushNotificationEvent;

public class DefaultAndroidNotification implements IAndroidNotification {
    private final NotificationManager notificationManager;
    private final SparseArray<Thread> notificationUpdateThread = new SparseArray<>();

    /**
     * The 'tag' specified by Teak to the {@link NotificationCompat}
     */
    private static final String NOTIFICATION_TAG = "io.teak.sdk.TeakNotification";

    public DefaultAndroidNotification(@NonNull Context context) {
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                switch (event.eventType) {
                    case PushNotificationEvent.Cleared: {
                        final Intent intent = ((PushNotificationEvent)event).intent;
                        if (intent != null) {
                            final Bundle bundle = intent.getExtras();
                            cancelNotification(bundle.getInt("platformId"));
                        }
                        break;
                    }
                    case PushNotificationEvent.Interaction: {
                        final Intent intent = ((PushNotificationEvent)event).intent;
                        if (intent == null) break;

                        final Bundle bundle = intent.getExtras();
                        cancelNotification(bundle.getInt("platformId"));
                        break;
                    }
                    case NotificationDisplayEvent.Type: {
                        NotificationDisplayEvent notificationDisplayEvent = (NotificationDisplayEvent)event;
                        displayNotification(notificationDisplayEvent.teakNotification, notificationDisplayEvent.nativeNotification);
                    }
                }
            }
        });
    }

    @Override
    public void cancelNotification(int platformId) {
        Teak.log.i("notification.cancel", Helpers.mm.h("platformId", platformId));

        notificationManager.cancel(NOTIFICATION_TAG, platformId);

        Thread updateThread = this.notificationUpdateThread.get(platformId);
        if (updateThread != null) {
            updateThread.interrupt();
        }
    }

    @Override
    public void displayNotification(@NonNull TeakNotification teakNotification, @NonNull Notification nativeNotification) {
        // Send it out
        Teak.log.i("notification.display", Helpers.mm.h("teakNotifId", teakNotification.teakNotifId, "platformId", teakNotification.platformId));

        try {
            notificationManager.notify(NOTIFICATION_TAG, teakNotification.platformId, nativeNotification);
        } catch (SecurityException ignored) {
            // This likely means that they need the VIBRATE permission on old versions of Android
            Teak.log.e("notification.permission_needed.vibrate", "Please add this to your AndroidManifest.xml: <uses-permission android:name=\"android.permission.VIBRATE\" />");
        }

        // TODO: Here is where any kind of thread/update logic will live
    }
}
