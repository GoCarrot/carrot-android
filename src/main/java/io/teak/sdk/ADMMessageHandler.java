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

import android.util.Log;

import android.content.Intent;

import com.amazon.device.messaging.ADMMessageHandlerBase;
import com.amazon.device.messaging.ADMMessageReceiver;

public class ADMMessageHandler extends ADMMessageHandlerBase {
    private static final String LOG_TAG = "Teak:ADMMessageHandler";

    @Override
    protected void onMessage(Intent intent) {
        if (!Teak.isEnabled()) {
            Log.e(LOG_TAG, "Teak is disabled, ignoring onMessage().");
            return;
        }

        Teak.handlePushNotificationReceived(getApplicationContext(), intent);
    }

    @Override
    protected void onRegistrationError(String s) {
        Log.e(LOG_TAG, "Error registering for ADM id: " + s);
    }

    @Override
    protected void onRegistered(String s) {
        Teak.deviceConfiguration.admId = s;
        Teak.deviceConfiguration.notifyPushIdChangedListeners();
    }

    @Override
    protected void onUnregistered(String s) {
        Teak.deviceConfiguration.admId = null;
        Teak.deviceConfiguration.notifyPushIdChangedListeners();
    }

    public static class MessageAlertReceiver extends ADMMessageReceiver {
        public MessageAlertReceiver() {
            super(ADMMessageHandler.class);
        }
    }

    public ADMMessageHandler() {
        super(ADMMessageHandler.class.getName());
    }
}
