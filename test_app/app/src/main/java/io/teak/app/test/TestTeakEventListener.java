package io.teak.app.test;

import android.support.annotation.NonNull;

import io.teak.sdk.TeakEvent;

abstract class TestTeakEventListener implements TeakEvent.EventListener {
    @Override
    public void onNewEvent(@NonNull TeakEvent event) {
        eventRecieved(event.getClass(), event.eventType);
    }

    abstract void eventRecieved(Class eventClass, String eventType);
}
