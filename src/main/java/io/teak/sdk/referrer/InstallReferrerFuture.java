package io.teak.sdk.referrer;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.concurrent.Future;

import io.teak.sdk.InstallReferrerReceiver;

public class InstallReferrerFuture {
    public static Future<String> get(@NonNull final Context context) {
        try {
            final Class<?> installReferrerStateListener = Class.forName("com.android.installreferrer.api.InstallReferrerStateListener");
            if (installReferrerStateListener != null) {
                return new GooglePlayInstallReferrer(context);
            }
        } catch (Exception ignored) {
        }

        // Fallback to InstallReferrerReceiver
        return new InstallReferrerReceiver.Future();
    }
}
