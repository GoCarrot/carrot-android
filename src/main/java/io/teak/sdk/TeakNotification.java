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
import android.os.PowerManager;

import android.content.Intent;
import android.content.Context;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;

import android.database.Cursor;
import android.database.SQLException;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import android.app.PendingIntent;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import android.util.Log;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;

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

/**
 * An app-to-user notification received from Teak via GCM.
 *
 * The following parameters from the GCM payload are used to create a <code>TeakNotification</code>.
 * <pre>
 * {@code
 * {
 *   [noAutolaunch] : boolean - automatically launch the app when a push notification is 'opened',
 *   [teakRewardId] : string  - associated Teak Reward Id,
 *   teakNotifId    : string  - associated Teak Notification Id,
 *   message        : string  - the body text of the notification,
 *   title          : string  - the title of the notification,
 *   tickerText     : string  - text for the ticker at the top of the screen,
 *   [extras]       : string  - JSON encoded extra data
 * }
 * }
 * </pre>
 */
public class TeakNotification {

    /** The 'tag' specified by Teak to the {@link NotificationCompat} */
    public static final String NOTIFICATION_TAG = "io.teak.sdk.TeakNotification";

    /**
     * The {@link Intent} action sent by Teak when a notification has been opened by the user.
     *
     * This allows you to take special actions, it is not required that you listen for it.
     */
    public static final String TEAK_PUSH_OPENED_INTENT_ACTION_SUFFIX = ".intent.TEAK_PUSH_OPENED";

    /**
     * Intent action used by Teak to notify you that there are pending inbox notifications.
     *
     * You can listen for this using a {@link BroadcastReceiver} and the {@link LocalBroadcastManager}.
     * <pre>
     * {@code
     *     IntentFilter filter = new IntentFilter();
     *     filter.addAction(TeakNotification.TEAK_INBOX_HAS_NOTIFICATIONS_INTENT);
     *     LocalBroadcastManager.getInstance(context).registerReceiver(yourBroadcastListener, filter);
     * }
     * </pre>
     */
    public static final String TEAK_INBOX_HAS_NOTIFICATIONS_INTENT = "io.teak.sdk.TeakNotification.intent.INBOX_HAS_NOTIFICATIONS";

    /**
     * Check to see if this notification has a reward attached to it.
     *
     * @return <code>true</code> if this notification has a reward attached to it.
     */
    public boolean hasReward() {
        return (this.teakRewardId != null);
    }

    /**
     * Get optional extra data associated with this notification.
     *
     * @return {@link JSONObject} containing extra data sent by the server.
     */
    public JSONObject getExtras() {
        return this.extras;
    }

    /**
     * Gets all of the pending notifications.
     *
     * @return A {@link List} containing all pending notifications.
     */
    public static List<TeakNotification> inbox() {
        List<TeakNotification> inbox = new ArrayList<TeakNotification>();
        String[] inboxReadColumns = {"notification_payload"};

        Cursor cursor = TeakNotification.database.query("inbox", inboxReadColumns,
                null, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            try {
                TeakNotification notif = new TeakNotification(cursor.getString(0));
                inbox.add(notif);
            } catch (Exception e) {
                Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
            }
            cursor.moveToNext();
        }
        cursor.close();

        return inbox;
    }

    /**
     * Get the number of pending notifications.
     *
     * @return The number of pending notifications.
     */
    public static long inboxCount() {
        return DatabaseUtils.queryNumEntries(TeakNotification.database, "inbox", null);
    }

    public class Reward {

        /** An unknown error occured while processing the reward. */
        public static final int UNKNOWN = 1;

        /** Valid reward claim, grant the user the reward. */
        public static final int GRANT_REWARD = 0;

        /** The user has attempted to claim a reward from their own social post. */
        public static final int SELF_CLICK = -1;

        /** The user has already been issued this reward. */
        public static final int ALREADY_CLICKED = -2;

        /** The reward has already been claimed its maximum number of times globally. */
        public static final int TOO_MANY_CLICKS = -3;

        /** The user has already claimed their maximum number of rewards of this type for the day. */
        public static final int EXCEED_MAX_CLICKS_FOR_DAY = -4;

        /** This reward has expired and is no longer valid. */
        public static final int EXPIRED = -5;

        /** Teak does not recognize this reward id. */
        public static final int INVALID_POST = -6;

        /**
         * Status of this reward.
         *
         * One of the following status codes:
         *   {@link Reward#UNKNOWN}
         *   {@link Reward#GRANT_REWARD}
         *   {@link Reward#SELF_CLICK}
         *   {@link Reward#ALREADY_CLICKED}
         *   {@link Reward#TOO_MANY_CLICKS}
         *   {@link Reward#EXCEED_MAX_CLICKS_FOR_DAY}
         *   {@link Reward#EXPIRED}
         *   {@link Reward#INVALID_POST}
         *
         * If status is {@link Reward#GRANT_REWARD}, the 'reward' field will contain the reward that should be granted.
         */
        public int status;

        /**
         * The reward(s) to grant, or <code>null</code>.
         *
         * <p>The reward(s) contained are in the format:
         * <code>{
         *   “internal_id” : quantity,
         *   “another_internal_id” : anotherQuantity
         * }</code></p>
         */
        public JSONObject reward;

        Reward(JSONObject json) {
            String statusString = json.optString("status");

            if(GRANT_REWARD_STRING.equals(statusString)) {
                status = GRANT_REWARD;
                reward = json.optJSONObject("reward");
            } else if(SELF_CLICK_STRING.equals(statusString)) {
                status = SELF_CLICK;
            } else if(ALREADY_CLICKED_STRING.equals(statusString)) {
                status = ALREADY_CLICKED;
            } else if(TOO_MANY_CLICKS_STRING.equals(statusString)) {
                status = TOO_MANY_CLICKS;
            } else if(EXCEED_MAX_CLICKS_FOR_DAY_STRING.equals(statusString)) {
                status = EXCEED_MAX_CLICKS_FOR_DAY;
            } else if(EXPIRED_STRING.equals(statusString)) {
                status = EXPIRED;
            } else if(INVALID_POST_STRING.equals(statusString)) {
                status = INVALID_POST;
            } else {
                status = UNKNOWN;
            }
        }

        private static final String GRANT_REWARD_STRING = "grant_reward";
        private static final String SELF_CLICK_STRING = "self_click";
        private static final String ALREADY_CLICKED_STRING = "already_clicked";
        private static final String TOO_MANY_CLICKS_STRING = "too_many_clicks";
        private static final String EXCEED_MAX_CLICKS_FOR_DAY_STRING = "exceed_max_clicks_for_day";
        private static final String EXPIRED_STRING = "expired";
        private static final String INVALID_POST_STRING = "invalid_post";
    }

    /**
     * Consumes the notification, removing it from the Inbox, and returns a {@link Reward}.
     *
     * <p>The <code>Reward</code> will be <code>null</code> if there is no reward associated
     * with the notification.</p>
     *
     * @return A {@link Future} which will contain the reward that should be granted, or <code>null</code> if there is no associated reward.
     */
    public Future<Reward> consumeNotification() {
        final TeakNotification notif = this;

        FutureTask<Reward> ret = new FutureTask<Reward>(new Callable<Reward>() {
            public Reward call() {
                if(!notif.hasReward()) {
                    notif.removeFromCache();
                    return null;
                }

                HttpsURLConnection connection = null;
                String userId = null;
                try {
                    userId = Teak.userId.get();
                } catch(Exception e) {
                    Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                    return null;
                }

                if(Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "Claiming reward id: " + notif.teakRewardId);
                }

                try {
                    // https://rewards.gocarrot.com/<<teak_reward_id>>/clicks?clicking_user_id=<<your_user_id>>
                    String requestBody = "clicking_user_id=" + URLEncoder.encode(userId, "UTF-8");

                    URL url = new URL("https://rewards.gocarrot.com/" + notif.teakRewardId + "/clicks");
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
                    InputStream is = null;
                    if (connection.getResponseCode() < 400) {
                        is = connection.getInputStream();
                    } else {
                        is = connection.getErrorStream();
                    }
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                    String line;
                    StringBuffer response = new StringBuffer();
                    while ((line = rd.readLine()) != null) {
                        response.append(line);
                        response.append('\r');
                    }
                    rd.close();

                    JSONObject responseJson = new JSONObject(response.toString());
                    JSONObject rewardResponse = responseJson.optJSONObject("response");
                    Reward reward = new Reward(rewardResponse);

                    if(Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "Reward claim response: " + responseJson.toString(2));
                    }

                    notif.removeFromCache();

                    return reward;
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                } finally {
                    connection.disconnect();
                    connection = null;
                }

                return null;
            }
        });

        Teak.asyncExecutor.submit(ret);

        return ret;
    }

    /**************************************************************************/

    static void init() {
        try {
            database = Teak.cacheOpenHelper.getWritableDatabase();
        } catch (SQLException e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
        }
    }

    private static SQLiteDatabase database;

    static final String INBOX_CACHE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS inbox(teak_notification_id INTEGER, android_id INTEGER, notification_payload TEXT)";

    String title;
    String message;
    String tickerText;
    String teakRewardId;
    int platformId;
    long teakNotifId;
    JSONObject extras;

    private TeakNotification(Bundle bundle) {
        this.title = bundle.getString("title");
        this.message = bundle.getString("message");
        this.tickerText = bundle.getString("tickerText");
        this.teakRewardId = bundle.getString("teakRewardId");
        try {
            this.extras = bundle.getString("extras") == null ? null : new JSONObject(bundle.getString("extras"));
        } catch(JSONException e) {
            this.extras = null;
        }

        try {
            this.teakNotifId = Long.parseLong(bundle.getString("teakNotifId"));
        } catch (Exception e) {
            this.teakNotifId = 0;
        }

        this.platformId = new Random().nextInt();

        ContentValues values = new ContentValues();
        values.put("teak_notification_id", this.teakNotifId);
        values.put("android_id", this.platformId);
        values.put("notification_payload", this.toJson());

        if(TeakNotification.database != null) {
            TeakNotification.database.insert("inbox", null, values);
        }
    }

    private TeakNotification(String json) throws JSONException {
        JSONObject contents = new JSONObject(json);

        title = contents.getString("title");
        message = contents.getString("message");
        tickerText = contents.getString("tickerText");
        teakRewardId = contents.getString("teakRewardId");
        platformId = contents.getInt("platformId");
        teakNotifId = contents.getLong("teakNotifId");
        this.extras = contents.optJSONObject("extras");
    }

    private String toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("title", title);
            json.put("message", message);
            json.put("tickerText", tickerText);
            json.put("teakRewardId", teakRewardId);
            json.put("platformId", Integer.valueOf(platformId));
            json.put("teakNotifId", Long.valueOf(teakNotifId));
            if(this.extras != null) { json.put("extras", this.extras); }
        } catch(JSONException e) {
            Log.e(Teak.LOG_TAG, "Error converting TeakNotification to JSON: " + Log.getStackTraceString(e));
        }
        return json.toString();
    }

    private void removeFromCache() {
        TeakNotification.database.delete("inbox", "teak_notification_id = " + this.teakNotifId, null);
    }

    static TeakNotification notificationFromIntent(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();

        if (!bundle.containsKey("teakNotifId")) {
            return null;
        }

        TeakNotification ret = new TeakNotification(bundle);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

        // TODO: If auto-dismiss
        if (true) {
            builder.setAutoCancel(true);
        }

        // TODO: Custom icon resource support
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), 0);
            builder.setSmallIcon(ai.icon);
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Unable to get icon resource id for GCM notification.");
        }

        // Configure notification content
        builder.setContentTitle(ret.title);
        builder.setContentText(ret.message);
        builder.setTicker(ret.tickerText);

        // Create intent to fire if/when notification is opened, attach bundle info
        Intent pushOpenedIntent = new Intent(context.getPackageName() + TEAK_PUSH_OPENED_INTENT_ACTION_SUFFIX);
        pushOpenedIntent.putExtras(bundle);

        // Send out the actual notification, with the push-opened intent
        PendingIntent pushOpenedPendingIntent = PendingIntent.getBroadcast(context, ret.platformId, pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT);
        builder.setContentIntent(pushOpenedPendingIntent);
        notificationManager.notify(NOTIFICATION_TAG, ret.platformId, builder.build());

        // Wake the screen
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, Teak.LOG_TAG);
        wakeLock.acquire();
        wakeLock.release();

        return ret;
    }
}
