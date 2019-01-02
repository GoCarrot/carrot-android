package io.teak.sdk.event;

import io.teak.sdk.TeakEvent;

public class DeepLinksReadyEvent extends TeakEvent {
    public static final String Type = "DeepLinksReadyEvent";

    public DeepLinksReadyEvent() {
        super(Type);
    }
}
