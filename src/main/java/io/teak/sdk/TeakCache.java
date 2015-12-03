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
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

class TeakCache {
    private SQLiteDatabase mDatabase;
    private TeakCacheOpenHelper mOpenHelper;
    private ExecutorService mExecutorService;

    public TeakCache() {
        mOpenHelper = new TeakCacheOpenHelper(Teak.getHostActivity());
    }

    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public boolean open() {
        boolean ret = false;
        try {
            mDatabase = mOpenHelper.getWritableDatabase();
            ret = true;
        } catch (SQLException e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
        }
        return ret;
    }

    public void close() {
        stop();
        synchronized (mDatabase) {
            mOpenHelper.close();
        }
    }

    public boolean isRunning() {
        return (mExecutorService != null && !mExecutorService.isTerminated());
    }

    public void start() {
        if (!isRunning()) {
            mExecutorService = Executors.newSingleThreadExecutor();

            // Load requests from cache
            List<TeakCachedRequest> cachedRequests = TeakCachedRequest.requestsInCache(mDatabase);
            for (TeakCachedRequest request : cachedRequests) {
                mExecutorService.submit(request);
            }
        }
    }

    public void stop() {
        if (isRunning()) mExecutorService.shutdownNow();
    }

    public boolean addRequest(String endpoint, Map<String, Object> payload) {
        boolean ret = false;
        try {
            TeakCachedRequest request = new TeakCachedRequest(mDatabase, endpoint, payload);
            ret = true;
            if (mExecutorService != null) mExecutorService.submit(request);
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
        }
        return ret;
    }

    class TeakCacheOpenHelper extends SQLiteOpenHelper {
        private static final String kDatabaseName = "teak.db";
        private static final int kDatabaseVersion = 1;

        private static final String kCacheCreateSQL = "CREATE TABLE IF NOT EXISTS cache(request_endpoint TEXT, request_payload TEXT, request_id TEXT, request_date INTEGER, retry_count INTEGER)";

        public TeakCacheOpenHelper(Context context) {
            super(context, kDatabaseName, null, kDatabaseVersion);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(kCacheCreateSQL);
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            database.execSQL("DROP TABLE IF EXISTS cache");
            onCreate(database);
        }
    }
}
