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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.amazon.device.messaging.ADM;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;

import io.teak.sdk.Helpers._;

class DeviceConfiguration {
    public String gcmId;
    public String admId;

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

    private final AppConfiguration appConfiguration;

    private static final String PREFERENCE_DEVICE_ID = "io.teak.sdk.Preferences.DeviceId";

    public DeviceConfiguration(@NonNull final Context context, @NonNull AppConfiguration appConfiguration) {
        if (android.os.Build.VERSION.RELEASE == null) {
            this.platformString = "android_unknown";
        } else {
            this.platformString = "android_" + android.os.Build.VERSION.RELEASE;
        }

        this.appConfiguration = appConfiguration;

        // ADM support
        {
            boolean tempAdmSupported = false;
            try {
                Class.forName("com.amazon.device.messaging.ADM");
                tempAdmSupported = new ADM(context).isSupported();
            } catch (Exception ignored) {
            }
            this.admIsSupported = tempAdmSupported;
        }

        // Google Play support
        {
            boolean tempGooglePlaySupported = false;
            try {
                Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
                tempGooglePlaySupported = true;
            } catch (Exception ignored) {
            }
            this.googlePlayIsSupported = tempGooglePlaySupported;
        }

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
        // https://raw.githubusercontent.com/jaredrummler/AndroidDeviceNames/master/library/src/main/java/com/jaredrummler/android/device/DeviceName.java
        {
            this.deviceManufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
            this.deviceModel = Build.MODEL == null ? "" : Build.MODEL;
            if (this.deviceModel.startsWith(Build.MANUFACTURER)) {
                this.deviceFallback = capitalize(Build.MODEL);
            } else {
                this.deviceFallback = capitalize(Build.MANUFACTURER) + " " + Build.MODEL;
            }
        }

        // Device id
        {
            String tempDeviceId = null;
            try {
                tempDeviceId = UUID.nameUUIDFromBytes(android.os.Build.SERIAL.getBytes("utf8")).toString();
            } catch (Exception e) {
                Teak.log.e("device_configuration", "android.os.Build.SERIAL not available, falling back to Settings.Secure.ANDROID_ID.");
                Teak.log.exception(e);
            }

            if (tempDeviceId == null) {
                try {
                    String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                    if (androidId.equals("9774d56d682e549c")) {
                        Teak.log.e("device_configuration", "Settings.Secure.ANDROID_ID == '9774d56d682e549c', falling back to random UUID stored in preferences.");
                    } else {
                        tempDeviceId = UUID.nameUUIDFromBytes(androidId.getBytes("utf8")).toString();
                    }
                } catch (Exception e) {
                    Teak.log.e("device_configuration", "Error generating device id from Settings.Secure.ANDROID_ID, falling back to random UUID stored in preferences.");
                    Teak.log.exception(e);
                }
            }

            if (tempDeviceId == null) {
                if (this.preferences != null) {
                    tempDeviceId = this.preferences.getString(PREFERENCE_DEVICE_ID, null);
                    if (tempDeviceId == null) {
                        try {
                            String prefDeviceId = UUID.randomUUID().toString();
                            SharedPreferences.Editor editor = this.preferences.edit();
                            editor.putString(PREFERENCE_DEVICE_ID, prefDeviceId);
                            editor.apply();
                            tempDeviceId = prefDeviceId;
                        } catch (Exception e) {
                            Teak.log.e("device_configuration", "Error storing random UUID, no more fallbacks.");
                            Teak.log.exception(e);
                        }
                    }
                } else {
                    Teak.log.e("device_configuration", "getSharedPreferences() returned null, unable to store random UUID, no more fallbacks.");
                }
            }

            this.deviceId = tempDeviceId;

            if (this.deviceId == null) {
                return;
            }
        }

        // Listen for ADM messages if ADM is available
        if (this.admIsSupported) {
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

            RemoteConfiguration.addEventListener(this.remoteConfigurationEventListener);
        }

        // Kick off Advertising Info request
        fetchAdvertisingInfo(context);
    }

    private final RemoteConfiguration.EventListener remoteConfigurationEventListener = new RemoteConfiguration.EventListener() {
        @Override
        public void onConfigurationReady(RemoteConfiguration configuration) {
            // GCM sender id
            gcmSenderId = configuration.gcmSenderId == null ? appConfiguration.pushSenderId : configuration.gcmSenderId;
            registerForGCM(appConfiguration, "remote_configuration");
        }
    };

    public void reRegisterPushToken(@NonNull AppConfiguration appConfiguration, String source) {
        if (this.admIsSupported) {
            ADM adm = (ADM) this.admInstance;
            adm.startRegister();
        } else {
            registerForGCM(appConfiguration, source);
        }
    }

    private void fetchAdvertisingInfo(@NonNull final Context context) {
        if (this.admIsSupported) {
            try {
                ContentResolver cr = context.getContentResolver();
                boolean limitAdTracking = Settings.Secure.getInt(cr, "limit_ad_tracking") != 0;
                String advertisingId = Settings.Secure.getString(cr, "advertising_id");

                if (!advertisingId.equals(this.advertisingId) || limitAdTracking != this.limitAdTracking) {
                    this.limitAdTracking = limitAdTracking;
                    this.advertisingId = advertisingId;
                    synchronized (eventListenersMutex) {
                        for (EventListener e : eventListeners) {
                            e.onAdvertisingInfoChanged(this);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        } else if (this.googlePlayIsSupported) {
            final DeviceConfiguration _this = this;
            final FutureTask<AdvertisingIdClient.Info> adInfoFuture = new FutureTask<>(new RetriableTask<>(100, 7000L, new Callable<AdvertisingIdClient.Info>() {
                @Override
                public AdvertisingIdClient.Info call() throws Exception {
                    if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
                        return AdvertisingIdClient.getAdvertisingIdInfo(context);
                    }
                    throw new Exception("Retrying GooglePlayServicesUtil.isGooglePlayServicesAvailable()");
                }
            }));
            new Thread(adInfoFuture).start();

            // TODO: This needs to be re-checked in case it's something like SERVICE_UPDATING or SERVICE_VERSION_UPDATE_REQUIRED
            if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AdvertisingIdClient.Info adInfo = adInfoFuture.get();

                            // Inform listeners Ad Info has changed
                            if (_this.advertisingId == null ||
                                    !_this.advertisingId.equals(adInfo.getId()) ||
                                    _this.limitAdTracking != adInfo.isLimitAdTrackingEnabled()) {
                                _this.advertisingId = adInfo.getId();
                                _this.limitAdTracking = adInfo.isLimitAdTrackingEnabled();

                                synchronized (eventListenersMutex) {
                                    for (EventListener e : eventListeners) {
                                        e.onAdvertisingInfoChanged(_this);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                }).start();
            }
        }
    }

    private void registerForGCM(@NonNull final AppConfiguration appConfiguration, final String source) {
        try {
            if (gcmSenderId != null) {
                final DeviceConfiguration _this = this;

                final FutureTask<String> gcmRegistration = new FutureTask<>(new RetriableTask<>(100, 7000L, new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        GoogleCloudMessaging gcm = _this.gcm.get();
                        Teak.log.i("device_configuration", _.h("sender_id", gcmSenderId, "source", source));
                        return gcm == null ? null : gcm.register(gcmSenderId);
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

    public void assignGcmRegistration(final String registration) {
        final DeviceConfiguration _this = this;
        new Thread(new Runnable() {
            public void run() {
                try {
                    // Inform event listeners GCM is here
                    if (!registration.equals(gcmId)) {
                        _this.gcmId = registration;
                        _this.notifyPushIdChangedListeners();
                        Teak.log.i("gcm.key_updated", _.h("gcm_id", registration));
                    }
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            }
        }).start();
    }

    public void notifyPushIdChangedListeners() {
        synchronized (eventListenersMutex) {
            for (EventListener e : eventListeners) {
                e.onPushIdChanged(this);
            }
        }
    }

    // region Event Listener
    public interface EventListener {
        void onPushIdChanged(DeviceConfiguration deviceConfiguration);

        void onAdvertisingInfoChanged(DeviceConfiguration deviceConfiguration);
    }

    private static final Object eventListenersMutex = new Object();
    private static ArrayList<EventListener> eventListeners = new ArrayList<>();

    public static void addEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            if (!eventListeners.contains(e)) {
                eventListeners.add(e);
            }
        }
    }

    public static void removeEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            eventListeners.remove(e);
        }
    }
    // endregion

    // region Helpers
    // https://raw.githubusercontent.com/jaredrummler/AndroidDeviceNames/master/library/src/main/java/com/jaredrummler/android/device/DeviceName.java
    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;
        String phrase = "";
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase += Character.toUpperCase(c);
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase += c;
        }
        return phrase;
    }
    // endregion

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

    public class RetriableTask<T> implements Callable<T> {
        private final Callable<T> wrappedTask;
        private final int tries;
        private final long retryDelay;

        public RetriableTask(final int tries, final long retryDelay, final Callable<T> taskToWrap) {
            this.wrappedTask = taskToWrap;
            this.tries = tries;
            this.retryDelay = retryDelay;
        }

        public T call() throws Exception {
            int triesLeft = this.tries;
            while (true) {
                try {
                    return this.wrappedTask.call();
                } catch (final CancellationException | InterruptedException e) {
                    throw e;
                } catch (final Exception e) {
                    triesLeft--;
                    if (triesLeft == 0) throw e;
                    if (this.retryDelay > 0) Thread.sleep(this.retryDelay);
                }
            }
        }
    }
}
