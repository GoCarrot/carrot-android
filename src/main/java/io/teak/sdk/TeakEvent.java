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

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TeakEvent {
    public final String eventType;

    protected TeakEvent(String eventType) {
        this.eventType = eventType;
    }

    public static boolean postEvent(@NonNull TeakEvent event) {
        synchronized (eventProcessingThreadMutex) {
            if (eventProcessingThread == null || !eventProcessingThread.isAlive()) {
                eventProcessingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            TeakEvent event;
                            while ((event = eventQueue.take()).eventType != null) {
                                TeakEvent.eventListeners.processEvent(event);
                            }
                        } catch (Exception ignored){
                        }
                    }
                });
                eventProcessingThread.start();
            }
        }

        return TeakEvent.eventQueue.offer(event);
    }

    private static Thread eventProcessingThread;
    private static final BlockingQueue<TeakEvent> eventQueue = new LinkedBlockingQueue<>();
    private static final Object eventProcessingThreadMutex = new Object();

    ///// Event Listener

    public interface EventListener {
        void onNewEvent(@NonNull TeakEvent event);
    }

    public static void addEventListener(EventListener e) {
        TeakEvent.eventListeners.add(e);
    }

    public static void removeEventListener(EventListener e) {
        TeakEvent.eventListeners.remove(e);
    }

    public static class EventListeners {
        private final Object eventListenersMutex = new Object();
        private final ArrayList<EventListener> eventListeners = new ArrayList<>();

        void add(EventListener e) {
            synchronized (this.eventListenersMutex) {
                if (!this.eventListeners.contains(e)) {
                    this.eventListeners.add(e);
                }
            }
        }

        void remove(EventListener e) {
            synchronized (this.eventListenersMutex) {
                this.eventListeners.remove(e);
            }
        }

        void processEvent(TeakEvent event) {
            synchronized (this.eventListenersMutex) {
                for (EventListener e : this.eventListeners) {
                    e.onNewEvent(event);
                }
            }
        }
    }
    private static EventListeners eventListeners = new EventListeners();
}
