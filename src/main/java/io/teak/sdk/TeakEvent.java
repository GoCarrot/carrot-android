package io.teak.sdk;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.teak.sdk.core.ThreadFactory;

public class TeakEvent {
    public final String eventType;

    public static final TeakEvent StopEvent = new TeakEvent(null);

    protected TeakEvent(String eventType) {
        this.eventType = eventType;
    }

    public static boolean postEvent(@NonNull TeakEvent event) {
        synchronized (eventProcessingThreadMutex) {
            if (eventProcessingThread == null || !eventProcessingThread.isAlive()) {
                eventProcessingThread = ThreadFactory.autoStart(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            TeakEvent event;
                            while ((event = eventQueue.take()).eventType != null) {
                                TeakEvent.eventListeners.processEvent(event);
                            }
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                });
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
        private ArrayList<EventListener> eventListeners = new ArrayList<>();

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

        void processEvent(final TeakEvent event) {
            // Because events can cause other event listeners to get added (and that should be allowed)
            // copy the list on each event
            // TODO: There is probably a better way
            ArrayList<EventListener> eventListenersForEvent;
            synchronized (this.eventListenersMutex) {
                eventListenersForEvent = this.eventListeners;
                this.eventListeners = new ArrayList<>(this.eventListeners);
            }

            for (EventListener e : eventListenersForEvent) {
                // TODO: This seems...kind of horrible, but maybe the Java runtime will be fine with it
                final EventListener currentListener = e;
                final Thread thread = ThreadFactory.autoStart(new Runnable() {
                    @Override
                    public void run() {
                        currentListener.onNewEvent(event);
                    }
                });
                try {
                    thread.join(5000);
                } catch (Exception ignored) {
                }

                if (thread.isAlive()) {
                    StackTraceElement[] trace = thread.getStackTrace();
                    StringBuilder backTrace = new StringBuilder();
                    for (StackTraceElement element : trace) {
                        backTrace.append("\n\t").append(element.toString());
                    }

                    String errorText = "Took too long processing '" + event.eventType + "' in:" + backTrace;

                    // TODO: Probably shouldn't throw here, but report it somehow
                    if (android.os.Debug.isDebuggerConnected()) {
                        android.util.Log.e(Teak.LOG_TAG, errorText);
                    } else {
                        thread.interrupt();
                        throw new IllegalStateException(errorText);
                    }
                }
            }
        }
    }

    private static EventListeners eventListeners = new EventListeners();
}
