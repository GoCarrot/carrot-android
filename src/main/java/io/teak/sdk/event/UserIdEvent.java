package io.teak.sdk.event;

import androidx.annotation.NonNull;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;

public class UserIdEvent extends TeakEvent {
    public static final String Type = "UserIdEvent";

    public final String userId;
    public final Teak.UserConfiguration userConfiguration;

    public UserIdEvent(@NonNull String userId, @NonNull Teak.UserConfiguration userConfiguration) {
        super(Type);
        this.userId = userId;
        this.userConfiguration = userConfiguration;
    }
}
