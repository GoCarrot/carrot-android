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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.UUID;

public class DeviceConfiguration {
    private static final String LOG_TAG = "Teak:DeviceConfig";

    public String gcmId;

    public final String deviceId;
    public final String deviceManufacturer;
    public final String deviceModel;
    public final String deviceFallback;
    public final String platformString;

    public AdvertisingIdClient.Info advertsingInfo;

    private GoogleCloudMessaging gcm;
    private SharedPreferences preferences;

    private static final String PREFERENCES_FILE = "io.teak.sdk.Preferences";
    private static final String PREFERENCE_GCM_ID = "io.teak.sdk.Preferences.GcmId";
    private static final String PREFERENCE_APP_VERSION = "io.teak.sdk.Preferences.AppVersion";
    private static final String PREFERENCE_DEVICE_ID = "io.teak.sdk.Preferences.DeviceId";

    public DeviceConfiguration(@NonNull Activity activity, @NonNull AppConfiguration appConfiguration) {
        if (android.os.Build.VERSION.RELEASE == null) {
            this.platformString = "android_unknown";
        } else {
            this.platformString = "android_" + android.os.Build.VERSION.RELEASE;
        }

        this.preferences = activity.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        if (this.preferences == null) {
            Log.e(LOG_TAG, "getSharedPreferences() returned null. Some caching is disabled.");
        }

        // TODO: Firebase
        this.gcm = GoogleCloudMessaging.getInstance(activity.getApplicationContext());

        // Device id
        {
            String androidId = null;
            String tempDeviceId = null;
            try {
                tempDeviceId = UUID.nameUUIDFromBytes(android.os.Build.SERIAL.getBytes("utf8")).toString();
            } catch (Exception e) {
                Log.e(LOG_TAG, "android.os.Build.SERIAL not available, falling back to Settings.Secure.ANDROID_ID. " + Log.getStackTraceString(e));
            }

            if (tempDeviceId == null) {
                try {
                    androidId = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
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

            this.deviceId = tempDeviceId; // TODO: If still null, Teak has to bail out entirely
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
            } else {
                registerForGCM(appConfiguration);
            }
        } else {
            // Register each time
            registerForGCM(appConfiguration);
        }

        // Kick off Advertising Info request
        fetchAdvertisingInfo(activity);
    }

    public void reRegisterPushToken(@NonNull AppConfiguration appConfiguration) {
        if (this.preferences != null) {
            SharedPreferences.Editor editor = this.preferences.edit();
            editor.putInt(PREFERENCE_APP_VERSION, 0);
            editor.putString(PREFERENCE_GCM_ID, null);
            editor.apply();
        }
        registerForGCM(appConfiguration);
    }

    private void fetchAdvertisingInfo(@NonNull final Activity activity) {
        // TODO: This needs to be re-checked in case it's something like SERVICE_UPDATING or SERVICE_VERSION_UPDATE_REQUIRED
        int googlePlayStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
        if (googlePlayStatus == ConnectionResult.SUCCESS) {
            final DeviceConfiguration _this = this;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(activity);
                        // Inform listeners Ad Info has changed
                        if (adInfo != _this.advertsingInfo) {
                            _this.advertsingInfo = adInfo;
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

    private void registerForGCM(@NonNull final AppConfiguration appConfiguration) {
        try {
            if (appConfiguration.pushSenderId != null) {
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Registering for GCM with sender id: " + appConfiguration.pushSenderId);
                }

                // Register for GCM in the background
                final DeviceConfiguration _this = this;
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            String registration = _this.gcm.register(appConfiguration.pushSenderId);

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
                                synchronized (eventListenersMutex) {
                                    for (EventListener e : eventListeners) {
                                        e.onGCMIdChanged(_this);
                                    }
                                }
                            }

                            Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
                                @Override
                                public void run(Session session) {
                                    try {
                                        String urlString = "https://app.teak.io/apps/" + session.appConfiguration.appId + "/test_accounts/new" +
                                                "?api_key=" + URLEncoder.encode(session.userId(), "UTF-8") +
                                                "&gcm_push_key=" + URLEncoder.encode(_this.gcmId, "UTF-8") +
                                                "&device_manufacturer=" + URLEncoder.encode(_this.deviceManufacturer, "UTF-8") +
                                                "&device_model=" + URLEncoder.encode(_this.deviceModel, "UTF-8") +
                                                "&device_fallback=" + URLEncoder.encode(_this.deviceFallback, "UTF-8") +
                                                "&bundle_id=" + URLEncoder.encode(session.appConfiguration.bundleId, "UTF-8");

                                        Log.d(LOG_TAG, "If you want to debug or test push notifications on this device please click the link below, or copy/paste into your browser:");
                                        Log.d(LOG_TAG, "    " + urlString);
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, Log.getStackTraceString(e));
                                    }
                                }
                            });
                        } catch (Exception e) {
                            Log.e(LOG_TAG, Log.getStackTraceString(e));
                            // TODO: exponential back-off, re-register
                        }
                    }
                }).start();
            }
        } catch (Exception ignored) {
        }
    }

    // region Event Listener
    public interface EventListener {
        void onGCMIdChanged(DeviceConfiguration deviceConfiguration);
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
}
