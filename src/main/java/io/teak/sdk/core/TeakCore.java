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

package io.teak.sdk.core;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.io.DefaultAndroidNotification;
import io.teak.sdk.io.DefaultAndroidResources;
import io.teak.sdk.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import io.teak.sdk.Helpers;
import io.teak.sdk.NotificationBuilder;
import io.teak.sdk.Request;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.event.ExternalBroadcastEvent;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.PurchaseEvent;
import io.teak.sdk.event.PurchaseFailedEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.TrackEventEvent;

public class TeakCore implements ITeakCore {
    private static TeakCore Instance = null;
    public static TeakCore get(@NonNull Context context) throws IntegrationChecker.MissingDependencyException {
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

    public TeakCore(@NonNull Context context) throws IntegrationChecker.MissingDependencyException {
        IntegrationChecker.requireDependency("android.support.v4.content.LocalBroadcastManager");

        this.localBroadcastManager = LocalBroadcastManager.getInstance(context);

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
                case ExternalBroadcastEvent.Type: {
                    final Intent intent = ((ExternalBroadcastEvent) event).intent;
                    sendLocalBroadcast(intent);
                    break;
                }
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

                    asyncExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                                @Override
                                public void run(Session session) {
                                    Request.submit("/me/events", payload, session);
                                }
                            });
                        }
                    });
                    break;
                }
                case PurchaseEvent.Type: {
                    final Map<String, Object> payload = ((PurchaseEvent) event).payload;
                    Teak.log.i("purchase.succeeded", payload);

                    asyncExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                                @Override
                                public void run(Session session) {
                                    Request.submit("/me/purchase", payload, session);
                                }
                            });
                        }
                    });
                    break;
                }
                case PurchaseFailedEvent.Type: {
                    final Map<String, Object> payload = new HashMap<>();
                    payload.put("errorCode", ((PurchaseFailedEvent) event).errorCode);
                    Teak.log.i("purchase.failed", payload);

                    asyncExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                                @Override
                                public void run(Session session) {
                                    Request.submit("/me/purchase", payload, session);
                                }
                            });
                        }
                    });
                    break;
                }
                case PushNotificationEvent.Received: {
                    final Intent intent = ((PushNotificationEvent) event).intent;
                    if (intent == null) break;

                    final Bundle bundle = intent.getExtras();
                    if (bundle == null || !bundle.containsKey("teakNotifId")) break;

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

                    // Foreground notification?
                    boolean showInForeground = Helpers.getBooleanFromBundle(bundle, "teakShowInForeground");
                    if (showInForeground || !Session.isExpiringOrExpired()) break;

                    // Create Teak Notification
                    final TeakNotification teakNotification = new TeakNotification(bundle);

                    // Add platformId to bundle
                    bundle.putInt("platformId", teakNotification.platformId);

                    // Create & display native notification asynchronously, image downloads etc
                    final Context context = ((PushNotificationEvent) event).context;
                    asyncExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            // Create native notification
                            Notification nativeNotification = NotificationBuilder.createNativeNotification(context, bundle, teakNotification);
                            if (nativeNotification == null) return;

                            // Ensure that DefaultAndroidNotification instance exists.
                            if (DefaultAndroidNotification.get(context) == null) return;

                            // Send metric
                            final String teakUserId = bundle.getString("teakUserId", null);
                            final String teakAppId = bundle.getString("teakAppId", null);
                            if (teakAppId != null && teakUserId != null) {
                                HashMap<String, Object> payload = new HashMap<>();
                                payload.put("app_id", teakAppId);
                                payload.put("user_id", teakUserId);
                                payload.put("platform_id", teakNotification.teakNotifId);
                                if (teakNotification.teakNotifId == 0) {
                                    payload.put("impression", false);
                                }

                                // If the API key is null, assign that
                                try {
                                    if (!Request.hasTeakApiKey()) {
                                        AppConfiguration tempAppConfiguration = new AppConfiguration(context, new DefaultAndroidResources(context));
                                        Request.setTeakApiKey(tempAppConfiguration.apiKey);
                                    }
                                    Request.submit("parsnip.gocarrot.com", "/notification_received", payload, Session.NullSession);
                                } catch (IntegrationChecker.InvalidConfigurationException ignored) {
                                }
                            }

                            // Send display event
                            TeakEvent.postEvent(new NotificationDisplayEvent(teakNotification, nativeNotification));
                        }
                    });
                    break;
                }

                case PushNotificationEvent.Interaction: {
                    final Intent intent = ((PushNotificationEvent) event).intent;
                    if (intent == null) break;

                    final Bundle bundle = intent.getExtras();
                    if (bundle == null) break;

                    final boolean autoLaunch = !Helpers.getBooleanFromBundle(bundle, "noAutolaunch");
                    Teak.log.i("notification.opened", Helpers.mm.h("teakNotifId", bundle.getString("teakNotifId"), "autoLaunch", autoLaunch));

                    // Launch the app
                    final Context context = ((PushNotificationEvent) event).context;
                    if (context != null && autoLaunch) {
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

                            // Close notification tray
                            final Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                            context.sendBroadcast(it);
                        }
                    }
                    break;
                }
            }
        }
    };

    @Override
    public void registerLocalBroadcastReceiver(BroadcastReceiver broadcastReceiver, IntentFilter filter) {
        this.localBroadcastManager.registerReceiver(broadcastReceiver, filter);
    }

    private void sendLocalBroadcast(final Intent intent) {
        Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
            @Override
            public void run(Session session) {
                localBroadcastManager.sendBroadcast(intent);
            }
        });
    }

    private void checkIntentForPushLaunchAndSendBroadcasts(Intent intent) {
        if (intent.hasExtra("teakNotifId") && intent.getExtras() != null) {
            final Bundle bundle = intent.getExtras();

            // Send broadcast
            if (bundle != null && this.localBroadcastManager != null) {
                final String teakRewardId = bundle.getString("teakRewardId");

                final HashMap<String, Object> eventDataDict = new HashMap<>();
                eventDataDict.put("teakNotifId", bundle.getString("teakNotifId"));
                eventDataDict.put("teakRewardId", teakRewardId);
                eventDataDict.put("incentivized", teakRewardId != null);
                eventDataDict.put("teakScheduleName", bundle.getString("teakScheduleName"));
                eventDataDict.put("teakCreativeName", bundle.getString("teakCreativeName"));

                if (teakRewardId != null) {
                    final Future<TeakNotification.Reward> rewardFuture = TeakNotification.Reward.rewardFromRewardId(teakRewardId);
                    if (rewardFuture != null) {
                        this.asyncExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    TeakNotification.Reward reward = rewardFuture.get();
                                    HashMap<String, Object> rewardMap = new HashMap<>(reward.json.toMap());
                                    eventDataDict.putAll(rewardMap);

                                    // Broadcast reward only if everything goes well
                                    final Intent rewardIntent = new Intent(Teak.REWARD_CLAIM_ATTEMPT);
                                    rewardIntent.putExtra("reward", eventDataDict);
                                    sendLocalBroadcast(rewardIntent);
                                } catch (Exception e) {
                                    Teak.log.exception(e);
                                } finally {
                                    final Intent broadcastEvent = new Intent(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);
                                    broadcastEvent.putExtras(bundle);
                                    broadcastEvent.putExtra("eventData", eventDataDict);
                                    sendLocalBroadcast(broadcastEvent);
                                }
                            }
                        });
                    }
                } else {
                    final Intent broadcastEvent = new Intent(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);
                    broadcastEvent.putExtras(bundle);
                    broadcastEvent.putExtra("eventData", eventDataDict);
                    sendLocalBroadcast(broadcastEvent);
                }
            }
        }
    }

    ///// Data Members

    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();
    private final LocalBroadcastManager localBroadcastManager;

    public static final ScheduledExecutorService operationQueue = Executors.newSingleThreadScheduledExecutor();
}
