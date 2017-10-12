/* Teak -- Copyright (C) 2016-2017 GoCarrot Inc.
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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;

public class TeakConfiguration {
    static boolean initialize(Context context) {
        TeakConfiguration teakConfiguration = new TeakConfiguration(context);
        if (teakConfiguration.deviceConfiguration.deviceId != null) {
            Instance = teakConfiguration;
            synchronized (eventListenersMutex) {
                for (EventListener e : eventListeners) {
                    e.onConfigurationReady(teakConfiguration);
                }
            }
        }

        return Instance != null;
    }

    public final DebugConfiguration debugConfiguration;
    public final AppConfiguration appConfiguration;
    public final DeviceConfiguration deviceConfiguration;

    private TeakConfiguration(Context context) {
        this.debugConfiguration = new DebugConfiguration(context);
        this.appConfiguration = new AppConfiguration(context);
        this.deviceConfiguration = new DeviceConfiguration(context, this.appConfiguration);
    }

    private static TeakConfiguration Instance;
    public static @Nullable TeakConfiguration get() {
        return Instance;
    }

    ///// Events

    interface EventListener {
        void onConfigurationReady(@NonNull TeakConfiguration configuration);
    }

    private static final Object eventListenersMutex = new Object();
    private static ArrayList<EventListener> eventListeners = new ArrayList<>();

    public static void addEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            if (!eventListeners.contains(e)) {
                eventListeners.add(e);
            }
        }

        // Configuration is already ready, so call it now
        if (Instance != null) {
            e.onConfigurationReady(Instance);
        }
    }

    public static void removeEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            eventListeners.remove(e);
        }
    }
}
