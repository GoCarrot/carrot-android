package io.teak.sdk.event;

import android.support.annotation.Nullable;

import java.util.Map;

import io.teak.sdk.TeakEvent;

public class PurchaseEvent extends TeakEvent {
    public static final String Type = "PurchaseEvent";
    public final Map<String, Object> payload;

    public PurchaseEvent(@Nullable Map<String, Object> payload) {
        super(Type);
        this.payload = payload;
    }
}
