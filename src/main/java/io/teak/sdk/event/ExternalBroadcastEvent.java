package io.teak.sdk.event;

import android.content.Intent;
import androidx.annotation.NonNull;
import io.teak.sdk.TeakEvent;

public class ExternalBroadcastEvent extends TeakEvent {
    public static final String Type = "ExternalBroadcastEvent";

    public final Intent intent;

    public ExternalBroadcastEvent(@NonNull Intent intent) {
        super(Type);
        this.intent = intent;
    }
}
