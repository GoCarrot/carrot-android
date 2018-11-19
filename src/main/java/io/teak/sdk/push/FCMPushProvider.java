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
package io.teak.sdk.push;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import io.teak.sdk.Helpers;
import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.PushRegistrationEvent;

public class FCMPushProvider extends FirebaseMessagingService implements IPushProvider {
    public FCMPushProvider() {
        super();
    }

    public FCMPushProvider(@NonNull final Context context) throws IntegrationChecker.MissingDependencyException {
        IntegrationChecker.requireDependency("com.google.firebase.messaging.FirebaseMessagingService");
        FirebaseApp.initializeApp(context);
    }

    //// FirebaseMessagingService

    @Override
    public void onNewToken(String token) {
        if (token == null) {
            Teak.log.e("google.fcm.null_token", "Got null token from onNewToken.");
        } else {
            Teak.log.i("google.fcm.registered", Helpers.mm.h("fcmId", token));
            if (Teak.isEnabled()) {
                TeakEvent.postEvent(new PushRegistrationEvent("fcm_push_key", token));
            }
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        this.postEvent(getApplicationContext(), remoteMessage.toIntent());
    }

    // For unit testing
    public void postEvent(final Context context, final Intent intent) {
        TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, context, intent));
    }

    //// IPushProvider

    @Override
    public void requestPushKey(@NonNull final String ignored) {
        final Task<InstanceIdResult> instanceIdTask = FirebaseInstanceId.getInstance().getInstanceId();

        instanceIdTask.addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
            @Override
            public void onSuccess(InstanceIdResult instanceIdResult) {
                final String registrationId = instanceIdResult.getToken();
                Teak.log.i("google.fcm.registered", Helpers.mm.h("fcmId", registrationId));
                if (Teak.isEnabled()) {
                    TeakEvent.postEvent(new PushRegistrationEvent("fcm_push_key", registrationId));
                }
            }
        });
    }
}
