package io.teak.sdk.event;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import io.teak.sdk.TeakEvent;

public class PushNotificationEvent extends TeakEvent {
    public static final String Received = "PushNotificationEvent.Received";
    public static final String Cleared = "PushNotificationEvent.Cleared";
    public static final String Interaction = "PushNotificationEvent.Interaction";

    public final Intent intent;
    public final Context context;

    public PushNotificationEvent(@NonNull String type, @NonNull Context context, @NonNull Intent intent) {
        super(type);
        if (type.equals(Received) || type.equals(Cleared) || type.equals(Interaction)) {
            this.intent = intent;
            this.context = context;
        } else {
            throw new IllegalArgumentException("Type must be one of 'Received', 'Cleared' or 'Interaction'");
        }
    }
}
