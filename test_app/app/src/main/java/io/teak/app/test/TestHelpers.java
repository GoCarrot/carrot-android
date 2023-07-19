package io.teak.app.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.TeakEvent;

class TestHelpers {
    static void resetTeakEventListeners() throws NoSuchFieldException, IllegalAccessException {
        Field f = TeakEvent.class.getDeclaredField("eventListeners");
        f.setAccessible(true);
        TeakEvent.EventListeners listeners = ((TeakEvent.EventListeners) f.get(null));

        f = TeakEvent.EventListeners.class.getDeclaredField("eventListeners");
        f.setAccessible(true);
        f.set(listeners, new ArrayList<TeakEvent.EventListener>());
    }

    static void resetTeakConfiguration() throws NoSuchFieldException, IllegalAccessException {
        Field f = TeakConfiguration.class.getDeclaredField("eventListeners");
        f.setAccessible(true);
        ArrayList<TeakConfiguration.EventListener> listeners = ((ArrayList<TeakConfiguration.EventListener>) f.get(null));
        listeners.clear();

        f = TeakConfiguration.class.getDeclaredField("Instance");
        f.setAccessible(true);
        f.set(null, null);
    }
}
