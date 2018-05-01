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

import io.teak.sdk.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import io.teak.sdk.event.UserIdEvent;
import io.teak.sdk.service.RavenService;

class Raven implements Thread.UncaughtExceptionHandler {
    private static final String LOG_TAG = "Teak.Raven";
    private static final String TEAK_SENTRY_PROGUARD_UUID = "io_teak_sentry_proguard_uuid";

    private enum Level {
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

    Raven(@NonNull Context context, @NonNull String appId, @NonNull TeakConfiguration configuration, @NonNull IObjectFactory objectFactory) {
        //noinspection deprecation - This must be a string as per Sentry API
        @SuppressWarnings("deprecation")
        final String teakSdkVersion = Teak.SDKVersion;

        this.applicationContext = context;
        this.appId = appId;

        final String proguardUuid = objectFactory.getAndroidResources().getStringResource(Raven.TEAK_SENTRY_PROGUARD_UUID);
        if (proguardUuid != null && proguardUuid.length() > 0) {
            HashMap<String, Object> debug_meta = new HashMap<>();
            ArrayList<Object> debugImages = new ArrayList<>();
            HashMap<String, Object> proguard = new HashMap<>();
            proguard.put("type", "proguard");
            proguard.put("uuid", proguardUuid);
            debugImages.add(proguard);
            debug_meta.put("images", debugImages);
            payloadTemplate.put("debug_meta", debug_meta);
        }

        // Fill in as much of the payload template as we can
        payloadTemplate.put("logger", "teak");
        payloadTemplate.put("platform", "java");
        payloadTemplate.put("release", teakSdkVersion);
        this.payloadTemplate.put("server_name", configuration.appConfiguration.bundleId);

        final HashMap<String, Object> sdkAttribute = new HashMap<>();
        sdkAttribute.put("name", "teak");
        sdkAttribute.put("version", RavenService.TEAK_SENTRY_VERSION);
        this.payloadTemplate.put("sdk", sdkAttribute);

        final HashMap<String, Object> device = new HashMap<>();
        device.put("name", configuration.deviceConfiguration.deviceFallback);
        device.put("family", configuration.deviceConfiguration.deviceManufacturer);
        device.put("model", configuration.deviceConfiguration.deviceModel);

        final HashMap<String, Object> os = new HashMap<>();
        os.put("version", Build.VERSION.SDK_INT);
        os.put("build", Build.VERSION.RELEASE);

        final HashMap<String, Object> contexts = new HashMap<>();
        contexts.put("device", device);
        contexts.put("os", os);
        this.payloadTemplate.put("contexts", contexts);

        final HashMap<String, Object> user = new HashMap<>();
        user.put("device_id", configuration.deviceConfiguration.deviceId);
        user.put("log_run_id", Teak.log.runId); // Run id is always available
        this.payloadTemplate.put("user", user);

        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event instanceof UserIdEvent) {
                    user.put("id", ((UserIdEvent) event).userId);
                }
            }
        });

        final HashMap<String, Object> tagsAttribute = new HashMap<>();
        tagsAttribute.put("app_id", configuration.appConfiguration.appId);
        tagsAttribute.put("app_version", configuration.appConfiguration.appVersion);
        this.payloadTemplate.put("tags", tagsAttribute);
    }

    void setAsUncaughtExceptionHandler() {
        if (Thread.getDefaultUncaughtExceptionHandler() instanceof Raven) {
            Raven raven = (Raven) Thread.getDefaultUncaughtExceptionHandler();
            raven.unsetAsUncaughtExceptionHandler();
        }
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    private void unsetAsUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousUncaughtExceptionHandler);
        previousUncaughtExceptionHandler = null;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        reportException(ex, null);
    }

    void setDsn(@NonNull String dsn) {
        Intent intent = new Intent(RavenService.SET_DSN_INTENT_ACTION, null, applicationContext, RavenService.class);
        intent.putExtra("appId", appId);
        intent.putExtra("dsn", dsn);
        try {
            applicationContext.startService(intent);
        } catch (Exception e) {
            Teak.log.exception(e, false);
        }
    }

    static Map<String, Object> throwableToMap(Throwable t) {
        Throwable throwable = t;
        if (throwable instanceof InvocationTargetException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }

        HashMap<String, Object> exception = new HashMap<>();

        exception.put("type", throwable.getClass().getSimpleName());
        exception.put("value", throwable.getMessage());
        exception.put("module", throwable.getClass().getPackage().getName());

        HashMap<String, Object> stacktrace = new HashMap<>();
        ArrayList<Object> stackFrames = new ArrayList<>();

        StackTraceElement[] steArray = throwable.getStackTrace();
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

        return exception;
    }

    void reportException(Throwable t, Map<String, Object> extras) {
        if (t == null) {
            return;
        }

        Throwable throwable = t;
        if (throwable instanceof InvocationTargetException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }

        HashMap<String, Object> additions = new HashMap<>();
        ArrayList<Object> exceptions = new ArrayList<>();
        Map<String, Object> exception = Raven.throwableToMap(throwable);

        exceptions.add(exception);
        additions.put("exception", exceptions);

        try {
            Report report = new Report(t.getMessage(), Level.ERROR, additions);
            report.sendToService(extras);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to report Teak SDK exception. " + Log.getStackTraceString(t) + "\n" + Log.getStackTraceString(e));
        }
    }

    private Map<String, Object> toMap() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("appId", this.appId);
        ret.put("applicationContext", this.applicationContext);
        ret.put("payloadTemplate", this.payloadTemplate);
        return ret;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.toMap())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }

    private class Report {
        HashMap<String, Object> payload = new HashMap<>();
        Date timestamp = new Date();

        Report(String m, @NonNull Level level, HashMap<String, Object> additions) {
            String message = m;
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

        void sendToService(Map<String, Object> extras) {
            payload.putAll(payloadTemplate);
            if (extras != null) payload.put("extra", extras);
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

        Map<String, Object> toMap() {
            HashMap<String, Object> ret = new HashMap<>();
            ret.put("payload", this.payload);
            ret.put("timestamp", this.timestamp);
            return ret;
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
}
