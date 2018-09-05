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

import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.teak.sdk.TeakEvent;

public class PurchaseFailedEvent extends TeakEvent {
    public static final String Type = "PurchaseFailedEvent";
    public final int errorCode;
    public final Map<String, Object> extras;

    public PurchaseFailedEvent(int errorCode, @Nullable Map<String, Object> extras) {
        super(Type);
        this.errorCode = errorCode;
        this.extras = extras == null ? new HashMap<String, Object>() : extras;
    }
}
