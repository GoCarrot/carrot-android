package io.teak.sdk.io;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Random;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.work.Data;
import io.teak.sdk.Helpers;
import io.teak.sdk.NotificationBuilder;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.core.DeviceScreenState;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.NotificationReDisplayEvent;
import io.teak.sdk.event.PushNotificationEvent;

import androidx.core.app.NotificationCompat;
import android.service.notification.StatusBarNotification;

public class DefaultAndroidNotification implements IAndroidNotification {
    private final NotificationManager notificationManager;
    private final ArrayList<AnimationEntry> animatedNotifications = new ArrayList<>();
    private final Handler handler;
    private final DeviceScreenState deviceScreenState;

    private static class AnimationEntry {
        final Notification notification;
        final Bundle bundle;

        AnimationEntry(Notification notification, TeakNotification teakNotification) {
            this.notification = notification;
            this.bundle = teakNotification.bundle;
            this.bundle.putInt("platformId", teakNotification.platformId);
        }
    }

    private static final String NOTIFICATION_TAG = "io.teak.sdk.TeakNotification";

    private static final Object InstanceMutex = new Object();
    private static DefaultAndroidNotification Instance = null;
    public static DefaultAndroidNotification get(@NonNull Context context) {
        synchronized (InstanceMutex) {
            if (Instance == null) {
                Instance = new DefaultAndroidNotification(context);
            }
            return Instance;
        }
    }

    public DefaultAndroidNotification(@NonNull final Context context) {
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.deviceScreenState = new DeviceScreenState(context);

        if ("test_package_name".equalsIgnoreCase(context.getPackageName())) {
            this.handler = null;
        } else {
            EventBus.getDefault().register(this);
            this.handler = new Handler(Looper.getMainLooper());
        }

        TeakEvent.addEventListener(event -> {
            switch (event.eventType) {
                case PushNotificationEvent.Cleared:
                case PushNotificationEvent.Interaction: {
                    final Intent intent = ((PushNotificationEvent) event).intent;
                    if (intent != null && intent.getExtras() != null) {
                        final Bundle bundle = intent.getExtras();
                        cancelNotification(context, bundle.getInt("platformId"));
                    }
                } break;
                case NotificationDisplayEvent.Type: {
                    NotificationDisplayEvent notificationDisplayEvent = (NotificationDisplayEvent) event;
                    displayNotification(context, notificationDisplayEvent.teakNotification, notificationDisplayEvent.nativeNotification);
                } break;
            }
        });
    }

    private void scheduleScreenStateWork() {
        try {
            if (this.animatedNotifications.size() > 0) {
                final Data data = new Data.Builder()
                                      .putInt(IAndroidNotification.ANIMATED_NOTIFICATION_COUNT_KEY, this.animatedNotifications.size())
                                      .build();
                this.deviceScreenState.scheduleScreenStateWork(data);
            }
        } catch (Exception e) {
            Teak.log.exception(e, false);
        }
    }

    private class NotificationGroup {
        final public ArrayList<StatusBarNotification> children;
        final public StatusBarNotification summary;

        NotificationGroup(ArrayList<StatusBarNotification> children, StatusBarNotification summary) {
            this.children = children;
            this.summary = summary;
        }
    }

    private NotificationGroup getActiveNotificationsForGroup(String groupKey) {
        ArrayList<StatusBarNotification> children = new ArrayList<StatusBarNotification>();
        StatusBarNotification summary = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StatusBarNotification[] notifications = notificationManager.getActiveNotifications();
            if(notifications != null) {
                for(StatusBarNotification sbn : notifications) {
                    final Notification notification = sbn.getNotification();
                    if(Objects.equals(groupKey, NotificationCompat.getGroup(notification))) {
                        if(NotificationCompat.isGroupSummary(notification)) {
                            summary = sbn;
                        } else {
                            children.add(sbn);
                        }
                    }
                }
            }
        }

        return new NotificationGroup(children, summary);
    }

    @Override
    public void cancelNotification(@NonNull Context context, int platformId) {
        Teak.log.i("notification.cancel", Helpers.mm.h("platformId", platformId));

        this.notificationManager.cancel(NOTIFICATION_TAG, platformId);

        synchronized (this.animatedNotifications) {
            ArrayList<AnimationEntry> removeList = new ArrayList<>();
            for (int i = 0; i < this.animatedNotifications.size(); i++) {
                if (this.animatedNotifications.get(i).bundle.getInt("platformId") == platformId) {
                    removeList.add(this.animatedNotifications.get(i));
                }
            }
            this.animatedNotifications.removeAll(removeList);
            this.scheduleScreenStateWork();
        }
    }

    @Override
    public void displayNotification(@NonNull final Context context, @NonNull final TeakNotification teakNotification, @NonNull final Notification nativeNotification) {
        // Send it out
        Teak.log.i("notification.display", Helpers.mm.h("teakNotifId", teakNotification.teakNotifId, "platformId", teakNotification.platformId));

        // This should only be the case during unit tests, but catch it here anyway
        if (this.handler == null) {
            Teak.log.e("notification.display.error", "this.handler is null, skipping display");
            return;
        }

        this.handler.post(() -> {
            // Run the GC
            Helpers.runAndLogGC("display_notification.gc");

            try {
                final String groupKey = NotificationCompat.getGroup(nativeNotification);
                final NotificationGroup groupInfo = DefaultAndroidNotification.this.getActiveNotificationsForGroup(groupKey);

                final StatusBarNotification groupSummary = groupInfo.summary;
                final ArrayList<StatusBarNotification> ourNotifications = groupInfo.children;

                final int notificationCount = ourNotifications.size() + 1;

                Teak.log.i(
                    "default_android_notification.display_notification.summary_info",
                    Helpers.mm.h("liveCount", notificationCount, "hasSummary", groupSummary != null)
                );

                DefaultAndroidNotification.this.notificationManager.notify(NOTIFICATION_TAG, teakNotification.platformId, nativeNotification);

                if(notificationCount >= teakNotification.minGroupSize) {
                    int summaryId = teakNotification.groupSummaryId;
                    if(groupSummary != null) {
                        summaryId = groupSummary.getId();
                    }
                    DefaultAndroidNotification.this.notificationManager.notify(
                        NOTIFICATION_TAG,
                        summaryId,
                        NotificationBuilder.createSummaryNotification(context, groupKey, ourNotifications)
                    );
                }

                if (teakNotification.isAnimated) {
                    synchronized (DefaultAndroidNotification.this.animatedNotifications) {
                        DefaultAndroidNotification.this.animatedNotifications.add(new AnimationEntry(nativeNotification, teakNotification));
                        DefaultAndroidNotification.this.scheduleScreenStateWork();
                    }
                }
            } catch (SecurityException ignored) {
                // This likely means that they need the VIBRATE permission on old versions of Android
                Teak.log.e("notification.permission_needed.vibrate", "Please add this to your AndroidManifest.xml: <uses-permission android:name=\"android.permission.VIBRATE\" />");
            } catch (OutOfMemoryError e) {
                Teak.log.exception(e);
            } catch (Exception e) {
                // Unit testing case
                if (nativeNotification.flags != Integer.MAX_VALUE) {
                    throw e;
                }
            }
        });
    }

    @Subscribe
    public void onScreenState(final DeviceScreenState.ScreenStateChangeEvent event) {
        if (event.newState == DeviceScreenState.State.ScreenOn) return;

        // This should only be the case during unit tests, but catch it here anyway
        if (this.handler == null) {
            Teak.log.e("notification.animation.error", "this.handler is null, skipping animation refresh");
            return;
        }

        // Double, double, toil and trouble...
        String tempNotificationChannelId = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tempNotificationChannelId = NotificationBuilder.getQuietNotificationChannelId(event.context);
        }
        final String notificationChannelId = tempNotificationChannelId;

        this.handler.postDelayed(() -> {
            synchronized (DefaultAndroidNotification.this.animatedNotifications) {
                Random rng = new Random();
                for (final AnimationEntry entry : DefaultAndroidNotification.this.animatedNotifications) {
                    try {
                        DefaultAndroidNotification.this.notificationManager.cancel(NOTIFICATION_TAG, entry.bundle.getInt("platformId"));

                        class Deprecated {
                            @SuppressWarnings("deprecation")
                            private void assignDeprecated() {
                                entry.notification.defaults = 0; // Disable sound/vibrate etc
                                entry.notification.vibrate = new long[] {0L};
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
                        ComponentName cn = new ComponentName(event.context.getPackageName(), "io.teak.sdk.Teak");

                        // Create intent to fire if/when notification is cleared
                        Intent pushClearedIntent = new Intent(event.context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX);
                        pushClearedIntent.putExtras(entry.bundle);
                        pushClearedIntent.setComponent(cn);
                        entry.notification.deleteIntent = PendingIntent.getBroadcast(event.context, rng.nextInt(), pushClearedIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

                        // Create intent to fire if/when notification is opened, attach bundle info
                        Intent pushOpenedIntent = new Intent(event.context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX);
                        pushOpenedIntent.putExtras(entry.bundle);
                        pushOpenedIntent.setComponent(cn);
                        entry.notification.contentIntent = PendingIntent.getBroadcast(event.context, rng.nextInt(), pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

                        DefaultAndroidNotification.this.notificationManager.notify(NOTIFICATION_TAG, entry.bundle.getInt("platformId"), entry.notification);
                        TeakEvent.postEvent(new NotificationReDisplayEvent(entry.bundle, entry.notification));
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                }
            }

            // Re-issue the work, this will overwrite the current worker, and reset the retry-backoff
            DefaultAndroidNotification.this.scheduleScreenStateWork();
        }, 1000);
    }
}
