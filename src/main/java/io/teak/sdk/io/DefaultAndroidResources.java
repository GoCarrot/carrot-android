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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class DefaultAndroidResources implements IAndroidResources {
    private final Context context;

    public DefaultAndroidResources(@NonNull Context context) {
        this.context = context;
    }

    @Nullable
    @Override
    public String getStringResource(@NonNull String name) {
        try {
            String packageName = this.context.getPackageName();
            int resId = this.context.getResources().getIdentifier(name, "string", packageName);
            return this.context.getString(resId);
        } catch (Exception ignored) {
        }
        return null;
    }
}
