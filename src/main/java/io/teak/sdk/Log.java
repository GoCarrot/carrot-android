package io.teak.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.core.ThreadFactory;
import io.teak.sdk.json.JSONObject;
import io.teak.sdk.raven.Raven;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private final Map<String, Object> commonPayload = new HashMap<>();

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

    public void exception(@NonNull Throwable t, boolean reportToRaven) {
        exception(t, null, reportToRaven);
    }

    public void exception(@NonNull Throwable t) {
        exception(t, null, true);
    }

    public void exception(@NonNull Throwable t, @Nullable Map<String, Object> extras) {
        exception(t, extras, true);
    }

    public void exception(@NonNull Throwable t, @Nullable Map<String, Object> extras, boolean reportToRaven) {
        // Send to Raven
        if (reportToRaven && Teak.Instance != null && Teak.Instance.sdkRaven != null) {
            Teak.Instance.sdkRaven.reportException(t, extras);
        }

        this.log(Level.Error, "exception", Raven.throwableToMap(t));
    }
    // endregion

    // region State
    // Always available, can't change
    private final String androidLogTag;
    private final int jsonIndentation;
    public final String runId;
    private final AtomicLong eventCounter;

    private boolean logLocally;
    private boolean logRemotely;
    private boolean sendToRapidIngestion;

    private Teak.LogListener logListener;
    // endregion

    private final ExecutorService remoteLogQueue = Executors.newSingleThreadExecutor(ThreadFactory.autonamed());

    public Log(String androidLogTag, int jsonIndentation) {
        this.androidLogTag = androidLogTag;
        this.jsonIndentation = jsonIndentation;
        this.runId = UUID.randomUUID().toString().replace("-", "");
        this.commonPayload.put("run_id", this.runId);
        this.eventCounter = new AtomicLong(0);

        TeakConfiguration.addEventListener(new TeakConfiguration.EventListener() {
            @Override
            public void onConfigurationReady(@NonNull TeakConfiguration configuration) {
                synchronized (commonPayload) {
                    // Add sdk version to common payload, and log init message
                    commonPayload.put("sdk_version", Teak.Version);
                    log(Level.Info, "sdk_init", null);

                    // Log full device configuration, then add common payload after
                    log(Level.Info, "configuration.device", configuration.deviceConfiguration.toMap());
                    commonPayload.put("device_id", configuration.deviceConfiguration.deviceId);

                    // Log full app configuration, then add common payload after
                    log(Level.Info, "configuration.app", configuration.appConfiguration.toMap());
                    commonPayload.put("bundle_id", configuration.appConfiguration.bundleId);
                    commonPayload.put("app_id", configuration.appConfiguration.appId);
                    commonPayload.put("client_app_version", configuration.appConfiguration.appVersion);
                    commonPayload.put("client_app_version_name", configuration.appConfiguration.appVersionName);

                    // Log data collection configuration
                    log(Level.Info, "configuration.data_collection", configuration.dataCollectionConfiguration.toMap());

                    synchronized (queuedLogEvents) {
                        for (LogEvent event : queuedLogEvents) {
                            logEvent(event);
                        }
                        processedQueuedLogEvents = true;
                    }
                }
            }
        });
    }

    public void useRapidIngestionEndpoint(boolean useRapidIngestionEndpoint) {
        this.sendToRapidIngestion = useRapidIngestionEndpoint;
    }

    public void setLoggingEnabled(boolean logLocally, boolean logRemotely) {
        this.logLocally = logLocally;
        this.logRemotely = logRemotely;
    }

    public void setLogListener(Teak.LogListener logListener) {
        this.logListener = logListener;
    }

    protected class LogEvent {
        final Level logLevel;
        final String eventType;
        final Map<String, Object> eventData;

        LogEvent(final @NonNull Level logLevel, final @NonNull String eventType, @Nullable Map<String, Object> eventData) {
            this.logLevel = logLevel;
            this.eventType = eventType;
            this.eventData = eventData;
        }
    }

    private boolean processedQueuedLogEvents = false;
    private final ArrayList<LogEvent> queuedLogEvents = new ArrayList<>();

    protected void log(final @NonNull Level logLevel, final @NonNull String eventType, @Nullable Map<String, Object> eventData) {
        LogEvent logEvent = new LogEvent(logLevel, eventType, eventData);
        synchronized (queuedLogEvents) {
            if (processedQueuedLogEvents) {
                this.logEvent(logEvent);
            } else {
                queuedLogEvents.add(logEvent);
            }
        }
    }

    private void logEvent(final @NonNull LogEvent logEvent) {
        // Payload including common payload
        final Map<String, Object> payload = new HashMap<>(this.commonPayload);

        payload.put("event_id", this.eventCounter.getAndAdd(1));
        payload.put("timestamp", new Date().getTime() / 1000); // Milliseconds -> Seconds
        payload.put("log_level", logEvent.logLevel.name);

        // Event-specific payload
        payload.put("event_type", logEvent.eventType);
        if (logEvent.eventData != null) {
            payload.put("event_data", logEvent.eventData);
        }

        if (this.logListener != null) {
            this.logListener.logEvent(logEvent.eventType, logEvent.logLevel.name, payload);
        }

        // Remote logging
        if (this.logRemotely) {
            this.remoteLogQueue.execute(new Runnable() {
                @Override
                public void run() {
                    HttpsURLConnection connection = null;
                    try {
                        URL endpoint = sendToRapidIngestion ? new URL("https://logs.gocarrot.com/dev.sdk.log." + logEvent.logLevel.name)
                                                            : new URL("https://logs.gocarrot.com/sdk.log." + logEvent.logLevel.name);
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
            });
        }

        // Log to Android log
        if (this.logLocally && android.util.Log.isLoggable(this.androidLogTag, logEvent.logLevel.androidLogPriority)) {
            String jsonStringForAndroidLog = "{}";
            try {
                if (this.jsonIndentation > 0) {
                    jsonStringForAndroidLog = new JSONObject(payload).toString(this.jsonIndentation);
                } else {
                    jsonStringForAndroidLog = new JSONObject(payload).toString();
                }
            } catch (Exception ignored) {
            }
            android.util.Log.println(logEvent.logLevel.androidLogPriority, this.androidLogTag, jsonStringForAndroidLog);
        }
    }
}
