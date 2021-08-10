package io.teak.sdk.event;

import java.util.Map;

import androidx.annotation.NonNull;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;

public class UserIdEvent extends TeakEvent {
    public static final String Type = "UserIdEvent";

    public final String userId;
    public final String email;
    public final Map<String, Object> configuration;

    public UserIdEvent(@NonNull final Map<String, Object> userConfiguration) {
        super(Type);
        this.userId = (String) userConfiguration.get(Teak.UserConfiguration.UserId.key);
        this.email = (String) userConfiguration.get(Teak.UserConfiguration.Email.key);
        this.configuration = userConfiguration;
    }
}
