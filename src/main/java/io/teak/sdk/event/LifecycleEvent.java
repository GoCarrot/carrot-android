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

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.teak.sdk.TeakEvent;

public class LifecycleEvent extends TeakEvent {
    public static final String Created = "LifecycleEvent.Created";
    public static final String Paused = "LifecycleEvent.Paused";
    public static final String Resumed = "LifecycleEvent.Resumed";

    public final Intent intent;

    public LifecycleEvent(@NonNull String type, @NonNull Intent intent) {
        super(type);
        if (type.equals(Created) || type.equals(Paused) || type.equals(Resumed)) {
            this.intent = intent;
        } else {
            throw new IllegalArgumentException("Type must be one of 'Created', 'Paused' or 'Resumed'");
        }
    }
}
