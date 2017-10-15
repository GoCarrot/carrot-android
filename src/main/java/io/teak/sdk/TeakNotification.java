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

import android.os.Bundle;

import android.content.Intent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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

import io.teak.sdk.Helpers.mm;
import io.teak.sdk.core.Session;

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
     * The {@link Intent} action sent by Teak when a notification has been opened by the user.
     * <p/>
     * This allows you to take special actions, it is not required that you listen for it.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX = ".intent.TEAK_NOTIFICATION_OPENED";

    /**
     * The {@link Intent} action sent by Teak when a notification has been cleared by the user.
     * <p/>
     * This allows you to take special actions, it is not required that you listen for it.
     */
    @SuppressWarnings("WeakerAccess")
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

    @SuppressWarnings("WeakerAccess")
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
                        if (rewardResponse.optJSONObject("reward") != null) {
                            fullParsedResponse.put("reward", rewardResponse.get("reward"));
                        } else if (rewardResponse.opt("reward") != null) {
                            fullParsedResponse.put("reward", new JSONObject(rewardResponse.getString("reward")));
                        }
                        Reward reward = new Reward(fullParsedResponse);

                        Teak.log.i("reward.claim", Helpers.jsonToMap(responseJson));

                        q.offer(reward);
                    } catch (Exception e) {
                        Teak.log.exception(e);
                        q.offer(null); // TODO: Fix this?
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
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static FutureTask<String> scheduleNotification(final String creativeId, final String defaultMessage, final long delayInSeconds) {
        if (Teak.Instance == null || !Teak.Instance.isEnabled()) {
            Teak.log.e("notification.schedule.disabled", "Teak is disabled, ignoring scheduleNotification().");

            final Map<String, Object> ret = new HashMap<>();
            ret.put("status", "error.teak.disabled");

            return new FutureTask<>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return new JSONObject(ret).toString();
                }
            });
        }

        if (creativeId == null || creativeId.isEmpty()) {
            Teak.log.e("notification.schedule.error", "creativeId cannot be null or empty");

            final Map<String, Object> ret = new HashMap<>();
            ret.put("status", "error.parameter.creativeId");

            return new FutureTask<>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return new JSONObject(ret).toString();
                }
            });
        }

        if (defaultMessage == null || defaultMessage.isEmpty()) {
            Teak.log.e("notification.schedule.error", "defaultMessage cannot be null or empty");

            final Map<String, Object> ret = new HashMap<>();
            ret.put("status", "error.parameter.defaultMessage");

            return new FutureTask<>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return new JSONObject(ret).toString();
                }
            });
        }

        if (delayInSeconds > 2630000 /* one month in seconds */ || delayInSeconds < 0) {
            Teak.log.e("notification.schedule.error", "delayInSeconds can not be negative, or greater than one month");

            final Map<String, Object> ret = new HashMap<>();
            ret.put("status", "error.parameter.delayInSeconds");

            return new FutureTask<>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return new JSONObject(ret).toString();
                }
            });
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

                            final Map<String, Object> contents = new HashMap<>();
                            contents.put("status", response.getString("status"));

                            if (response.getString("status").equals("ok")) {
                                Teak.log.i("notification.schedule", "Scheduled notification.", mm.h("notification", response.getJSONObject("event").getString("id")));
                                contents.put("data", response.getJSONObject("event").getString("id"));
                            } else {
                                Teak.log.e("notification.schedule.error", "Error scheduling notification.", mm.h("response", response.toString()));
                            }

                            q.offer(new JSONObject(contents).toString());
                        } catch (Exception e) {
                            Teak.log.exception(e, mm.h("teakCreativeId", creativeId));

                            final Map<String, Object> contents = new HashMap<>();
                            contents.put("status", "error.internal");
                            q.offer(new JSONObject(contents).toString());
                        }

                        ret.run();
                    }
                }
                    .run();
            }
        });
        return ret;
    }

    /**
     * Cancel a push notification that was scheduled with {@link TeakNotification#scheduleNotification(String, String, long)}
     *
     * @param scheduleId Id returned by {@link TeakNotification#scheduleNotification(String, String, long)}
     * @return The status of the operation
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static FutureTask<String> cancelNotification(final String scheduleId) {
        if (Teak.Instance == null || !Teak.Instance.isEnabled()) {
            Teak.log.e("notification.cancel.disabled", "Teak is disabled, ignoring cancelNotification().");

            final Map<String, Object> ret = new HashMap<>();
            ret.put("status", "error.teak.disabled");

            return new FutureTask<>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return new JSONObject(ret).toString();
                }
            });
        }

        if (scheduleId == null || scheduleId.isEmpty()) {
            Teak.log.e("notification.cancel.error", "scheduleId cannot be null or empty");

            final Map<String, Object> ret = new HashMap<>();
            ret.put("status", "error.parameter.creativeId");

            return new FutureTask<>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return new JSONObject(ret).toString();
                }
            });
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

                            final Map<String, Object> contents = new HashMap<>();
                            contents.put("status", response.getString("status"));

                            if (response.getString("status").equals("ok")) {
                                Teak.log.i("notification.cancel", "Canceled notification.", mm.h("notification", scheduleId));
                                contents.put("data", response.getJSONObject("event").getString("id"));
                            } else {
                                Teak.log.e("notification.cancel.error", "Error canceling notification.", mm.h("response", response.toString()));
                            }
                            q.offer(new JSONObject(contents).toString());
                        } catch (Exception e) {
                            final Map<String, Object> contents = new HashMap<>();
                            contents.put("status", "error.internal");
                            q.offer(new JSONObject(contents).toString());
                            Teak.log.exception(e, _.h("scheduleId", scheduleId));
                        }
                        ret.run();
                    }
                }.run();
            }
        });

        return ret;
    }

    @SuppressWarnings("unused")
    public static FutureTask<String> cancelAll() {
        if (!Teak.isEnabled()) {
            Teak.log.e("notification.cancel_all.disabled", "Teak is disabled, ignoring cancelAll().");

            final Map<String, Object> ret = new HashMap<>();
            ret.put("status", "error.teak.disabled");

            return new FutureTask<>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return new JSONObject(ret).toString();
                }
            });
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

                new Request("/me/cancel_all_local_notifications.json", payload, session) {
                    @Override
                    protected void done(int responseCode, String responseBody) {
                        try {
                            JSONObject response = new JSONObject(responseBody);

                            final Map<String, Object> contents = new HashMap<>();
                            contents.put("status", response.getString("status"));

                            if (response.getString("status").equals("ok")) {
                                contents.put("data", response.getJSONArray("canceled"));
                                Teak.log.i("notification.cancel_all", "Canceled all notifications.");
                            } else {
                                Teak.log.e("notification.cancel_all.error", "Error canceling all notifications.", mm.h("response", response.toString()));
                            }
                            q.offer(new JSONObject(contents).toString());
                        } catch (Exception e) {
                            final Map<String, Object> contents = new HashMap<>();
                            contents.put("status", "error.internal");
                            q.offer(new JSONObject(contents).toString());
                            Teak.log.exception(e, _.h("responseBody", responseBody));
                        }
                        ret.run();
                    }
                }
                    .run();
            }
        });

        return ret;
    }

    /**************************************************************************/

    // Version of the push from Teak
    static final int TEAK_NOTIFICATION_V0 = 0;
    int notificationVersion = TEAK_NOTIFICATION_V0;

    final String teakCreativeName;

    // v1
    public final int platformId;
    public final long teakNotifId;

    final String message;
    final String longText;
    final String imageAssetA;

    @SuppressWarnings("WeakerAccess")
    final JSONObject extras;
    @SuppressWarnings("WeakerAccess")
    final String teakDeepLink;
    @SuppressWarnings("WeakerAccess")
    final String teakRewardId;

    // v2+
    final JSONObject display;

    public TeakNotification(Bundle bundle) {
        this.message = bundle.getString("message");
        this.longText = bundle.getString("longText");
        this.teakRewardId = bundle.getString("teakRewardId");
        this.imageAssetA = bundle.getString("imageAssetA");
        this.teakDeepLink = bundle.getString("teakDeepLink");
        this.teakCreativeName = bundle.getString("teakCreativeName");

        JSONObject tempExtras = null;
        try {
            tempExtras = bundle.getString("extras") == null ? null : new JSONObject(bundle.getString("extras"));
        } catch (Exception ignored) {
        }
        this.extras = tempExtras;

        try {
            this.notificationVersion = Integer.parseInt(bundle.getString("version"));
        } catch (Exception ignored) {
            this.notificationVersion = TEAK_NOTIFICATION_V0;
        }

        JSONObject tempDisplay = null;
        if (bundle.getString("display") != null) {
            try {
                tempDisplay = new JSONObject(bundle.getString("display"));
            } catch (Exception e) {
                Teak.log.exception(e);
                this.notificationVersion = TEAK_NOTIFICATION_V0;
            }
        }
        this.display = tempDisplay;

        long tempTeakNotifId = 0;
        try {
            tempTeakNotifId = Long.parseLong(bundle.getString("teakNotifId"));
        } catch (Exception ignored) {
        }
        this.teakNotifId = tempTeakNotifId;

        this.platformId = new Random().nextInt();
    }
}
