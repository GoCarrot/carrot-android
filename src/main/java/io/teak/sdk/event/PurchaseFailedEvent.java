package io.teak.sdk.event;

import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.teak.sdk.TeakEvent;

public class PurchaseFailedEvent extends TeakEvent {
    public static final String Type = "PurchaseFailedEvent";
    public final int errorCode;
    public final Map<String, Object> extras;

    public PurchaseFailedEvent(int errorCode, @Nullable Map<String, Object> extras) {
        super(Type);
        this.errorCode = errorCode;
        this.extras = extras == null ? new HashMap<String, Object>() : extras;
    }
}
