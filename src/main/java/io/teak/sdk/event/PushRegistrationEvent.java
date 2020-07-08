package io.teak.sdk.event;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.TeakEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PushRegistrationEvent extends TeakEvent {
    public static final String Registered = "PushRegistrationEvent.Registered";
    public static final String UnRegistered = "PushRegistrationEvent.UnRegistered";

    public final Map<String, String> registration;

    public PushRegistrationEvent(@NonNull String key, @NonNull String value, @Nullable String senderId) {
        super(Registered);
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        if (senderId != null) {
            map.put("gcm_sender_id", senderId);
        }
        this.registration = Collections.unmodifiableMap(map);
    }

    public PushRegistrationEvent(@NonNull String unregistered) {
        super(UnRegistered);
        this.registration = null;
        if (!unregistered.equals(UnRegistered)) {
            throw new IllegalArgumentException("Must pass 'UnRegistered' so that the intent of the code is clear.");
        }
    }
}
