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

class TeakNotification {

    public static boolean containsTeakNotification(Context context, Intent intent) {
        return true; // TODO: Actually check this
    }

    public static TeakNotification notificationFromIntent(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        Intent pushReceivedIntent = new Intent(context.getPackageName() + TeakGcmReceiver.TEAK_PUSH_RECEIVED_INTENT_ACTION_SUFFIX);

        if (!containsTeakNotification(context, intent)) {
            return null;
        }

        TeakNotification ret = new TeakNotification();
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

            int messageId = 0;
            if (bundle != null) {
                builder.setContentTitle(bundle.getString("title"));
                builder.setContentText(bundle.getString("message"));
                builder.setTicker(bundle.getString("tickerText"));
                try {
                    messageId = Integer.parseInt(bundle.getString("message_id"));
                } catch (Exception e) {
                    messageId = 0;
                }
            }

            pushReceivedIntent.putExtras(bundle);
            pushOpenedIntent.putExtras(bundle);

            PendingIntent pushOpenedPendingIntent = PendingIntent.getBroadcast(context, messageId, pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT);
            builder.setContentIntent(pushOpenedPendingIntent);

            context.sendBroadcast(pushReceivedIntent);

            notificationManager.notify("TEAK", messageId, builder.build());

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
