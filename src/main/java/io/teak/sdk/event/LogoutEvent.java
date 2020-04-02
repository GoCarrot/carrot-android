package io.teak.sdk.event;

import io.teak.sdk.TeakEvent;

public class LogoutEvent extends TeakEvent {
    public static final String Type = "LogoutEvent";

    public LogoutEvent() {
        super(LogoutEvent.Type);
    }
}
