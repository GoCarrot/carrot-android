package io.teak.app.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import io.teak.sdk.Teak;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class DeepLink extends TeakUnitTest {
    @Test
    public void simple() throws Exception {
        io.teak.sdk.core.DeepLink.routes.clear();

        final Teak.DeepLink callback = mock(Teak.DeepLink.class);
        Teak.registerDeepLink("/foo/:bar/:baz", "Test", "Also test", callback);
        Thread.sleep(10); // sleep to make sure the async happens
        assertTrue(io.teak.sdk.core.DeepLink.routes.containsKey("/foo/(?<bar>[^/?#]+)/(?<baz>[^/?#]+)"));

        final URI uri = new URI("/foo/1234/abcd");
        assertNotNull(uri);
        assertTrue(io.teak.sdk.core.DeepLink.processUri(uri));

        final Map<String, Object> arg = new HashMap<>();
        arg.put("bar", "1234");
        arg.put("baz", "abcd");
        verify(callback).call(arg);
    }

    @Test
    public void withQuery() throws Exception {
        io.teak.sdk.core.DeepLink.routes.clear();

        final Teak.DeepLink callback = mock(Teak.DeepLink.class);
        Teak.registerDeepLink("/foo/:bar/:baz", "", "", callback);
        Thread.sleep(10);
        assertTrue(io.teak.sdk.core.DeepLink.routes.containsKey("/foo/(?<bar>[^/?#]+)/(?<baz>[^/?#]+)"));

        final URI uri = new URI("/foo/1234/abcd?foo=bar");
        assertNotNull(uri);
        assertTrue(io.teak.sdk.core.DeepLink.processUri(uri));

        final Map<String, Object> arg = new HashMap<>();
        arg.put("bar", "1234");
        arg.put("baz", "abcd");
        arg.put("foo", "bar");
        verify(callback).call(arg);
    }

    @Test
    public void queryOverwritesPath() throws Exception {
        io.teak.sdk.core.DeepLink.routes.clear();

        final Teak.DeepLink callback = mock(Teak.DeepLink.class);
        Teak.registerDeepLink("/foo/:bar/:baz", "", "", callback);
        Thread.sleep(10);
        assertTrue(io.teak.sdk.core.DeepLink.routes.containsKey("/foo/(?<bar>[^/?#]+)/(?<baz>[^/?#]+)"));

        final URI uri = new URI("/foo/1234/abcd?bar=barbar");
        assertNotNull(uri);
        assertTrue(io.teak.sdk.core.DeepLink.processUri(uri));

        final Map<String, Object> arg = new HashMap<>();
        arg.put("bar", "barbar");
        arg.put("baz", "abcd");
        verify(callback).call(arg);
    }
}
