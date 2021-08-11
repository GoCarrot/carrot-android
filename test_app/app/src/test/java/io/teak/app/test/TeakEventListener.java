package io.teak.app.test;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.junit.MockitoJUnitRunner;

import io.teak.sdk.Teak;
import io.teak.sdk.event.UserIdEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TeakEventListener extends TeakUnitTest {

    ///// Verify that the event listener array gets reset in between tests

    private static TestTeakEventListener verifyResetListener;

    @BeforeClass
    public static void initResetVerification() {
        verifyResetListener = spy(TestTeakEventListener.class);
    }

    @Test
    public void a_verify_setupReset() throws Exception {
        io.teak.sdk.TeakEvent.addEventListener(verifyResetListener);
        io.teak.sdk.TeakEvent.postEvent(new UserIdEvent("test", new Teak.UserConfiguration()));
        verify(verifyResetListener, timeout(5000).times(1)).eventRecieved(any(Class.class), anyString());
    }

    @Test
    public void b_verify_reset() throws Exception {
        // Make sure that the Event Listeners are getting reset in-between tests
        verify(verifyResetListener, timeout(5000).times(1)).eventRecieved(any(Class.class), anyString());
    }
}