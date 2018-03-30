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
import java.util.Random;
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
    protected boolean sent;

    @SuppressWarnings("WeakerAccess")
    protected final boolean blackhole;
    @SuppressWarnings("WeakerAccess")
    protected final RetryConfiguration retry;
    @SuppressWarnings("WeakerAccess")
    protected final BatchConfiguration batch;

    ///// Mini-configs

    class RetryConfiguration {
        float jitter;
        float[] times;
        int retryIndex;

        RetryConfiguration() {
            this.jitter = 0.0f;
            this.times = new float[] {};
            this.retryIndex = 0;
        }
    }

    public class BatchConfiguration {
        public long count;
        public float time;

        BatchConfiguration() {
            this.count = 1;
            this.time = 0.0f;
        }
    }

    ///// Callback

    public interface Callback {
        void onRequestCompleted(int responseCode, String responseBody);
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
        final List<Object> batchContents = new LinkedList<>();

        BatchedRequest(@Nullable String hostname, @NonNull String endpoint, @NonNull Session session, boolean addStandardAttributes) {
            super(hostname, endpoint, new HashMap<String, Object>(), session, null, addStandardAttributes);
        }

        synchronized boolean add(@NonNull String endpoint, @NonNull Map<String, Object> payload, @Nullable Callback callback) {
            if (this.sent) {
                return false;
            }

            if (this.scheduledFuture != null && !this.scheduledFuture.cancel(false)) {
                requestExecutor.execute(this);
                return false;
            }

            if (this.batchContents.size() >= this.batch.count || this.batch.time == 0.0f) {
                return false;
            }

            if (callback != null) {
                this.callbacks.add(callback);
            }
            this.batchContents.add(payload);
            this.scheduledFuture = Request.requestExecutor.schedule(this, (long) (this.batch.time * 1000.0f), TimeUnit.MILLISECONDS);
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
        private static final Object mutex = new Object();
        private static BatchedParsnipRequest currentBatch;

        static BatchedParsnipRequest getCurrentBatch(@Nullable String hostname, @NonNull Session session) {
            synchronized (mutex) {
                if (currentBatch == null || currentBatch.sent) {
                    currentBatch = new BatchedParsnipRequest(hostname, session);
                }
                return currentBatch;
            }
        }

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
            synchronized (mutex) {
                // Add batch elements
                this.payload.put("events", this.batchContents);
                super.run();
            }
        }
    }

    private static class BatchedTrackEventRequest extends BatchedRequest {
        private static final Object mutex = new Object();
        private static BatchedTrackEventRequest currentBatch;

        static BatchedTrackEventRequest getCurrentBatch(@Nullable String hostname, @NonNull Session session) {
            synchronized (mutex) {
                if (currentBatch == null || currentBatch.sent) {
                    currentBatch = new BatchedTrackEventRequest(hostname, session);
                }
                return currentBatch;
            }
        }

        private BatchedTrackEventRequest(@Nullable String hostname, @NonNull Session session) {
            super(hostname, "/me/events", session, true);
        }

        @Override
        synchronized boolean add(@NonNull String endpoint, @NonNull Map<String, Object> payload, @Nullable Callback callback) {
            return super.add(endpoint, payload, callback);
        }

        @Override
        public void run() {
            synchronized (mutex) {
                // Add batch elements
                this.payload.put("batch", this.batchContents);
                super.run();
            }
        }
    }

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

    public static void submit(final @Nullable String hostname, final @NonNull String endpoint, final @NonNull Map<String, Object> payload, final @NonNull Session session, final @Nullable Callback callback) {
        BatchedRequest batch = null;

        // HEY! Future-Pat, this doesn't work if the app gets killed in the background and a notification_received metric goes out
        if ("parsnip.gocarrot.com".equals(hostname)) {
            batch = BatchedParsnipRequest.getCurrentBatch(hostname, session);
        } else if ("/me/events".equals(endpoint)) {
            batch = BatchedTrackEventRequest.getCurrentBatch(hostname, session);
        }

        if (batch != null) {
            if (!batch.add(endpoint, payload, callback)) {
                requestExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        submit(hostname, endpoint, payload, session, callback);
                    }
                });
            }
        } else {
            requestExecutor.execute(new Request(hostname, endpoint, payload, session, callback, true));
        }
    }

    /////

    public Request(@Nullable String hostname, @NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session, @Nullable Callback callback, boolean addStandardAttributes) {
        if (!endpoint.startsWith("/")) {
            throw new IllegalArgumentException("Parameter 'endpoint' must start with '/' or things will break, and you will lose an hour of your life debugging.");
        }

        this.hostname = hostname;
        this.endpoint = endpoint;
        this.payload = new HashMap<>(payload);
        this.session = session;
        this.requestId = UUID.randomUUID().toString().replace("-", "");
        this.callback = callback;
        this.sent = false;

        if (addStandardAttributes) {
            if (session.userId() != null) {
                this.payload.put("api_key", session.userId());
            }

            this.payload.putAll(Request.configurationPayload);
        }

        // Defaults
        boolean blackhole = false;
        RetryConfiguration retry = new RetryConfiguration();
        BatchConfiguration batch = new BatchConfiguration();

        // Configure if possible
        try {
            if (Request.remoteConfiguration != null) {
                Object objHost = Request.remoteConfiguration.endpointConfigurations.containsKey(hostname) ? Request.remoteConfiguration.endpointConfigurations.get(hostname) : null;
                @SuppressWarnings("unchecked")
                Map<String, Object> host = (objHost != null && objHost instanceof Map) ? (Map<String, Object>) objHost : null;
                if (host != null && host.containsKey(endpoint) && host.get(endpoint) instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> endpointConfig = (Map<String, Object>) host.get(endpoint);

                    blackhole = endpointConfig.containsKey("blackhole") ? (boolean) endpointConfig.get("blackhole") : blackhole;

                    // Retry configuration
                    if (endpointConfig.containsKey("retry") && endpointConfig.get("retry") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> retryConfig = (Map<String, Object>) endpointConfig.get("retry");

                        try {
                            retry.jitter = retryConfig.containsKey("jitter") ? (retryConfig.get("jitter") instanceof Number ? ((Number) retryConfig.get("jitter")).floatValue()
                                                                                                                            : Float.parseFloat(retryConfig.get("jitter").toString()))
                                                                             : retry.jitter;
                        } catch (Exception ignored) {
                        }

                        if (retryConfig.containsKey("times") && retryConfig.get("times") instanceof List) {
                            List timesList = (List) retryConfig.get("times");
                            float[] timesArray = new float[timesList.size()];
                            int i = 0;
                            for (Object o : timesList) {
                                try {
                                    timesArray[i] = o instanceof Number ? ((Number) o).floatValue()
                                                                        : Float.parseFloat(o.toString());
                                } catch (Exception ignored) {
                                    timesArray[i] = 10.0f;
                                }
                                i++;
                            }
                            retry.times = timesArray;
                        }
                    }

                    // Batch configuration
                    if (endpointConfig.containsKey("batch") && endpointConfig.get("batch") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> batchConfig = (Map<String, Object>) endpointConfig.get("batch");

                        try {
                            batch.count = batchConfig.containsKey("count") ? (batchConfig.get("count") instanceof Number ? ((Number) batchConfig.get("count")).longValue()
                                                                                                                         : Long.parseLong(batchConfig.get("count").toString()))
                                                                           : batch.count;
                        } catch (Exception ignored) {
                        }

                        try {
                            batch.time = batchConfig.containsKey("time") ? (batchConfig.get("time") instanceof Number ? ((Number) batchConfig.get("time")).floatValue()
                                                                                                                      : Float.parseFloat(batchConfig.get("time").toString()))
                                                                         : batch.time;
                        } catch (Exception ignored) {
                        }

                        if (batchConfig.containsKey("lww")) {
                            boolean lww = false;
                            try {
                                lww = batchConfig.containsKey("lww") ? (batchConfig.get("lww") instanceof Boolean ? (Boolean) batchConfig.get("lww")
                                                                                                                  : Boolean.parseBoolean(batchConfig.get("lww").toString()))
                                                                     : lww;
                            } catch (Exception ignored) {
                            }
                            if (lww) {
                                batch.count = Long.MAX_VALUE;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }

        // Assign to the finals
        this.blackhole = blackhole;
        this.retry = retry;
        this.batch = batch;
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
        this.sent = true;

        if (this.blackhole) return;

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
                    h.put("payload", new JSONObject(response.body).toMap());
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
        if (responseCode >= 500 && this.retry.retryIndex < this.retry.times.length) {
            // Retry with delay + jitter
            float jitter = (new Random().nextFloat() * 2.0f - 1.0f) * this.retry.jitter;
            float delay = this.retry.times[this.retry.retryIndex] + jitter;
            if (delay < 0.0f) delay = 0.0f;

            this.retry.retryIndex++;

            Request.requestExecutor.schedule(this, (long) (delay * 1000.0f), TimeUnit.MILLISECONDS);
        } else if (this.callback != null) {
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
