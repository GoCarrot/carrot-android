package io.teak.app.test;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.teak.sdk.event.LifecycleEvent;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class Lifecycle extends TeakIntegrationTest {
    @Test
    public void basicLifecycle() {
        launchActivity();
    }

    @Test
    public void integratedLifecycle() {
        launchActivity();

        // Background the app, it should now be paused
        backgroundApp();
        verify(eventListener, timeout(5000).times(1)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Paused);

        // Bring the app back, it should be resumed again
        foregroundApp();
        verify(eventListener, timeout(5000).times(2)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Resumed);
    }
}
