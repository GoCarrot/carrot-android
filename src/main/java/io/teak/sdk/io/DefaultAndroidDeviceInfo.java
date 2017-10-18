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

import com.amazon.device.messaging.ADM;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import io.teak.sdk.RetriableTask;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.AdvertisingInfoEvent;

public class DefaultAndroidDeviceInfo implements IAndroidDeviceInfo {
    private final Context context;

    public DefaultAndroidDeviceInfo(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public boolean hasGooglePlay() {
        try {
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public boolean hasADM() {
        try {
            Class.forName("com.amazon.device.messaging.ADM");
            return new ADM(this.context).isSupported();
        } catch (Exception ignored) {
        }
        return false;
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
        try {
            tempDeviceId = UUID.nameUUIDFromBytes(android.os.Build.SERIAL.getBytes("utf8")).toString();
        } catch (Exception e) {
            Teak.log.e("getDeviceId", "android.os.Build.SERIAL not available, falling back to Settings.Secure.ANDROID_ID.");
            Teak.log.exception(e);
        }

        if (tempDeviceId == null) {
            try {
                String androidId = Settings.Secure.getString(this.context.getContentResolver(), Settings.Secure.ANDROID_ID);
                if (androidId.equals("9774d56d682e549c")) {
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
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString(PREFERENCE_DEVICE_ID, prefDeviceId);
                        editor.apply();
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
        // TODO: I'm not certain if this is the best way to check if we need to use this method to get the advertising id
        if (this.hasADM()) {
            try {
                ContentResolver cr = this.context.getContentResolver();
                boolean limitAdTracking = Settings.Secure.getInt(cr, "limit_ad_tracking") != 0;
                String advertisingId = Settings.Secure.getString(cr, "advertising_id");

                TeakEvent.postEvent(new AdvertisingInfoEvent(advertisingId, limitAdTracking));
            } catch (Exception ignored) {
            }
        } else if (this.hasGooglePlay()) {
            final FutureTask<AdvertisingIdClient.Info> adInfoFuture = new FutureTask<>(new RetriableTask<>(10, 7000L, new Callable<AdvertisingIdClient.Info>() {
                @Override
                public AdvertisingIdClient.Info call() throws Exception {
                    //noinspection deprecation
                    if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
                        return AdvertisingIdClient.getAdvertisingIdInfo(context);
                    }
                    throw new Exception("Retrying GooglePlayServicesUtil.isGooglePlayServicesAvailable()");
                }
            }));
            new Thread(adInfoFuture).start();

            // TODO: This needs to be re-checked in case it's something like SERVICE_UPDATING or SERVICE_VERSION_UPDATE_REQUIRED

            //noinspection deprecation
            if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AdvertisingIdClient.Info adInfo = adInfoFuture.get();
                            String advertisingId = adInfo.getId();
                            boolean limitAdTracking = adInfo.isLimitAdTrackingEnabled();

                            TeakEvent.postEvent(new AdvertisingInfoEvent(advertisingId, limitAdTracking));
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                }).start();
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
}
