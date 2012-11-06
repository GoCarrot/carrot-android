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

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.List;
import java.util.Map;

class CarrotCache
{
   private SQLiteDatabase mDatabase;
   private CarrotCacheOpenHelper mOpenHelper;
   private ExecutorService mExecutorService;
   private Carrot mCarrot;

   public CarrotCache(Carrot carrot) {
      mCarrot = carrot;
      mOpenHelper = new CarrotCacheOpenHelper(mCarrot.getHostActivity());
   }

   public boolean open() {
      boolean ret = false;
      try {
         mDatabase = mOpenHelper.getWritableDatabase();
         ret = true;
      }
      catch(SQLException e) {
         e.printStackTrace();
      }
      return ret;
   }

   public void close() {
      stop();
      synchronized(mDatabase) {
         mOpenHelper.close();
      }
   }

   public void start() {
      if(mExecutorService != null) mExecutorService.shutdownNow();
      mExecutorService = Executors.newSingleThreadExecutor();

      // Load requests from cache
      List<CarrotCachedRequest> cachedRequests = CarrotCachedRequest.requestsInCache(mDatabase, mCarrot);
      for(CarrotCachedRequest request : cachedRequests) {
         mExecutorService.submit(request);
      }
   }

   public void stop() {
      if(mExecutorService != null) mExecutorService.shutdownNow();
   }

   public boolean addRequest(String endpoint, Map<String, Object> payload) {
      boolean ret = false;
      try {
         CarrotCachedRequest request = new CarrotCachedRequest(mDatabase, mCarrot, endpoint, payload);
         ret = true;
         if(mExecutorService != null) mExecutorService.submit(request);
      }
      catch(Exception e) {
      }
      return ret;
   }

   class CarrotCacheOpenHelper extends SQLiteOpenHelper {
      private static final String kDatabaseName = "carrot.db";
      private static final int kDatabaseVersion = 1;

      private static final String kCacheCreateSQL = "CREATE TABLE IF NOT EXISTS cache(request_endpoint TEXT, request_payload TEXT, request_id TEXT, request_date INTEGER, retry_count INTEGER)";

      public CarrotCacheOpenHelper(Context context) {
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
