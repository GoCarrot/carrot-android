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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;

import java.net.URL;
import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.teak.sdk.json.JSONObject;
import io.teak.sdk.json.JSONArray;

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
    private final Callback callback;

    ///// Callback

    public interface Callback {
        public void onRequestCompleted(int responseCode, String responseBody);
    }

    ///// Common configuration

    private static String teakApiKey;
    public static void setTeakApiKey(@NonNull String apiKey) {
        Request.teakApiKey = apiKey;
    }
    public static boolean hasTeakApiKey() {
        return Request.teakApiKey != null && Request.teakApiKey.length() > 0;
    }

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
                configurationPayload.put("device_memory_class", configuration.deviceConfiguration.memoryClass);
                configurationPayload.put("miui_version", configuration.deviceConfiguration.miuiVersion);

                if (configuration.debugConfiguration.isDebug()) {
                    configurationPayload.put("debug", true);
                }
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

    ///// Batching

    private static abstract class BatchedRequest extends Request {
        private ScheduledFuture<?> scheduledFuture;
        private final List<Callback> callbacks = new LinkedList<>();
        final List<Object> batch = new LinkedList<>();

        BatchedRequest(@Nullable String hostname, @NonNull String endpoint, @NonNull Session session, boolean addStandardAttributes) {
            super(hostname, endpoint, new HashMap<String, Object>(), session, null, addStandardAttributes);
        }

        synchronized boolean add(@NonNull String endpoint, @NonNull Map<String, Object> payload, @Nullable Callback callback) {
            if (this.scheduledFuture != null && !this.scheduledFuture.cancel(false)) {
                return false;
            }

            if (callback != null) {
                this.callbacks.add(callback);
            }
            this.batch.add(payload);
            this.scheduledFuture = Request.requestExecutor.schedule(this, 5L, TimeUnit.SECONDS);
            return true;
        }

        @Override
        protected void onRequestCompleted(int responseCode, String responseBody) {
            super.onRequestCompleted(responseCode, responseBody);
            for (Callback callback : this.callbacks) {
                callback.onRequestCompleted(responseCode, responseBody);
            }
        }
    }

    private static class BatchedParsnipRequest extends BatchedRequest {
        BatchedParsnipRequest(@Nullable String hostname, @NonNull Session session) {
            super(hostname, "/batch", session, false);
        }

        @Override
        synchronized boolean add(@NonNull String endpoint, @NonNull Map<String, Object> payload, @Nullable Callback callback) {
            final Map<String, Object> batchPayload = new HashMap<>(payload);

            // Parsnip needs the standard attributes in every event
            batchPayload.putAll(Request.configurationPayload);

            // Parsnip event name
            batchPayload.put("name", endpoint.startsWith("/") ? endpoint.substring(1) : endpoint);

            return super.add(endpoint, batchPayload, callback);
        }

        @Override
        public void run() {
            // Add batch elements
            this.payload.put("events", this.batch);
            super.run();
        }
    }

    private static class BatchedTrackEventRequest extends BatchedRequest {
        BatchedTrackEventRequest(@Nullable String hostname, @NonNull Session session) {
            super(hostname, "/me/events", session, true);
        }

        @Override
        synchronized boolean add(@NonNull String endpoint, @NonNull Map<String, Object> payload, @Nullable Callback callback) {
            return super.add(endpoint, payload, callback);
        }

        @Override
        public void run() {
            // Update the request date
            this.payload.put("request_date", new Date().getTime() / 1000);

            // Add batch elements
            this.payload.put("batch", this.batch);
            super.run();
        }
    }

    private static BatchedParsnipRequest parsnipBatch;
    private static final Object parsnipBatchMutex = new Object();

    private static BatchedTrackEventRequest batchedTrackEventRequest;
    private static final Object trackEventBatchMutex = new Object();

    ///// SDK interface

    static ScheduledExecutorService requestExecutor = Executors.newSingleThreadScheduledExecutor();

    public static void submit(@NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session) {
        submit(endpoint, payload, session, null);
    }

    public static void submit(@NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session, @Nullable Callback callback) {
        submit(Request.remoteConfiguration.getHostnameForEndpoint(endpoint), endpoint, payload, session, callback);
    }

    public static void submit(@Nullable String hostname, @NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session) {
        submit(hostname, endpoint, payload, session, null);
    }

    public static void submit(@Nullable String hostname, @NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session, @Nullable Callback callback) {
        BatchedRequest batch = null;
        if ("parsnip.gocarrot.com".equals(hostname)) {
            synchronized (parsnipBatchMutex) {
                if (parsnipBatch == null) {
                    parsnipBatch = new BatchedParsnipRequest(hostname, session);
                }
            }
            batch = parsnipBatch;
        } else if ("/me/events".equals(endpoint)) {
            synchronized (trackEventBatchMutex) {
                if (batchedTrackEventRequest == null) {
                    batchedTrackEventRequest = new BatchedTrackEventRequest(hostname, session);
                }
                batch = batchedTrackEventRequest;
            }
        }

        if(batch != null) {
            if (!batch.add(endpoint, payload, callback)) {
                // Retry
            }
        } else {
            requestExecutor.execute(new Request(hostname, endpoint, payload, session, callback, true));
        }
    }

    /////

    private Request(@Nullable String hostname, @NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session, @Nullable Callback callback, boolean addStandardAttributes) {
        if (!endpoint.startsWith("/")) {
            throw new IllegalArgumentException("Parameter 'endpoint' must start with '/' or things will break, and you will lose an hour of your life debugging.");
        }

        this.hostname = hostname;
        this.endpoint = endpoint;
        this.payload = new HashMap<>(payload);
        this.session = session;
        this.requestId = UUID.randomUUID().toString().replace("-", "");
        this.callback = callback;

        if (addStandardAttributes) {
            if (session.userId() != null) {
                this.payload.put("api_key", session.userId());
            }

            this.payload.put("request_date", new Date().getTime() / 1000); // Milliseconds -> Seconds

            this.payload.putAll(Request.configurationPayload);
        }
    }

    public static class Payload {
        @SuppressWarnings("WeakerAccess")
        public static String toSigningString(Map<String, Object> payload) throws UnsupportedEncodingException {
            final StringBuilder builder = payloadToString(payload, false);
            builder.deleteCharAt(builder.length() - 1);
            return builder.toString();
        }

        @SuppressWarnings("WeakerAccess")
        public static String toRequestBody(Map<String, Object> payload, String sig) throws UnsupportedEncodingException {
            final StringBuilder builder = payloadToString(payload, true);
            builder.append("sig=").append(URLEncoder.encode(sig, "UTF-8"));
            return builder.toString();
        }

        private static StringBuilder payloadToString(Map<String, Object> payload, boolean escape) throws UnsupportedEncodingException {
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
                    builder.append(key).append("=").append(escape ? URLEncoder.encode(valueString, "UTF-8") : valueString).append("&");
                } else {
                    Teak.log.e("request", "Value for key is null.", Helpers.mm.h("key", key));
                }
            }
            return builder;
        }
    }

    @Override
    public void run() {
        final SecretKeySpec keySpec = new SecretKeySpec(Request.teakApiKey.getBytes(), "HmacSHA256");
        String requestBody;

        try {
            final String stringToSign = "POST\n" + this.hostname + "\n" + this.endpoint + "\n" + Payload.toSigningString(this.payload);
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            final byte[] result = mac.doFinal(stringToSign.getBytes());
            final String sig = Base64.encodeToString(result, Base64.NO_WRAP);

            requestBody = Payload.toRequestBody(this.payload, sig);
        } catch (Exception e) {
            Teak.log.exception(e);
            return;
        }

        try {
            Teak.log.i("request.send", this.toMap());
            final long startTime = System.nanoTime();
            final URL url = new URL("https://" + this.hostname + this.endpoint);
            final IHttpsRequest request = new DefaultHttpsRequest(); // TODO: Do this properly with a Factory
            final IHttpsRequest.Response response = request.synchronousRequest(url, requestBody);

            final int statusCode = response == null ? 0 : response.statusCode;
            final String body = response == null ? null : response.body;

            final Map<String, Object> h = this.toMap();
            h.remove("payload");
            h.put("response_time", (System.nanoTime() - startTime) / 1000000.0);

            if (response != null) {
                try {
                    h.put("payload", Helpers.jsonToMap(new JSONObject(response.body)));
                } catch (Exception ignored) {
                }
            }

            Teak.log.i("request.reply", h);

            this.onRequestCompleted(statusCode, body);
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    protected void onRequestCompleted(int responseCode, String responseBody) {
        if (this.callback != null) {
            this.callback.onRequestCompleted(responseCode, responseBody);
        }
    }

    private Map<String, Object> toMap() {
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
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.toMap())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
