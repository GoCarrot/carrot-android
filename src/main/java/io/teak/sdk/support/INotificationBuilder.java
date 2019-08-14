package io.teak.sdk.support;

import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Bitmap;

public interface INotificationBuilder {
    void setTicker(CharSequence tickerText);
    void setSmallIcon(int icon);
    void setLargeIcon(Bitmap icon);
    void setDeleteIntent(PendingIntent intent);
    void setContentIntent(PendingIntent intent);
    Notification build();
}
