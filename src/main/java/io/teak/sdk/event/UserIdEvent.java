package io.teak.sdk.event;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.TeakEvent;

public class UserIdEvent extends TeakEvent {
    public static final String Type = "UserIdEvent";

    public final String userId;
    public final Set<String> optOut;
    public final String email;

    public UserIdEvent(@NonNull String userId, @NonNull String[] optOut, @Nullable String email) {
        super(Type);
        this.userId = userId;
        this.optOut = new HashSet<>(Arrays.asList(optOut));
        this.email = email;
    }
}
