package io.teak.sdk.support.androidx;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import io.teak.sdk.support.INotificationManager;

public class NotificationManager implements INotificationManager {
    private final NotificationManagerCompat notificationManager;

    public NotificationManager(@NonNull Context context) {
        this.notificationManager = NotificationManagerCompat.from(context);
    }

    @Override
    public boolean areNotificationsEnabled() {
        return this.notificationManager.areNotificationsEnabled();
    }

    @Override
    public boolean hasNotificationsEnabled() {
        return true;
    }
}
