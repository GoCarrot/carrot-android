package io.teak.sdk.configuration;

import android.app.ActivityManager;
import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import io.teak.sdk.IObjectFactory;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.AdvertisingInfoEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.json.JSONObject;
import io.teak.sdk.push.IPushProvider;

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

        // Request push token
        // TODO: If strange behavior happens, the previous code waited for remote configuration, so maybe that's the cause
        this.requestNewPushToken();
        //        TeakEvent.addEventListener(new TeakEvent.EventListener() {
        //            @Override
        //            public void onNewEvent(@NonNull TeakEvent event) {
        //                if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
        //                    requestNewPushToken();
        //                }
        //            }
        //        });
    }

    public void requestNewPushToken() {
        if (this.pushProvider != null) {
            this.pushProvider.requestPushKey();
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

    /**
     * Gets the number of cores available in this device, across all processors.
     * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
     * <p>
     * http://forums.makingmoneywithandroid.com/android-development/280-%5Bhow-%5D-get-number-cpu-cores-android-device.html
     *
     * @return The number of cores, or 1 if failed to get result
     */
    private int getNumCores() {
        //Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                //Check if filename is "cpu", followed by a single digit number
                if (Pattern.matches("cpu[0-9]+", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        try {
            //Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");

            //Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());

            //Return the number of cores (virtual CPU devices)
            return files.length;
        } catch (Exception e) {
            Teak.log.exception(e);
            return 1;
        }
    }
}
