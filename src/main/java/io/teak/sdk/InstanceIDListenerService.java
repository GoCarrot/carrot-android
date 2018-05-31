/* Teak -- Copyright (C) 2016 GoCarrot Inc.
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

import java.util.ArrayList;

public class InstanceIDListenerService extends com.google.android.gms.iid.InstanceIDListenerService implements Unobfuscable {
    @Override
    public void onTokenRefresh() {
        synchronized (eventListenersMutex) {
            for (EventListener e : eventListeners) {
                e.onTokenRefresh();
            }
        }
    }

    ///// Event Listener

    public interface EventListener {
        void onTokenRefresh();
    }

    private static final Object eventListenersMutex = new Object();
    private static ArrayList<EventListener> eventListeners = new ArrayList<>();

    public static void addEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            if (!eventListeners.contains(e)) {
                eventListeners.add(e);
            }
        }
    }
}
