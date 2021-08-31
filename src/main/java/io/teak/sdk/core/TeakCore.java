package io.teak.sdk.core;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import androidx.annotation.NonNull;
import io.teak.sdk.Helpers;
import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.NotificationBuilder;
import io.teak.sdk.Request;
import io.teak.sdk.RetriableTask;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.PurchaseEvent;
import io.teak.sdk.event.PurchaseFailedEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.TrackEventEvent;
import io.teak.sdk.io.DefaultAndroidNotification;
import io.teak.sdk.io.DefaultAndroidResources;
import io.teak.sdk.json.JSONObject;

public class TeakCore {
    private static TeakCore Instance = null;
    public static TeakCore get(@NonNull Context context) {
        if (Instance == null) {
            Instance = new TeakCore(context);
        }
        return Instance;
    }

    public static TeakCore getWithoutThrow(@NonNull Context context) {
        try {
            return TeakCore.get(context);
        } catch (Exception ignored) {
        }
        return null;
    }

    public TeakCore(@NonNull Context context) {
        TeakEvent.addEventListener(this.teakEventListener);

        registerStaticTeakEventListeners();
    }

    // TODO: Would love to make this Annotation based
    private void registerStaticTeakEventListeners() {
        RemoteConfiguration.registerStaticEventListeners();
        Session.registerStaticEventListeners();
        Request.registerStaticEventListeners();
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final TeakEvent.EventListener teakEventListener = new TeakEvent.EventListener() {
        @Override
        public void onNewEvent(@NonNull TeakEvent event) {
            switch (event.eventType) {
                case LifecycleEvent.Resumed: {
                    final Intent intent = ((LifecycleEvent) event).intent;
                    if (!intent.getBooleanExtra("teakProcessedForPush", false)) {
                        intent.putExtra("teakProcessedForPush", true);
                        checkIntentForPushLaunchAndSendBroadcasts(intent);
                    }
                    break;
                }
                case TrackEventEvent.Type: {
                    final Map<String, Object> payload = ((TrackEventEvent) event).payload;

                    asyncExecutor.execute(() -> Session.whenUserIdIsReadyRun(session -> Request.submit("/me/events", payload, session)));
                    break;
                }
                case PurchaseEvent.Type: {
                    final Map<String, Object> payload = ((PurchaseEvent) event).payload;
                    Teak.log.i("purchase.succeeded", payload);

                    asyncExecutor.execute(() -> Session.whenUserIdIsReadyRun(session -> Request.submit("/me/purchase", payload, session)));
                    break;
                }
                case PurchaseFailedEvent.Type: {
                    final Map<String, Object> payload = ((PurchaseFailedEvent) event).extras;
                    payload.put("errorCode", ((PurchaseFailedEvent) event).errorCode);
                    Teak.log.i("purchase.failed", payload);

                    asyncExecutor.execute(() -> Session.whenUserIdIsReadyRun(session -> Request.submit("/me/purchase", payload, session)));
                    break;
                }
                case PushNotificationEvent.Received: {
                    final Intent intent = ((PushNotificationEvent) event).intent;
                    if (intent == null) break;

                    final Bundle bundle = intent.getExtras();
                    if (bundle == null || !bundle.containsKey("teakNotifId")) break;

                    final boolean isUnitTest = bundle.getBoolean("teakUnitTest");

                    // Debug output
                    HashMap<String, Object> debugHash = new HashMap<>();
                    Set<String> keys = bundle.keySet();
                    for (String key : keys) {
                        Object o = bundle.get(key);
                        if (o instanceof String) {
                            try {
                                JSONObject jsonObject = new JSONObject(o.toString());
                                o = jsonObject.toMap();
                            } catch (Exception ignored) {
                            }
                        }
                        debugHash.put(key, o);
                    }
                    Teak.log.i("notification.received", debugHash);

                    // If the session is not expiring or expired, we are in the foreground
                    // If we're not supposed to show notifications in the foreground, trigger the
                    //   broadcast for a foreground receipt of a notification.
                    final boolean gameIsInForeground = !Session.isExpiringOrExpired();
                    final boolean showInForeground = Helpers.getBooleanFromBundle(bundle, "teakShowInForeground");
                    if (gameIsInForeground) {
                        Session.whenUserIdIsReadyPost(new Teak.NotificationEvent(bundle, true));
                    }

                    // Create Teak Notification
                    final TeakNotification teakNotification = new TeakNotification(bundle, gameIsInForeground);

                    // Add platformId to bundle
                    bundle.putInt("platformId", teakNotification.platformId);

                    // Add notification placement to bundle
                    bundle.putString("teakNotificationPlacement", teakNotification.notificationPlacement.name);

                    // Create & display native notification asynchronously, image downloads etc
                    final Context context = ((PushNotificationEvent) event).context;
                    asyncExecutor.submit(new RetriableTask<>(3, 2000L, 2, () -> {
                        // Send metric
                        final String teakUserId = bundle.getString("teakUserId", null);
                        final String teakAppId = bundle.getString("teakAppId", null);
                        if (teakAppId != null && teakUserId != null) {
                            HashMap<String, Object> payload = new HashMap<>();
                            payload.put("app_id", teakAppId);
                            payload.put("user_id", teakUserId);
                            payload.put("platform_id", teakNotification.teakNotifId);

                            if (gameIsInForeground) {
                                payload.put("notification_placement", "foreground");
                            }

                            if (teakNotification.teakNotifId == 0) {
                                payload.put("impression", false);
                            }

                            // If the API key is null, assign that
                            if (!isUnitTest) {
                                try {
                                    if (!Request.hasTeakApiKey()) {
                                        AppConfiguration tempAppConfiguration = new AppConfiguration(context, new DefaultAndroidResources(context));
                                        Request.setTeakApiKey(tempAppConfiguration.apiKey);
                                    }
                                    Request.submit("parsnip.gocarrot.com", "/notification_received", payload, Session.NullSession);
                                } catch (IntegrationChecker.InvalidConfigurationException ignored) {
                                }
                            }
                        }

                        // If the game is backgrounded, or we can show in foreground, display notification
                        if (!gameIsInForeground || showInForeground) {
                            // Create native notification
                            final Notification nativeNotification = NotificationBuilder.createNativeNotification(context, bundle, teakNotification);
                            if (nativeNotification == null) return null;

                            // Ensure that DefaultAndroidNotification instance exists.
                            if (DefaultAndroidNotification.get(context) == null) return null;

                            // Send display event
                            TeakEvent.postEvent(new NotificationDisplayEvent(teakNotification, nativeNotification));
                        }
                        return null;
                    }));
                    break;
                }

                case PushNotificationEvent.Interaction: {
                    final Intent intent = ((PushNotificationEvent) event).intent;
                    if (intent == null) break;

                    final Bundle bundle = intent.getExtras();
                    if (bundle == null) break;

                    Teak.log.i("notification.opened",
                        Helpers.mm.h("teakNotifId", bundle.getString("teakNotifId"),
                            "teakNotificationPlacement", bundle.getString("teakNotificationPlacement")));

                    // Launch the app
                    final Context context = ((PushNotificationEvent) event).context;
                    if (context != null) {
                        final Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());

                        if (launchIntent != null) {
                            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            launchIntent.putExtras(bundle);
                            if (bundle.getString("teakDeepLink") != null) {
                                Uri teakDeepLink = Uri.parse(bundle.getString("teakDeepLink"));
                                launchIntent.setData(teakDeepLink);
                            }
                            context.startActivity(launchIntent);

                            // Close notification tray if we are not on Android 12+
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                final Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                                context.sendBroadcast(it);
                            }
                        }
                    }
                    break;
                }
            }
        }
    };

    private void checkIntentForPushLaunchAndSendBroadcasts(Intent intent) {
        if (intent.hasExtra("teakNotifId") && intent.getExtras() != null) {
            final Bundle bundle = intent.getExtras();

            // Send broadcast
            if (bundle != null) {
                final String teakRewardId = bundle.getString("teakRewardId");

                if (teakRewardId != null) {
                    final Future<TeakNotification.Reward> rewardFuture = TeakNotification.Reward.rewardFromRewardId(teakRewardId);
                    if (rewardFuture != null) {
                        this.asyncExecutor.execute(() -> {
                            Teak.NotificationEvent notificationEvent = new Teak.NotificationEvent(bundle, false);
                            try {
                                final TeakNotification.Reward reward = rewardFuture.get();

                                final HashMap<String, Object> rewardMap = new HashMap<>(reward.json.toMap());
                                rewardMap.put("teakNotifId", notificationEvent.teakSourceSendId);
                                rewardMap.put("incentivized", true);
                                rewardMap.put("teakRewardId", notificationEvent.teakRewardId);
                                rewardMap.put("teakScheduleName", notificationEvent.teakScheduleName);
                                rewardMap.put("teakScheduleId", notificationEvent.teakScheduleId);
                                rewardMap.put("teakCreativeName", notificationEvent.teakCreativeName);
                                rewardMap.put("teakCreativeId", notificationEvent.teakCreativeId);
                                rewardMap.put("teakChannelName", notificationEvent.teakChannelName);

                                notificationEvent = new Teak.NotificationEvent(bundle, false, rewardMap);

                                Session.whenUserIdIsReadyPost(new Teak.RewardClaimEvent(rewardMap));
                            } catch (Exception e) {
                                Teak.log.exception(e);
                            } finally {
                                Session.whenUserIdIsReadyPost(notificationEvent);
                            }
                        });
                    }
                } else {
                    Session.whenUserIdIsReadyPost(new Teak.NotificationEvent(bundle, false));
                }
            }
        }
    }

    ///// Data Members

    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    static final ScheduledExecutorService operationQueue = Executors.newSingleThreadScheduledExecutor();
}
