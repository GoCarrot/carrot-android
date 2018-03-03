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

import android.app.Notification;
import android.os.Bundle;
import android.support.annotation.NonNull;

import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;

public class NotificationReDisplayEvent extends TeakEvent {
    public static final String Type = "NotificationReDisplayEvent";

    public final Bundle bundle;
    public final Notification nativeNotification;

    public NotificationReDisplayEvent(@NonNull Bundle bundle, @NonNull Notification nativeNotification) {
        super(Type);
        this.bundle = bundle;
        this.nativeNotification = nativeNotification;
    }
}
