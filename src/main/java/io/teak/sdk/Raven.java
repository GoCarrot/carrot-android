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
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import io.teak.sdk.service.RavenService;

public class Raven {
    public static final int SENTRY_VERSION = 7;
    public static final String TEAK_SENTRY_VERSION = "1.0.0";
    public static final String SENTRY_CLIENT = "teak-android/" + TEAK_SENTRY_VERSION;
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

    String SENTRY_KEY;
    String SENTRY_SECRET;
    URL endpoint;
    HashMap<String, Object> payloadTemplate = new HashMap<>();
    boolean reportingEnabled = false;
    final Object monitor = new Object();
    Context applicationContext;
    String appId;

    static SimpleDateFormat timestampFormatter;

    static {
        // Timestamp formatting
        timestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        timestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    ThreadPoolExecutor reportThreadQueueExecutor = new ThreadPoolExecutor(1, 3, 2, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>()) {
        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            synchronized(monitor) {
                while (!reportingEnabled) {
                    try {
                        monitor.wait();
                    } catch (Exception ignored) {
                    }
                }
            }
            super.beforeExecute(t, r);
        }
    };

    public Raven(Context context, String appId) {
        this.applicationContext = context.getApplicationContext();
        this.appId = appId;

        // Fill in as much of the payload template as we can
        payloadTemplate.put("logger", "teak");
        payloadTemplate.put("platform", "java");
        payloadTemplate.put("release", Teak.SDKVersion);

        HashMap<String, Object> sdkAttribute = new HashMap<>();
        sdkAttribute.put("name", "teak");
        sdkAttribute.put("version", TEAK_SENTRY_VERSION);
        payloadTemplate.put("sdk", sdkAttribute);

        HashMap<String, Object> deviceAttribute = new HashMap<>();
        HashMap<String, Object> deviceInfo = new HashMap<>();
        Helpers.addDeviceNameToPayload(deviceInfo);
        deviceAttribute.put("name", deviceInfo.get("device_fallback"));
        deviceAttribute.put("version", Build.VERSION.SDK_INT);
        deviceAttribute.put("build", Build.VERSION.RELEASE);
        payloadTemplate.put("device", deviceAttribute);

        payloadTemplate.put("user", new HashMap<String, Object>());
    }

    public void setDsn(String dsn) {
        if (dsn.isEmpty()) {
            reportingEnabled = false;
        } else {
            Uri uri = Uri.parse(dsn);

            String port = "";
            if (uri.getPort() >= 0) {
                port = ":" + uri.getPort();
            }

            try {
                String project = uri.getPath().substring(uri.getPath().lastIndexOf("/"));

                String[] userInfo = uri.getUserInfo().split(":");
                SENTRY_KEY = userInfo[0];
                SENTRY_SECRET = userInfo[1];

                endpoint = new URL(String.format("%s://%s%s/api%s/store/",
                        uri.getScheme(), uri.getHost(), port, project));

                reportingEnabled = true;
            } catch (Exception e) {
                Log.e(LOG_TAG, Log.getStackTraceString(e));
            }
        }

        if (reportingEnabled) {
            synchronized (monitor) {
                monitor.notify();
            }
        }
    }

    public void reportException(Throwable t) {
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

        Report report = new Report(t.getMessage(), Level.ERROR, additions);
        reportThreadQueueExecutor.execute(report);
        report.sendToService();
    }

    public synchronized void addTeakPayload() {
        payloadTemplate.put("server_name", Teak.bundleId);

        HashMap<String, Object> tagsAttribute = new HashMap<>();
        tagsAttribute.put("app_id", Teak.appId);
        tagsAttribute.put("app_version", Teak.appVersion);
        payloadTemplate.put("tags", tagsAttribute);

        @SuppressWarnings("unchecked") HashMap<String, Object> user = (HashMap<String, Object>) payloadTemplate.get("user");
        user.put("device_id", Teak.deviceId);
    }

    public synchronized void addUserData(String key, Object value) {
        @SuppressWarnings("unchecked") HashMap<String, Object> user = (HashMap<String, Object>) payloadTemplate.get("user");
        if (value != null) {
            user.put(key, value);
        } else {
            user.remove(key);
        }
    }

    class Report implements Runnable {
        HashMap<String, Object> payload = new HashMap<>();
        Date timestamp = new Date();

        public Report(String message, Level level, HashMap<String, Object> additions) {
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
            try {
                Intent intent = new Intent(RavenService.REPORT_EXCEPTION_INTENT_ACTION, null, applicationContext, RavenService.class);
                intent.putExtra("appId", appId);
                intent.putExtra("timestamp", this.timestamp.getTime() / 1000L);
                intent.putExtra("payload", this.toString());
                applicationContext.startService(intent);
            } catch (Exception e) {
                Log.e(LOG_TAG, Log.getStackTraceString(e));
            }
        }

        @Override
        public String toString() {
            try {
                return new JSONObject(payload).toString(2);
            } catch (Exception ignored) {
                return payload.toString();
            }
        }

        @Override
        public void run() {
            HttpsURLConnection connection = null;

            try {
                payload.putAll(payloadTemplate);
                JSONObject requestBody = new JSONObject(payload);

                connection = (HttpsURLConnection) endpoint.openConnection();
                connection.setRequestProperty("Accept-Charset", "UTF-8");
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Content-Encoding", "gzip");
                connection.setRequestProperty("User-Agent", SENTRY_CLIENT);
                connection.setRequestProperty("X-Sentry-Auth",
                        String.format(Locale.US, "Sentry sentry_version=%d,sentry_timestamp=%d,sentry_key=%s,sentry_secret=%s,sentry_client=%s",
                                SENTRY_VERSION, timestamp.getTime() / 1000, SENTRY_KEY, SENTRY_SECRET, SENTRY_CLIENT));

                GZIPOutputStream wr = new GZIPOutputStream(connection.getOutputStream());
                wr.write(requestBody.toString().getBytes());
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

                JSONObject jsonResponse = new JSONObject(response.toString());
                if (Teak.isDebug) {
                    Log.d(LOG_TAG, "Exception reported: " + jsonResponse.toString(2));
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, Log.getStackTraceString(e));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
