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
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.firebase.jobdispatcher.Job;

import io.teak.sdk.Helpers;
import io.teak.sdk.NotificationBuilder;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.core.DeviceScreenState;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.NotificationReDisplayEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.service.JobService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Random;

public class DefaultAndroidNotification extends BroadcastReceiver implements IAndroidNotification {
    private final NotificationManager notificationManager;
    private final ArrayList<AnimationEntry> animatedNotifications = new ArrayList<>();
    private final Handler handler;
    private final DeviceScreenState deviceScreenState = new DeviceScreenState();

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

        if (!"test_package_name".equalsIgnoreCase(context.getPackageName())) {
            IntentFilter screenStateFilter = new IntentFilter();
            screenStateFilter.addAction(DeviceScreenState.SCREEN_ON);
            screenStateFilter.addAction(DeviceScreenState.SCREEN_OFF);
            context.registerReceiver(this, screenStateFilter);

            this.handler = new Handler(Looper.getMainLooper());
        } else {
            this.handler = null;
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
                            cancelNotification(context, bundle.getInt("platformId"));
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

    private void issueAnimatedNotificationSizeJob() {
        try {
            if (this.animatedNotifications.size() > 0) {
                final Bundle bundle = new Bundle();
                bundle.putInt(IAndroidNotification.ANIMATED_NOTIFICATION_COUNT_KEY, this.animatedNotifications.size());
                bundle.putString(JobService.JOB_TYPE_KEY, IAndroidNotification.ANIMATED_NOTIFICATION_JOB_TYPE);

                final Job job = Teak.Instance.jobBuilder(IAndroidNotification.ANIMATED_NOTIFICATION_JOB_TYPE, bundle)
                                    .build();
                Teak.Instance.dispatcher.mustSchedule(job);
            } else {
                Teak.Instance.dispatcher.cancel(IAndroidNotification.ANIMATED_NOTIFICATION_JOB_TYPE);
            }
        } catch (Exception e) {
            Teak.log.exception(e, false);
        }
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
            this.issueAnimatedNotificationSizeJob();
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

        this.handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    DefaultAndroidNotification.this.notificationManager.notify(NOTIFICATION_TAG, teakNotification.platformId, nativeNotification);

                    if (teakNotification.isAnimated) {
                        synchronized (DefaultAndroidNotification.this.animatedNotifications) {
                            DefaultAndroidNotification.this.animatedNotifications.add(new AnimationEntry(nativeNotification, teakNotification));
                            DefaultAndroidNotification.this.issueAnimatedNotificationSizeJob();
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
        DeviceScreenState.State state = DeviceScreenState.State.Unknown;
        if (DeviceScreenState.SCREEN_ON.equals(intent.getAction())) {
            state = DeviceScreenState.State.ScreenOn;
        } else if (DeviceScreenState.SCREEN_OFF.equals(intent.getAction())) {
            state = DeviceScreenState.State.ScreenOff;
        }

        this.deviceScreenState.setState(state, new DeviceScreenState.Callbacks() {
            @Override
            public void onStateChanged(DeviceScreenState.State oldState, DeviceScreenState.State newState) {
                if (newState == DeviceScreenState.State.ScreenOff) {
                    DefaultAndroidNotification.this.reIssueAnimatedNotifications(context);
                }
            }
        });
    }

    private void reIssueAnimatedNotifications(@NonNull final Context context) {
        // This should only be the case during unit tests, but catch it here anyway
        if (this.handler == null) {
            Teak.log.e("notification.animation.error", "this.handler is null, skipping animation refresh");
            return;
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

                            DefaultAndroidNotification.this.notificationManager.notify(NOTIFICATION_TAG, entry.bundle.getInt("platformId"), entry.notification);
                            TeakEvent.postEvent(new NotificationReDisplayEvent(entry.bundle, entry.notification));
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                }

                // Re-issue the job, this will overwrite the current job, and reset the retry-backoff
                DefaultAndroidNotification.this.issueAnimatedNotificationSizeJob();
            }
        }, 1000);
    }
}
