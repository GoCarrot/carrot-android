package io.teak.sdk.event;

import java.util.Map;

import io.teak.sdk.TeakEvent;

public class TrackEventEvent extends TeakEvent {
    public static final String Type = "TrackEventEvent";

    public final Map<String, Object> payload;

    public TrackEventEvent(Map<String, Object> payload) {
        super(Type);
        this.payload = payload;
    }
}
