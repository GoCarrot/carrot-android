package io.teak.sdk.event;

import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.teak.sdk.TeakEvent;

public class UserIdEvent extends TeakEvent {
    public static final String Type = "UserIdEvent";

    public final String userId;
    public final Set<String> optOut;

    public UserIdEvent(@NonNull String userId, @NonNull String[] optOut) {
        super(Type);
        this.userId = userId;
        this.optOut = new HashSet<String>(Arrays.asList(optOut));
    }
}
