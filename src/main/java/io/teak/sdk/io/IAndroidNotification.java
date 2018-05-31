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
package io.teak.sdk.io;

import android.app.Notification;
import android.content.Context;
import android.support.annotation.NonNull;

import io.teak.sdk.TeakNotification;

public interface IAndroidNotification {
    void cancelNotification(@NonNull Context context, int platformId);

    void displayNotification(@NonNull Context context, @NonNull TeakNotification teakNotification, @NonNull Notification nativeNotification);
}
