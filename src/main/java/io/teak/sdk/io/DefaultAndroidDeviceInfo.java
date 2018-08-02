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
package io.teak.sdk.io;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.RetriableTask;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.AdvertisingInfoEvent;

public class DefaultAndroidDeviceInfo implements IAndroidDeviceInfo {
    private final Context context;

    public DefaultAndroidDeviceInfo(@NonNull Context context) throws IntegrationChecker.MissingDependencyException {
        IntegrationChecker.requireDependency("com.google.android.gms.common.GooglePlayServicesUtil");

        this.context = context;
    }

    @NonNull
    @Override
    public Map<String, String> getDeviceDescription() {
        // https://raw.githubusercontent.com/jaredrummler/AndroidDeviceNames/master/library/src/main/java/com/jaredrummler/android/device/DeviceName.java
        String deviceManufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String deviceModel = Build.MODEL == null ? "" : Build.MODEL;
        String deviceFallback;
        if (deviceModel.startsWith(Build.MANUFACTURER)) {
            deviceFallback = capitalize(Build.MODEL);
        } else {
            deviceFallback = capitalize(Build.MANUFACTURER) + " " + Build.MODEL;
        }

        HashMap<String, String> info = new HashMap<>();
        info.put("deviceManufacturer", deviceManufacturer);
        info.put("deviceModel", deviceModel);
        info.put("deviceFallback", deviceFallback);
        return info;
    }

    @Nullable
    @Override
    @SuppressLint("HardwareIds") // The fallback device id uses these
    public String getDeviceId() {
        String tempDeviceId = null;

        // Build.SERIAL will always return "UNKNOWN" on Android P (API 28+) and greater
        // TODO: Replace hard coded '28' with Build.VERSION_CODES.P once we have that SDK
        if (Build.VERSION.SDK_INT < 28) {
            try {
                @SuppressWarnings("deprecation")
                final byte[] buildSerial = android.os.Build.SERIAL.getBytes("utf8");
                tempDeviceId = UUID.nameUUIDFromBytes(buildSerial).toString();
            } catch (Exception e) {
                Teak.log.e("getDeviceId", "android.os.Build.SERIAL not available, falling back to Settings.Secure.ANDROID_ID.");
                Teak.log.exception(e);
            }
        }

        if (tempDeviceId == null) {
            try {
                String androidId = Settings.Secure.getString(this.context.getContentResolver(), Settings.Secure.ANDROID_ID);
                if ("9774d56d682e549c".equals(androidId)) {
                    Teak.log.e("getDeviceId", "Settings.Secure.ANDROID_ID == '9774d56d682e549c', falling back to random UUID stored in preferences.");
                } else {
                    tempDeviceId = UUID.nameUUIDFromBytes(androidId.getBytes("utf8")).toString();
                }
            } catch (Exception e) {
                Teak.log.e("getDeviceId", "Error generating device id from Settings.Secure.ANDROID_ID, falling back to random UUID stored in preferences.");
                Teak.log.exception(e);
            }
        }

        if (tempDeviceId == null) {
            SharedPreferences preferences = null;
            try {
                preferences = this.context.getSharedPreferences(Teak.PREFERENCES_FILE, Context.MODE_PRIVATE);
            } catch (Exception ignored) {
            }

            if (preferences != null) {
                tempDeviceId = preferences.getString(PREFERENCE_DEVICE_ID, null);
                if (tempDeviceId == null) {
                    try {
                        String prefDeviceId = UUID.randomUUID().toString();
                        synchronized (Teak.PREFERENCES_FILE) {
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString(PREFERENCE_DEVICE_ID, prefDeviceId);
                            editor.apply();
                        }
                        tempDeviceId = prefDeviceId;
                    } catch (Exception e) {
                        Teak.log.e("getDeviceId", "Error storing random UUID, no more fallbacks.");
                        Teak.log.exception(e);
                    }
                }
            } else {
                Teak.log.e("getDeviceId", "getSharedPreferences() returned null, unable to store random UUID, no more fallbacks.");
            }
        }

        return tempDeviceId;
    }

    @Override
    public void requestAdvertisingId() {
        // First try to use Google Play
        boolean usingGooglePlayForAdId = false;
        try {
            final FutureTask<AdvertisingIdClient.Info> adInfoFuture = new FutureTask<>(new RetriableTask<>(10, 7000L, new Callable<AdvertisingIdClient.Info>() {
                @Override
                public AdvertisingIdClient.Info call() throws Exception {
                    @SuppressWarnings("deprecation")
                    final int gpsAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
                    if (gpsAvailable == ConnectionResult.SUCCESS) {
                        return AdvertisingIdClient.getAdvertisingIdInfo(context);
                    }
                    throw new Exception("Retrying GooglePlayServicesUtil.isGooglePlayServicesAvailable()");
                }
            }));

            // TODO: This needs to be re-checked in case it's something like SERVICE_UPDATING or SERVICE_VERSION_UPDATE_REQUIRED
            @SuppressWarnings("deprecation")
            final int gpsAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
            if (gpsAvailable == ConnectionResult.SUCCESS) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final AdvertisingIdClient.Info adInfo = adInfoFuture.get();
                            if (adInfo != null) {
                                final String advertisingId = adInfo.getId();
                                final boolean limitAdTracking = adInfo.isLimitAdTrackingEnabled();

                                TeakEvent.postEvent(new AdvertisingInfoEvent(advertisingId, limitAdTracking));
                            }
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                })
                    .start();

                // Only start running the future if we get this far
                new Thread(adInfoFuture).start();

                // And we're good
                usingGooglePlayForAdId = true;
            }
        } catch (Exception ignored) {
        }

        // If not using Google Play, try Settings.Secure
        if (!usingGooglePlayForAdId) {
            try {
                ContentResolver cr = this.context.getContentResolver();
                String advertisingId = Settings.Secure.getString(cr, "advertising_id");
                boolean limitAdTracking = Settings.Secure.getInt(cr, "limit_ad_tracking") != 0;

                TeakEvent.postEvent(new AdvertisingInfoEvent(advertisingId, limitAdTracking));
            } catch (Exception ignored) {
            }
        }
    }

    private static final String PREFERENCE_DEVICE_ID = "io.teak.sdk.Preferences.DeviceId";

    // https://raw.githubusercontent.com/jaredrummler/AndroidDeviceNames/master/library/src/main/java/com/jaredrummler/android/device/DeviceName.java
    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;
        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }
        return phrase.toString();
    }

    @Override
    public String getSystemProperty(String propName) {
        String line;
        BufferedReader input = null;
        try {
            java.lang.Process p = Runtime.getRuntime().exec("getprop " + propName);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return line;
    }
}
