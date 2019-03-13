package io.teak.sdk.event;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.teak.sdk.Helpers;
import io.teak.sdk.TeakEvent;

public class TrackEventEvent extends TeakEvent {
    public static final String Type = "TrackEventEvent";
    public static final String ActionIdKey = "action_type";
    public static final String ObjectIdKey = "object_type";
    public static final String ObjectInstanceIdKey = "object_instance_id";
    public static final String DurationKey = "duration";
    public static final String CountKey = "count";
    public static final String SumOfSquaresKey = "sum_of_squares";

    public final Map<String, Object> payload;

    public TrackEventEvent(Map<String, Object> payload) {
        super(Type);
        this.payload = payload;
    }

    public static boolean payloadEquals(@NonNull Map<String, Object> a, @Nullable Map<String, Object> b) {
        if (b == null) return false;
        if (!a.get(ActionIdKey).equals(b.get(ActionIdKey))) return false;
        if (!Helpers.is_equal(a.get(ObjectIdKey), b.get(ObjectIdKey))) return false;
        return Helpers.is_equal(a.get(ObjectInstanceIdKey), b.get(ObjectInstanceIdKey));
    }

    public static Map<String, Object> payloadForEvent(final @NonNull String actionId, final @Nullable String objectTypeId, final @Nullable String objectInstanceId, final long duration) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(TrackEventEvent.ActionIdKey, actionId);
        if (objectTypeId != null && objectTypeId.trim().length() > 0) {
            payload.put(TrackEventEvent.ObjectIdKey, objectTypeId);
        }
        if (objectInstanceId != null && objectInstanceId.trim().length() > 0) {
            payload.put(TrackEventEvent.ObjectInstanceIdKey, objectInstanceId);
        }
        payload.put(TrackEventEvent.DurationKey, new Long(duration));
        payload.put(TrackEventEvent.CountKey, new Long(1));
        payload.put(TrackEventEvent.SumOfSquaresKey, new Long(duration * duration));
        return payload;
    }
}
