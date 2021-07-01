package io.teak.sdk.referrer;

import android.content.Context;

import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import io.teak.sdk.InstallReferrerReceiver;

public class InstallReferrerFuture {
    public static Future<String> get(@NonNull final Context context) {
        try {
            return new GooglePlayInstallReferrer(context);
        } catch (Exception ignored) {
        }

        // Fallback to InstallReferrerReceiver
        return new InstallReferrerReceiver.Future();
    }
}
