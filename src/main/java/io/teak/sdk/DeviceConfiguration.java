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
import android.util.Log;

import com.amazon.device.messaging.ADM;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;

class DeviceConfiguration {
    private static final String LOG_TAG = "Teak:DeviceConfig";

    public String gcmId;
    public String admId;

    public final boolean admIsSupported;
    public final boolean googlePlayIsSupported;

    public final String deviceId;
    public final String deviceManufacturer;
    public final String deviceModel;
    public final String deviceFallback;
    public final String platformString;

    public String advertisingId;
    public boolean limitAdTracking;

    private FutureTask<GoogleCloudMessaging> gcm;
    private SharedPreferences preferences;
    private Object admInstance;

    private static final String PREFERENCE_GCM_ID = "io.teak.sdk.Preferences.GcmId";
    private static final String PREFERENCE_APP_VERSION = "io.teak.sdk.Preferences.AppVersion";
    private static final String PREFERENCE_DEVICE_ID = "io.teak.sdk.Preferences.DeviceId";

    public DeviceConfiguration(@NonNull final Context context, @NonNull AppConfiguration appConfiguration) {
        if (android.os.Build.VERSION.RELEASE == null) {
            this.platformString = "android_unknown";
        } else {
            this.platformString = "android_" + android.os.Build.VERSION.RELEASE;
        }

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
                Log.e(LOG_TAG, "Error calling getSharedPreferences(). " + Log.getStackTraceString(e));
            } finally {
                this.preferences = tempPreferences;
            }

            if (this.preferences == null) {
                Log.e(LOG_TAG, "getSharedPreferences() returned null. Some caching is disabled.");
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
                Log.e(LOG_TAG, "android.os.Build.SERIAL not available, falling back to Settings.Secure.ANDROID_ID. " + Log.getStackTraceString(e));
            }

            if (tempDeviceId == null) {
                try {
                    String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                    if (androidId.equals("9774d56d682e549c")) {
                        Log.e(LOG_TAG, "Settings.Secure.ANDROID_ID == '9774d56d682e549c', falling back to random UUID stored in preferences.");
                    } else {
                        tempDeviceId = UUID.nameUUIDFromBytes(androidId.getBytes("utf8")).toString();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error generating device id from Settings.Secure.ANDROID_ID, falling back to random UUID stored in preferences. " + Log.getStackTraceString(e));
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
                            Log.e(LOG_TAG, "Error storing random UUID, no more fallbacks. " + Log.getStackTraceString(e));
                        }
                    }
                } else {
                    Log.e(LOG_TAG, "getSharedPreferences() returned null, unable to store random UUID, no more fallbacks.");
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
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "ADM supported, starting registration.");
                }
                adm.startRegister();
            } else {
                this.admId = adm.getRegistrationId();
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "ADM Id found in cache: " + this.admId);
                }
            }
        } else {
            // Kick off GCM request
            if (this.preferences != null) {
                int storedAppVersion = this.preferences.getInt(PREFERENCE_APP_VERSION, 0);
                String storedGcmId = this.preferences.getString(PREFERENCE_GCM_ID, null);
                if (storedAppVersion == appConfiguration.appVersion && storedGcmId != null) {
                    // No need to get a new one, so put it on the blocking queue
                    if (Teak.isDebug) {
                        Log.d(LOG_TAG, "GCM Id found in cache: " + storedGcmId);
                    }
                    this.gcmId = storedGcmId;
                    displayPushDebugMessage();
                }
            }

            this.gcm = new FutureTask<>(new RetriableTask<>(100, 2000L, new Callable<GoogleCloudMessaging>() {
                @Override
                public GoogleCloudMessaging call() throws Exception {
                    return GoogleCloudMessaging.getInstance(context);
                }
            }));
            new Thread(this.gcm).start();

            if (this.gcmId == null) {
                registerForGCM(appConfiguration);
            }
        }

        // Kick off Advertising Info request
        fetchAdvertisingInfo(context);
    }

    public void reRegisterPushToken(@NonNull AppConfiguration appConfiguration) {
        if (this.admIsSupported) {
            ADM adm = (ADM) this.admInstance;
            adm.startRegister();
        } else {
            if (this.preferences != null) {
                SharedPreferences.Editor editor = this.preferences.edit();
                editor.putInt(PREFERENCE_APP_VERSION, 0);
                editor.putString(PREFERENCE_GCM_ID, null);
                editor.apply();
            }
            registerForGCM(appConfiguration);
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
                            if (!_this.advertisingId.equals(adInfo.getId()) ||
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
                            if (Teak.isDebug) {
                                Log.e(LOG_TAG, "Couldn't get Google Play Advertising Information.");
                            }
                        }
                    }
                }).start();
            }
        }
    }

    private void registerForGCM(@NonNull final AppConfiguration appConfiguration) {
        try {
            if (appConfiguration.pushSenderId != null) {
                final DeviceConfiguration _this = this;

                final FutureTask<String> gcmRegistration = new FutureTask<>(new RetriableTask<>(100, 7000L, new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        GoogleCloudMessaging gcm = _this.gcm.get();

                        if (Teak.isDebug) {
                            Log.d(LOG_TAG, "Registering for GCM with sender id: " + appConfiguration.pushSenderId);
                        }
                        return gcm.register(appConfiguration.pushSenderId);
                    }
                }));
                new Thread(gcmRegistration).start();

                new Thread(new Runnable() {
                    public void run() {
                        try {
                            String registration = gcmRegistration.get();

                            if (registration == null) {
                                Log.e(LOG_TAG, "Got null token during GCM registration.");
                                return;
                            }

                            if (_this.preferences != null) {
                                SharedPreferences.Editor editor = _this.preferences.edit();
                                editor.putInt(PREFERENCE_APP_VERSION, appConfiguration.appVersion);
                                editor.putString(PREFERENCE_GCM_ID, registration);
                                editor.apply();
                            }

                            // Inform event listeners GCM is here
                            if (!registration.equals(gcmId)) {
                                _this.gcmId = registration;
                                _this.notifyPushIdChangedListeners();
                            }

                            displayPushDebugMessage();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, Log.getStackTraceString(e));
                        }
                    }
                }).start();
            }
        } catch (Exception ignored) {
        }
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

    private void displayPushDebugMessage() {
        if (Teak.isDebug) {
            final DeviceConfiguration _this = this;
            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                @Override
                public void run(Session session) {
                    try {
                        String urlString = "https://app.teak.io/apps/" + session.appConfiguration.appId + "/test_accounts/new" +
                                "?api_key=" + URLEncoder.encode(session.userId(), "UTF-8") +
                                "&device_manufacturer=" + URLEncoder.encode(_this.deviceManufacturer, "UTF-8") +
                                "&device_model=" + URLEncoder.encode(_this.deviceModel, "UTF-8") +
                                "&device_fallback=" + URLEncoder.encode(_this.deviceFallback, "UTF-8") +
                                "&bundle_id=" + URLEncoder.encode(session.appConfiguration.bundleId, "UTF-8") +
                                "&device_id=" + URLEncoder.encode(_this.deviceId, "UTF-8");

                        if (_this.gcmId != null) {
                            urlString += "&gcm_push_key=" + URLEncoder.encode(_this.gcmId, "UTF-8");
                        }

                        if (_this.admId != null) {
                            urlString += "&adm_push_key=" + URLEncoder.encode(_this.admId, "UTF-8");
                        }

                        Log.d(LOG_TAG, "If you want to debug or test push notifications on this device please click the link below, or copy/paste into your browser:");
                        Log.d(LOG_TAG, "    " + urlString);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, Log.getStackTraceString(e));
                    }
                }
            });
        }
    }

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
            return String.format(Locale.US, "%s: %s", super.toString(), new JSONObject(this.to_h()).toString(2));
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
