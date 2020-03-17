package io.teak.sdk.configuration;

import android.app.ActivityManager;
import android.content.Context;
import androidx.annotation.NonNull;
import io.teak.sdk.IObjectFactory;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.AdvertisingInfoEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.json.JSONObject;
import io.teak.sdk.push.IPushProvider;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DeviceConfiguration {
    public Map<String, String> pushRegistration;

    public final String deviceId;
    public final String deviceManufacturer;
    public final String deviceModel;
    public final String deviceFallback;
    public final String deviceBoard;
    public final String deviceProduct;
    public final String platformString;
    public final int memoryClass;

    public String advertisingId;
    public boolean limitAdTracking;

    private final IPushProvider pushProvider;
    private Map<String, Object> pushConfiguration = new HashMap<>();

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
            this.deviceBoard = deviceInfo.get("deviceBoard");
            this.deviceProduct = deviceInfo.get("deviceProduct");
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

        // Push Configuration (Can be overridden via RemoteConfiguration)
        TeakConfiguration.addEventListener(new TeakConfiguration.EventListener() {
            @Override
            public void onConfigurationReady(@NonNull TeakConfiguration configuration) {
                DeviceConfiguration.this.pushConfiguration.put("gcmSenderId", configuration.appConfiguration.gcmSenderId);
                DeviceConfiguration.this.pushConfiguration.put("firebaseAppId", configuration.appConfiguration.firebaseAppId);
                DeviceConfiguration.this.pushConfiguration.put("firebaseApiKey", configuration.appConfiguration.firebaseApiKey);
                DeviceConfiguration.this.pushConfiguration.put("ignoreDefaultFirebaseConfiguration", configuration.appConfiguration.ignoreDefaultFirebaseConfiguration);
            }
        });

        // TODO: Test/handle the case where remote config is already ready.

        // Listen for remote configuration events
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                    final RemoteConfiguration remoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;

                    // Override the provided GCM Sender Id with one from Teak, if applicable
                    if (remoteConfiguration.gcmSenderId != null) {
                        DeviceConfiguration.this.pushConfiguration.put("gcmSenderId", remoteConfiguration.gcmSenderId);
                    }

                    // Override the provided Firebase App Id with one from Teak, if applicable
                    if (remoteConfiguration.firebaseAppId != null) {
                        DeviceConfiguration.this.pushConfiguration.put("firebaseAppId", remoteConfiguration.firebaseAppId);
                    }

                    // Override ignoring the default Firebase configuration
                    if (remoteConfiguration.firebaseAppId != null) {
                        DeviceConfiguration.this.pushConfiguration.put("ignoreDefaultFirebaseConfiguration", remoteConfiguration.firebaseAppId);
                    }

                    requestNewPushToken();
                }
            }
        });
    }

    public void requestNewPushToken() {
        if (this.pushProvider != null) {
            this.pushProvider.requestPushKey(this.pushConfiguration);
        }
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> ret = new HashMap<>();
        if (this.pushRegistration != null) {
            ret.put("pushRegistration", this.pushRegistration);
        }
        if (this.advertisingId != null) {
            ret.put("advertisingId", this.advertisingId);
            ret.put("limitAdTracking", this.limitAdTracking);
        }
        ret.put("deviceId", this.deviceId);
        ret.put("deviceManufacturer", this.deviceManufacturer);
        ret.put("deviceModel", this.deviceModel);
        ret.put("deviceFallback", this.deviceFallback);
        ret.put("deviceBoard", this.deviceBoard);
        ret.put("deviceProduct", this.deviceProduct);
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
