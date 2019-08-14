package io.teak.sdk.support.v4;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;

import java.util.UUID;

import androidx.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import io.teak.sdk.support.INotificationBuilder;

public class NotificationBuilder implements INotificationBuilder {
    private final NotificationCompat.Builder builder;

    public NotificationBuilder(@NonNull Context context, @NonNull String notificationChannelId) {
        this.builder = new NotificationCompat.Builder(context, notificationChannelId);
        this.builder.setGroup(UUID.randomUUID().toString());

        // Set visibility of our notifications to public
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                this.builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            } catch (Exception ignored) {
            }
        }

        // Configure notification behavior
        this.builder.setPriority(NotificationCompat.PRIORITY_MAX);
        this.builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        this.builder.setOnlyAlertOnce(true);
        this.builder.setAutoCancel(true);
    }

    @Override
    public void setTicker(CharSequence tickerText) {
        this.builder.setTicker(tickerText);
    }

    @Override
    public void setSmallIcon(int icon) {
        this.builder.setSmallIcon(icon);
    }

    @Override
    public void setLargeIcon(Bitmap icon) {
        this.builder.setLargeIcon(icon);
    }

    @Override
    public void setDeleteIntent(PendingIntent intent) {
        this.builder.setDeleteIntent(intent);
    }

    @Override
    public void setContentIntent(PendingIntent intent) {
        this.builder.setContentIntent(intent);
    }

    @Override
    public Notification build() {
        return this.builder.build();
    }
}
