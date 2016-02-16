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

import android.app.PendingIntent;
import android.os.PowerManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import javax.net.ssl.HttpsURLConnection;

import java.net.URL;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class TeakNotification {

    public String title;
    public String message;
    public String tickerText;
    public String teakRewardId;
    public int platformId;
    public long teakNotifId;

    private TeakNotification(Bundle bundle) {
        if (bundle != null) {
            title = bundle.getString("title");
            message = bundle.getString("message");
            tickerText = bundle.getString("tickerText");
            teakRewardId = bundle.getString("teakRewardId");
            try {
                teakNotifId = Long.parseLong(bundle.getString("teakNotifId"));
            } catch (Exception e) {
                teakNotifId = 0;
            }

            platformId = new Random().nextInt();
        }
    }

    TeakNotification(String json) {
        Map<String, Object> contents = new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());

        title = (String)contents.get("title");
        message = (String)contents.get("message");
        tickerText = (String)contents.get("tickerText");
        teakRewardId = (String)contents.get("teakRewardId");
        platformId = ((Double)contents.get("platformId")).intValue();
        teakNotifId = ((Double)contents.get("teakNotifId")).longValue();
    }

    String toJson() {
        HashMap<String, Object> contents = new HashMap<String, Object>();
        contents.put("title", title);
        contents.put("message", message);
        contents.put("tickerText", tickerText);
        contents.put("teakRewardId", teakRewardId);
        contents.put("platformId", new Integer(platformId));
        contents.put("teakNotifId", new Long(teakNotifId));
        return new Gson().toJson(contents);
    }

    public boolean hasReward() {
        return (teakRewardId != null);
    }

    public static List<TeakNotification> notificationInbox() {
        return Teak.getTeakCache().notificationInbox();
    }

    // TODO: Should really standardize on AsyncTask
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

    static boolean containsTeakNotification(Context context, Intent intent) {
        return true; // TODO: Actually check this
    }

    static TeakNotification notificationFromIntent(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        Intent pushReceivedIntent = new Intent(context.getPackageName() + TeakGcmReceiver.TEAK_PUSH_RECEIVED_INTENT_ACTION_SUFFIX);

        if (!containsTeakNotification(context, intent)) {
            return null;
        }

        TeakNotification ret = new TeakNotification(bundle);
        Teak.getTeakCache().cacheNotification(ret);

        if (true) { // TODO: Filter out data messages
            Intent pushOpenedIntent = new Intent(context.getPackageName() + TeakGcmReceiver.TEAK_PUSH_OPENED_INTENT_ACTION_SUFFIX);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

            if (true) { // TODO: If auto-dismiss
                builder.setAutoCancel(true);
            }

            PackageManager pm = context.getPackageManager();
            try {
                ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), 0);
                builder.setSmallIcon(ai.icon);
            } catch (Exception e) {
                Log.e(Teak.LOG_TAG, "Unable to get icon resource id for GCM notification.");
            }

            builder.setContentTitle(ret.title);
            builder.setContentText(ret.message);
            builder.setTicker(ret.tickerText);

            pushReceivedIntent.putExtras(bundle);
            pushOpenedIntent.putExtras(bundle);

            PendingIntent pushOpenedPendingIntent = PendingIntent.getBroadcast(context, ret.platformId, pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT);
            builder.setContentIntent(pushOpenedPendingIntent);

            context.sendBroadcast(pushReceivedIntent);

            notificationManager.notify("TEAK", ret.platformId, builder.build());

            // Wake the screen
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, Teak.LOG_TAG);
            wakeLock.acquire();
            wakeLock.release();
        } else {
            pushReceivedIntent.putExtras(bundle);
            context.sendBroadcast(pushReceivedIntent);
        }

        return ret;
    }
}
