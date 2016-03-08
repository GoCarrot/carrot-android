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

import android.content.ContentValues;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import android.util.Log;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import java.util.concurrent.ExecutionException;

import org.json.JSONObject;

class CachedRequest extends Request implements Runnable {
    private String requestId;
    private int retryCount;
    private long cacheId;
    private Date dateIssued;

    private static SQLiteDatabase database;

    static final String REQUEST_CACHE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS cache(request_endpoint TEXT, request_payload TEXT, request_id TEXT, request_date INTEGER, retry_count INTEGER)";
    private static final String[] REQUEST_CACHE_READ_COLUMNS = {"rowid", "request_endpoint", "request_payload", "request_id", "request_date", "retry_count"};

    public CachedRequest(String endpoint, Map<String, Object> payload, Date dateIssued) {
        super("POST", endpoint, payload);

        this.dateIssued = dateIssued;
        this.requestId = UUID.randomUUID().toString();

        ContentValues values = new ContentValues();
        values.put("request_endpoint", this.endpoint);
        values.put("request_payload", new JSONObject(this.payload).toString());
        values.put("request_id", this.requestId);
        values.put("request_date", this.dateIssued.getTime());
        values.put("retry_count", 0);

        if(CachedRequest.database != null) {
            this.cacheId = CachedRequest.database.insert("cache", null, values);
        }

        // These parts of the payload should not be inserted into the database
        this.payload.put("request_id", this.requestId);
        this.payload.put("request_date", this.dateIssued.getTime() / 1000); // Milliseconds -> Seconds
    }

    public static void init() {
        try {
            CachedRequest.database = Teak.cacheOpenHelper.getWritableDatabase();
        } catch (SQLException e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
        }
    }

    private CachedRequest(Cursor cursor) throws Exception {
        super("POST", null, null);

        this.cacheId = cursor.getLong(0);
        this.endpoint = cursor.getString(1);
        this.payload = Helpers.jsonToMap(new JSONObject(cursor.getString(2)));
        this.requestId = cursor.getString(3);
        this.dateIssued = new Date(cursor.getLong(4));
        this.retryCount = cursor.getInt(5);

        this.payload.put("request_id", this.requestId);
        this.payload.put("request_date", this.dateIssued.getTime() / 1000); // Milliseconds -> Seconds
    }

    @Override
    protected void addCommonPayload(Map<String, Object> payload) throws InterruptedException, ExecutionException {
        payload.put("api_key", Teak.userId.get());
        super.addCommonPayload(payload);
    }

    @Override
    protected void done(int responseCode, String responseBody) {
        if(responseCode < 500) {
            CachedRequest.database.delete("cache", "rowid = " + this.cacheId, null);
        } else {
            ContentValues values = new ContentValues();
            values.put("retry_count", this.retryCount + 1);
            CachedRequest.database.update("cache", values, "rowid = " + this.cacheId, null);
        }
    }

    public static List<CachedRequest> requestsInCache() {
        List<CachedRequest> requests = new ArrayList<CachedRequest>();

        Cursor cursor = CachedRequest.database.query("cache", REQUEST_CACHE_READ_COLUMNS, null, null, null, null, "retry_count");

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            try {
                CachedRequest request = new CachedRequest(cursor);
                requests.add(request);
            } catch (Exception e) {
                Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
            }
            cursor.moveToNext();
        }
        cursor.close();

        return requests;
    }
}
