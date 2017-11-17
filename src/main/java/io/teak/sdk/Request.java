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
import android.support.annotation.Nullable;
import android.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.lang.reflect.Array;

import java.net.URL;
import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;
import org.json.JSONArray;

import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.core.Session;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.io.DefaultHttpsRequest;
import io.teak.sdk.io.IHttpsRequest;

public class Request implements Runnable {
    private final String endpoint;
    private final String hostname;
    protected final Map<String, Object> payload;
    private final Session session;
    private final String requestId;

    ///// Common configuration

    private static String teakApiKey;
    private static Map<String, Object> configurationPayload = new HashMap<>();

    // TODO: Can't do this as a static-init block, will screw up unit tests
    static {
        TeakConfiguration.addEventListener(new TeakConfiguration.EventListener() {
            @Override
            public void onConfigurationReady(@NonNull TeakConfiguration configuration) {
                Request.teakApiKey = configuration.appConfiguration.apiKey;

                configurationPayload.put("sdk_version", Teak.Version);
                configurationPayload.put("game_id", configuration.appConfiguration.appId);
                configurationPayload.put("app_version", String.valueOf(configuration.appConfiguration.appVersion));
                configurationPayload.put("bundle_id", configuration.appConfiguration.bundleId);
                if (configuration.appConfiguration.installerPackage != null) {
                    configurationPayload.put("appstore_name", configuration.appConfiguration.installerPackage);
                }

                configurationPayload.put("device_id", configuration.deviceConfiguration.deviceId);
                configurationPayload.put("sdk_platform", configuration.deviceConfiguration.platformString);
                configurationPayload.put("device_manufacturer", configuration.deviceConfiguration.deviceManufacturer);
                configurationPayload.put("device_model", configuration.deviceConfiguration.deviceModel);
                configurationPayload.put("device_fallback", configuration.deviceConfiguration.deviceFallback);
            }
        });
    }

    ///// Remote Configuration

    private static RemoteConfiguration remoteConfiguration;

    public static void registerStaticEventListeners() {
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                    Request.remoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;
                }
            }
        });
    }

    /////

    public Request(@NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session) {
        this(Request.remoteConfiguration.getHostnameForEndpoint(endpoint), endpoint, payload, session);
    }

    public Request(@Nullable String hostname, @NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session) {
        this.hostname = hostname;
        this.endpoint = endpoint;
        this.payload = new HashMap<>(payload);
        this.session = session;
        this.requestId = UUID.randomUUID().toString().replace("-", "");

        if (session.userId() != null) {
            this.payload.put("api_key", session.userId());
        }

        this.payload.put("request_date", new Date().getTime() / 1000); // Milliseconds -> Seconds

        this.payload.putAll(Request.configurationPayload);
    }

    private StringBuilder payloadToSigningString(Map<String, Object> payload) {
        ArrayList<String> payloadKeys = new ArrayList<>(payload.keySet());
        Collections.sort(payloadKeys);
        StringBuilder builder = new StringBuilder();
        for (String key : payloadKeys) {
            Object value = payload.get(key);
            if (value != null) {
                String valueString;
                if (value instanceof Map) {
                    valueString = new JSONObject((Map) value).toString();
                } else if (value instanceof Array) {
                    valueString = new JSONArray(Collections.singletonList(value)).toString();
                } else if (value instanceof Collection) {
                    valueString = new JSONArray((Collection) value).toString();
                } else {
                    valueString = value.toString();
                }
                builder.append(key).append("=").append(valueString).append("&");
            } else {
                Teak.log.e("request", "Value for key is null.", Helpers.mm.h("key", key));
            }
        }
        return builder;
    }

    @Override
    public void run() {
        SecretKeySpec keySpec = new SecretKeySpec(Request.teakApiKey.getBytes(), "HmacSHA256");
        String requestBody;

        try {
            StringBuilder builder = payloadToSigningString(this.payload);
            builder.deleteCharAt(builder.length() - 1);

            String stringToSign = "POST\n" + this.hostname + "\n" + this.endpoint + "\n" + builder.toString();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(stringToSign.getBytes());

            builder = payloadToSigningString(this.payload);
            builder.append("sig=").append(URLEncoder.encode(Base64.encodeToString(result, Base64.NO_WRAP), "UTF-8"));

            requestBody = builder.toString();
        } catch (Exception e) {
            Teak.log.exception(e);
            return;
        }

        try {
            Teak.log.i("request.send", this.to_h());
            long startTime = System.nanoTime();

            URL url = new URL("https://" + this.hostname + this.endpoint);

            IHttpsRequest request = new DefaultHttpsRequest(); // TODO: Do this properly with a Factory
            IHttpsRequest.Response response = request.synchronousRequest(url, requestBody);

            if (response != null) {
                Map<String, Object> h = this.to_h();
                h.remove("payload");
                h.put("response_time", (System.nanoTime() - startTime) / 1000000.0);
                try {
                    h.put("payload", Helpers.jsonToMap(new JSONObject(response.body)));
                } catch (Exception ignored) {
                }
                Teak.log.i("request.reply", h);

                // For extending classes
                done(response.statusCode, response.body);
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    protected void done(int responseCode, String responseBody) {
    }

    private Map<String, Object> to_h() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("request_id", this.requestId);
        map.put("hostname", this.hostname);
        map.put("endpoint", this.endpoint);
        map.put("session", Integer.toHexString(this.session.hashCode()));
        map.put("payload", this.payload);
        return map;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.to_h())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
