package io.teak.app.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import io.teak.sdk.event.TrackEventEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class TrackEventPayloadCompare {
    private final Map<String, Object> a = TrackEventEvent.payloadForEvent("a", "b", "c", 1);
    private final Map<String, Object> b1 = TrackEventEvent.payloadForEvent("b", null, null, 1);
    private final Map<String, Object> b2 = TrackEventEvent.payloadForEvent("b", "b", null, 1);
    private final Map<String, Object> b3 = TrackEventEvent.payloadForEvent("b", "b", "c", 1);

    private final Map<String, Object> b3_dupe = TrackEventEvent.payloadForEvent("b", "b", "c", 1);
    private final Map<String, Object> b3_with_duration = TrackEventEvent.payloadForEvent("b", "b", "c", 42);

    @Test
    public void CompareVsNull() {
        assertFalse(TrackEventEvent.payloadEquals(a, null));
    }

    @Test
    public void CompareNotEqual() {
        assertFalse(TrackEventEvent.payloadEquals(a, b1));
        assertFalse(TrackEventEvent.payloadEquals(a, b2));
        assertFalse(TrackEventEvent.payloadEquals(a, b3));

        assertFalse(TrackEventEvent.payloadEquals(b1, a));
        assertFalse(TrackEventEvent.payloadEquals(b2, a));
        assertFalse(TrackEventEvent.payloadEquals(b3, a));

        assertFalse(TrackEventEvent.payloadEquals(b1, b2));
        assertFalse(TrackEventEvent.payloadEquals(b2, b1));

        assertFalse(TrackEventEvent.payloadEquals(b1, b3));
        assertFalse(TrackEventEvent.payloadEquals(b3, b1));

        assertFalse(TrackEventEvent.payloadEquals(b2, b3));
        assertFalse(TrackEventEvent.payloadEquals(b3, b2));
    }

    @Test
    public void CompareEqual() {
        assertTrue(TrackEventEvent.payloadEquals(a, a));
        assertTrue(TrackEventEvent.payloadEquals(b1, b1));
        assertTrue(TrackEventEvent.payloadEquals(b2, b2));
        assertTrue(TrackEventEvent.payloadEquals(b3, b3));
        assertTrue(TrackEventEvent.payloadEquals(b3, b3_dupe));
        assertTrue(TrackEventEvent.payloadEquals(b3, b3_with_duration));
    }
}
