package io.teak.sdk.event;

import android.app.Notification;
import android.os.Bundle;
import android.support.annotation.NonNull;
import io.teak.sdk.TeakEvent;

public class NotificationReDisplayEvent extends TeakEvent {
    public static final String Type = "NotificationReDisplayEvent";

    public final Bundle bundle;
    public final Notification nativeNotification;

    public NotificationReDisplayEvent(@NonNull Bundle bundle, @NonNull Notification nativeNotification) {
        super(Type);
        this.bundle = bundle;
        this.nativeNotification = nativeNotification;
    }
}
