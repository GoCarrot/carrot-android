package io.teak.sdk.configuration;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.Request;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.core.DeepLink;
import io.teak.sdk.core.Session;
import io.teak.sdk.event.DeepLinksReadyEvent;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.io.AndroidResources;
import io.teak.sdk.io.DefaultAndroidResources;
import io.teak.sdk.json.JSONObject;

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
    public final String firebaseAppId;
    @SuppressWarnings("WeakerAccess")
    public final boolean ignoreDefaultFirebaseConfiguration;
    @SuppressWarnings("WeakerAccess")
    public final boolean enhancedIntegrationChecks;
    @SuppressWarnings("WeakerAccess")
    public final Map<String, Object> endpointConfigurations;
    @SuppressWarnings("WeakerAccess")
    public final Map<String, Object> dynamicParameters;
    @SuppressWarnings("WeakerAccess")
    public final int heartbeatInterval;
    @SuppressWarnings("WeakerAccess")
    public final boolean isMocked;

    private static final String defaultHostname = "gocarrot.com";

    private static final String defaultDynamicParameters = "{  \n"
                                                           +
                                                           "  \"app_version_developer\":{\n"
                                                           +
                                                           "    \"android\":\"io_teak_developer_version\","
                                                           +
                                                           "    \"ios\":\"TeakDeveloperVersion\","
                                                           +
                                                           "  }\n"
                                                           +
                                                           "}";

    private static final String defaultEndpointJson = "{  \n"
                                                      +
                                                      "  \"gocarrot.com\":{  \n"
                                                      +
                                                      "    \"/me/events\":{  \n"
                                                      +
                                                      "      \"batch\":{  \n"
                                                      +
                                                      "        \"time\":5,\n"
                                                      +
                                                      "        \"count\":50\n"
                                                      +
                                                      "      },\n"
                                                      +
                                                      "      \"retry\":{  \n"
                                                      +
                                                      "        \"times\":[  \n"
                                                      +
                                                      "          10,\n"
                                                      +
                                                      "          20,\n"
                                                      +
                                                      "          30\n"
                                                      +
                                                      "        ],\n"
                                                      +
                                                      "        \"jitter\":6\n"
                                                      +
                                                      "      }\n"
                                                      +
                                                      "    },\n"
                                                      +
                                                      "    \"/me/profile\":{  \n"
                                                      +
                                                      "      \"batch\":{  \n"
                                                      +
                                                      "        \"time\":10,\n"
                                                      +
                                                      "        \"lww\":true\n"
                                                      +
                                                      "      }\n"
                                                      +
                                                      "    }\n"
                                                      +
                                                      "  },\n"
                                                      +
                                                      "  \"parsnip.gocarrot.com\":{  \n"
                                                      +
                                                      "    \"/batch\":{  \n"
                                                      +
                                                      "      \"batch\":{  \n"
                                                      +
                                                      "        \"time\":5,\n"
                                                      +
                                                      "        \"count\":100\n"
                                                      +
                                                      "      }\n"
                                                      +
                                                      "    }\n"
                                                      +
                                                      "  }\n"
                                                      +
                                                      "}";

    public RemoteConfiguration(@NonNull AppConfiguration appConfiguration, @NonNull String hostname,
        String sdkSentryDsn, String appSentryDsn, String gcmSenderId, String firebaseAppId,
        boolean ignoreDefaultFirebaseConfiguration, boolean enhancedIntegrationChecks,
        JSONObject endpointConfigurations, JSONObject dynamicParameters, int heartbeatInterval, boolean isMocked) {
        this.appConfiguration = appConfiguration;
        this.hostname = hostname;
        this.appSentryDsn = appSentryDsn;
        this.sdkSentryDsn = sdkSentryDsn;
        this.gcmSenderId = gcmSenderId;
        this.firebaseAppId = firebaseAppId;
        this.ignoreDefaultFirebaseConfiguration = ignoreDefaultFirebaseConfiguration;
        this.enhancedIntegrationChecks = enhancedIntegrationChecks;
        this.isMocked = isMocked;
        this.heartbeatInterval = heartbeatInterval;

        this.endpointConfigurations = endpointConfigurations == null ? new JSONObject(defaultEndpointJson).toMap() : endpointConfigurations.toMap();

        this.dynamicParameters = new HashMap<>();
        if (dynamicParameters == null) {
            dynamicParameters = new JSONObject(defaultDynamicParameters);
        }

        final AndroidResources androidResources = new AndroidResources(appConfiguration.applicationContext, new DefaultAndroidResources(appConfiguration.applicationContext));
        final Iterator<String> keys = dynamicParameters.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final JSONObject dynamicParameter = dynamicParameters.optJSONObject(key);
            if (dynamicParameter != null) {
                final String parameterValue = androidResources.getTeakStringResource(dynamicParameter.getString("android"));
                if (parameterValue != null) {
                    this.dynamicParameters.put(key, parameterValue);
                }
            }
        }
    }

    public static void registerStaticEventListeners() {
        // When Deep Links are ready, send out request for remote settings.
        // Must wait for Deep Link Routes to be registered so we can send them along
        TeakEvent.addEventListener(event -> {
            if (event.eventType.equals(DeepLinksReadyEvent.Type)) {
                final TeakConfiguration teakConfiguration = TeakConfiguration.get();
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("id", teakConfiguration.appConfiguration.appId);
                payload.put("deep_link_routes", DeepLink.getRouteNamesAndDescriptions());

                final String locale = Locale.getDefault().toString();
                payload.put("locale", locale);

                Request.submit("gocarrot.com", "/games/" + teakConfiguration.appConfiguration.appId + "/settings.json", payload, Session.NullSession,
                    (responseCode, responseBody) -> {
                        try {
                            final JSONObject response = new JSONObject((responseBody == null || responseBody.trim().isEmpty()) ? "{}" : responseBody);

                            class ResponseHelper {
                                private String nullInsteadOfEmpty(String input) {
                                    if (input != null && !input.trim().isEmpty()) {
                                        return input;
                                    }
                                    return null;
                                }
                                private String strOrNull(String key) {
                                    return nullInsteadOfEmpty(response.isNull(key) ? null : response.getString(key));
                                }
                                private boolean boolOrFalse(String key) {
                                    return response.optBoolean(key, false);
                                }
                                private JSONObject jsonOrNull(String key) {
                                    return response.has(key) ? response.getJSONObject(key) : null;
                                }
                            }
                            final ResponseHelper helper = new ResponseHelper();

                            // Future-Pat: This looks ugly, the reason we aren't moving it into the constructor itself is
                            // so that it can be easily mocked for the functional tests.
                            final RemoteConfiguration configuration = new RemoteConfiguration(teakConfiguration.appConfiguration,
                                response.isNull("auth") ? RemoteConfiguration.defaultHostname : response.getString("auth"),
                                helper.strOrNull("sdk_sentry_dsn"),
                                helper.strOrNull("app_sentry_dsn"),
                                helper.strOrNull("gcm_sender_id"),
                                helper.strOrNull("firebase_app_id"),
                                helper.boolOrFalse("ignore_default_firebase_configuration"),
                                helper.boolOrFalse("enhanced_integration_checks"),
                                helper.jsonOrNull("endpoint_configurations"),
                                helper.jsonOrNull("dynamic_parameters"),
                                response.optInt("heartbeat_interval", 60),
                                false);

                            Teak.log.i("configuration.remote", configuration.toHash());
                            TeakEvent.postEvent(new RemoteConfigurationEvent(configuration));
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    });
            } else if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                RemoteConfiguration.activeRemoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;
            }
        });
    }

    // region Hostnames
    private static RemoteConfiguration activeRemoteConfiguration;

    public String getHostnameForEndpoint(@NonNull String endpoint) {
        return RemoteConfiguration.getHostnameForEndpoint(endpoint, this);
    }

    public static String getHostnameForEndpoint(@NonNull String endpoint, @Nullable RemoteConfiguration remoteConfiguration) {
        if (remoteConfiguration == null) remoteConfiguration = RemoteConfiguration.activeRemoteConfiguration;

        if (remoteConfiguration != null) {
            return remoteConfiguration.hostname;
        }

        // Defaults
        return RemoteConfiguration.defaultHostname;
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
    @NonNull
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.toHash())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
