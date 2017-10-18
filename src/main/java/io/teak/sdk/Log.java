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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HttpsURLConnection;

// Things I assume
// - Remote log space is not an issue
// - Customers don't look at logs when integrating

// Goals of logging
// - Logging should help me develop and debug the SDK
// - Logging should help me to do automated testing
// - Logging should help me understand how many times known error cases happen in the wild
// - Logging should help me track down errors I haven't yet found during development and in the wild

// To help me develop and debug the SDK
// - Log something locally
// - Log something locally as a warning so it shows up in yellow
// - Log something locally as an error so it shows up in red

// To help me do automated testing
// - Logs are in a defined, easily-parsable format
// -

// To help understand how our SDK behaves in the wild
// - Log something remotely
// - Log an exception locally as an error so it shows up in red, send exception to Sentry
// - Ask that future logs send more information for a specific log event or exception

public class Log {
    // region Log Level enum
    private enum Level {
        Verbose("VERBOSE", android.util.Log.VERBOSE),
        Info("INFO", android.util.Log.INFO),
        Warn("WARN", android.util.Log.WARN),
        Error("ERROR", android.util.Log.ERROR);

        public final String name;
        public final int androidLogPriority;

        Level(String name, int androidLogPriority) {
            this.name = name;
            this.androidLogPriority = androidLogPriority;
        }
    }
    // endregion

    private Map<String, Object> commonPayload = new HashMap<>();

    // region Public API
    public void e(@NonNull String eventType, @NonNull String message) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message", message);
        this.log(Level.Error, eventType, eventData);
    }

    public void e(@NonNull String eventType, @NonNull Map<String, Object> eventData) {
        this.log(Level.Error, eventType, eventData);
    }

    public void e(@NonNull String eventType, @NonNull String message, @NonNull Map<String, Object> eventData) {
        eventData.put("message", message);
        this.log(Level.Error, eventType, eventData);
    }

    public void i(@NonNull String eventType, @NonNull String message) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message", message);
        this.log(Level.Info, eventType, eventData);
    }

    public void i(@NonNull String eventType, @NonNull String message, @NonNull Map<String, Object> eventData) {
        eventData.put("message", message);
        this.log(Level.Info, eventType, eventData);
    }

    public void i(@NonNull String eventType, @NonNull Map<String, Object> eventData) {
        this.log(Level.Info, eventType, eventData);
    }

    public void w(@NonNull String eventType, @NonNull String message) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message", message);
        this.log(Level.Warn, eventType, eventData);
    }

    public void w(@NonNull String eventType, @NonNull String message, @NonNull Map<String, Object> eventData) {
        eventData.put("message", message);
        this.log(Level.Warn, eventType, eventData);
    }

    public void exception(@NonNull Throwable t) {
        exception(t, null);
    }

    public void exception(@NonNull Throwable t, @Nullable Map<String, Object> extras) {
        // Send to Raven
        if (Teak.Instance != null && Teak.Instance.sdkRaven != null) {
            Teak.Instance.sdkRaven.reportException(t, extras);
        }

        this.log(Level.Error, "exception", Raven.throwableToMap(t));
    }
    // endregion

    // region State
    // Always available, can't change
    private final String androidLogTag;
    private final int jsonIndentation;
    final String runId;
    private final AtomicLong eventCounter;

    private boolean logLocally;
    private boolean logRemotely;
    private boolean sendToRapidIngestion;
    // endregion

    public Log(String androidLogTag, int jsonIndentation) {
        this.androidLogTag = androidLogTag;
        this.jsonIndentation = jsonIndentation;
        this.runId = UUID.randomUUID().toString().replace("-", "");
        commonPayload.put("run_id", this.runId);
        this.eventCounter = new AtomicLong(0);

        TeakConfiguration.addEventListener(new TeakConfiguration.EventListener() {
            @Override
            public void onConfigurationReady(@NonNull TeakConfiguration configuration) {
                // Add sdk version to common payload, and log init message
                commonPayload.put("sdk_version", Teak.Version);
                log(Level.Info, "sdk_init", null);

                // Log full device configuration, then add common payload after
                log(Level.Info, "configuration.device", configuration.deviceConfiguration.to_h());
                commonPayload.put("device_id", configuration.deviceConfiguration.deviceId);

                // Log full app configuration, then add common payload after
                log(Level.Info, "configuration.app", configuration.appConfiguration.to_h());
                commonPayload.put("bundle_id", configuration.appConfiguration.bundleId);
                commonPayload.put("app_id", configuration.appConfiguration.appId);
                commonPayload.put("client_app_version", configuration.appConfiguration.appVersion);
            }
        });
    }

    public void useRapidIngestionEndpoint(boolean useRapidIngestionEndpoint) {
        this.sendToRapidIngestion = useRapidIngestionEndpoint;
    }

    public void setLoggingEnabled(boolean enableLogs) {
        this.logLocally = this.logRemotely = enableLogs;
    }

    protected void log(final @NonNull Level logLevel, final @NonNull String eventType, @Nullable Map<String, Object> eventData) {

        // Payload including common payloar
        final Map<String, Object> payload = new HashMap<>(this.commonPayload);

        payload.put("event_id", this.eventCounter.getAndAdd(1));
        payload.put("timestamp", new Date().getTime() / 1000); // Milliseconds -> Seconds
        payload.put("log_level", logLevel.name);

        // Event-specific payload
        payload.put("event_type", eventType);
        if (eventData == null) {
            eventData = new HashMap<>();
        }
        payload.put("event_data", eventData);

        // Remote logging
        if (this.logRemotely) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpsURLConnection connection = null;
                    try {
                        URL endpoint = sendToRapidIngestion ?
                                new URL("https://logs.gocarrot.com/dev.sdk.log." + logLevel.name)
                                : new URL("https://logs.gocarrot.com/sdk.log." + logLevel.name);
                        connection = (HttpsURLConnection) endpoint.openConnection();
                        connection.setRequestProperty("Accept-Charset", "UTF-8");
                        connection.setUseCaches(false);
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/json");
                        //connection.setRequestProperty("Content-Encoding", "gzip");

                        //GZIPOutputStream wr = new GZIPOutputStream(connection.getOutputStream());
                        OutputStream wr = connection.getOutputStream();
                        wr.write(new JSONObject(payload).toString().getBytes());
                        wr.flush();
                        wr.close();

                        InputStream is;
                        if (connection.getResponseCode() < 400) {
                            is = connection.getInputStream();
                        } else {
                            is = connection.getErrorStream();
                        }
                        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                        String line;
                        //noinspection MismatchedQueryAndUpdateOfStringBuilder
                        StringBuilder response = new StringBuilder();
                        while ((line = rd.readLine()) != null) {
                            response.append(line);
                            response.append('\r');
                        }
                        rd.close();
                    } catch (Exception ignored) {
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            }).start();
        }

        // Log to Android log
        if (this.logLocally && android.util.Log.isLoggable(this.androidLogTag, logLevel.androidLogPriority)) {
            String jsonStringForAndroidLog = "{}";
            try {
                if (this.jsonIndentation > 0) {
                    jsonStringForAndroidLog = new JSONObject(payload).toString(this.jsonIndentation);
                } else {
                    jsonStringForAndroidLog = new JSONObject(payload).toString();
                }
            } catch (Exception ignored) {
            }
            android.util.Log.println(logLevel.androidLogPriority, this.androidLogTag, jsonStringForAndroidLog);
        }
    }
}
