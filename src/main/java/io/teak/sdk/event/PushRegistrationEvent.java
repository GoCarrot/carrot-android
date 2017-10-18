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

import android.support.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.teak.sdk.TeakEvent;

public class PushRegistrationEvent extends TeakEvent {
    public static final String Type = "PushRegistrationEvent";

    public final Map<String, String> registration;

    public PushRegistrationEvent(@NonNull String key, @NonNull String value) {
        super(Type);
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        this.registration = Collections.unmodifiableMap(map);
    }
}
