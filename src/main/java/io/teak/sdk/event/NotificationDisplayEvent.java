package io.teak.sdk.event;

import android.app.Notification;
import androidx.annotation.NonNull;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;

public class NotificationDisplayEvent extends TeakEvent {
    public static final String Type = "NotificationDisplayEvent";

    public final TeakNotification teakNotification;
    public final Notification nativeNotification;

    public NotificationDisplayEvent(@NonNull TeakNotification teakNotification, @NonNull Notification nativeNotification) {
        super(Type);
        this.teakNotification = teakNotification;
        this.nativeNotification = nativeNotification;
    }
}
