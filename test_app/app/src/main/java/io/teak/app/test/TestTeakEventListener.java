package io.teak.app.test;

import android.support.annotation.NonNull;

import io.teak.sdk.TeakEvent;

@SuppressWarnings("WeakerAccess") // Must be public in order to mock
public abstract class TestTeakEventListener implements TeakEvent.EventListener {
    @Override
    public void onNewEvent(@NonNull TeakEvent event) {
        eventRecieved(event.getClass(), event.eventType);
    }

    public abstract void eventRecieved(Class eventClass, String eventType);
}
