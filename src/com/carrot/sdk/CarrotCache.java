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

class CarrotCache
{
   private SQLiteDatabase mDatabase;
   private CarrotCacheOpenHelper mOpenHelper;
   private ExecutorService mExecutorService;
   private ExecutorService mInstallExecutorService;

   public CarrotCache() {
      mOpenHelper = new CarrotCacheOpenHelper(Carrot.getHostActivity());
   }

   protected void finalize() throws Throwable {
      try {
         close();
      }
      finally {
         super.finalize();
      }
   }

   public boolean open() {
      boolean ret = false;
      try {
         mDatabase = mOpenHelper.getWritableDatabase();

         // Send install metric if needed
         if(!mOpenHelper.mInstallMetricSent) {
            HashMap<String, Object> payload = new HashMap<String, Object>();
            payload.put("install_date", mOpenHelper.mInstallDate);

            mInstallExecutorService = Executors.newSingleThreadExecutor();
            CarrotRequest installReportRequest = new CarrotRequest("POST", "/install.json", payload, new Carrot.RequestCallback() {
               @Override
               public void requestComplete(int responseCode, String responseBody) {
                  synchronized(mDatabase) {
                     mDatabase.execSQL("UPDATE install_tracking SET metric_sent=1");
                  }
                  mOpenHelper.mInstallMetricSent = true;

                  mInstallExecutorService.shutdown();
               }
            });
            mInstallExecutorService.submit(installReportRequest);
         }

         ret = true;
      }
      catch(SQLException e) {
         Log.e(Carrot.LOG_TAG, Log.getStackTraceString(e));
      }
      return ret;
   }

   public void close() {
      stop();
      synchronized(mDatabase) {
         mOpenHelper.close();
      }
   }

   public boolean isRunning() {
      return (mExecutorService != null && !mExecutorService.isTerminated());
   }

   public void start() {
      if(!isRunning()) {
         mExecutorService = Executors.newSingleThreadExecutor();

         // Load requests from cache
         List<CarrotCachedRequest> cachedRequests = CarrotCachedRequest.requestsInCache(mDatabase);
         for(CarrotCachedRequest request : cachedRequests) {
            mExecutorService.submit(request);
         }
      }
   }

   public void stop() {
      if(isRunning()) mExecutorService.shutdownNow();
   }

   public boolean addRequest(String endpoint, Map<String, Object> payload) {
      boolean ret = false;
      try {
         CarrotCachedRequest request = new CarrotCachedRequest(mDatabase, endpoint, payload);
         ret = true;
         if(mExecutorService != null) mExecutorService.submit(request);
      }
      catch(Exception e) {
         Log.e(Carrot.LOG_TAG, Log.getStackTraceString(e));
      }
      return ret;
   }

   class CarrotCacheOpenHelper extends SQLiteOpenHelper {
      private static final String kDatabaseName = "carrot.db";
      private static final int kDatabaseVersion = 1;

      private static final String kCacheCreateSQL = "CREATE TABLE IF NOT EXISTS cache(request_endpoint TEXT, request_payload TEXT, request_id TEXT, request_date INTEGER, retry_count INTEGER)";

      private static final String kInstallTableCreateSQL = "CREATE TABLE IF NOT EXISTS install_tracking(install_date REAL, metric_sent INTEGER)";
      private final String[] kInstallTableReadColumns = { "install_date", "metric_sent" };

      public double mInstallDate;
      public boolean mInstallMetricSent;

      public CarrotCacheOpenHelper(Context context) {
         super(context, kDatabaseName, null, kDatabaseVersion);
      }

      @Override
      public void onCreate(SQLiteDatabase database) {
         database.execSQL(kCacheCreateSQL);
         database.execSQL(kInstallTableCreateSQL);
      }

      @Override
      public void onOpen(SQLiteDatabase database) {
         // Install tracking
         database.execSQL(kInstallTableCreateSQL);
         Cursor cursor = database.query("install_tracking", kInstallTableReadColumns,
            null, null, null, null, "install_date");

         mInstallMetricSent = false;
         mInstallDate = System.currentTimeMillis() / 1000.0;

         if(cursor.getCount() > 0) {
            cursor.moveToFirst();
            mInstallDate = cursor.getDouble(0);
            mInstallMetricSent = (cursor.getInt(1) > 0);
         }
         else {
            database.execSQL("INSERT INTO install_tracking (install_date, metric_sent) VALUES (" + mInstallDate +", 0)");
         }

         cursor.close();
      }

      @Override
      public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
         database.execSQL("DROP TABLE IF EXISTS cache");
         onCreate(database);
      }
   }
}
