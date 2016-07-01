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
import android.database.ContentObserver;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;

public class RavenService extends Service {
    public static final String LOG_TAG = "Teak:Raven:Service";
    HashMap<String, ObserveAndReport> observeAndReports = new HashMap<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "Lifecycle - onStartCommand");

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        // Create SQLite content observer
        Log.d(LOG_TAG, "Lifecycle - onCreate");
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "Lifecycle - onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class ObserveAndReport extends ContentObserver {
        public ObserveAndReport(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            // Handle change.
        }

        class DatabaseHelper extends SQLiteOpenHelper {
            public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version, DatabaseErrorHandler errorHandler) {
                super(context, name, factory, version, errorHandler);
            }

            @Override
            public void onCreate(SQLiteDatabase db) {

            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                Log.d(LOG_TAG, "Upgrading database " + db + " from version " + oldVersion + " to " + newVersion);
                db.execSQL("DROP TABLE IF EXISTS cache");
                db.execSQL("DROP TABLE IF EXISTS inbox");
                onCreate(db);
            }
        }
    }
}
