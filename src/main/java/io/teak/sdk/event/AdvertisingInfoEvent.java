package io.teak.sdk.event;

import androidx.annotation.Nullable;
import io.teak.sdk.TeakEvent;

public class AdvertisingInfoEvent extends TeakEvent {
    public static final String Type = "AdvertisingInfoEvent";

    public final String advertisingId;
    public final boolean limitAdTracking;

    public AdvertisingInfoEvent(@Nullable String advertisingId, boolean limitAdTracking) {
        super(Type);
        this.advertisingId = advertisingId;
        this.limitAdTracking = limitAdTracking;
    }
}
