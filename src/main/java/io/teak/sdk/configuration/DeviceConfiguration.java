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
package io.teak.sdk.configuration;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.amazon.device.messaging.ADM;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import io.teak.sdk.ADMMessageHandler;
import io.teak.sdk.Helpers.mm;
import io.teak.sdk.InstanceIDListenerService;
import io.teak.sdk.RetriableTask;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.AdvertisingInfoEvent;
import io.teak.sdk.event.DeviceInfoEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import io.teak.sdk.io.IAndroidDeviceInfo;

public class DeviceConfiguration {
    public Map<String, String> pushRegistration;

    public final boolean admIsSupported;
    public final boolean googlePlayIsSupported;

    public final String deviceId;
    public final String deviceManufacturer;
    public final String deviceModel;
    public final String deviceFallback;
    public final String platformString;

    public final SharedPreferences preferences;

    public String advertisingId;
    public boolean limitAdTracking;

    private FutureTask<GoogleCloudMessaging> gcm;
    private Object admInstance;
    private String gcmSenderId;

    public DeviceConfiguration(@NonNull final Context context, @NonNull IAndroidDeviceInfo androidDeviceInfo) {
        if (android.os.Build.VERSION.RELEASE == null) {
            this.platformString = "android_unknown";
        } else {
            this.platformString = "android_" + android.os.Build.VERSION.RELEASE;
        }

        // ADM support
        this.admIsSupported = androidDeviceInfo.hasADM();

        // Google Play support
        this.googlePlayIsSupported = androidDeviceInfo.hasGooglePlay();

        // Preferences file
        {
            SharedPreferences tempPreferences = null;
            try {
                tempPreferences = context.getSharedPreferences(Teak.PREFERENCES_FILE, Context.MODE_PRIVATE);
            } catch (Exception e) {
                Teak.log.exception(e);
            } finally {
                this.preferences = tempPreferences;
            }

            if (this.preferences == null) {
                Teak.log.e("device_configuration", "getSharedPreferences() returned null. Some caching is disabled.");
            }
        }

        // Device model/manufacturer
        {
            Map<String, String> deviceInfo = androidDeviceInfo.getDeviceDescription();
            this.deviceManufacturer = deviceInfo.get("deviceManufacturer");
            this.deviceModel = deviceInfo.get("deviceModel");
            this.deviceFallback = deviceInfo.get("deviceFallback");
        }

        // Device id
        this.deviceId = androidDeviceInfo.getDeviceId();
        if (this.deviceId == null) {
            return;
        }

        // Listen for events coming in from InstanceIDListenerService
        try {
            Class<?> clazz = Class.forName("com.google.android.gms.iid.InstanceIDListenerService");
            if (clazz != null) {
                InstanceIDListenerService.addEventListener(new InstanceIDListenerService.EventListener() {
                    @Override
                    public void onTokenRefresh() {
                        reRegisterPushToken("InstanceIDListenerService");
                    }
                });
            }
        } catch (Exception ignored) {
            // This means that com.google.android.gms.iid.InstanceIDListenerService doesn't exist
        }

        // Listen for Ad Info and Push Key events
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(AdvertisingInfoEvent.Type)) {
                    advertisingId = ((AdvertisingInfoEvent)event).advertisingId;
                    limitAdTracking = ((AdvertisingInfoEvent)event).limitAdTracking;
                } else if (event.eventType.equals(PushRegistrationEvent.Type)) {
                    pushRegistration = ((PushRegistrationEvent)event).registration;
                }
            }
        });

        // Request Ad Info
        androidDeviceInfo.requestAdvertisingId();

        // Listen for ADM messages if ADM is available
        if (this.admIsSupported) {
            ADMMessageHandler.addEventListener(new ADMMessageHandler.EventListener() {
                @Override
                public void onRegistered(String s) {
                    admId = s;
                    notifyPushIdChangedListeners();
                }

                @Override
                public void onUnregistered() {
                    admId = null;
                    notifyPushIdChangedListeners();
                }
            });

            ADM adm = new ADM(context);
            this.admInstance = adm;
            if (adm.getRegistrationId() == null) {
                adm.startRegister();
            } else {
                this.admId = adm.getRegistrationId();
            }
        } else {
            this.gcm = new FutureTask<>(new RetriableTask<>(100, 2000L, new Callable<GoogleCloudMessaging>() {
                @Override
                public GoogleCloudMessaging call() throws Exception {
                    return GoogleCloudMessaging.getInstance(context);
                }
            }));
            new Thread(this.gcm).start();

            // TODO: Event this
            //RemoteConfiguration.addEventListener(this.remoteConfigurationEventListener);
        }
    }
/*
    private final RemoteConfiguration.EventListener remoteConfigurationEventListener = new RemoteConfiguration.EventListener() {
        @Override
        public void onConfigurationReady(RemoteConfiguration configuration) {
            // GCM sender id
            gcmSenderId = configuration.gcmSenderId == null ? appConfiguration.pushSenderId : configuration.gcmSenderId;
            registerForGCM(appConfiguration, "remote_configuration");
        }
    };
*/
    void reRegisterPushToken(String source) {
        if (this.admIsSupported) {
            ADM adm = (ADM) this.admInstance;
            adm.startRegister();
        } else {
            registerForGCM(source);
        }
    }

    private void registerForGCM(final String source) {
        try {
            if (gcmSenderId != null) {
                final DeviceConfiguration _this = this;

                final FutureTask<String> gcmRegistration = new FutureTask<>(new RetriableTask<>(100, 7000L, new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        GoogleCloudMessaging gcm = _this.gcm.get();
                        Teak.log.i("device_configuration", mm.h("sender_id", gcmSenderId, "source", source));
                        return gcm.register(gcmSenderId);
                    }
                }));
                new Thread(gcmRegistration).start();

                new Thread(new Runnable() {
                    public void run() {
                        try {
                            String registration = gcmRegistration.get();

                            if (registration == null) {
                                Teak.log.e("device_configuration", "Got null token during GCM registration.");
                                return;
                            }

                            _this.assignGcmRegistration(registration);
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                }).start();
            }
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> to_h() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("gcmId", this.gcmId);
        ret.put("deviceId", this.deviceId);
        ret.put("deviceManufacturer", this.deviceManufacturer);
        ret.put("deviceModel", this.deviceModel);
        ret.put("deviceFallback", this.deviceFallback);
        ret.put("platformString", this.platformString);
        return ret;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.to_h())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
