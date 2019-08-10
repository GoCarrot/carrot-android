package io.teak.sdk.event;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.Helpers;
import io.teak.sdk.TeakEvent;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

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
        payload.put(TrackEventEvent.DurationKey, duration);
        payload.put(TrackEventEvent.CountKey, 1);
        payload.put(TrackEventEvent.SumOfSquaresKey, BigInteger.valueOf(duration).pow(2));
        return payload;
    }
}
