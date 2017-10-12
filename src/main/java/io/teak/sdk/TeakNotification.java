/* Teak -- Copyright (C) 2016 GoCarrot Inc.
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

import android.app.NotificationManager;
import android.os.Bundle;

import android.content.Intent;
import android.content.Context;

import android.app.Notification;

import android.support.v4.app.NotificationCompat;

import android.util.SparseArray;

import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;
import org.json.JSONException;

import io.teak.sdk.Helpers.mm;

/**
 * An app-to-user notification received from Teak via GCM.
 * <p/>
 * The following parameters from the GCM payload are used to create a <code>TeakNotification</code>.
 * <pre>
 * {@code
 * {
 *   [noAutolaunch] : boolean - automatically launch the app when a push notification is 'opened',
 *   [teakRewardId] : string  - associated Teak Reward Id,
 *   [deepLink]     : string  - a deep link to navigate to on launch,
 *   teakNotifId    : string  - associated Teak Notification Id,
 *   message        : string  - the body text of the notification,
 *   longText       : string  - text displayed when the notification is expanded,
 *   imageAssetA    : string  - URI of an image asset to use for a banner image,
 *   [extras]       : string  - JSON encoded extra data
 * }
 * }
 * </pre>
 */
public class TeakNotification {
    /**
     * The 'tag' specified by Teak to the {@link NotificationCompat}
     */
    public static final String NOTIFICATION_TAG = "io.teak.sdk.TeakNotification";

    /**
     * The {@link Intent} action sent by Teak when a notification has been opened by the user.
     * <p/>
     * This allows you to take special actions, it is not required that you listen for it.
     */
    public static final String TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX = ".intent.TEAK_NOTIFICATION_OPENED";

    /**
     * The {@link Intent} action sent by Teak when a notification has been cleared by the user.
     * <p/>
     * This allows you to take special actions, it is not required that you listen for it.
     */
    public static final String TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX = ".intent.TEAK_NOTIFICATION_CLEARED";

    /**
     * Get optional extra data associated with this notification.
     *
     * @return {@link JSONObject} containing extra data sent by the server.
     */
    @SuppressWarnings("unused")
    public JSONObject getExtras() {
        return this.extras;
    }

    public static class Reward {

        /**
         * An unknown error occured while processing the reward.
         */
        public static final int UNKNOWN = 1;

        /**
         * Valid reward claim, grant the user the reward.
         */
        public static final int GRANT_REWARD = 0;

        /**
         * The user has attempted to claim a reward from their own social post.
         */
        public static final int SELF_CLICK = -1;

        /**
         * The user has already been issued this reward.
         */
        public static final int ALREADY_CLICKED = -2;

        /**
         * The reward has already been claimed its maximum number of times globally.
         */
        public static final int TOO_MANY_CLICKS = -3;

        /**
         * The user has already claimed their maximum number of rewards of this type for the day.
         */
        public static final int EXCEED_MAX_CLICKS_FOR_DAY = -4;

        /**
         * This reward has expired and is no longer valid.
         */
        public static final int EXPIRED = -5;

        /**
         * Teak does not recognize this reward id.
         */
        public static final int INVALID_POST = -6;

        /**
         * Status of this reward.
         * <p/>
         * One of the following status codes:
         * {@link Reward#UNKNOWN}
         * {@link Reward#GRANT_REWARD}
         * {@link Reward#SELF_CLICK}
         * {@link Reward#ALREADY_CLICKED}
         * {@link Reward#TOO_MANY_CLICKS}
         * {@link Reward#EXCEED_MAX_CLICKS_FOR_DAY}
         * {@link Reward#EXPIRED}
         * {@link Reward#INVALID_POST}
         * <p/>
         * If status is {@link Reward#GRANT_REWARD}, the 'reward' field will contain the reward that should be granted.
         */
        public int status;

        public JSONObject json;

        Reward(JSONObject json) {
            String statusString = "";
            // Try/catch is unneeded practically, but needed to compile
            try {
                statusString = json.isNull("status") ? "" : json.getString("status");
            } catch (Exception ignored) {
            }
            this.json = json;

            if (GRANT_REWARD_STRING.equals(statusString)) {
                status = GRANT_REWARD;
            } else if (SELF_CLICK_STRING.equals(statusString)) {
                status = SELF_CLICK;
            } else if (ALREADY_CLICKED_STRING.equals(statusString)) {
                status = ALREADY_CLICKED;
            } else if (TOO_MANY_CLICKS_STRING.equals(statusString)) {
                status = TOO_MANY_CLICKS;
            } else if (EXCEED_MAX_CLICKS_FOR_DAY_STRING.equals(statusString)) {
                status = EXCEED_MAX_CLICKS_FOR_DAY;
            } else if (EXPIRED_STRING.equals(statusString)) {
                status = EXPIRED;
            } else if (INVALID_POST_STRING.equals(statusString)) {
                status = INVALID_POST;
            } else {
                status = UNKNOWN;
            }
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s{status: %d, json: %s}", super.toString(), status, json == null ? "null" : json.toString());
        }

        private static final String GRANT_REWARD_STRING = "grant_reward";
        private static final String SELF_CLICK_STRING = "self_click";
        private static final String ALREADY_CLICKED_STRING = "already_clicked";
        private static final String TOO_MANY_CLICKS_STRING = "too_many_clicks";
        private static final String EXCEED_MAX_CLICKS_FOR_DAY_STRING = "exceed_max_clicks_for_day";
        private static final String EXPIRED_STRING = "expired";
        private static final String INVALID_POST_STRING = "invalid_post";

        /**
         * @return A {@link Future} which will contain the reward that should be granted, or <code>null</code> if there is no associated reward.
         */
        @SuppressWarnings("unused")
        public static Future<Reward> rewardFromRewardId(final String teakRewardId) {
            if (Teak.Instance == null || !Teak.Instance.isEnabled()) {
                Teak.log.e("reward", "Teak is disabled, ignoring rewardFromRewardId().");
                return null;
            }

            if (teakRewardId == null || teakRewardId.isEmpty()) {
                Teak.log.e("reward", "teakRewardId cannot be null or empty");
                return null;
            }

            final ArrayBlockingQueue<Reward> q = new ArrayBlockingQueue<>(1);
            final FutureTask<Reward> ret = new FutureTask<>(new Callable<Reward>() {
                public Reward call() {
                    try {
                        return q.take();
                    } catch (InterruptedException e) {
                        Teak.log.exception(e);
                    }
                    return null;
                }
            });
            new Thread(ret).start();

            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                @Override
                public void run(Session session) {
                    HttpsURLConnection connection = null;

                    Teak.log.i("reward.claim", mm.h("teakRewardId", teakRewardId));

                    try {
                        // https://rewards.gocarrot.com/<<teak_reward_id>>/clicks?clicking_user_id=<<your_user_id>>
                        String requestBody = "clicking_user_id=" + URLEncoder.encode(session.userId(), "UTF-8");

                        URL url = new URL("https://rewards.gocarrot.com/" + teakRewardId + "/clicks");
                        connection = (HttpsURLConnection) url.openConnection();

                        connection.setRequestProperty("Accept-Charset", "UTF-8");
                        connection.setUseCaches(false);
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        connection.setRequestProperty("Content-Length",
                                "" + Integer.toString(requestBody.getBytes().length));

                        // Send request
                        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                        wr.writeBytes(requestBody);
                        wr.flush();
                        wr.close();

                        // Get Response
                        InputStream is;
                        if (connection.getResponseCode() < 400) {
                            is = connection.getInputStream();
                        } else {
                            is = connection.getErrorStream();
                        }
                        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                        String line;
                        StringBuilder response = new StringBuilder();
                        while ((line = rd.readLine()) != null) {
                            response.append(line);
                            response.append('\r');
                        }
                        rd.close();

                        JSONObject responseJson = new JSONObject(response.toString());
                        JSONObject rewardResponse = responseJson.optJSONObject("response");

                        JSONObject fullParsedResponse = new JSONObject();
                        fullParsedResponse.put("status", rewardResponse.get("status"));
                        if(rewardResponse.optJSONObject("reward") != null) {
                            fullParsedResponse.put("reward", rewardResponse.get("reward"));
                        } else if(rewardResponse.opt("reward") != null) {
                            fullParsedResponse.put("reward", new JSONObject(rewardResponse.getString("reward")));
                        }
                        Reward reward = new Reward(fullParsedResponse);

                        Teak.log.i("reward.claim", Helpers.jsonToMap(responseJson));

                        q.offer(reward);
                    } catch (Exception e) {
                        Teak.log.exception(e);
                        q.offer(null);
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            });

            return ret;
        }
    }

    /**
     * Schedules a push notification for some time in the future.
     *
     * @param creativeId     The identifier of the notification in the Teak dashboard (will create if not found).
     * @param defaultMessage The default message to send, may be over-ridden in the dashboard.
     * @param delayInSeconds The delay in seconds from now to send the notification.
     * @return The identifier of the scheduled notification (see {@link TeakNotification#cancelNotification(String)} or null.
     */
    @SuppressWarnings("unused")
    public static FutureTask<String> scheduleNotification(final String creativeId, final String defaultMessage, final long delayInSeconds) {
        if (Teak.Instance == null || !Teak.Instance.isEnabled()) {
            Teak.log.e("notification.schedule.disabled", "Teak is disabled, ignoring scheduleNotification().");
            return null;
        }

        if (creativeId == null || creativeId.isEmpty()) {
            Teak.log.e("notification.schedule.error", "creativeId cannot be null or empty");
            return null;
        }

        if (defaultMessage == null || defaultMessage.isEmpty()) {
            Teak.log.e("notification.schedule.error", "defaultMessage cannot be null or empty");
            return null;
        }

        final ArrayBlockingQueue<String> q = new ArrayBlockingQueue<>(1);
        final FutureTask<String> ret = new FutureTask<>(new Callable<String>() {
            public String call() {
                try {
                    return q.take();
                } catch (InterruptedException e) {
                    Teak.log.exception(e);
                }
                return null;
            }
        });

        Session.whenUserIdIsOrWasReadyRun(new Session.SessionRunnable() {
            @Override
            public void run(Session session) {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("identifier", creativeId);
                payload.put("message", defaultMessage);
                payload.put("offset", delayInSeconds);

                new Request("/me/local_notify.json", payload, session) {
                    @Override
                    protected void done(int responseCode, String responseBody) {
                        try {
                            JSONObject response = new JSONObject(responseBody);
                            if (response.getString("status").equals("ok")) {
                                q.offer(response.getJSONObject("event").getString("id"));
                                Teak.log.i("notification.schedule", "Scheduled notification.", Helpers.mm.h("notification", response.getJSONObject("event").getString("id")));
                            } else {
                                q.offer("");
                                Teak.log.e("notification.schedule.error", "Error scheduling notification.", Helpers.mm.h("response", response.toString()));
                            }
                        } catch (Exception ignored) {
                            q.offer("");
                        }

                        ret.run();
                    }
                }.run();
            }
        });
        return ret;
    }

    /**
     * Cancel a push notification that was scheduled with {@link TeakNotification#scheduleNotification(String, String, long)}
     *
     * @param scheduleId
     * @return
     */
    @SuppressWarnings("unused")
    public static FutureTask<String> cancelNotification(final String scheduleId) {
        if (Teak.Instance == null || !Teak.Instance.isEnabled()) {
            Teak.log.e("notification.cancel.disabled", "Teak is disabled, ignoring cancelNotification().");
            return null;
        }

        if (scheduleId == null || scheduleId.isEmpty()) {
            Teak.log.e("notification.cancel.error", "scheduleId cannot be null or empty");
            return null;
        }

        final ArrayBlockingQueue<String> q = new ArrayBlockingQueue<>(1);
        final FutureTask<String> ret = new FutureTask<>(new Callable<String>() {
            public String call() {
                try {
                    return q.take();
                } catch (InterruptedException e) {
                    Teak.log.exception(e);
                }
                return null;
            }
        });

        Session.whenUserIdIsOrWasReadyRun(new Session.SessionRunnable() {
            @Override
            public void run(Session session) {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("id", scheduleId);

                new Request("/me/cancel_local_notify.json", payload, session) {
                    @Override
                    protected void done(int responseCode, String responseBody) {
                        try {
                            JSONObject response = new JSONObject(responseBody);
                            if (response.getString("status").equals("ok")) {
                                q.offer(response.getJSONObject("event").getString("id"));
                            } else {
                                q.offer("");
                            }
                        } catch (Exception ignored) {
                            q.offer("");
                        }
                        ret.run();
                    }
                }.run();
            }
        });

        return ret;
    }

    /**************************************************************************/

    static final String INBOX_CACHE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS inbox(teak_notification_id INTEGER, android_id INTEGER, notification_payload TEXT)";

    // Version of the push from Teak
    static final int TEAK_NOTIFICATION_V0 = 0;
    int notificationVersion = TEAK_NOTIFICATION_V0;

    // v1
    String message;
    String longText;
    String teakRewardId;
    String imageAssetA;
    String teakDeepLink;
    int platformId;
    long teakNotifId;
    JSONObject extras;

    // v2+
    JSONObject display;

    static SparseArray<Thread> notificationUpdateThread = new SparseArray<>();

    private TeakNotification(Bundle bundle) {
        this.message = bundle.getString("message");
        this.longText = bundle.getString("longText");
        this.teakRewardId = bundle.getString("teakRewardId");
        this.imageAssetA = bundle.getString("imageAssetA");
        this.teakDeepLink = bundle.getString("teakDeepLink");
        try {
            this.extras = bundle.getString("extras") == null ? null : new JSONObject(bundle.getString("extras"));
        } catch (JSONException e) {
            this.extras = null;
        }

        try {
            this.notificationVersion = Integer.parseInt(bundle.getString("version"));
        } catch (Exception ignored) {
            this.notificationVersion = TEAK_NOTIFICATION_V0;
        }

        if (bundle.getString("display") != null) {
            try {
                this.display = new JSONObject(bundle.getString("display"));
            } catch (Exception e) {
                Teak.log.exception(e);
                this.notificationVersion = TEAK_NOTIFICATION_V0;
            }
        }

        try {
            this.teakNotifId = Long.parseLong(bundle.getString("teakNotifId"));
        } catch (Exception e) {
            this.teakNotifId = 0;
        }

        this.platformId = new Random().nextInt();
    }

    static NotificationManager notificationManager;

    static TeakNotification remoteNotificationFromIntent(final Context context, Intent intent) {
        final Bundle bundle = intent.getExtras();

        if (!bundle.containsKey("teakNotifId")) {
            return null;
        }

        final TeakNotification ret = new TeakNotification(bundle);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Add platformId to bundle
                bundle.putInt("platformId", ret.platformId);
                // Create native notification
                Notification nativeNotification = NotificationBuilder.createNativeNotification(context, bundle, ret);
                if (nativeNotification != null) {
                    displayNotification(context, ret, nativeNotification);
                }
            }
        }).start();

        return ret;
    }

    static void displayNotification(Context context, TeakNotification teakNotif, Notification nativeNotification) {
        // TODO: This is TeakIO functionality
        if (notificationManager == null) {
            try {
                notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            } catch (Exception e) {
                Teak.log.exception(e);
                return;
            }
        }

        // Send it out
        Teak.log.i("notification.show", Helpers.mm.h("teakNotifId", teakNotif.teakNotifId, "platformId", teakNotif.platformId));

        try {
            notificationManager.notify(NOTIFICATION_TAG, teakNotif.platformId, nativeNotification);
        } catch (SecurityException ignored) {
            // This likely means that they need the VIBRATE permission on old versions of Android
            Teak.log.e("permission_needed.vibrate", "Please add this to your AndroidManifest.xml: <uses-permission android:name=\"android.permission.VIBRATE\" />");
        }

        // TODO: Here is where any kind of thread/update logic will live
    }

    static void cancel(Context context, int platformId) {
        // TODO: This is TeakIO functionality
        if (notificationManager == null) {
            try {
                notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            } catch (Exception e) {
                Teak.log.exception(e);
                return;
            }
        }

        Teak.log.i("notification.cancel", Helpers.mm.h("platformId", platformId));

        notificationManager.cancel(NOTIFICATION_TAG, platformId);

        Thread updateThread = TeakNotification.notificationUpdateThread.get(platformId);
        if (updateThread != null) {
            updateThread.interrupt();
        }
    }
}
