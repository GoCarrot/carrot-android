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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.teak.sdk.Helpers;
import io.teak.sdk.NotificationBuilder;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.NotificationDisplayEvent;
import io.teak.sdk.event.PurchaseEvent;
import io.teak.sdk.event.PurchaseFailedEvent;
import io.teak.sdk.event.PushNotificationEvent;

public class TeakCore implements ITeakCore {
    public TeakCore(Context context) {
        this.localBroadcastManager = LocalBroadcastManager.getInstance(context);

        TeakEvent.addEventListener(teakEventListener);
    }

    private final TeakEvent.EventListener teakEventListener = new TeakEvent.EventListener() {
        @Override
        public void onNewEvent(@NonNull TeakEvent event) {
            switch (event.eventType) {
                case LifecycleEvent.Resumed: {
                    Intent intent = ((LifecycleEvent) event).intent;
                    if (!intent.getBooleanExtra("teakProcessedForPush", false)) {
                        intent.putExtra("teakProcessedForPush", true);
                        checkIntentForPushLaunchAndSendBroadcasts(intent);
                    }
                    break;
                }
                case PurchaseEvent.Type: {
                    final Map<String, Object> payload = ((PurchaseEvent) event).payload;
                    Teak.log.i("purchase.succeeded", payload);
                    /*
                    Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                        @Override
                        public void run(Session session) {
                            new Request("/me/purchase", payload, session).run();
                        }
                    });*/
                    break;
                }
                case PurchaseFailedEvent.Type: {
                    final Map<String, Object> payload = new HashMap<>();
                    payload.put("errorCode", ((PurchaseFailedEvent) event).errorCode);
                    Teak.log.i("purchase.failed", payload);
                    /*
                    Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                        @Override
                        public void run(Session session) {
                            new Request("/me/purchase", payload, session).run();
                        }
                    });*/
                    break;
                }
                case PushNotificationEvent.Received: {
                    final Intent intent = ((PushNotificationEvent)event).intent;
                    if (intent == null) break;

                    final Bundle bundle = intent.getExtras();
                    if (!bundle.containsKey("teakNotifId")) break;

                    // Debug output
                    HashMap<String, Object> debugHash = new HashMap<>();
                    Set<String> keys = bundle.keySet();
                    for (String key : keys) {
                        Object o = bundle.get(key);
                        if (o instanceof String) {
                            try {
                                JSONObject jsonObject = new JSONObject(o.toString());
                                o = Helpers.jsonToMap(jsonObject);
                            } catch (Exception ignored) {
                            }
                        }
                        debugHash.put(key, o);
                    }
                    Teak.log.i("notification.received", debugHash);

                    // Foreground notification?
                    boolean showInForeground = Helpers.getBooleanFromBundle(bundle, "teakShowInForeground");
                    //if (!showInForeground && Session.isExpiringOrExpired()) break;
                    // TODO: Session

                    // Create Teak Notification
                    final TeakNotification teakNotification = new TeakNotification(bundle);

                    // Add platformId to bundle
                    bundle.putInt("platformId", teakNotification.platformId);

                    // Create native notification
                    final Context context = ((PushNotificationEvent)event).context;
                    Notification nativeNotification = NotificationBuilder.createNativeNotification(context, bundle, teakNotification);
                    if (nativeNotification == null) break;

                    // Send display event
                    TeakEvent.postEvent(new NotificationDisplayEvent(teakNotification, nativeNotification));

                    // Send metric
                    final String teakUserId = bundle.getString("teakUserId", null);
                    if (teakUserId == null) break;

                    // TODO: Session
                    /*
                    final Session session = Session.getCurrentSessionOrNull();
                    final TeakConfiguration teakConfiguration = TeakConfiguration.get();
                    if (session != null && teakConfiguration != null) {
                        HashMap<String, Object> payload = new HashMap<>();
                        payload.put("app_id", teakConfiguration.appConfiguration.appId);
                        payload.put("user_id", teakUserId);
                        payload.put("platform_id", teakNotification.teakNotifId);
                        if (teakNotification.teakNotifId == 0) {
                            payload.put("impression", false);
                        }

                        asyncExecutor.submit(new Request("parsnip.gocarrot.com", "/notification_received", payload, session));
                    }*/
                    break;
                }

                case PushNotificationEvent.Interaction: {
                    final Intent intent = ((PushNotificationEvent)event).intent;
                    if (intent == null) break;

                    final Bundle bundle = intent.getExtras();
                    final boolean autoLaunch = !Helpers.getBooleanFromBundle(bundle, "noAutolaunch");
                    Teak.log.i("notification.opened", Helpers.mm.h("teakNotifId", bundle.getString("teakNotifId"), "autoLaunch", autoLaunch));

                    // Launch the app
                    final Context context = ((PushNotificationEvent)event).context;
                    if (context != null && autoLaunch) {
                        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        launchIntent.putExtras(bundle);
                        if (bundle.getString("teakDeepLink") != null) {
                            Uri teakDeepLink = Uri.parse(bundle.getString("teakDeepLink"));
                            launchIntent.setData(teakDeepLink);
                        }
                        context.startActivity(launchIntent);
                    }
                    break;
                }
            }
        }
    };

    private void checkIntentForPushLaunchAndSendBroadcasts(Intent intent) {
        if (intent.hasExtra("teakNotifId")) {
            Bundle bundle = intent.getExtras();

            // Send broadcast
            if (this.localBroadcastManager != null) {
                final HashMap<String, Object> eventDataDict = new HashMap<>();
                if (bundle.getString("teakRewardId") != null) {
                    eventDataDict.put("incentivized", true);
                    eventDataDict.put("teakRewardId", bundle.getString("teakRewardId"));
                } else {
                    eventDataDict.put("incentivized", false);
                }
                if (bundle.getString("teakScheduleName") != null) eventDataDict.put("teakScheduleName", bundle.getString("teakScheduleName"));
                if (bundle.getString("teakCreativeName") != null) eventDataDict.put("teakCreativeName", bundle.getString("teakCreativeName"));

                final Intent broadcastEvent = new Intent(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);
                broadcastEvent.putExtras(bundle);
                broadcastEvent.putExtra("eventData", eventDataDict);
                this.localBroadcastManager.sendBroadcast(broadcastEvent);

                String teakRewardId = bundle.getString("teakRewardId");
                if (teakRewardId != null) {
                    final Future<TeakNotification.Reward> rewardFuture = TeakNotification.Reward.rewardFromRewardId(teakRewardId);
                    if (rewardFuture != null) {
                        this.asyncExecutor.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    TeakNotification.Reward reward = rewardFuture.get();
                                    HashMap<String, Object> rewardMap = Helpers.jsonToMap(reward.json);
                                    rewardMap.putAll(eventDataDict);

                                    // Broadcast reward only if everything goes well
                                    final Intent rewardIntent = new Intent(Teak.REWARD_CLAIM_ATTEMPT);
                                    rewardIntent.putExtra("reward", rewardMap);
                                    localBroadcastManager.sendBroadcast(rewardIntent);
                                } catch (Exception e) {
                                    Teak.log.exception(e);
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    ///// Data Members

    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();
    private final LocalBroadcastManager localBroadcastManager;
}
