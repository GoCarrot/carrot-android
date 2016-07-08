/* Teak -- Copyright (C) 2016 GoCarrot Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.teak.sdk;

import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

class RemoteConfiguration {
    private static final String LOG_TAG = "Teak:RemoteConfig";

    public final AppConfiguration appConfiguration;
    private final String hostname;
    private final String sdkSentryDsn;
    private final String appSentryDsn;

    // region Event Listener
    public interface EventListener {
        void onConfigurationReady(RemoteConfiguration configuration);
    }

    private static final Object eventListenersMutex = new Object();
    private static ArrayList<EventListener> eventListeners = new ArrayList<>();

    public static void addEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            if (!eventListeners.contains(e)) {
                eventListeners.add(e);
            }
        }
    }

    public static void removeEventListener(EventListener e) {
        synchronized (eventListenersMutex) {
            eventListeners.remove(e);
        }
    }
    // endregion

    private RemoteConfiguration(@NonNull AppConfiguration appConfiguration, @NonNull String hostname, String sdkSentryDsn, String appSentryDsn) {
        this.appConfiguration = appConfiguration;
        this.hostname = hostname;
        this.appSentryDsn = appSentryDsn;
        this.sdkSentryDsn = sdkSentryDsn;
    }

    public static void requestConfigurationForApp(final Session session) {
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("id", session.appConfiguration.appId);

        new Thread(new Request("POST", "gocarrot.com", "/games/" + session.appConfiguration.appId + "/settings.json", payload, session) {
            @Override
            protected void done(int responseCode, String responseBody) {
                try {
                    JSONObject response = new JSONObject(responseBody);

                    RemoteConfiguration configuration = new RemoteConfiguration(session.appConfiguration,
                            response.optString("auth", "gocarrot.com"),
                            nullInsteadOfEmpty(response.optString("sdk_sentry_dsn", null)),
                            nullInsteadOfEmpty(response.optString("app_sentry_dsn", null)));

                    synchronized (eventListenersMutex) {
                        for (EventListener e : RemoteConfiguration.eventListeners) {
                            e.onConfigurationReady(configuration);
                        }
                    }
                } catch (Exception e) {
                    // TODO: Report error
                }
            }
        }).start();
    }

    // region Accessors
    public String getHostnameForEndpoint(@NonNull String endpoint) {
        if (endpoint.equals("/notification_received")) {
            return "parsnip.gocarrot.com";
        }
        return this.hostname;
    }

    public String sdkSentryDSN() {
        return this.sdkSentryDsn;
    }

    public String appSentryDSN() {
        return this.appSentryDsn;
    }
    // endregion

    // region Helpers
    private static String nullInsteadOfEmpty(String input) {
        if(input != null && !input.isEmpty()) {
            return input;
        }
        return null;
    }
    // endregion
}
