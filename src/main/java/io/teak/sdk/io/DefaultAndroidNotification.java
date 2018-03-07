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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import io.teak.sdk.Helpers;
import io.teak.sdk.NotificationBuilder;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.NotificationReDisplayEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.service.DeviceStateService;

public class DefaultAndroidNotification extends BroadcastReceiver implements IAndroidNotification {
    private final NotificationManager notificationManager;
    private final ArrayList<AnimationEntry> animatedNotifications = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private class AnimationEntry {
        final Notification notification;
        final Bundle bundle;

        AnimationEntry(Notification notification, TeakNotification teakNotification) {
            this.notification = notification;
            this.bundle = teakNotification.bundle;
            this.bundle.putInt("platformId", teakNotification.platformId);
        }
    }

    /**
     * The 'tag' specified by Teak to the {@link NotificationCompat}
     */
    private static final String NOTIFICATION_TAG = "io.teak.sdk.TeakNotification";

    private static DefaultAndroidNotification Instance = null;
    public static DefaultAndroidNotification get(@NonNull Context context) {
        if (Instance == null) {
            Instance = new DefaultAndroidNotification(context);
        }
        return Instance;
    }

    public DefaultAndroidNotification(@NonNull final Context context) {
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (!"test_package_name".equalsIgnoreCase(context.getPackageName())) {
            IntentFilter screenStateFilter = new IntentFilter();
            screenStateFilter.addAction(DeviceStateService.SCREEN_ON);
            screenStateFilter.addAction(DeviceStateService.SCREEN_OFF);
            context.registerReceiver(this, screenStateFilter);
        }

        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                switch (event.eventType) {
                    case PushNotificationEvent.Cleared:
                    case PushNotificationEvent.Interaction: {
                        final Intent intent = ((PushNotificationEvent) event).intent;
                        if (intent != null && intent.getExtras() != null) {
                            final Bundle bundle = intent.getExtras();
                            cancelNotification(bundle.getInt("platformId"));
                        }
                    } break;
                    case NotificationDisplayEvent.Type: {
                        NotificationDisplayEvent notificationDisplayEvent = (NotificationDisplayEvent) event;
                        displayNotification(context, notificationDisplayEvent.teakNotification, notificationDisplayEvent.nativeNotification);
                    } break;
                }
            }
        });
    }

    @Override
    public void cancelNotification(int platformId) {
        Teak.log.i("notification.cancel", Helpers.mm.h("platformId", platformId));

        notificationManager.cancel(NOTIFICATION_TAG, platformId);

        synchronized (this.animatedNotifications) {
            ArrayList<AnimationEntry> removeList = new ArrayList<>();
            for (int i = 0; i < this.animatedNotifications.size(); i++) {
                if (this.animatedNotifications.get(i).bundle.getInt("platformId") == platformId) {
                    removeList.add(this.animatedNotifications.get(i));
                }
            }
            this.animatedNotifications.removeAll(removeList);
        }
    }

    @Override
    public void displayNotification(@NonNull final Context context, @NonNull final TeakNotification teakNotification, @NonNull final Notification nativeNotification) {
        // Send it out
        Teak.log.i("notification.display", Helpers.mm.h("teakNotifId", teakNotification.teakNotifId, "platformId", teakNotification.platformId));

        try {
            Intent intent = new Intent(context, DeviceStateService.class);
            context.startService(intent);
        } catch (Exception ignored) {
            // Android-O has issues with background services
            // https://developer.android.com/about/versions/oreo/background.html
            // Since Android O doesn't have an install base worth mentioning, this can be fixed later
        }

        this.handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    notificationManager.notify(NOTIFICATION_TAG, teakNotification.platformId, nativeNotification);

                    if (teakNotification.isAnimated) {
                        synchronized (animatedNotifications) {
                            animatedNotifications.add(new AnimationEntry(nativeNotification, teakNotification));
                        }
                    }
                } catch (SecurityException ignored) {
                    // This likely means that they need the VIBRATE permission on old versions of Android
                    Teak.log.e("notification.permission_needed.vibrate", "Please add this to your AndroidManifest.xml: <uses-permission android:name=\"android.permission.VIBRATE\" />");
                } catch (Exception e) {
                    // Unit testing case
                    if (nativeNotification.flags != Integer.MAX_VALUE) {
                        throw e;
                    }
                }
            }
        });
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (DeviceStateService.SCREEN_ON.equals(intent.getAction())) {
            Teak.log.i("notification.animation", Helpers.mm.h("animating", true));
        } else if (DeviceStateService.SCREEN_OFF.equals(intent.getAction())) {
            Teak.log.i("notification.animation", Helpers.mm.h("animating", false));

            // Start service again (just in case)
            try {
                Intent serviceIntent = new Intent(context, DeviceStateService.class);
                context.startService(serviceIntent);
            } catch (Exception ignored) {
                // Android-O has issues with background services
                // https://developer.android.com/about/versions/oreo/background.html
                // Since Android O doesn't have an install base worth mentioning, this can be fixed later
            }

            // Double, double, toil and trouble...
            String tempNotificationChannelId = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tempNotificationChannelId = NotificationBuilder.getQuietNotificationChannelId(context);
            }
            final String notificationChannelId = tempNotificationChannelId;

            this.handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (animatedNotifications) {
                        Random rng = new Random();
                        for (final AnimationEntry entry : animatedNotifications) {
                            try {
                                notificationManager.cancel(NOTIFICATION_TAG, entry.bundle.getInt("platformId"));

                                class Deprecated {
                                    @SuppressWarnings("deprecation")
                                    private void assignDeprecated() {
                                        entry.notification.defaults = 0; // Disable sound/vibrate etc
                                        entry.notification.vibrate = new long[]{0L};
                                        entry.notification.sound = null;
                                    }
                                }
                                final Deprecated deprecated = new Deprecated();
                                deprecated.assignDeprecated();

                                entry.bundle.putInt("platformId", rng.nextInt());

                                // Fire burn, and cauldron bubble...
                                if (notificationChannelId != null) {
                                    try {
                                        final Field mChannelIdField = Notification.class.getDeclaredField("mChannelId");
                                        mChannelIdField.setAccessible(true);
                                        mChannelIdField.set(entry.notification, notificationChannelId);
                                    } catch (Exception ignored) {
                                    }
                                }

                                // Now it needs new intents
                                ComponentName cn = new ComponentName(context.getPackageName(), "io.teak.sdk.Teak");

                                // Create intent to fire if/when notification is cleared
                                Intent pushClearedIntent = new Intent(context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX);
                                pushClearedIntent.putExtras(entry.bundle);
                                pushClearedIntent.setComponent(cn);
                                entry.notification.deleteIntent = PendingIntent.getBroadcast(context, rng.nextInt(), pushClearedIntent, PendingIntent.FLAG_ONE_SHOT);

                                // Create intent to fire if/when notification is opened, attach bundle info
                                Intent pushOpenedIntent = new Intent(context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX);
                                pushOpenedIntent.putExtras(entry.bundle);
                                pushOpenedIntent.setComponent(cn);
                                entry.notification.contentIntent = PendingIntent.getBroadcast(context, rng.nextInt(), pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT);

                                notificationManager.notify(NOTIFICATION_TAG, entry.bundle.getInt("platformId"), entry.notification);
                                TeakEvent.postEvent(new NotificationReDisplayEvent(entry.bundle, entry.notification));
                            } catch (Exception e) {
                                Teak.log.exception(e);
                            }
                        }
                    }
                }
            }, 1000);
        }
    }
}
