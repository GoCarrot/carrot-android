package io.teak.app.test;

import java.lang.reflect.Field;
import java.util.ArrayList;

import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.TeakEvent;

class TestHelpers {
    static void resetTeakEventListeners() throws NoSuchFieldException, IllegalAccessException {
        Field f = TeakEvent.class.getDeclaredField("eventListeners");
        f.setAccessible(true);
        f.set(null, new TeakEvent.EventListeners());
    }

    static void resetTeakConfiguration() throws NoSuchFieldException, IllegalAccessException {
        Field f = TeakConfiguration.class.getDeclaredField("eventListeners");
        f.setAccessible(true);
        f.set(null, new ArrayList<TeakConfiguration.EventListener>());

        f = TeakConfiguration.class.getDeclaredField("Instance");
        f.setAccessible(true);
        f.set(null, null);
    }
}
