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

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import io.teak.sdk.service.RavenService;

public class Raven implements Thread.UncaughtExceptionHandler {
    public static final String LOG_TAG = "Teak:Raven";

    public enum Level {
        FATAL("fatal"),
        ERROR("error"),
        WARNING("warning"),
        INFO("info"),
        DEBUG("debug");

        private final String value;

        Level(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }

    private final HashMap<String, Object> payloadTemplate = new HashMap<>();
    private final Context applicationContext;
    private final String appId;
    private Thread.UncaughtExceptionHandler previousUncaughtExceptionHandler;

    private static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

    static {
        timestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public Raven(@NonNull Context context, @NonNull String appId, @NonNull AppConfiguration appConfiguration, @NonNull DeviceConfiguration deviceConfiguration) {
        this.applicationContext = context;
        this.appId = appId;

        // Fill in as much of the payload template as we can
        payloadTemplate.put("logger", "teak");
        payloadTemplate.put("platform", "java");
        payloadTemplate.put("release", Teak.SDKVersion);
        payloadTemplate.put("server_name", appConfiguration.bundleId);

        HashMap<String, Object> sdkAttribute = new HashMap<>();
        sdkAttribute.put("name", "teak");
        sdkAttribute.put("version", RavenService.TEAK_SENTRY_VERSION);
        payloadTemplate.put("sdk", sdkAttribute);

        HashMap<String, Object> deviceAttribute = new HashMap<>();
        deviceAttribute.put("name", deviceConfiguration.deviceFallback);
        deviceAttribute.put("version", Build.VERSION.SDK_INT);
        deviceAttribute.put("build", Build.VERSION.RELEASE);
        payloadTemplate.put("device", deviceAttribute);

        HashMap<String, Object> user = new HashMap<>();
        user.put("device_id", deviceConfiguration.deviceId);
        payloadTemplate.put("user", user);

        HashMap<String, Object> tagsAttribute = new HashMap<>();
        tagsAttribute.put("app_id", appConfiguration.appId);
        tagsAttribute.put("app_version", appConfiguration.appVersion);
        payloadTemplate.put("tags", tagsAttribute);
    }

    public void setAsUncaughtExceptionHandler() {
        if (Thread.getDefaultUncaughtExceptionHandler() instanceof Raven) {
            Raven raven = (Raven) Thread.getDefaultUncaughtExceptionHandler();
            raven.unsetAsUncaughtExceptionHandler();
        }
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public void unsetAsUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousUncaughtExceptionHandler);
        previousUncaughtExceptionHandler = null;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        reportException(ex);
    }

    public void setDsn(@NonNull String dsn) {
        Intent intent = new Intent(RavenService.SET_DSN_INTENT_ACTION, null, applicationContext, RavenService.class);
        intent.putExtra("appId", appId);
        intent.putExtra("dsn", dsn);
        applicationContext.startService(intent);
    }

    public void reportException(Throwable t) {
        if (t == null) {
            return;
        }

        HashMap<String, Object> additions = new HashMap<>();
        ArrayList<Object> exceptions = new ArrayList<>();
        HashMap<String, Object> exception = new HashMap<>();

        exception.put("type", t.getClass().getSimpleName());
        exception.put("value", t.getMessage());
        exception.put("module", t.getClass().getPackage().getName());

        HashMap<String, Object> stacktrace = new HashMap<>();
        ArrayList<Object> stackFrames = new ArrayList<>();

        StackTraceElement[] steArray = t.getStackTrace();
        for (int i = steArray.length - 1; i >= 0; i--) {
            StackTraceElement ste = steArray[i];
            HashMap<String, Object> frame = new HashMap<>();

            frame.put("filename", ste.getFileName());

            String method = ste.getMethodName();
            if (method.length() != 0) {
                frame.put("function", method);
            }

            int lineno = ste.getLineNumber();
            if (!ste.isNativeMethod() && lineno >= 0) {
                frame.put("lineno", lineno);
            }

            String module = ste.getClassName();
            frame.put("module", module);

            boolean in_app = true;
            if (module.startsWith("android.") || module.startsWith("java.") || module.startsWith("dalvik.") || module.startsWith("com.android.")) {
                in_app = false;
            }

            frame.put("in_app", in_app);

            stackFrames.add(frame);
        }
        stacktrace.put("frames", stackFrames);

        exception.put("stacktrace", stacktrace);

        exceptions.add(exception);
        additions.put("exception", exceptions);

        try {
            Report report = new Report(t.getMessage(), Level.ERROR, additions);
            report.sendToService();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to report Teak SDK exception. " + Log.getStackTraceString(t) + "\n" + Log.getStackTraceString(e));
        }
    }

    public synchronized void addUserData(@NonNull String key, Object value) {
        @SuppressWarnings("unchecked") HashMap<String, Object> user = (HashMap<String, Object>) payloadTemplate.get("user");
        if (user == null) {
            user = new HashMap<>();
            payloadTemplate.put("user", user);
        }

        if (value != null) {
            user.put(key, value);
        } else {
            user.remove(key);
        }
    }

    public Map<String, Object> to_h() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("appId", this.appId);
        ret.put("applicationContext", this.applicationContext);
        ret.put("payloadTemplate", this.payloadTemplate);
        return ret;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), new JSONObject(this.to_h()).toString(2));
        } catch (Exception ignored) {
            return super.toString();
        }
    }

    class Report {
        HashMap<String, Object> payload = new HashMap<>();
        Date timestamp = new Date();

        public Report(String message, @NonNull Level level, HashMap<String, Object> additions) {
            if (message == null || message.length() < 1) {
                message = "undefined";
            }

            payload.put("event_id", UUID.randomUUID().toString().replace("-", ""));
            payload.put("message", message.substring(0, Math.min(message.length(), 1000)));

            payload.put("timestamp", timestampFormatter.format(timestamp));

            payload.put("level", level.toString());

            try {
                // 0 dalvik.system.VMStack.getThreadStackTrace
                // 1 java.lang.Thread.getStackTrace
                // 2 io.teak.sdk.Raven$Request.<init>
                // 3 Raven.report*
                // 4 culprit method
                final int depth = 4;
                final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
                payload.put("culprit", ste[depth].toString());
                //for (StackTraceElement elem : ste) {
                //    Log.d(LOG_TAG, elem.toString());
                //}
            } catch (Exception e) {
                payload.put("culprit", "unknown");
            }

            if (additions != null) {
                payload.putAll(additions);
            }
        }

        public void sendToService() {
            payload.putAll(payloadTemplate);
            try {
                Intent intent = new Intent(RavenService.REPORT_EXCEPTION_INTENT_ACTION, null, applicationContext, RavenService.class);
                intent.putExtra("appId", appId);
                intent.putExtra("timestamp", this.timestamp.getTime() / 1000L);
                intent.putExtra("payload", new JSONObject(payload).toString());
                applicationContext.startService(intent);
            } catch (Exception e) {
                Log.e(LOG_TAG, Log.getStackTraceString(e));
            }
        }

        public Map<String, Object> to_h() {
            HashMap<String, Object> ret = new HashMap<>();
            ret.put("payload", this.payload);
            ret.put("timestamp", this.timestamp);
            return ret;
        }

        @Override
        public String toString() {
            try {
                return String.format(Locale.US, "%s: %s", super.toString(), new JSONObject(this.to_h()).toString(2));
            } catch (Exception ignored) {
                return super.toString();
            }
        }
    }
}
