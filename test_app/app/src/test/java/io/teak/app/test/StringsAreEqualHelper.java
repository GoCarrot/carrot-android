package io.teak.app.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.teak.sdk.Helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class StringsAreEqualHelper {
    @Test
    public void NullVsNull() {
        assertTrue(Helpers.stringsAreEqual(null, null));
    }

    @Test
    public void NullVsNonNull() {
        assertFalse(Helpers.stringsAreEqual(null, "foo"));
    }

    @Test
    public void NonNullVsNonNull() {
        assertFalse(Helpers.stringsAreEqual("foo", "bar"));
    }

    @Test
    public void CapitalizedVsNonCapitalized() {
        assertFalse(Helpers.stringsAreEqual("Foo", "foo"));
    }

    @Test
    public void Equal() {
        assertTrue(Helpers.stringsAreEqual("Foo", "Foo"));
    }
}
