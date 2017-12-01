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
package io.teak.sdk.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import io.teak.sdk.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

public class RavenService extends Service {
    public static final String LOG_TAG = "Teak.Raven.Service";
    public static final int DATABASE_VERSION = 1;

    public static final int SENTRY_VERSION = 7;
    public static final String TEAK_SENTRY_VERSION = "1.0.0";
    public static final String SENTRY_CLIENT = "teak-android/" + TEAK_SENTRY_VERSION;

    public static final String REPORT_EXCEPTION_INTENT_ACTION = "REPORT_EXCEPTION";
    public static final String SET_DSN_INTENT_ACTION = "SET_DSN";

    HashMap<String, AppReporter> appReporterMap = new HashMap<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String appId = intent.getStringExtra("appId");

            if (appId != null && !appId.isEmpty()) {
                AppReporter appReporter;
                if (!appReporterMap.containsKey(appId)) {
                    appReporter = new AppReporter(this, appId);
                    appReporterMap.put(appId, appReporter);
                } else {
                    appReporter = appReporterMap.get(appId);
                }

                String action = intent.getAction();
                if (action != null && !action.isEmpty()) {
                    if (SET_DSN_INTENT_ACTION.equals(action)) {
                        appReporter.setDsn(intent);
                    } else if (REPORT_EXCEPTION_INTENT_ACTION.equals(action)) {
                        appReporter.reportException(intent);
                    }
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "Lifecycle - onCreate");

        // Debugging
        //android.os.Debug.waitForDebugger();
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "Lifecycle - onDestroy");
        for (Map.Entry<String, AppReporter> entry : appReporterMap.entrySet()) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final String[] EXCEPTIONS_READ_COLUMNS = {"rowid", "payload", "timestamp", "retries"};

    private class AppReporter {
        private DatabaseHelper databaseHelper;
        private String SENTRY_KEY;
        private String SENTRY_SECRET;
        private URL endpoint;

        AppReporter(Context context, String appId) {
            databaseHelper = new DatabaseHelper(context, "raven." + appId + ".db");
        }

        void reportException(Intent intent) {
            Thread senderThread = new Thread(new ReportSender(intent));
            senderThread.start();
        }

        void setDsn(Intent intent) {
            String dsn = intent.getStringExtra("dsn");
            if (dsn == null || dsn.isEmpty()) {
                Log.e(LOG_TAG, "DSN empty for app: " + intent.getStringExtra("appId"));
                return;
            }

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
                Log.e(LOG_TAG, "Error parsing DSN: '" + uri.toString() + "'" + Log.getStackTraceString(e));
            }
        }

        class ReportSender implements Runnable {
            long timestamp;
            JSONObject requestBody;

            ReportSender(Intent intent) {
                timestamp = intent.getLongExtra("timestamp", new Date().getTime() / 1000L);
                try {
                    requestBody = new JSONObject(intent.getStringExtra("payload"));
                } catch (Exception e) {
                    requestBody = null;
                }
            }

            @Override
            public void run() {
                if (requestBody == null || endpoint == null) return;

                HttpsURLConnection connection = null;
                BufferedReader rd = null;

                try {
                    connection = (HttpsURLConnection) endpoint.openConnection();
                    connection.setRequestProperty("Accept-Charset", "UTF-8");
                    connection.setUseCaches(false);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Content-Encoding", "gzip");
                    connection.setRequestProperty("User-Agent", SENTRY_CLIENT);
                    connection.setRequestProperty("X-Sentry-Auth",
                        String.format(Locale.US, "Sentry sentry_version=%d,sentry_timestamp=%d,sentry_key=%s,sentry_secret=%s,sentry_client=%s",
                            SENTRY_VERSION, timestamp, SENTRY_KEY, SENTRY_SECRET, SENTRY_CLIENT));

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
                    rd = new BufferedReader(new InputStreamReader(is));
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = rd.readLine()) != null) {
                        response.append(line);
                        response.append('\r');
                    }

                    try {
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        Log.e(LOG_TAG, "Exception reported: " + jsonResponse.toString(2));
                    } catch (Exception ignored) {
                        Log.e(LOG_TAG, "Exception reported: " + response.toString());
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                } finally {
                    if (rd != null) {
                        try {
                            rd.close();
                        } catch (Exception ignored) {
                        }
                    }

                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }

        // TODO: Pull this class out
        class DatabaseHelper extends SQLiteOpenHelper {
            private AtomicInteger openCounter = new AtomicInteger();
            private SQLiteDatabase database;

            DatabaseHelper(Context context, String name) {
                super(context, name, null, DATABASE_VERSION);
            }

            public synchronized SQLiteDatabase acquire() {
                if (openCounter.incrementAndGet() == 1) {
                    database = getWritableDatabase();
                }
                return database;
            }

            public synchronized void release() {
                if (openCounter.decrementAndGet() == 0) {
                    database.close();
                }
            }

            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE IF NOT EXISTS exceptions(payload TEXT, timestamp INTEGER, retries INTEGER)");
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                Log.d(LOG_TAG, "Upgrading database " + db + " from version " + oldVersion + " to " + newVersion);
                db.execSQL("DROP TABLE IF EXISTS exceptions");
                onCreate(db);
            }
        }
    }
}
