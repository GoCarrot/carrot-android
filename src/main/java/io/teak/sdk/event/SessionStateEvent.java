package io.teak.sdk.event;

import android.support.annotation.NonNull;

import io.teak.sdk.TeakEvent;
import io.teak.sdk.core.Session;

public class SessionStateEvent extends TeakEvent {
    public static final String Type = "SessionStateEvent";

    public final Session session;
    public final Session.State state;
    public final Session.State previousState;

    public SessionStateEvent(@NonNull Session session, @NonNull Session.State state, @NonNull Session.State previousState) {
        super(Type);

        this.session = session;
        this.state = state;
        this.previousState = previousState;
    }
}
