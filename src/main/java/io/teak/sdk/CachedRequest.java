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

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import java.util.concurrent.ExecutionException;

import org.json.JSONObject;

class CachedRequest extends Request implements Runnable {
    private final String requestId;
    private final int retryCount;
    private final long cacheId;
    private final Date dateIssued;

    static final String REQUEST_CACHE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS cache(request_endpoint TEXT, request_payload TEXT, request_id TEXT, request_date INTEGER, retry_count INTEGER)";
    private static final String[] REQUEST_CACHE_READ_COLUMNS = {"rowid", "request_endpoint", "request_payload", "request_id", "request_date", "retry_count"};

    private CachedRequest(@NonNull String endpoint, Map<String, Object> payload, Date dateIssued, @NonNull Session session, @NonNull String userId) {
        super("POST", endpoint, payload, session);

        this.dateIssued = dateIssued;
        this.requestId = UUID.randomUUID().toString();
        this.retryCount = 0;

        this.payload.put("api_key", userId);
        this.payload.put("request_id", this.requestId);
        this.payload.put("request_date", this.dateIssued.getTime() / 1000); // Milliseconds -> Seconds

        ContentValues values = new ContentValues();
        values.put("request_endpoint", endpoint);
        values.put("request_payload", new JSONObject(this.payload).toString());
        values.put("request_id", this.requestId);
        values.put("request_date", this.dateIssued.getTime());
        values.put("retry_count", 0);

        {
            long tempCacheId = 0;
            try {
                tempCacheId = CacheManager.instance().open().insert("cache", null, values);
                CacheManager.instance().close();
            } catch (Exception e) {
                Log.e(Teak.LOG_TAG, "Error inserting request into cache: " + Log.getStackTraceString(e));
                Teak.sdkRaven.reportException(e);
            } finally {
                this.cacheId = tempCacheId;
            }
        }
    }

    public static void submitCachedRequest(@NonNull final String endpoint, @NonNull final Map<String, Object> payload, @NonNull final Date dateIssued) {
        Session.whenUserIdIsReadyRun(new Session.SessionRunnable() {
            @Override
            public void run(Session session) {
                new CachedRequest(endpoint, payload, dateIssued, session, session.userId()).run();
            }
        });
    }

    public static void submitCachedRequests(@NonNull Session session) {
        List<CachedRequest> requests = CachedRequest.requestsInCache(session);
        for (CachedRequest request : requests) {
            Teak.asyncExecutor.submit(request);
        }
    }

    private CachedRequest(long cacheId, @NonNull String endpoint, @NonNull Map<String, Object> payload,
                          @NonNull String requestId, @NonNull Date dateIssued, int retryCount,
                          @NonNull Session session) {
        super("POST", endpoint, payload, session, new Object());
        this.cacheId = cacheId;
        this.requestId = requestId;
        this.dateIssued = dateIssued;
        this.retryCount = retryCount;
    }

    @Override
    protected void done(int responseCode, String responseBody) {
        try {
            if (responseCode < 500) {
                CacheManager.instance().open().delete("cache", "rowid = " + this.cacheId, null);
            } else {
                ContentValues values = new ContentValues();
                values.put("retry_count", this.retryCount + 1);
                CacheManager.instance().open().update("cache", values, "rowid = " + this.cacheId, null);
            }
            CacheManager.instance().close();
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Error removing request from cache: " + Log.getStackTraceString(e));
            Teak.sdkRaven.reportException(e);
        }
        super.done(responseCode, responseBody);
    }

    private static List<CachedRequest> requestsInCache(@NonNull Session session) {
        List<CachedRequest> requests = new ArrayList<>();

        try {
            Cursor cursor = CacheManager.instance().open().query("cache", REQUEST_CACHE_READ_COLUMNS, null, null, null, null, "retry_count");
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                try {
                    Map<String, Object> payload = Helpers.jsonToMap(new JSONObject(cursor.getString(2)));
                    CachedRequest request = new CachedRequest(cursor.getLong(0), cursor.getString(1),
                            payload, cursor.getString(3), new Date(cursor.getLong(4)), cursor.getInt(5), session);
                    requests.add(request);
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, "Error loading request from cache: " + Log.getStackTraceString(e));
                    Teak.sdkRaven.reportException(e);
                }
                cursor.moveToNext();
            }
            cursor.close();
            CacheManager.instance().close();
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
            Teak.sdkRaven.reportException(e);
        }

        return requests;
    }

    public static class IdentifyUserRequest extends CachedRequest {
        public IdentifyUserRequest(@NonNull final String endpoint, @NonNull final Map<String, Object> payload, @NonNull final Date dateIssued, @NonNull Session session, @NonNull String userId) {
            super(endpoint, payload, dateIssued, session, userId);
        }
    }
}
