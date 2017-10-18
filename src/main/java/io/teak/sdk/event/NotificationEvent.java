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

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import io.teak.sdk.TeakEvent;

public class NotificationEvent extends TeakEvent {
    public static final String Received = "NotificationEvent.Received";
    public static final String Cleared = "NotificationEvent.Cleared";
    public static final String Interaction = "NotificationEvent.Interaction";

    public final Intent intent;
    public final Context context;

    public NotificationEvent(@NonNull String type, @NonNull Context context, @NonNull Intent intent) {
        super(type);
        if (type.equals(Received) || type.equals(Cleared) || type.equals(Interaction)) {
            this.intent = intent;
            this.context = context;
        } else {
            throw new IllegalArgumentException("Type must be one of 'Received', 'Cleared' or 'Interaction'");
        }
    }
}
