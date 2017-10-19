package io.teak.sdk.io;

import android.app.Notification;
import android.support.annotation.NonNull;

import io.teak.sdk.TeakNotification;

public interface IAndroidNotification {
    void cancelNotification(int platformId);
    void displayNotification(@NonNull TeakNotification teakNotification, @NonNull Notification nativeNotification);
}
