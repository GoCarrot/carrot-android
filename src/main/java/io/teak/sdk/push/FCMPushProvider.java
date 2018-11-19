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
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import io.teak.sdk.Helpers;
import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.core.TeakCore;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.PushRegistrationEvent;

public class FCMPushProvider extends FirebaseMessagingService implements IPushProvider {
    private static FCMPushProvider Instance = null;

    private Context context;
    private FirebaseApp firebaseApp;

    public FCMPushProvider() {
        super();
        Instance = this;
    }

    public static FCMPushProvider initialize(@NonNull final Context context) throws IntegrationChecker.MissingDependencyException {
        IntegrationChecker.requireDependency("com.google.firebase.messaging.FirebaseMessagingService");

        if (Instance == null) {
            Instance = new FCMPushProvider();
            Instance.context = context;
        }

        return Instance;
    }

    public void postEvent(final Context context, final Intent intent) {
        final TeakCore teakCore = TeakCore.getWithoutThrow(context);
        if (teakCore == null) {
            Teak.log.e("google.fcm.null_teak_core", "TeakCore.getWithoutThrow returned null.");
        }
        TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, context, intent));
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

        // Future-Pat, this method will only be invoked via an incoming message,
        // in which case getApplicationContext() will work
        this.postEvent(getApplicationContext(), remoteMessage.toIntent());
    }

    //// IPushProvider

    @Override
    public void requestPushKey(@NonNull Map<String, Object> pushConfiguration) {
        // Future-Pat, this method will only be invoked via Teak SDK, so getApplicationContext()
        // will not work.
        if (this.firebaseApp == null) {
            // First try and get a Firebase App if it's already initialized
            try {
                this.firebaseApp = FirebaseApp.getInstance();
            } catch (Exception ignored) {
            }

            // Try and initialize our own, then, and name it just in case something else comes along
            // and wants to use the [DEFAULT] app name.
            if (this.firebaseApp == null) {
                try {
                    FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                            .setGcmSenderId((String) pushConfiguration.get("gcmSenderId"))
                            .setApplicationId((String) pushConfiguration.get("firebaseAppId"));
                    this.firebaseApp = FirebaseApp.initializeApp(this.context, builder.build(), "teak");
                } catch (Exception ignored) {
                }
            }
        }

        if (this.firebaseApp == null) {
            Teak.log.e("google.fcm.null_app", "Could not get or create Firebase App. Push notifications are unlikely to work.");
        } else {
            try {
                final Task<InstanceIdResult> instanceIdTask = FirebaseInstanceId
                        .getInstance(this.firebaseApp)
                        .getInstanceId();

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
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }
    }
}
