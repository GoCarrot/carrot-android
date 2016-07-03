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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RavenService extends Service {
    public static final String LOG_TAG = "Teak:Raven:Service";
    public static final int DATABASE_VERSION = 1;
    public static final String REPORT_EXCEPTION_INTENT_ACTION = "REPORT_EXCEPTION";

    HashMap<String, ObserveAndReport> observeAndReporterMap = new HashMap<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String appId = intent.getStringExtra("appId");
        Log.d(LOG_TAG, "Lifecycle - onStartCommand: " + appId);

        if (appId != null && !appId.isEmpty()) {
            if (!observeAndReporterMap.containsKey(appId)) {
                Log.d(LOG_TAG, "   Creating new reporter for: " + appId);
                ObserveAndReport obs = new ObserveAndReport(this, appId);
                observeAndReporterMap.put(appId, obs);
            }

            String action = intent.getAction();
            if (action != null && !action.isEmpty()) {
                Log.d(LOG_TAG, "   Action: " + action);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "Lifecycle - onCreate");
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "Lifecycle - onDestroy");
        for (Map.Entry<String, ObserveAndReport> entry : observeAndReporterMap.entrySet()) {

        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final String[] EXCEPTIONS_READ_COLUMNS = {"rowid", "payload", "timestamp", "retries"};

    class ObserveAndReport {
        private DatabaseHelper databaseHelper;

        public ObserveAndReport(Context context, String appId) {
            databaseHelper = new DatabaseHelper(context, "raven." + appId + ".db");
        }

        class DatabaseHelper extends SQLiteOpenHelper {
            private AtomicInteger openCounter = new AtomicInteger();
            private SQLiteDatabase database;

            public DatabaseHelper(Context context, String name) {
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
                //db.execSQL("CREATE TABLE IF NOT EXISTS credentials(dsn TEXT)");
                db.execSQL("CREATE TABLE IF NOT EXISTS exceptions(payload TEXT, timestamp INTEGER, retries INTEGER)");
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                Log.d(LOG_TAG, "Upgrading database " + db + " from version " + oldVersion + " to " + newVersion);
                //db.execSQL("DROP TABLE IF EXISTS credentials");
                db.execSQL("DROP TABLE IF EXISTS exceptions");
                onCreate(db);
            }
        }
    }
}
