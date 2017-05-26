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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
        Debug("DEBUG", android.util.Log.DEBUG),
        Local("INFO", android.util.Log.INFO),
        Info("INFO", android.util.Log.INFO),
        Warn("WARN", android.util.Log.WARN),
        Error("ERROR", android.util.Log.ERROR),
        LocalError("ERROR", android.util.Log.ERROR);

        public final String name;
        public final int androidLogPriority;

        Level(String name, int androidLogPriority) {
            this.name = name;
            this.androidLogPriority = androidLogPriority;
        }
    }
    // endregion

    // region Public API
    public void useSdk(@NonNull Map<String, Object> sdkVersion) {
        //this.sdkVersion = sdkVersion;
        this.local("sdk_init");
    }

    public void local(@NonNull String eventType) {
        this.log(Level.Local, eventType, null);
    }

    public void local(@NonNull String eventType, @NonNull String message) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message", message);
        this.log(Level.Local, eventType, eventData);
    }

    public void e(@NonNull String eventType, @NonNull String message) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("message", message);
        this.log(Level.Error, eventType, eventData);
    }

    public void exception(@NonNull Throwable t) {
        // Send to Raven
        if (Teak.sdkRaven != null) {
            Teak.sdkRaven.reportException(t);
        }

        // Log locally
        this.log(Level.LocalError, "exception", Raven.throwableToMap(t));
    }
    // endregion

    // region State
    // Always available, can't change
    public final String androidLogTag;
    private final int jsonIndentation;
    public final String runId;
    private final AtomicInteger eventCounter;

    // Always available, may change (in case of Air or Unity SDK nonsense)
    //private Map<String, Object> sdkVersion = Teak.to_h();
    // endregion

    public Log(String androidLogTag, int jsonIndentation) {
        this.androidLogTag = androidLogTag;
        this.jsonIndentation = jsonIndentation;
        this.runId = UUID.randomUUID().toString().replace("-", "");
        this.eventCounter = new AtomicInteger(0);
    }

    protected void log(@NonNull Level logLevel, @NonNull String eventType, @Nullable Map<String, Object> eventData) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("run_id", this.runId);
        payload.put("event_id", this.eventCounter.getAndAdd(1));
        payload.put("timestamp", new Date().getTime() / 1000); // Milliseconds -> Seconds
        payload.put("log_level", logLevel.name);
        //payload.put("sdk_version", this.sdkVersion);

        // Event-specific payload
        payload.put("event_type", eventType);
        if (eventData == null) {
            eventData = new HashMap<>();
        }
        payload.put("event_data", eventData);

        // Log to Android log
        if (logLevel.logLocally && android.util.Log.isLoggable(this.androidLogTag, logLevel.androidLogPriority)) {
            String jsonStringForAndroidLog = "{}";
            try {
                if (this.jsonIndentation > 0) {
                    jsonStringForAndroidLog = new JSONObject(payload).toString(this.jsonIndentation);
                } else {
                    jsonStringForAndroidLog = new JSONObject(payload).toString();
                }
            } catch (Exception ignored){
            }
            //android.util.Log.println(logLevel.priority, this.androidLogTag, jsonStringForAndroidLog);
        }
    }
}
