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
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import android.util.Log;

public final class TeakGcmReceiver extends BroadcastReceiver {
    private static final String GCM_RECEIVE_INTENT_ACTION = "com.google.android.c2dm.intent.RECEIVE";
    private static final String GCM_REGISTRATION_INTENT_ACTION = "com.google.android.c2dm.intent.REGISTRATION";

    public static final String TEAK_PUSH_RECEIVED_INTENT_ACTION_SUFFIX = ".intent.TEAK_PUSH_RECEIVED";
    public static final String TEAK_PUSH_OPENED_INTENT_ACTION_SUFFIX = ".intent.TEAK_PUSH_OPENED";

    public static final int TEAK_PUSH_RECEIVED = 0;
    public static final int TEAK_PUSH_OPENED = 1;
    public static final int TEAK_UNKNOWN = -1;
    public static final int TEAK_GCM_RECEIVED = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (GCM_REGISTRATION_INTENT_ACTION.equals(action)) {
            //
        } else if (GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            processIntent(context, intent);
        }
    }

    public static int processIntent(final Context context, final Intent intent) {
        String action = intent.getAction();

        if (action.endsWith(TEAK_PUSH_RECEIVED_INTENT_ACTION_SUFFIX)) {
            // Send push received metric
            return TEAK_PUSH_RECEIVED;
        } else if (action.endsWith(TEAK_PUSH_OPENED_INTENT_ACTION_SUFFIX)) {
            // Send push opened metric
            return TEAK_PUSH_OPENED;
        } else if (GCM_RECEIVE_INTENT_ACTION.equals(action)) {
            if (TeakNotification.containsTeakNotification(context, intent)) { // Test for if we should handle all messages or just Teak ones
                new AsyncTask<Void, String, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {

                        // <debug>
                        Bundle bundle = intent.getExtras();
                        if (bundle != null && !bundle.isEmpty()) {
                            for (String key : bundle.keySet()) {
                                Object value = bundle.get(key);
                                Log.d(Teak.LOG_TAG, String.format("%s %s (%s)", key, value.toString(), value.getClass().getName()));
                            }
                        }
                        // </debug>

                        TeakNotification notification = TeakNotification.notificationFromIntent(context, intent);

                        // Otherwise 
                        if (notification != null) {

                        }


                        return null;
                    }
                }.execute(null, null, null);
                return TEAK_GCM_RECEIVED;
            }
        }

        return TEAK_UNKNOWN;
    }
}
