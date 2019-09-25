package io.teak.sdk.support.v4;

import android.content.Context;
import android.os.Build;
import android.support.v4.app.NotificationManagerCompat;
import androidx.annotation.NonNull;
import io.teak.sdk.support.INotificationManager;

public class NotificationManager implements INotificationManager {
    private final NotificationManagerCompat notificationManager;
    private final boolean hasNotificationsEnabled;

    public NotificationManager(@NonNull Context context) {
        this.notificationManager = NotificationManagerCompat.from(context);

        boolean tempHasNotificationsEnabled = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationManagerCompat.class.getMethod("areNotificationsEnabled");
                tempHasNotificationsEnabled = true;
            } catch (Exception ignored) {
            }
        }
        this.hasNotificationsEnabled = tempHasNotificationsEnabled;
    }

    @Override
    public boolean areNotificationsEnabled() {
        if (this.hasNotificationsEnabled) {
            return this.notificationManager.areNotificationsEnabled();
        }

        return false;
    }

    @Override
    public boolean hasNotificationsEnabled() {
        return this.hasNotificationsEnabled;
    }
}
