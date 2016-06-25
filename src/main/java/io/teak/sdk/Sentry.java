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

import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Sentry {
    public static final int SENTRY_VERSION = 7; // Sentry protocol version
    public static final String TEAK_SENTRY_VERSION = "1.0.0";
    public static final String SENTRY_CLIENT= "teak/" + TEAK_SENTRY_VERSION;

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

    static String SENTRY_KEY;
    static String SENTRY_SECRET;
    static URL endpoint;
    static HashMap<String, Object> payloadTemplate = new HashMap<>();
    static ExecutorService requestExecutor = Executors.newCachedThreadPool();

    public static void init(String dsn) {
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
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
        }

        // Build out a template to base all payloads off of
        payloadTemplate.put("logger", "teak");
        payloadTemplate.put("platform", "java");
        payloadTemplate.put("release", Teak.SDKVersion);
        payloadTemplate.put("server_name", Teak.bundleId);

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

        HashMap<String, Object> tagsAttribute = new HashMap<>();
        tagsAttribute.put("app_id", Teak.appId);
        tagsAttribute.put("app_version", Teak.appVersion);
        payloadTemplate.put("tags", tagsAttribute);

        // extra : map
    }

    public static void reportException(String message, Throwable t) {
        Sentry.requestExecutor.submit(new Request(message, Level.ERROR));
    }

    static class Request implements Runnable {
        HashMap<String, Object> payload = new HashMap<>(payloadTemplate);
        Date timestamp = new Date();

        public Request(String message, Level level) {
            payload.put("event_id", UUID.randomUUID().toString().replace("-", ""));
            payload.put("message", message.substring(0, Math.min(message.length(), 1000)));

            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
            df.setTimeZone(tz);
            payload.put("timestamp", df.format(timestamp));

            payload.put("level", level.toString());

            try {
                // 0 dalvik.system.VMStack.getThreadStackTrace
                // 1 java.lang.Thread.getStackTrace
                // 2 io.teak.sdk.Sentry$Request.<init>
                // 3 method calling this function
                final int depth = 3;
                final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
                payload.put("culprit", ste[depth].toString());
                for (StackTraceElement elem : ste) {
                    Log.d(Teak.LOG_TAG, elem.toString());
                }
            } catch (Exception e) {
                payload.put("culprit", "unknown");
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
            Date timestamp = new Date();

            StringBuilder requestBody = new StringBuilder();

            try {
                connection = (HttpsURLConnection) endpoint.openConnection();
                connection.setRequestProperty("Accept-Charset", "UTF-8");
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", SENTRY_CLIENT);
                connection.setRequestProperty("X-Sentry-Auth",
                        String.format("Sentry sentry_version=%d\nsentry_timestamp=%d\nsentry_key=%s\nsentry_secret=%s\nsentry_client=%s",
                                SENTRY_VERSION, timestamp.getTime() / 1000, SENTRY_KEY, SENTRY_SECRET, SENTRY_CLIENT));

                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(requestBody.toString());
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

                // Reply
                try {
                    Log.d(Teak.LOG_TAG, new JSONObject(response.toString()).toString(2));
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
