package io.teak.app.test;

import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.TrackEventEvent;
import io.teak.sdk.event.UserIdEvent;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class PublicAPI extends TeakIntegrationTest {
    // Teak#onCreate is already in use by the runner

    @Test
    public void onActivityResult() {
        launchActivity();

        Intent intent = spy(Intent.class);
        Teak.onActivityResult(0, 42, intent);
        verify(store, timeout(5000).times(1)).checkActivityResultForPurchase(42, intent);
    }

    @Test
    public void identifyUser() {
        launchActivity();

        TestTeakEventListener listener = spy(TestTeakEventListener.class);
        TeakEvent.addEventListener(listener);

        String userId = "test user id";
        Teak.identifyUser(userId);

        verify(listener, timeout(5000).times(1)).eventRecieved(UserIdEvent.class, UserIdEvent.Type);
    }

    @Test
    public void trackEvent() {
        launchActivity();

        TestTeakEventListener listener = spy(TestTeakEventListener.class);
        TeakEvent.addEventListener(listener);

        Teak.trackEvent("foo", "bar", "baz");

        verify(listener, timeout(5000).times(1)).eventRecieved(TrackEventEvent.class, TrackEventEvent.Type);
    }
}
