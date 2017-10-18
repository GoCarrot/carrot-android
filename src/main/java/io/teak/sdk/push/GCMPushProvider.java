package io.teak.sdk.push;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import io.teak.sdk.Helpers;
import io.teak.sdk.InstanceIDListenerService;
import io.teak.sdk.RetriableTask;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.PushRegistrationEvent;

public class GCMPushProvider implements IPushProvider {
    private final FutureTask<GoogleCloudMessaging> gcmFuture;
    private String gcmSenderId;

    public GCMPushProvider(@NonNull final Context context) {
        this.gcmFuture = new FutureTask<>(new RetriableTask<>(100, 2000L, new Callable<GoogleCloudMessaging>() {
            @Override
            public GoogleCloudMessaging call() throws Exception {
                return GoogleCloudMessaging.getInstance(context);
            }
        }));
        new Thread(this.gcmFuture).start();

        // Listen for events coming in from InstanceIDListenerService
        try {
            Class<?> clazz = Class.forName("com.google.android.gms.iid.InstanceIDListenerService");
            if (clazz != null) {
                InstanceIDListenerService.addEventListener(new InstanceIDListenerService.EventListener() {
                    @Override
                    public void onTokenRefresh() {
                        if (gcmSenderId == null) {
                            Teak.log.e("google.gcm.sender_id", "InstanceIDListenerService requested a token refresh, but gcmSenderId is null.");
                        } else {
                            requestPushKey(gcmSenderId);
                        }
                    }
                });
            }
        } catch (Exception ignored) {
            // This means that com.google.android.gms.iid.InstanceIDListenerService doesn't exist, which is fine
        }
    }

    @Override
    public void requestPushKey(@NonNull final String gcmSenderId) {
        this.gcmSenderId = gcmSenderId;

        try {
            final FutureTask<String> gcmRegistration = new FutureTask<>(new RetriableTask<>(100, 7000L, new Callable<String>() {
                @Override
                public String call() throws Exception {
                    GoogleCloudMessaging gcm = gcmFuture.get();
                    Teak.log.i("device_configuration", Helpers.mm.h("sender_id", gcmSenderId));

                    //noinspection deprecation - Using deprecated method for backward compatibility
                    return gcm.register(gcmSenderId);
                }
            }));
            new Thread(gcmRegistration).start();

            new Thread(new Runnable() {
                public void run() {
                    try {
                        String registrationId = gcmRegistration.get();

                        if (registrationId == null) {
                            Teak.log.e("google.gcm.null_token", "Got null token during GCM registration.");
                        } else {
                            Teak.log.i("google.gcm.registered", Helpers.mm.h("admId", registrationId));
                            if (Teak.isEnabled()) {
                                TeakEvent.postEvent(new PushRegistrationEvent("adm_push_key", registrationId));
                            }
                        }
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                }
            }).start();
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }
}
