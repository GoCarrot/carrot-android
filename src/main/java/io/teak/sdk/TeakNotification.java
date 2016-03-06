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
import android.database.sqlite.SQLiteDatabase;

import android.app.PendingIntent;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import android.util.Log;

import java.util.Random;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * {
 *   [noAutolaunch] : boolean - automatically launch the app when a push notification is 'opened',
 *   [teakRewardId] : string  - associated Teak Reward Id,
 *   teakNotifId    : string  - associated Teak Notification Id,
 *   message        : string  - the body text of the notification,
 *   title          : string  - the title of the notification,
 *   tickerText     : string  - text for the ticker at the top of the screen,
 * }
 */
public class TeakNotification {

    public static final String NOTIFICATION_TAG = "io.teak.sdk.TeakNotification";

    public static final String TEAK_PUSH_OPENED_INTENT_ACTION_SUFFIX = ".intent.TEAK_PUSH_OPENED";

    public boolean hasReward() {
        return (teakRewardId != null);
    }

    /**************************************************************************/

    public static void init() {
        try {
            database = Teak.cacheOpenHelper.getWritableDatabase();
        } catch (SQLException e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
        }
    }

    private static SQLiteDatabase database;

    static final String REQUEST_CACHE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS inbox(teak_notification_id INTEGER, android_id INTEGER, notification_payload TEXT)";

    String title;
    String message;
    String tickerText;
    String teakRewardId;
    int platformId;
    long teakNotifId;

    private TeakNotification(Bundle bundle) {
        this.title = bundle.getString("title");
        this.message = bundle.getString("message");
        this.tickerText = bundle.getString("tickerText");
        this.teakRewardId = bundle.getString("teakRewardId");
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

    private TeakNotification(JSONObject contents) throws JSONException {
        title = contents.getString("title");
        message = contents.getString("message");
        tickerText = contents.getString("tickerText");
        teakRewardId = contents.getString("teakRewardId");
        platformId = contents.getInt("platformId");
        teakNotifId = contents.getLong("teakNotifId");
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
        } catch(JSONException e) {
            Log.e(Teak.LOG_TAG, "Error converting TeakNotification to JSON: " + Log.getStackTraceString(e));
        }
        return json.toString();
    }

/*
    public AsyncTask<Void, Void, String> claimReward() {
        // https://rewards.gocarrot.com/<<teak_reward_id>>/clicks?clicking_user_id=<<your_user_id>>
        return new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        String ret = null;
                        HttpsURLConnection connection = null;
                        String requestBody = "clicking_user_id=" + Teak.getUserId();

                        try {
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

                            Gson gson = new Gson();
                            Map<String, Object> responseJson = gson.fromJson(response.toString(), new TypeToken<Map<String, Object>>() {}.getType());
                            Map<String, Object> foo = (Map<String, Object>)responseJson.get("response");
                            String rewardStatus = (String)foo.get("status");

                            Log.d(Teak.LOG_TAG, "Reward status: " + rewardStatus);

                            // Either way, uncache notification if there were no exceptions
                            Teak.getTeakCache().uncacheNotification(teakNotifId);

                            ret = rewardStatus;
                        } catch (Exception e) {
                            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
                        } finally {
                            connection.disconnect();
                            connection = null;
                        }

                        return ret;
                    }
                }.execute(null, null, null);
    }
*/
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
