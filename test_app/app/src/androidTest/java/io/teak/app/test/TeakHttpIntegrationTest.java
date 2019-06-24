package io.teak.app.test;

import android.os.Build;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import net.jodah.concurrentunit.Waiter;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import io.teak.sdk.Request;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.core.Session;
import io.teak.sdk.event.RemoteConfigurationEvent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.Mockito.mock;

public class TeakHttpIntegrationTest extends TeakIntegrationTest {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(Request.MOCKED_PORT);

    @BeforeClass
    public static void setupWireMock() {
//        // RemoteConfiguration for mocking
//        Request.registerStaticEventListeners();
//        final AppConfiguration appConfiguration = mock(AppConfiguration.class);
//        final RemoteConfiguration remoteConfiguration =
//                new RemoteConfiguration(appConfiguration,
//                        "127.0.0.1",
//                        null,
//                        null,
//                        "mock_gcm_sender_id",
//                        "mock_firebase_app_id",
//                        false,
//                        null,
//                        true);
//        TeakEvent.postEvent(new RemoteConfigurationEvent(remoteConfiguration));
    }

    @Before
    public void resetWireMockAndIdentifyUser() {
        WireMock.reset();

        final String userId = "integration-tests-" + Build.MODEL.toLowerCase();
        Teak.identifyUser(userId);
    }

    @Test
    public void foo() throws Throwable {
        // WireMock
        stubFor(post(urlEqualTo("/verify/this"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello world!")));

        Map<String, Object> payload = new HashMap<>();
        final Waiter waiter = new Waiter();
        Request.submit("/verify/this", payload, Session.NullSession, new Request.Callback() {
            @Override
            public void onRequestCompleted(int responseCode, String responseBody) {
                waiter.resume();
            }
        });
        waiter.await(500000, 1);
        verify(postRequestedFor(urlEqualTo("/verify/this"))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));
    }
}
