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

import io.teak.sdk.TeakEvent;

// TODO: This could have a better name?
public class DeviceInfoEvent extends TeakEvent {
    public static final String AdvertisingId = "DeviceInfoEvent.AdvertisingId";
    public static final String GCMKey = "DeviceInfoEvent.GCMKey";
    public static final String ADMKey = "DeviceInfoEvent.ADMKey";

    public final String value;

    public DeviceInfoEvent(@NonNull String type, @NonNull String value) {
        super(type);
        if (type.equals(AdvertisingId) || type.equals(GCMKey) || type.equals(ADMKey)) {
            this.value = value;
        } else {
            throw new IllegalArgumentException("Type must be one of 'AdvertisingId', 'GCMKey' or 'ADMKey'");
        }
    }
}
