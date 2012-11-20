/* Carrot -- Copyright (C) 2012 Carrot Inc.
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
package com.CarrotInc.Carrot;

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

class CarrotCachedRequest implements Runnable {
   private String mRequestId;
   private int mRetryCount;
   private long mCacheId;
   private Date mDateIssued;
   private String mEndpoint;
   private Map<String, Object> mPayload;
   private SQLiteDatabase mDatabase;
   private Carrot mCarrot;

   private static final String[] kCacheReadColumns = { "rowid", "request_endpoint", "request_payload", "request_id", "request_date", "retry_count"};

   public CarrotCachedRequest(SQLiteDatabase database, Carrot carrot, String endpoint,
      Map<String, Object> payload) throws Exception {
      mDateIssued = new Date();
      mRequestId = UUID.randomUUID().toString();
      mEndpoint = endpoint;
      mPayload = payload;
      mDatabase = database;
      mCarrot = carrot;
      Gson gson = new Gson();

      ContentValues values = new ContentValues();
      values.put("request_endpoint", mEndpoint);
      values.put("request_payload", gson.toJson(mPayload));
      values.put("request_id", mRequestId);
      values.put("request_date", mDateIssued.getTime());
      values.put("retry_count", 0);

      synchronized(mDatabase) {
         mCacheId = database.insert("cache", null, values);
      }
   }

   private CarrotCachedRequest(SQLiteDatabase database, Carrot carrot, Cursor cursor) {
      mDatabase = database;
      mCarrot = carrot;

      Gson gson = new Gson();
      Type payloadType = new TypeToken<Map<String, Object>>(){}.getType();

      mCacheId = cursor.getLong(0);
      mEndpoint = cursor.getString(1);
      mPayload = gson.fromJson(cursor.getString(2), payloadType);
      mRequestId = cursor.getString(3);
      mDateIssued = new Date(cursor.getLong(4));
      mRetryCount = cursor.getInt(5);
   }

   public void removeFromCache() {
      synchronized(mDatabase) {
         mDatabase.delete("cache", "rowid = " + mCacheId, null);
      }
   }

   public void addRetryInCache() {
      ContentValues values = new ContentValues();
      values.put("retry_count", mRetryCount + 1);
      synchronized(mDatabase) {
         mDatabase.update("cache", values, "rowid = " + mCacheId, null);
      }
   }

   public void run() {
      HttpsURLConnection connection = null;
      SecretKeySpec keySpec = new SecretKeySpec(mCarrot.getAppSecret().getBytes(), "HmacSHA256");

      try {
         Gson gson = new Gson();
         HashMap<String, Object> postBodyObject = new HashMap<String, Object>();
         postBodyObject.putAll(mPayload);
         postBodyObject.put("api_key", mCarrot.getUDID());
         postBodyObject.put("game_id", mCarrot.getAppId());
         postBodyObject.put("request_id", mRequestId);
         postBodyObject.put("request_date", mDateIssued.getTime() / 1000); // Milliseconds -> Seconds

         ArrayList<String> payloadKeys = new ArrayList<String>(postBodyObject.keySet());
         Collections.sort(payloadKeys);

         StringBuilder postBody = new StringBuilder();
         for(String key : payloadKeys) {
            Object value = postBodyObject.get(key);
            String valueString = null;
            if(!Map.class.isInstance(value) &&
               !Array.class.isInstance(value) &&
               !List.class.isInstance(value)) {
               valueString = value.toString();
            }
            else {
               valueString = gson.toJson(value);
            }
            postBody.append(key + "=" + valueString + "&");
         }
         postBody.deleteCharAt(postBody.length() - 1);

         String stringToSign = "POST\n" + mCarrot.getHostname() + "\n" + mEndpoint + "\n" + postBody.toString();

         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(keySpec);
         byte[] result = mac.doFinal(stringToSign.getBytes());

         postBody = new StringBuilder();
         for(String key : payloadKeys) {
            Object value = postBodyObject.get(key);
            String valueString = null;
            if(!Map.class.isInstance(value) &&
               !Array.class.isInstance(value) &&
               !List.class.isInstance(value)) {
               valueString = value.toString();
            }
            else {
               valueString = gson.toJson(value);
            }
            postBody.append(key + "=" + URLEncoder.encode(valueString, "ISO-8859-1") + "&");
         }
         postBody.append("sig=" + URLEncoder.encode(Base64.encodeToString(result, Base64.DEFAULT), "ISO-8859-1"));

         URL url = new URL("https://" + mCarrot.getHostname() + mEndpoint);
         connection = (HttpsURLConnection)url.openConnection();
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
         connection.setRequestProperty("Content-Length",
            "" +  Integer.toString(postBody.toString().getBytes().length));
         connection.setUseCaches(false);
         connection.setDoOutput(true);

         // Send request
         DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
         wr.writeBytes(postBody.toString());
         wr.flush();
         wr.close();
/*
         //Get Response 
         InputStream is = connection.getInputStream();
         BufferedReader rd = new BufferedReader(new InputStreamReader(is));
         String line;
         StringBuffer response = new StringBuffer();
         while((line = rd.readLine()) != null) {
           response.append(line);
           response.append('\r');
         }
         rd.close();
*/
         if(connection.getResponseCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
            Log.e(Carrot.LOG_TAG, "Requested resource not found, removing request from cache.");
            removeFromCache();
         }
         else if(!mCarrot.updateAuthenticationStatus(connection.getResponseCode())) {
            Log.e(Carrot.LOG_TAG, "Unknown error (" + connection.getResponseCode() + ") submitting Carrot request: " /*+ response.toString()*/);
            addRetryInCache();
         }
         else if (mCarrot.getStatus() == Carrot.StatusReady) {
            removeFromCache();
         }
         else {
            mCarrot.setStatus(Carrot.StatusUndetermined);
            addRetryInCache();
         }
      }
      catch(Exception e) {
         Log.e(Carrot.LOG_TAG, Log.getStackTraceString(e));
      }
      finally {
         connection.disconnect();
         connection = null;
      }
   }

   public static List<CarrotCachedRequest> requestsInCache(SQLiteDatabase database, Carrot carrot) {
      List<CarrotCachedRequest> requests = new ArrayList<CarrotCachedRequest>();

      synchronized(database) {
         Cursor cursor = database.query("cache", kCacheReadColumns,
            null, null, null, null, "retry_count");

         cursor.moveToFirst();
         while(!cursor.isAfterLast()) {
            try {
               CarrotCachedRequest request = new CarrotCachedRequest(database, carrot, cursor);
               requests.add(request);
            }
            catch(Exception e) {
               Log.e(Carrot.LOG_TAG, Log.getStackTraceString(e));
            }
            cursor.moveToNext();
         }
         cursor.close();
      }

      return requests;
  }
}
