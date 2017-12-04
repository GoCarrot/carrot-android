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

package io.teak.sdk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

class RequirementsCheck {
    RequirementsCheck() {
        TeakConfiguration.addEventListener(new TeakConfiguration.EventListener() {
            @Override
            public void onConfigurationReady(@NonNull TeakConfiguration configuration) {
                onTeakConfigurationReady(configuration);
            }
        });
    }

    void checkActivity(@NonNull Activity activity) {
        // Check the launch mode of the activity for debugging purposes
        try {
            ComponentName cn = new ComponentName(activity, activity.getClass());
            ActivityInfo ai = activity.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
            // (LAUNCH_SINGLE_INSTANCE == LAUNCH_SINGLE_TASK | LAUNCH_SINGLE_TOP) but let's not
            // assume that those values will stay the same
            if ((ai.launchMode & ActivityInfo.LAUNCH_SINGLE_INSTANCE) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TASK) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TOP) == 0) {
                Teak.log.w("launch_mode", "The android:launchMode of this activity is not set to 'singleTask', 'singleTop' or 'singleInstance'. This could cause undesired behavior.");
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    private void onTeakConfigurationReady(@NonNull TeakConfiguration configuration) {
        // If the target SDK version is 26+ the linked support-v4 lib must be 26.1.0+
        if (configuration.appConfiguration.targetSdkVersion >= Build.VERSION_CODES.O) {
            try {
                Class<?> notificationCompatBuilderClass = NotificationCompat.Builder.class;
                notificationCompatBuilderClass.getMethod("setChannelId", String.class);
            } catch (Exception e) {
                throw new RuntimeException("App is targeting SDK version " + configuration.appConfiguration.targetSdkVersion +
                                           " but support-v4 library needs to be updated to at least version 26.1.0 to support notification categories.");
            }
        }
    }
}
