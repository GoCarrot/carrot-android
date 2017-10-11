/* Teak -- Copyright (C) 2017 GoCarrot Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.teak.sdk.event;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.Map;

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
    void purchase_onPurchaseSucceeded(Map<String, Object> payload);
    void purchase_onPurchaseFailed(Map<String, Object> payload);
}
