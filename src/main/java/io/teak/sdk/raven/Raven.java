package io.teak.sdk.raven;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;

import io.teak.sdk.IObjectFactory;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import io.teak.sdk.event.UserIdEvent;
import io.teak.sdk.service.JobService;

public class Raven implements Thread.UncaughtExceptionHandler {
    public static final String JOB_TYPE = "Teak.Raven";

    private static final String TEAK_SENTRY_PROGUARD_UUID = "io_teak_sentry_proguard_uuid";

    public static class ReportTestException extends Exception {
        public ReportTestException(@NonNull String version) {
            super("Version: " + version);
        }
    }

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

    private final List<Report> queuedReports = new ArrayList<>();
    private final HashMap<String, Object> payloadTemplate = new HashMap<>();
    private final Context applicationContext;
    private final String appId;
    private Thread.UncaughtExceptionHandler previousUncaughtExceptionHandler;

    private String SENTRY_KEY;
    private String SENTRY_SECRET;
    private URL endpoint;

    private static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

    static {
        Raven.timestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public Raven(@NonNull Context context, @NonNull String appId, @NonNull TeakConfiguration configuration, @NonNull IObjectFactory objectFactory) {
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

        final HashMap<String, Object> sdkAttribute = new HashMap<>();
        sdkAttribute.put("name", "teak");
        sdkAttribute.put("version", Sender.TEAK_SENTRY_VERSION);
        this.payloadTemplate.put("sdk", sdkAttribute);

        String deviceFamily = null;
        try {
            deviceFamily = Build.MODEL.split(" ")[0];
        } catch (Exception ignored) {
        }

        class Deprecated {
            @SuppressWarnings("deprecation")
            private String getCPU_ABI() {
                return Build.CPU_ABI;
            }
        }

        final HashMap<String, Object> device = new HashMap<>();
        device.put("manufacturer", Build.MANUFACTURER);
        device.put("brand", Build.BRAND);
        device.put("model", Build.MODEL);
        device.put("family", deviceFamily);
        device.put("model_id", Build.ID);
        device.put("arch", new Deprecated().getCPU_ABI());

        final HashMap<String, Object> os = new HashMap<>();
        os.put("name", "Android");
        os.put("version", Build.VERSION.RELEASE);
        os.put("build", Build.DISPLAY);

        final HashMap<String, Object> app = new HashMap<>();
        app.put("app_identifier", configuration.appConfiguration.bundleId);
        app.put("teak_app_identifier", configuration.appConfiguration.appId);
        app.put("app_version", configuration.appConfiguration.appVersion);
        app.put("app_version_name", configuration.appConfiguration.appVersionName);
        app.put("build_type", configuration.debugConfiguration.isDebug() ? "debug" : "production");
        app.put("target_sdk_version", configuration.appConfiguration.targetSdkVersion);

        final HashMap<String, Object> contexts = new HashMap<>();
        contexts.put("device", device);
        contexts.put("os", os);
        contexts.put("app", app);
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
        tagsAttribute.put("app_version_name", configuration.appConfiguration.appVersionName);
        this.payloadTemplate.put("tags", tagsAttribute);
    }

    public void setAsUncaughtExceptionHandler() {
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
        if (!(ex instanceof OutOfMemoryError)) {
            reportException(ex, null);
        }
    }

    public void setDsn(@NonNull String dsn) {
        if (dsn.isEmpty()) {
            Log.e(JOB_TYPE, "DSN empty for app: " + this.appId);
            return;
        }

        final Uri uri = Uri.parse(dsn);

        String port = "";
        if (uri.getPort() >= 0) {
            port = ":" + uri.getPort();
        }

        try {
            final String project = uri.getPath().substring(uri.getPath().lastIndexOf("/"));
            final String[] userInfo = uri.getUserInfo().split(":");

            this.SENTRY_KEY = userInfo[0];
            this.SENTRY_SECRET = userInfo[1];

            this.endpoint = new URL(String.format("%s://%s%s/api%s/store/",
                uri.getScheme(), uri.getHost(), port, project));

            synchronized (this.queuedReports) {
                for (Report report : this.queuedReports) {
                    report.submitJob();
                }
                this.queuedReports.clear();
            }
        } catch (Exception e) {
            Log.e(JOB_TYPE, "Error parsing DSN: '" + uri.toString() + "'" + Log.getStackTraceString(e));
        }
    }

    public static Map<String, Object> throwableToMap(Throwable t) {
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

    public void reportException(Throwable t, Map<String, Object> extras) {
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
        additions.put("extra", extras);

        try {
            Report report = new Report(t.getMessage(), Level.ERROR, additions);
            synchronized (this.queuedReports) {
                if (this.endpoint == null) {
                    this.queuedReports.add(report);
                } else {
                    report.submitJob();
                }
            }
        } catch (Exception e) {
            Log.e(JOB_TYPE, "Unable to report Teak SDK exception. " + Log.getStackTraceString(t) + "\n" + Log.getStackTraceString(e));
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

            payload.put("timestamp", Raven.timestampFormatter.format(timestamp));

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
                //    Log.d(JOB_TYPE, elem.toString());
                //}
            } catch (Exception e) {
                payload.put("culprit", "unknown");
            }

            if (additions != null) {
                payload.putAll(additions);
            }
        }

        void submitJob() {
            try {
                final Job job = Teak.Instance.jobBuilder((String) this.payload.get("event_id"), this.toBundle())
                                    .build();
                Teak.Instance.dispatcher.mustSchedule(job);
            } catch (Exception e) {
                Teak.log.exception(e, false);
            }
        }

        Bundle toBundle() {
            this.payload.putAll(Raven.this.payloadTemplate);
            try {
                Bundle bundle = new Bundle();
                bundle.putString(JobService.JOB_TYPE_KEY, Raven.JOB_TYPE);
                bundle.putLong(Sender.TIMESTAMP_KEY, this.timestamp.getTime() / 1000L);
                bundle.putString(Sender.PAYLOAD_KEY, new JSONObject(this.payload).toString());
                bundle.putString(Sender.ENDPOINT_KEY, Raven.this.endpoint.toString());
                bundle.putString(Sender.SENTRY_KEY_KEY, Raven.this.SENTRY_KEY);
                bundle.putString(Sender.SENTRY_SECRET_KEY, Raven.this.SENTRY_SECRET);

                return bundle;
            } catch (Exception e) {
                Log.e(JOB_TYPE, Log.getStackTraceString(e));
            }

            return null;
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
