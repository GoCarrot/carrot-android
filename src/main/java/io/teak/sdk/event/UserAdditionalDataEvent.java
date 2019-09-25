package io.teak.sdk.event;

import androidx.annotation.NonNull;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.json.JSONObject;

public class UserAdditionalDataEvent extends TeakEvent {
    public static final String Type = "UserAdditionalDataEvent";

    public final JSONObject additionalData;

    public UserAdditionalDataEvent(@NonNull JSONObject additionalData) {
        super(Type);

        this.additionalData = additionalData;
    }
}
