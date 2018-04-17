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

import android.app.ActivityManager;
import android.content.Context;
import android.support.annotation.NonNull;

import io.teak.sdk.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.teak.sdk.IObjectFactory;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.AdvertisingInfoEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.push.IPushProvider;

public class DeviceConfiguration {
    public Map<String, String> pushRegistration;

    public final String deviceId;
    public final String deviceManufacturer;
    public final String deviceModel;
    public final String deviceFallback;
    public final String platformString;
    public final int memoryClass;

    public String advertisingId;
    public boolean limitAdTracking;

    private final IPushProvider pushProvider;
    private String pushSenderId;

    public DeviceConfiguration(@NonNull Context context, @NonNull IObjectFactory objectFactory) {
        this.pushProvider = objectFactory.getPushProvider();

        final IAndroidDeviceInfo androidDeviceInfo = objectFactory.getAndroidDeviceInfo();

        if (android.os.Build.VERSION.RELEASE == null) {
            this.platformString = "android_unknown";
        } else {
            this.platformString = "android_" + android.os.Build.VERSION.RELEASE;
        }

        // Heap size (kind of)
        {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            this.memoryClass = am == null ? 0 : am.getMemoryClass();
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

        // Listen for Ad Info and Push Key events
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                switch (event.eventType) {
                    case AdvertisingInfoEvent.Type: {
                        advertisingId = ((AdvertisingInfoEvent) event).advertisingId;
                        limitAdTracking = ((AdvertisingInfoEvent) event).limitAdTracking;
                    } break;
                    case PushRegistrationEvent.Registered: {
                        pushRegistration = ((PushRegistrationEvent) event).registration;
                    } break;
                }
            }
        });

        // Request Ad Info, event will inform us when it's ready
        androidDeviceInfo.requestAdvertisingId();

        // TODO: Test/handle the case where remote config is already ready.

        // Listen for remote configuration events
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                    final RemoteConfiguration remoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;

                    // Override the provided GCM Sender Id with one from Teak, if applicable
                    if (remoteConfiguration.gcmSenderId != null) {
                        pushSenderId = remoteConfiguration.gcmSenderId;
                        // TODO: Future-Pat, when you add another push provider re-visit the RemoteConfiguration provided sender id
                    }

                    requestNewPushToken();
                }
            }
        });
    }

    public void requestNewPushToken() {
        if (this.pushSenderId == null) {
            final TeakConfiguration teakConfiguration = TeakConfiguration.get();
            this.pushSenderId = teakConfiguration.appConfiguration.pushSenderId;
        }

        // If the push provider isn't GCM, the push sender id parameter is ignored
        if (this.pushProvider != null) {
            this.pushProvider.requestPushKey(this.pushSenderId);
        }
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> ret = new HashMap<>();
        if (this.pushRegistration != null) {
            ret.put("pushRegistration", this.pushRegistration);
        }
        ret.put("deviceId", this.deviceId);
        ret.put("deviceManufacturer", this.deviceManufacturer);
        ret.put("deviceModel", this.deviceModel);
        ret.put("deviceFallback", this.deviceFallback);
        ret.put("platformString", this.platformString);
        ret.put("memoryClass", this.memoryClass);
        return ret;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.toMap())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
