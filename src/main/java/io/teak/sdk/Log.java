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
import java.util.zip.GZIPOutputStream;

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
    public enum Level {
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

    Map<String, Object> sdkVersion;
    DeviceConfiguration deviceConfiguration;
    AppConfiguration appConfiguration;
    RemoteConfiguration remoteConfiguration;

    // region Public API
    public void useSdk(@NonNull Map<String, Object> sdkVersion) {
        this.sdkVersion = sdkVersion;
        this.log(Level.Info, "sdk_init", null);
    }

    public void useDeviceConfiguration(@NonNull DeviceConfiguration deviceConfiguration) {
        this.log(Level.Info, "device_configuration", deviceConfiguration.to_h());
        this.deviceConfiguration = deviceConfiguration;
    }

    public void useAppConfiguration(@NonNull AppConfiguration appConfiguration) {
        this.log(Level.Info, "app_configuration", appConfiguration.to_h());
        this.appConfiguration = appConfiguration;
    }

    public void useRemoteConfiguration(@NonNull RemoteConfiguration remoteConfiguration) {
        this.log(Level.Info, "remote_configuration", remoteConfiguration.to_h());
        this.remoteConfiguration = remoteConfiguration;
    }

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
        // Send to Raven
        if (Teak.sdkRaven != null) {
            Teak.sdkRaven.reportException(t);
        }

        this.log(Level.Error, "exception", Raven.throwableToMap(t));
    }
    // endregion

    // region State
    // Always available, can't change
    public final String androidLogTag;
    private final int jsonIndentation;
    public final String runId;
    private final AtomicLong eventCounter;
    // endregion

    public Log(String androidLogTag, int jsonIndentation) {
        this.androidLogTag = androidLogTag;
        this.jsonIndentation = jsonIndentation;
        this.runId = UUID.randomUUID().toString().replace("-", "");
        this.eventCounter = new AtomicLong(0);
    }

    protected void log(final @NonNull Level logLevel, final @NonNull String eventType, @Nullable Map<String, Object> eventData) {
        final Map<String, Object> payload = new HashMap<>();
        payload.put("run_id", this.runId);
        payload.put("event_id", this.eventCounter.getAndAdd(1));
        payload.put("timestamp", new Date().getTime() / 1000); // Milliseconds -> Seconds
        payload.put("log_level", logLevel.name);
        payload.put("sdk_version", this.sdkVersion);

        if (this.deviceConfiguration != null) {
            payload.put("device_id", this.deviceConfiguration.deviceId);
        }

        if (this.appConfiguration != null) {
            payload.put("bundle_id", this.appConfiguration.bundleId);
            payload.put("app_id", this.appConfiguration.appId);
            payload.put("client_app_version", this.appConfiguration.appVersion);
        }

        // Event-specific payload
        payload.put("event_type", eventType);
        if (eventData == null) {
            eventData = new HashMap<>();
        }
        payload.put("event_data", eventData);

        // Remote logging
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpsURLConnection connection = null;
                try {
                    URL endpoint = Teak.isDebug ?
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

        // Log to Android log
        if (Teak.isDebug && android.util.Log.isLoggable(this.androidLogTag, logLevel.androidLogPriority)) {
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
