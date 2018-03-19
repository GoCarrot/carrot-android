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
package io.teak.sdk.configuration;

import android.support.annotation.NonNull;

import io.teak.sdk.Helpers;
import io.teak.sdk.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.core.DeepLink;
import io.teak.sdk.Request;
import io.teak.sdk.Teak;
import io.teak.sdk.core.Session;
import io.teak.sdk.event.DeepLinksReadyEvent;
import io.teak.sdk.event.RemoteConfigurationEvent;

public class RemoteConfiguration {
    @SuppressWarnings("WeakerAccess")
    public final AppConfiguration appConfiguration;
    @SuppressWarnings("WeakerAccess")
    private final String hostname;
    @SuppressWarnings("WeakerAccess")
    public final String sdkSentryDsn;
    @SuppressWarnings("WeakerAccess")
    public final String appSentryDsn;
    @SuppressWarnings("WeakerAccess")
    public final String gcmSenderId;
    @SuppressWarnings("WeakerAccess")
    public final boolean enhancedIntegrationChecks;
    @SuppressWarnings("WeakerAccess")
    public final Map<String, Object> endpointConfigurations;

    private RemoteConfiguration(@NonNull AppConfiguration appConfiguration, @NonNull String hostname,
        String sdkSentryDsn, String appSentryDsn, String gcmSenderId,
        boolean enhancedIntegrationChecks, JSONObject endpointConfigurations) {
        this.appConfiguration = appConfiguration;
        this.hostname = hostname;
        this.appSentryDsn = appSentryDsn;
        this.sdkSentryDsn = sdkSentryDsn;
        this.gcmSenderId = gcmSenderId;
        this.enhancedIntegrationChecks = enhancedIntegrationChecks;
        this.endpointConfigurations = endpointConfigurations.toMap();

        // Process
    }

    public static void registerStaticEventListeners() {
        // When Deep Links are ready, send out request for remote settings.
        // Must wait for Deep Link Routes to be registered so we can send them along
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(DeepLinksReadyEvent.Type)) {
                    final TeakConfiguration teakConfiguration = TeakConfiguration.get();
                    HashMap<String, Object> payload = new HashMap<>();
                    payload.put("id", teakConfiguration.appConfiguration.appId);
                    payload.put("deep_link_routes", DeepLink.getRouteNamesAndDescriptions());

                    Request.submit("gocarrot.com", "/games/" + teakConfiguration.appConfiguration.appId + "/settings.json", payload, Session.NullSession,
                        new Request.Callback() {
                            @Override
                            public void onRequestCompleted(int responseCode, String responseBody) {
                                try {
                                    final JSONObject response = new JSONObject((responseBody == null || responseBody.trim().isEmpty()) ? "{}" : responseBody);

                                    final RemoteConfiguration configuration = new RemoteConfiguration(teakConfiguration.appConfiguration,
                                        response.isNull("auth") ? "gocarrot.com" : response.getString("auth"),
                                        nullInsteadOfEmpty(response.isNull("sdk_sentry_dsn") ? null : response.getString("sdk_sentry_dsn")),
                                        nullInsteadOfEmpty(response.isNull("app_sentry_dsn") ? null : response.getString("app_sentry_dsn")),
                                        nullInsteadOfEmpty(response.isNull("gcm_sender_id") ? null : response.getString("gcm_sender_id")),
                                        response.optBoolean("enhanced_integration_checks", false),
                                            response.has("endpoint_configurations") ? response.getJSONObject("endpoint_configurations") : null);

                                    Teak.log.i("configuration.remote", configuration.toHash());
                                    TeakEvent.postEvent(new RemoteConfigurationEvent(configuration));
                                } catch (Exception e) {
                                    Teak.log.exception(e);
                                }
                            }
                        });
                }
            }
        });
    }

    // region Accessors
    public String getHostnameForEndpoint(@NonNull String endpoint) {
        return this.hostname;
    }
    // endregion

    // region Helpers
    private static String nullInsteadOfEmpty(String input) {
        if (input != null && !input.trim().isEmpty()) {
            return input;
        }
        return null;
    }
    // endregion

    private Map<String, Object> toHash() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("hostname", this.hostname);
        ret.put("sdkSentryDsn", this.sdkSentryDsn);
        ret.put("appSentryDsn", this.appSentryDsn);
        return ret;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.toHash())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
