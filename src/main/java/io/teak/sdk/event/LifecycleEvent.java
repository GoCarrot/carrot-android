package io.teak.sdk.event;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import io.teak.sdk.TeakEvent;

public class LifecycleEvent extends TeakEvent {
    public static final String Created = "LifecycleEvent.Created";
    public static final String Paused = "LifecycleEvent.Paused";
    public static final String Resumed = "LifecycleEvent.Resumed";

    public final Intent intent;
    public final Context context;

    public LifecycleEvent(@NonNull String type, @NonNull Intent intent, @NonNull Context context) {
        super(type);
        if (type.equals(Created) || type.equals(Paused) || type.equals(Resumed)) {
            this.intent = intent;
            this.context = context;
        } else {
            throw new IllegalArgumentException("Type must be one of 'Created', 'Paused' or 'Resumed'");
        }
    }
}
