package io.teak.sdk.event;

import androidx.annotation.NonNull;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.configuration.RemoteConfiguration;

public class RemoteConfigurationEvent extends TeakEvent {
    public static final String Type = "RemoteConfigurationEvent";

    public final RemoteConfiguration remoteConfiguration;

    public RemoteConfigurationEvent(@NonNull RemoteConfiguration remoteConfiguration) {
        super(Type);
        this.remoteConfiguration = remoteConfiguration;
    }
}
