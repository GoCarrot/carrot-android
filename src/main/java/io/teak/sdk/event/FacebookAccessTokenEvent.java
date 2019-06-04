package io.teak.sdk.event;

import android.support.annotation.Nullable;
import io.teak.sdk.TeakEvent;

public class FacebookAccessTokenEvent extends TeakEvent {
    public static final String Type = "FacebookAccessTokenEvent";

    public final String accessToken;

    public FacebookAccessTokenEvent(@Nullable String accessToken) {
        super(Type);
        this.accessToken = accessToken;
    }
}
