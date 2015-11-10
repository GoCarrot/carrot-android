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
package com.CarrotInc.Carrot;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.lang.reflect.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows you to interact with the Carrot service from your Android application.
 * <p>
 * Once a Carrot instance has been constructed, any calls to the following methods will
 * will be cached on the client and sent to the Carrot service once authentication has
 * occurred.
 * <ul>
 * <li>{@link #postAction(String,String) postAction} (and variants)
 * </ul>
 * This means that a user may authenticate with Facebook at a much later date
 * and calls made to Carrot will still be transmitted to the server. Each Carrot
 * request is timestamped, so achievements earned will be granted at the date and time
 * the achievement was earned, instead of a when the request is processed.
 * <p>
 * Once you are finished with a Carrot instance, you must call the {@link #close() close} method,
 * and make no further calls to the instance.
 */
public class Carrot {
   public static final int StatusNotAuthorized = -1;
   public static final int StatusUndetermined = 0;
   public static final int StatusReadOnly = 1;
   public static final int StatusReady = 2;

   public static final String SDKVersion = "1.1";

   /**
    * Constructor for a new Carrot instance.
    *
    * @param appId      the Facebook Application Id for your application.
    * @param appSecret  the Carrot Application Secret for your application.
    * @param userId     the active user id.
    */
   public Carrot(String appId, String appSecret, String userId) {
      mAppId = appId;
      mAppSecret = appSecret;
      mStatus = Carrot.StatusUndetermined;
      mUserId = userId;
   }

   /**
    * Get the authentication status of the Carrot user.
    *
    * @return the authentication status of the Carrot user.
    */
   public int getStatus() {
      return mStatus;
   }

   /**
    * Activate Carrot and attach the {@link Activity} to this instance.
    * <p>
    * Call this function from the <code>onResume()</code> function of your activity.
    *
    * @param activity the new <code>Activity</code> to which this instance should attach.
    */
   public void activateApp(Activity activity) {
      if(mExecutorService != null) {
        mExecutorService.shutdownNow();
      }
      mExecutorService = Executors.newSingleThreadExecutor();

      mHostActivity = activity;

      if(mCarrotCache == null) {
         mCarrotCache = new CarrotCache(this);
         if(!mCarrotCache.open()) {
            Log.e(LOG_TAG, "Failed to create Carrot cache.");
         }
         else {
            Log.d(LOG_TAG, "Attached to android.app.Activity: " + mHostActivity);
         }
      }

      if(!hasRequiredPermissions(activity)) {
         Log.e(LOG_TAG, "Carrot in offline mode until require permissions are added.");
      }
      else {
         servicesDiscovery();
      }
   }

   /**
    * Closes the request cache and stops the request threads for this Carrot instance.
    * <p>
    * Call this function from the <code>onPause()</code> function of your activity.
    *
    * @param activity the new <code>Activity</code> to which this instance should detach.
    */
   public void deactivateApp(Activity activity) {
      if(mExecutorService != null) {
        mExecutorService.shutdownNow();
        mExecutorService = null;
      }
      if(mCarrotCache != null) {
         mCarrotCache.close();
         mCarrotCache = null;
      }
   }

   /**
    * Assign a Facebook user token to this instance.
    * <p>
    * When a Facebook user token is assigned, Carrot will asynchronously check to see if the
    * user is already authenticated in the Carrot service, if not the user will be added. Once
    * the user has been authenticated or added, cached requests will be transmitted to the Carrot
    * service in the background.
    *
    * @param accessToken the Facebook access token for the current user.
    */
   public void setAccessToken(String accessToken) {
      mAccessToken = accessToken;
      if(getStatus() != StatusReady) {
         validateUser();
      }
   }

   /**
    * Assign a push notification key to the current Carrot user.
    * <p>
    * For Urban Airship, the device key can be obtained with:
    * <code>PushManager.shared().getPreferences().getPushId()</code>
    *
    * @param devicePushKey the push notification key for the current device.
    */
   public void setDevicePushKey(String devicePushKey) {
      HashMap<String, Object> payload = new HashMap<String, Object>();
      payload.put("push_key", devicePushKey);
      payload.put("device_type", "android");
      mCarrotCache.addRequest("/me/devices.json", payload);
   }

   /**
    * Post an Open Graph action with an existing object to the Carrot service.
    *
    * @param actionId the Carrot action id.
    * @param objectInstanceId the instance id of the Carrot object.
    * @return <code>true if the action was cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postAction(String actionId, String objectInstanceId) {
      return postAction(actionId, null, objectInstanceId);
   }

   /**
    * Post an Open Graph action with an existing object to the Carrot service.
    *
    * @param actionId the Carrot action id.
    * @param actionPropertiesJson the properties to be sent along with the Carrot action encoded to JSON.
    * @param objectInstanceId the instance id of the Carrot object.
    * @return <code>true if the action was cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postJsonAction(String actionId, String actionPropertiesJson, String objectInstanceId) {
      Map<String, Object> actionProperties = null;

      if(actionPropertiesJson != null && !actionPropertiesJson.isEmpty()) {
         Gson gson = new Gson();
         Type payloadType = new TypeToken<Map<String, Object>>(){}.getType();
         actionProperties = gson.fromJson(actionPropertiesJson, payloadType);
      }

      return postAction(actionId, actionProperties, objectInstanceId);
   }

   /**
    * Post an Open Graph action with an existing object to the Carrot service.
    *
    * @param actionId the Carrot action id.
    * @param actionProperties the properties to be sent along with the Carrot action.
    * @param objectInstanceId the instance id of the Carrot object.
    * @return <code>true if the action was cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postAction(String actionId, Map<String, Object> actionProperties,
      String objectInstanceId) {
      HashMap<String, Object> payload = new HashMap<String, Object>();
      payload.put("action_id", actionId);
      payload.put("object_instance_id", objectInstanceId);
      if(actionProperties != null) {
         payload.put("action_properties", actionProperties);
      }
      return mCarrotCache.addRequest("/me/actions.json", payload);
   }

   /**
    * Post an Open Graph action to the Carrot service which will create a new object.
    *
    * @param actionId the Carrot action id.
    * @param objectTypeId the object id of the Carrot object type to create.
    * @param objectProperties the properties for the new object.
    * @return <code>true if the action was cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postAction(String actionId, String objectTypeId,
      Map<String, Object> objectProperties) {
      return postAction(actionId, null, objectTypeId, objectProperties, null);
   }

   /**
    * Post an Open Graph action to the Carrot service which will create a new object or reuse an existing created object.
    *
    * @param actionId the Carrot action id.
    * @param objectTypeId the object id of the Carrot object type to create.
    * @param objectProperties the properties for the new object.
    * @param objectInstanceId the object instance id of the Carrot object to create or re-use.
    * @return <code>true if the action was cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postAction(String actionId, String objectTypeId,
      Map<String, Object> objectProperties, String objectInstanceId) {
      return postAction(actionId, null, objectTypeId, objectProperties, objectInstanceId);
   }

   /**
    * Post an Open Graph action to the Carrot service which will create a new object or reuse an existing created object.
    *
    * @param actionId the Carrot action id.
    * @param actionPropertiesJson the properties to be sent along with the Carrot action encoded to JSON.
    * @param objectTypeId the object id of the Carrot object type to create.
    * @param objectPropertiesJson the properties for the new object encoded as JSON.
    * @param objectInstanceId the object instance id of the Carrot object to create or re-use.
    * @return <code>true if the action was cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postJsonAction(String actionId, String actionPropertiesJson, String objectTypeId,
      String objectPropertiesJson, String objectInstanceId) {
      Map<String, Object> actionProperties = null;
      Gson gson = new Gson();
      Type payloadType = new TypeToken<Map<String, Object>>(){}.getType();

      if(actionPropertiesJson != null && !actionPropertiesJson.isEmpty()) {
         actionProperties = gson.fromJson(actionPropertiesJson, payloadType);
      }
      Map<String, Object> objectProperties = gson.fromJson(objectPropertiesJson, payloadType);

      return postAction(actionId, actionProperties, objectTypeId, objectProperties, objectInstanceId);
   }

   /**
    * Post an Open Graph action to the Carrot service which will create a new object or reuse an existing created object.
    *
    * @param actionId the Carrot action id.
    * @param actionProperties the properties to be sent along with the Carrot action.
    * @param objectTypeId the object id of the Carrot object type to create.
    * @param objectProperties the properties for the new object.
    * @param objectInstanceId the object instance id of the Carrot object to create or re-use.
    * @return <code>true if the action was cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postAction(String actionId, Map<String, Object> actionProperties,
      String objectTypeId, Map<String, Object> objectProperties, String objectInstanceId) {
      if(objectProperties == null) {
         Log.e(LOG_TAG, "objectProperties must not be null when calling postAction to create a new object.");
         return false;
      }

      String[] requiredObjectProperties = {"title", "image", "description"};
      for(String key : requiredObjectProperties) {
         if(!objectProperties.containsKey(key)) {
            Log.e(LOG_TAG, "objectProperties must contain a value for '" + key + "'");
            return false;
         }
      }

      HashMap<String, Object> fullObjectProperties = new HashMap<String, Object>(objectProperties);
      fullObjectProperties.put("object_type", objectTypeId);

      // TODO (v2): Support image uploading
      fullObjectProperties.put("image_url", fullObjectProperties.remove("image"));

      HashMap<String, Object> payload = new HashMap<String, Object>();
      payload.put("action_id", actionId);
      payload.put("object_properties", fullObjectProperties);
      if(objectInstanceId != null && !objectInstanceId.isEmpty()) {
        payload.put("object_instance_id", objectInstanceId);
      }
      if(actionProperties != null) {
         payload.put("action_properties", actionProperties);
      }
      return mCarrotCache.addRequest("/me/actions.json", payload);
   }

   /**
    * Check to see if your {@link Activity} has the permissions required by Carrot.
    * <p>
    * {@link Carrot} requires the {@link android.Manifest.permission.INTERNET} permission.
    *
    * @param activity the <code>Activity</code> for your application.
    * @return <code>true</code> if your <code>Activity</code> has the permissions {@link Carrot} requires;
    *         <code>false</code> otherwise.
    */
   public static boolean hasRequiredPermissions(Activity activity) {
      boolean ret = true;
      String packageName = activity.getPackageName();
      PackageManager pkgMan = activity.getPackageManager();

      String[] requiredRermissions = {
         android.Manifest.permission.INTERNET,
      };

      String[] suggestedPermissions = {
      };

      for(String permission : requiredRermissions) {
         if(pkgMan.checkPermission(permission, packageName) != PackageManager.PERMISSION_GRANTED) {
            Log.e(LOG_TAG, "Required permission: '" + permission + "' not granted. Please add this to AndroidManifest.xml");
            ret = false;
         }
      }

      for(String permission : suggestedPermissions) {
         if(pkgMan.checkPermission(permission, packageName) != PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_TAG, "Suggested permission: '" + permission + "' not granted.");
         }
      }

      return ret;
   }

   /**
    * Handler class for notification of Carrot events.
    */
   public interface Handler {
      /**
       * The authentication status of the user has changed.
       *
       * @param authStatus the updated authentication status of the current Carrot user.
       */
      void authenticationStatusChanged(int authStatus);
   }

   /**
    * Assign a handler for Carrot events.
    *
    * @param handler the handler you wish to be notified when Carrot events occur.
    */
   public void setHandler(Handler handler) {
      mHandler = handler;
      mLastAuthStatusReported = StatusUndetermined;
      setStatus(mStatus);
   }

   /**************************************************************************/

   protected interface RequestCallback {
      void requestComplete(int responseCode, String responseBody);
   }

   protected void finalize() throws Throwable {
      try {
         deactivateApp(null);
      }
      finally {
         super.finalize();
      }
   }

   boolean updateAuthenticationStatus(int httpStatus) {
      boolean ret = true;
      switch(httpStatus) {
         case HttpsURLConnection.HTTP_OK:
         case HttpsURLConnection.HTTP_CREATED: {
            setStatus(StatusReady);
            break;
         }
         case HttpsURLConnection.HTTP_UNAUTHORIZED: {
            setStatus(StatusReadOnly);
            break;
         }
         case HttpsURLConnection.HTTP_BAD_METHOD: {
            setStatus(StatusNotAuthorized);
            break;
         }
         default: {
            ret = false;
         }
      }
      return ret;
   }

   void setStatus(int status) {
      mStatus = status;
      if(mStatus == StatusReady) {
         mCarrotCache.start();
      }
      else {
         mCarrotCache.stop();
      }

      if(mLastAuthStatusReported != mStatus && mHandler != null) {
         mLastAuthStatusReported = mStatus;
         mHandler.authenticationStatusChanged(mStatus);
      }
   }

   String getUserId() {
      return mUserId;
   }

   String getHostname(String endpoint) {
      if(endpoint.equals("/install.json")) {
         return mMetricsHostname;
      }
      else if(endpoint.equals("/users.json")) {
         return mAuthHostname;
      }

      return mPostHostname;
   }

   String getAppId() {
      return mAppId;
   }

   String getAppSecret() {
      return mAppSecret;
   }

   Activity getHostActivity() {
      return mHostActivity;
   }

   private void servicesDiscovery() {
      mExecutorService.submit(new Runnable() {
         public void run() {
            HttpsURLConnection connection = null;
            try {
               Gson gson = new Gson();
               String versionName = mHostActivity.getPackageManager().getPackageInfo(mHostActivity.getPackageName(), 0).versionName;
               String queryString = "?sdk_version=" + URLEncoder.encode(SDKVersion, "UTF-8") +
                  "&sdk_platform=" + URLEncoder.encode("android_" + android.os.Build.VERSION.RELEASE, "UTF-8") +
                  "&game_id=" + URLEncoder.encode(mAppId, "UTF-8") +
                  "&app_version=" + URLEncoder.encode(versionName, "UTF-8");
               URL url = new URL("https", CARROT_SERVICES_HOSTNAME, "/services.json" + queryString);
               connection = (HttpsURLConnection)url.openConnection();
               connection.setRequestProperty("Accept-Charset", "UTF-8");
               connection.setUseCaches(false);

               // Get Response
               InputStream is = null;
               if(connection.getResponseCode() < 400) {
                  is = connection.getInputStream();
               }
               else {
                  is = connection.getErrorStream();
               }
               BufferedReader rd = new BufferedReader(new InputStreamReader(is));
               String line;
               StringBuffer response = new StringBuffer();
               while((line = rd.readLine()) != null) {
                 response.append(line);
                 response.append('\r');
               }
               rd.close();

               Log.d(LOG_TAG, response.toString());

               // Read the JSON
               if(connection.getResponseCode() < 400) {
                  Type payloadType = new TypeToken<Map<String, String>>(){}.getType();
                  Map<String, String> services = gson.fromJson(response.toString(), payloadType);

                  mAuthHostname = services.get("auth");
                  mMetricsHostname = services.get("metrics");
                  mPostHostname = services.get("post");

                  Log.d(LOG_TAG, services.get("auth"));
                  Log.d(LOG_TAG, services.get("post"));
                  Log.d(LOG_TAG, services.get("metrics"));
               }
               else {
                  // Error
               }
            }
            catch(Exception e) {
               Log.e(LOG_TAG, Log.getStackTraceString(e));
            }

            finally {
               connection.disconnect();
               connection = null;
            }
         }
      });
   }

   private void validateUser() {
      mExecutorService.submit(new Runnable() {
         public void run() {
            HttpsURLConnection connection = null;
            try {
               if(mAccessToken != null && !mAccessToken.isEmpty())
               {
                  String postBody = "api_key=" + getUserId() + "&access_token=" + mAccessToken;

                  URL url = new URL("https", getHostname("/users.json"), "/games/" + mAppId + "/users.json");
                  connection = (HttpsURLConnection)url.openConnection();
                  connection.setRequestMethod("POST");
                  connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                  connection.setRequestProperty("Content-Length",
                     "" +  Integer.toString(postBody.getBytes().length));
                  connection.setUseCaches(false);
                  connection.setDoOutput(true);

                  // Send request
                  DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                  wr.writeBytes(postBody);
                  wr.flush();
                  wr.close();

                  if(!updateAuthenticationStatus(connection.getResponseCode())) {
                     Log.e(LOG_TAG, "Unknown error adding Carrot user (" + connection.getResponseCode() + ").");
                     setStatus(StatusUndetermined);
                  }
               }
               else
               {
                  setStatus(StatusUndetermined);
               }
            }
            catch(IOException e) {
               // This is probably a 401
               if(!updateAuthenticationStatus(401)) {
                  Log.e(LOG_TAG, Log.getStackTraceString(e));
                  setStatus(StatusUndetermined);
               }
            }
            catch(Exception e) {
               Log.e(LOG_TAG, Log.getStackTraceString(e));
               setStatus(StatusUndetermined);
            }
            finally {
               connection.disconnect();
               connection = null;
            }
         }
      });
   }

   public static final String LOG_TAG = "Carrot";
   private static final String CARROT_SERVICES_HOSTNAME = "services.gocarrot.com";

   private Activity mHostActivity;
   private String mAppId;
   private String mUserId;
   private String mAppSecret;
   private String mAccessToken;
   private String mAuthHostname;
   private String mPostHostname;
   private String mMetricsHostname;
   private int mStatus;
   private int mLastAuthStatusReported;
   private CarrotCache mCarrotCache;
   private ExecutorService mExecutorService;
   private Handler mHandler;
}
