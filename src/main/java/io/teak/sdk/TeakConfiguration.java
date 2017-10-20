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

import java.util.ArrayList;

import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.configuration.DebugConfiguration;
import io.teak.sdk.configuration.DeviceConfiguration;
import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.event.RemoteConfigurationEvent;

public class TeakConfiguration {
    public static boolean initialize(@NonNull Context context, @NonNull IObjectFactory objectFactory) {
        TeakConfiguration teakConfiguration = new TeakConfiguration(context.getApplicationContext(), objectFactory);

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
    public RemoteConfiguration remoteConfiguration;

    private TeakConfiguration(@NonNull Context context, @NonNull IObjectFactory objectFactory) {
        this.debugConfiguration = new DebugConfiguration(context);
        this.appConfiguration = new AppConfiguration(context, objectFactory.getAndroidResources());
        this.deviceConfiguration = new DeviceConfiguration(objectFactory);
    }

    private static TeakConfiguration Instance;

    public static
    @NonNull
    TeakConfiguration get() {
        if (Instance == null) {
            throw new IllegalStateException("Call to TeakConfiguration.get() before initialization.");
        }

        return Instance;
    }

    static {
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(RemoteConfigurationEvent.Type) && Instance != null) {
                    Instance.remoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;
                }
            }
        });
    }

    ///// Events

    public interface EventListener {
        void onConfigurationReady(@NonNull TeakConfiguration configuration);
    }

    private static final Object eventListenersMutex = new Object();
    private static ArrayList<EventListener> eventListeners = new ArrayList<>();

    public static void addEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            if (!eventListeners.contains(e)) {
                eventListeners.add(e);
            }

            // Configuration is already ready, so call it now
            if (Instance != null) {
                e.onConfigurationReady(Instance);
            }
        }
    }
}
