package io.teak.sdk.event;

import io.teak.sdk.TeakEvent;

/**
 * Created by pat on 10/20/17.
 */

public class DeepLinksReadyEvent extends TeakEvent {
    public static final String Type = "DeepLinksReadyEvent";

    public DeepLinksReadyEvent() {
        super(Type);
    }
}
