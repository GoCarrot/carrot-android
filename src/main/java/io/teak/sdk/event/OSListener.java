package io.teak.sdk.event;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/**
 * Created by pat on 10/5/17.
 */

// Mono-directional: OS->SDK
public interface OSListener {
    // Lifecycle
    boolean lifecycle_onActivityCreated(Activity activity);

    void lifecycle_onActivityPaused(Activity activity);
    void lifecycle_onActivityResumed(Activity activity);

    // Notifications
    void notification_onNotificationReceived(Context context, Intent intent);
    void notification_onNotificationAction(Context context, Intent intent); // Tap/button-
    void notification_onNotificationCleared(Context context, Intent intent); // When someone dismisses a notification

    // Purchases
    void purchase_onPurchaseSucceeded(JSONObject json);
    void purchase_onPurchaseFailed(JSONObject json);
}
