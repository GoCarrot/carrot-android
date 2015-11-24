/* Carrot -- Copyright (C) 2012 GoCarrot Inc.
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
package com.carrot.sdk;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.util.Log;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

class CarrotCachedRequest extends CarrotRequest implements Runnable {
   private String mRequestId;
   private int mRetryCount;
   private long mCacheId;
   private Date mDateIssued;
   private SQLiteDatabase mDatabase;

   private static final String[] kCacheReadColumns = { "rowid", "request_endpoint", "request_payload", "request_id", "request_date", "retry_count"};

   public CarrotCachedRequest(SQLiteDatabase database, String endpoint, Map<String, Object> payload) throws Exception {
      super("POST", endpoint, payload, null);
      mCallback = new CarrotCachedRequestCallback();

      mDateIssued = new Date();
      mRequestId = UUID.randomUUID().toString();
      mDatabase = database;
      Gson gson = new Gson();

      ContentValues values = new ContentValues();
      values.put("request_endpoint", endpoint);
      values.put("request_payload", gson.toJson(mPayload));
      values.put("request_id", mRequestId);
      values.put("request_date", mDateIssued.getTime());
      values.put("retry_count", 0);

      synchronized(mDatabase) {
         mCacheId = database.insert("cache", null, values);
      }

      mPayload.put("request_id", mRequestId);
      mPayload.put("request_date", mDateIssued.getTime() / 1000); // Milliseconds -> Seconds
   }

   private CarrotCachedRequest(SQLiteDatabase database, Cursor cursor) throws Exception {
      super("POST", null, null, null);
      mCallback = new CarrotCachedRequestCallback();

      mDatabase = database;

      Gson gson = new Gson();
      Type payloadType = new TypeToken<Map<String, Object>>(){}.getType();

      mCacheId = cursor.getLong(0);
      mEndpoint = cursor.getString(1);
      mPayload = gson.fromJson(cursor.getString(2), payloadType);
      mRequestId = cursor.getString(3);
      mDateIssued = new Date(cursor.getLong(4));
      mRetryCount = cursor.getInt(5);

      mPayload.put("request_id", mRequestId);
      mPayload.put("request_date", mDateIssued.getTime() / 1000); // Milliseconds -> Seconds
   }

   class CarrotCachedRequestCallback implements Carrot.RequestCallback {
      @Override
      public void requestComplete(int responseCode, String responseBody) {
         if(responseCode == HttpsURLConnection.HTTP_NOT_FOUND) {
            Log.e(Carrot.LOG_TAG, "Requested resource not found, removing request from cache.");
            removeFromCache();
         }
         else if(!Carrot.updateAuthenticationStatus(responseCode)) {
            Log.e(Carrot.LOG_TAG, "Unknown error (" + responseCode + ") submitting Carrot request: " + responseBody);
            addRetryInCache();
         }
         else if(Carrot.getStatus() == Carrot.StatusReady) {
            removeFromCache();
         }
         else {
            addRetryInCache();
         }
      }
   }

   void removeFromCache() {
      synchronized(mDatabase) {
         mDatabase.delete("cache", "rowid = " + mCacheId, null);
      }
   }

   void addRetryInCache() {
      ContentValues values = new ContentValues();
      values.put("retry_count", mRetryCount + 1);
      synchronized(mDatabase) {
         mDatabase.update("cache", values, "rowid = " + mCacheId, null);
      }
   }

   public static List<CarrotCachedRequest> requestsInCache(SQLiteDatabase database) {
      List<CarrotCachedRequest> requests = new ArrayList<CarrotCachedRequest>();

      synchronized(database) {
         Cursor cursor = database.query("cache", kCacheReadColumns,
            null, null, null, null, "retry_count");
         Carrot.getHostActivity().startManagingCursor(cursor);

         cursor.moveToFirst();
         while(!cursor.isAfterLast()) {
            try {
               CarrotCachedRequest request = new CarrotCachedRequest(database, cursor);
               requests.add(request);
            }
            catch(Exception e) {
               Log.e(Carrot.LOG_TAG, Log.getStackTraceString(e));
            }
            cursor.moveToNext();
         }
      }

      return requests;
  }
}
