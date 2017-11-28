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
package io.teak.sdk.io;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.service.NotificationAnimationService;

public class DefaultAndroidNotification extends BroadcastReceiver implements IAndroidNotification {
    private final NotificationManager notificationManager;
    private final ArrayList<AnimationEntry> animatedNotifications = new ArrayList<>();

    private class AnimationEntry {
        final Notification notification;
        final TeakNotification teakNotification;

        AnimationEntry(Notification notification, TeakNotification teakNotification) {
            this.notification = notification;
            this.teakNotification = teakNotification;
        }
    }

    /**
     * The 'tag' specified by Teak to the {@link NotificationCompat}
     */
    private static final String NOTIFICATION_TAG = "io.teak.sdk.TeakNotification";

    public DefaultAndroidNotification(@NonNull Context context) {
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(NotificationAnimationService.START_ANIMATING);
        screenStateFilter.addAction(NotificationAnimationService.STOP_ANIMATING);
        context.registerReceiver(this, screenStateFilter);

        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                switch (event.eventType) {
                    case PushNotificationEvent.Cleared: {
                        final Intent intent = ((PushNotificationEvent) event).intent;
                        if (intent != null && intent.getExtras() != null) {
                            final Bundle bundle = intent.getExtras();
                            cancelNotification(bundle.getInt("platformId"));
                        }
                        break;
                    }
                    case PushNotificationEvent.Interaction: {
                        final Intent intent = ((PushNotificationEvent) event).intent;
                        if (intent != null && intent.getExtras() != null) {
                            final Bundle bundle = intent.getExtras();
                            cancelNotification(bundle.getInt("platformId"));
                        }
                        break;
                    }
                    case NotificationDisplayEvent.Type: {
                        NotificationDisplayEvent notificationDisplayEvent = (NotificationDisplayEvent) event;
                        displayNotification(notificationDisplayEvent.teakNotification, notificationDisplayEvent.nativeNotification);
                        break;
                    }
                }
            }
        });

        Intent intent = new Intent(context, NotificationAnimationService.class);
        ComponentName componentName = context.startService(intent);
        if (componentName == null) {
            Teak.log.w("notification.animation", "Unable to communicate with notification animation service. Please add:\n\t<service android:name=\"io.teak.sdk.service.NotificationAnimationService\" android:process=\":teak.animation\" android:exported=\"false\"/>\nTo the <application> section of your AndroidManifest.xml");
        }
    }

    @Override
    public void cancelNotification(int platformId) {
        Teak.log.i("notification.cancel", Helpers.mm.h("platformId", platformId));

        notificationManager.cancel(NOTIFICATION_TAG, platformId);

        synchronized (this.animatedNotifications) {
            ArrayList<AnimationEntry> removeList = new ArrayList<>();
            for (int i = 0; i < this.animatedNotifications.size(); i++) {
                if (this.animatedNotifications.get(i).teakNotification.platformId == platformId) {
                    removeList.add(this.animatedNotifications.get(i));
                }
            }
            this.animatedNotifications.removeAll(removeList);
        }
    }

    @Override
    public void displayNotification(@NonNull TeakNotification teakNotification, @NonNull Notification nativeNotification) {
        // Send it out
        Teak.log.i("notification.display", Helpers.mm.h("teakNotifId", teakNotification.teakNotifId, "platformId", teakNotification.platformId));

        try {
            this.notificationManager.notify(NOTIFICATION_TAG, teakNotification.platformId, nativeNotification);

            if (teakNotification.isAnimated) {
                synchronized (this.animatedNotifications) {
                    this.animatedNotifications.add(new AnimationEntry(nativeNotification, teakNotification));
                }
            }
        } catch (SecurityException ignored) {
            // This likely means that they need the VIBRATE permission on old versions of Android
            Teak.log.e("notification.permission_needed.vibrate", "Please add this to your AndroidManifest.xml: <uses-permission android:name=\"android.permission.VIBRATE\" />");
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (NotificationAnimationService.START_ANIMATING.equals(intent.getAction())) {
            Teak.log.i("notification.animation", Helpers.mm.h("animating", true));
        } else if (NotificationAnimationService.STOP_ANIMATING.equals(intent.getAction())) {
            Teak.log.i("notification.animation", Helpers.mm.h("animating", false));

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (animatedNotifications) {
                        Random rng = new Random();
                        for (AnimationEntry entry : animatedNotifications) {
                            try {
                                notificationManager.cancel(NOTIFICATION_TAG, entry.teakNotification.platformId);

                                entry.notification.defaults = 0; // Disable sound/vibrate etc
                                entry.teakNotification.platformId = rng.nextInt();
                                entry.teakNotification.bundle.putInt("platformId", entry.teakNotification.platformId);

                                // Now it needs new intents
                                ComponentName cn = new ComponentName(context.getPackageName(), "io.teak.sdk.Teak");

                                // Create intent to fire if/when notification is cleared
                                Intent pushClearedIntent = new Intent(context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX);
                                pushClearedIntent.putExtras(entry.teakNotification.bundle);
                                pushClearedIntent.setComponent(cn);
                                entry.notification.deleteIntent = PendingIntent.getBroadcast(context, rng.nextInt(), pushClearedIntent, PendingIntent.FLAG_ONE_SHOT);

                                // Create intent to fire if/when notification is opened, attach bundle info
                                Intent pushOpenedIntent = new Intent(context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX);
                                pushOpenedIntent.putExtras(entry.teakNotification.bundle);
                                pushOpenedIntent.setComponent(cn);
                                entry.notification.contentIntent = PendingIntent.getBroadcast(context, rng.nextInt(), pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT);


                                notificationManager.notify(NOTIFICATION_TAG, entry.teakNotification.platformId, entry.notification);
                            } catch (Exception e) {
                                Teak.log.exception(e);
                            }
                        }
                    }
                }
            }, 1);
        }
    }
}
