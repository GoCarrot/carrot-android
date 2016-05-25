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

import android.content.BroadcastReceiver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;

import android.content.Intent;
import android.content.Context;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;

import android.database.Cursor;
import android.database.DatabaseUtils;

import android.app.Notification;
import android.app.PendingIntent;

import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.view.View;
import android.widget.RemoteViews;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLConnection;
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
     * Intent action used by Teak to notify you that there are pending inbox notifications.
     * <p/>
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
    @SuppressWarnings("unused")
    public JSONObject getExtras() {
        return this.extras;
    }

    /**
     * Gets all of the pending notifications.
     *
     * @return A {@link List} containing all pending notifications.
     */
    @SuppressWarnings("unused")
    public static List<TeakNotification> inbox() {
        List<TeakNotification> inbox = new ArrayList<>();
        String[] inboxReadColumns = {"notification_payload"};

        if (Teak.database != null) {
            Cursor cursor = Teak.database.query("inbox", inboxReadColumns,
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
        }

        return inbox;
    }

    /**
     * Gets a notification by id.
     *
     * @return A {@link TeakNotification} for the provided id, or null if not found.
     */
    @SuppressWarnings("unused")
    public static TeakNotification byTeakNotifId(String teakNotifId) {
        TeakNotification notif = null;
        String[] inboxReadColumns = {"notification_payload"};

        if (Teak.database != null) {
            Cursor cursor = Teak.database.query("inbox", inboxReadColumns,
                    "teak_notification_id=?", new String[]{teakNotifId}, null, null, null);

            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                try {
                    notif = new TeakNotification(cursor.getString(0));
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                }
                cursor.moveToNext();
            }
            cursor.close();
        }

        return notif;
    }

    /**
     * Get the number of pending notifications.
     *
     * @return The number of pending notifications.
     */
    public static long inboxCount() {
        if (Teak.database != null) {
            // API v11 version: DatabaseUtils.queryNumEntries(Teak.database, "inbox", null);
            return DatabaseUtils.longForQuery(Teak.database, "SELECT COUNT(*) FROM inbox", null);
        } else {
            return 0;
        }
    }

    public class Reward {

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

        /**
         * The reward(s) to grant, or <code>null</code>.
         * <p/>
         * <p>The reward(s) contained are in the format:
         * <code>{
         * “internal_id” : quantity,
         * “another_internal_id” : anotherQuantity
         * }</code></p>
         */
        public JSONObject reward;

        Reward(JSONObject json) {
            String statusString = json.optString("status");

            if (GRANT_REWARD_STRING.equals(statusString)) {
                status = GRANT_REWARD;
                reward = json.optJSONObject("reward");
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
     * <p/>
     * <p>The <code>Reward</code> will be <code>null</code> if there is no reward associated
     * with the notification.</p>
     *
     * @return A {@link Future} which will contain the reward that should be granted, or <code>null</code> if there is no associated reward.
     */
    @SuppressWarnings("unused")
    public Future<Reward> consumeNotification() {
        final TeakNotification notif = this;

        FutureTask<Reward> ret = new FutureTask<>(new Callable<Reward>() {
            public Reward call() {
                if (!notif.hasReward()) {
                    notif.removeFromCache();
                    return null;
                }

                HttpsURLConnection connection = null;
                String userId;
                try {
                    userId = Teak.userId.get();
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                    return null;
                }

                if (Teak.isDebug) {
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
                    Reward reward = new Reward(rewardResponse);

                    if (Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "Reward claim response: " + responseJson.toString(2));
                    }

                    notif.removeFromCache();

                    return reward;
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }

                return null;
            }
        });

        Teak.asyncExecutor.submit(ret);

        return ret;
    }

    /**************************************************************************/

    static final String INBOX_CACHE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS inbox(teak_notification_id INTEGER, android_id INTEGER, notification_payload TEXT)";

    String message;
    String longText;
    String teakRewardId;
    String imageAssetA;
    String deepLink;
    int platformId;
    long teakNotifId;
    JSONObject extras;

    static SparseArray<Thread> notificationUpdateThread = new SparseArray<>();

    private TeakNotification(Bundle bundle) {
        this.message = bundle.getString("message");
        this.longText = bundle.getString("longText");
        this.teakRewardId = bundle.getString("teakRewardId");
        this.imageAssetA = bundle.getString("imageAssetA");
        this.deepLink = bundle.getString("deepLink");
        try {
            this.extras = bundle.getString("extras") == null ? null : new JSONObject(bundle.getString("extras"));
        } catch (JSONException e) {
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

        if (Teak.database != null) {
            Teak.database.insert("inbox", null, values);
        }
    }

    private TeakNotification(String json) throws JSONException {
        JSONObject contents = new JSONObject(json);

        this.message = contents.getString("message");
        this.longText = contents.optString("longText");
        this.teakRewardId = contents.optString("teakRewardId");
        this.imageAssetA = contents.optString("imageAssetA");
        this.deepLink = contents.optString("deepLink");
        this.platformId = contents.getInt("platformId");
        this.teakNotifId = contents.getLong("teakNotifId");
        this.extras = contents.optJSONObject("extras");
    }

    private String toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("message", this.message);
            json.putOpt("longText", this.longText);
            json.putOpt("imageAssetA", this.imageAssetA);
            json.putOpt("deepLink", this.deepLink);
            json.putOpt("teakRewardId", this.teakRewardId);
            json.put("platformId", Integer.valueOf(this.platformId));
            json.put("teakNotifId", Long.valueOf(this.teakNotifId));
            json.putOpt("extras", this.extras);
        } catch (JSONException e) {
            Log.e(Teak.LOG_TAG, "Error converting TeakNotification to JSON: " + Log.getStackTraceString(e));
        }
        return json.toString();
    }

    private void removeFromCache() {
        if (Teak.database != null) {
            Teak.database.delete("inbox", "teak_notification_id = " + this.teakNotifId, null);
        }
    }

    static NotificationManagerCompat notificationManager;

    static TeakNotification remoteNotificationFromIntent(final Context context, Intent intent) {
        final Bundle bundle = intent.getExtras();

        if (!bundle.containsKey("teakNotifId")) {
            return null;
        }

        final TeakNotification ret = new TeakNotification(bundle);

        Teak.asyncExecutor.submit(new Runnable() {
            @Override
            public void run() {
                // Add platformId to bundle
                bundle.putInt("platformId", ret.platformId);
                // Create native notification
                Notification nativeNotification = createNativeNotification(context, bundle, ret);
                if (nativeNotification != null) {
                    displayNotification(context, ret, nativeNotification);
                }
            }
        });

        return ret;
    }

    static void displayNotification(Context context, TeakNotification teakNotif, Notification nativeNotification) {
        if (notificationManager == null) {
            notificationManager = NotificationManagerCompat.from(context);
        }

        // Send it out
        if (Teak.isDebug) {
            Log.d(Teak.LOG_TAG, "Showing Notification");
            Log.d(Teak.LOG_TAG, "       Teak id: " + teakNotif.teakNotifId);
            Log.d(Teak.LOG_TAG, "   Platform id: " + teakNotif.platformId);
        }
        notificationManager.notify(NOTIFICATION_TAG, teakNotif.platformId, nativeNotification);

        // TODO: Here is where any kind of thread/update logic will live
    }

    static Notification createNativeNotification(Context context, Bundle bundle, TeakNotification teakNotificaton) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext());

        // Configue notification behavior
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        builder.setOnlyAlertOnce(true);
        builder.setAutoCancel(true);

        // Set small view image
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), 0);
            builder.setSmallIcon(ai.icon);
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Unable to load icon resource for Notification.");
            return null;
        }

        // Create intent to fire if/when notification is cleared
        Intent pushClearedIntent = new Intent(context.getPackageName() + TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX);
        pushClearedIntent.putExtras(bundle);
        PendingIntent pushClearedPendingIntent = PendingIntent.getBroadcast(context, 0, pushClearedIntent, PendingIntent.FLAG_ONE_SHOT);
        builder.setDeleteIntent(pushClearedPendingIntent);

        // Create intent to fire if/when notification is opened, attach bundle info
        Intent pushOpenedIntent = new Intent(context.getPackageName() + TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX);
        pushOpenedIntent.putExtras(bundle);
        PendingIntent pushOpenedPendingIntent = PendingIntent.getBroadcast(context, 0, pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT);
        builder.setContentIntent(pushOpenedPendingIntent);

        // Notification builder
        Notification nativeNotification = builder.build();

        // Configure notification small view
        RemoteViews smallView = new RemoteViews(
                context.getPackageName(),
                R.layout.teak_notif_no_title
        );

        // Set small view image
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), 0);
            smallView.setImageViewResource(R.id.left_image, ai.icon);
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Unable to load icon resource for Notification.");
            return null;
        }

        // Set small view text
        smallView.setTextViewText(R.id.text, Html.fromHtml(teakNotificaton.message));
        nativeNotification.contentView = smallView;

        // Check for Jellybean (API 16, 4.1)+ for expanded view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                teakNotificaton.longText != null &&
                !teakNotificaton.longText.isEmpty()) {
            RemoteViews bigView = new RemoteViews(
                    context.getPackageName(),
                    R.layout.teak_big_notif_image_text
            );

            // Set big view text
            bigView.setTextViewText(R.id.text, Html.fromHtml(teakNotificaton.longText));

            URI imageAssetA = null;
            try {
                imageAssetA = new URI(teakNotificaton.imageAssetA);
            } catch (Exception ignored) {
            }

            Bitmap topImageBitmap = null;
            if (imageAssetA != null) {
                try {
                    URL aURL = new URL(imageAssetA.toString());
                    URLConnection conn = aURL.openConnection();
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    BufferedInputStream bis = new BufferedInputStream(is);
                    topImageBitmap = BitmapFactory.decodeStream(bis);
                    bis.close();
                    is.close();
                } catch (Exception ignored) {
                }
            }

            if (topImageBitmap == null) {
                try {
                    InputStream istr = context.getAssets().open("teak_notif_large_image_default.png");
                    topImageBitmap = BitmapFactory.decodeStream(istr);
                } catch (Exception ignored) {
                }
            }

            if (topImageBitmap != null) {
                // Set large bitmap
                bigView.setImageViewBitmap(R.id.top_image, topImageBitmap);

                // Use reflection to avoid compile-time issues, we check minimum API version above
                try {
                    Field bigContentViewField = nativeNotification.getClass().getField("bigContentView");
                    bigContentViewField.set(nativeNotification, bigView);
                } catch (Exception ignored) {
                }
            } else {
                Log.e(Teak.LOG_TAG, "Unable to load image asset for Notification.");
                // Hide pulldown
                smallView.setViewVisibility(R.id.pulldown_layout, View.INVISIBLE);
            }

        } else {
            // Hide pulldown
            smallView.setViewVisibility(R.id.pulldown_layout, View.INVISIBLE);
        }

        return nativeNotification;
    }

    static void cancel(Context context, int platformId) {
        if (Teak.isDebug) {
            Log.d(Teak.LOG_TAG, "Canceling notification id: " + platformId);
        }

        notificationManager.cancel(NOTIFICATION_TAG, platformId);
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        Thread updateThread = TeakNotification.notificationUpdateThread.get(platformId);
        if (updateThread != null) {
            updateThread.interrupt();
        }
    }
}
