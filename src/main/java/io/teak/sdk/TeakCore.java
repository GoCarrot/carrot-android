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

package io.teak.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Set;

import io.teak.sdk.event.OSListener;

public class TeakCore implements OSListener {
    public TeakCore() {

    }

    @Override
    public boolean lifecycle_onActivityCreated(Activity activity) {
        return true;
    }

    @Override
    public void lifecycle_onActivityPaused(Activity activity) {

    }

    @Override
    public void lifecycle_onActivityResumed(Activity activity) {

    }

    @Override
    public void notification_onNotificationReceived(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        TeakNotification notif = null;
        boolean showInForeground = Helpers.getBooleanFromBundle(bundle, "teakShowInForeground");
        if (showInForeground || Session.isExpiringOrExpired()) {
            notif = TeakNotification.remoteNotificationFromIntent(context, intent);
            if (notif == null) {
                return;
            }
        }
        final long teakNotifId = notif == null ? 0 : notif.teakNotifId;
        final String teakUserId = bundle.getString("teakUserId", null);

        if (teakUserId == null) {
            return;
        }

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

        // Send Notification Received Metric
        Session session = Session.getCurrentSessionOrNull();
        if (session != null) {
            HashMap<String, Object> payload = new HashMap<>();
            payload.put("app_id", Teak.appConfiguration.appId);
            payload.put("user_id", teakUserId);
            payload.put("platform_id", teakNotifId);

            if (teakNotifId == 0) {
                payload.put("impression", false);
            }

            new Thread(new Request("parsnip.gocarrot.com", "/notification_received", payload, session)).start();
        }
    }

    @Override
    public void notification_onNotificationAction(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();

        // Cancel any updates pending
        TeakNotification.cancel(context, bundle.getInt("platformId"));

        // Launch the app
        boolean autoLaunch = !Helpers.getBooleanFromBundle(bundle, "noAutolaunch");
        Teak.log.i("notification.opened", Helpers._.h("teakNotifId", bundle.getString("teakNotifId"), "autoLaunch", autoLaunch));

        if (autoLaunch) {
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
    }

    @Override
    public void notification_onNotificationCleared(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        TeakNotification.cancel(context, bundle.getInt("platformId"));
    }

    @Override
    public void purchase_onPurchaseSucceeded(final Map<String, Object> payload) {
    }

    @Override
    public void purchase_onPurchaseFailed(final Map<String, Object> payload) {

    }

    private final Session.EventListener sessionEventListener = new Session.EventListener() {
        @Override
        public void onStateChange(Session session, Session.State oldState, Session.State newState) {
            if (newState == Session.State.Created) {
                // If Session state is now 'Created', we need the configuration from the Teak server
                RemoteConfiguration.requestConfigurationForApp(session);
            }
        }
    };

    private final RemoteConfiguration.EventListener remoteConfigurationEventListener = new RemoteConfiguration.EventListener() {
        @Override
        public void onConfigurationReady(RemoteConfiguration configuration) {
            // Begin exception reporting, if enabled
            if (configuration.sdkSentryDsn != null) {
                Teak.sdkRaven.setDsn(configuration.sdkSentryDsn);
            }

            if (configuration.appSentryDsn != null) {
                Teak.appRaven.setDsn(configuration.appSentryDsn);

                if (!android.os.Debug.isDebuggerConnected()) {
                    Teak.appRaven.setAsUncaughtExceptionHandler();
                }
            }

            Teak.log.useRemoteConfiguration(configuration);
        }
    };

    private void cleanup(Activity activity) {

        RemoteConfiguration.removeEventListener(this.remoteConfigurationEventListener);
        Session.removeEventListener(this.sessionEventListener);

        //if (Teak.facebookAccessTokenBroadcast != null) {
        //    Teak.facebookAccessTokenBroadcast.unregister(activity.getApplicationContext());
        //}
    }
}
