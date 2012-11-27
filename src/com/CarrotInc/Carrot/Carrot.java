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

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import com.facebook.android.carrot.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.DataOutputStream;
import java.lang.reflect.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.HashMap;
import java.util.Map;
import org.OpenUDID.*;

/**
 * Allows you to interact with the Carrot service from your Android application.
 * <p>
 * Once a Carrot instance has been constructed, any calls to the following methods will
 * will be cached on the client and sent to the Carrot service once authentication has
 * occurred.
 * <ul>
 * <li>{@link #postAchievement(string) postAchievement}
 * <li>{@link #postHighScore(int) postHighScore}
 * <li>{@link #postAction(string,string) postAction} (and variants)
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

   /**
    * Constructor for a new Carrot instance that uses the default Carrot server hostname.
    *
    * @param activity   the {@link Activity} to which this Carrot instance is attached.
    * @param appId      the Facebook Application Id for your application.
    * @param appSecret  the Carrot Application Secret for your application.
    */
   public Carrot(Activity activity, String appId, String appSecret) {
      this(activity, appId, appSecret, null);
   }

   /**
    * Constructor for a new Carrot instance specifying a Carrot server hostname.
    *
    * @param activity   the {@link Activity} to which this Carrot instance is attached.
    * @param appId      the Facebook Application Id for your application.
    * @param appSecret  the Carrot Application Secret for your application.
    * @param hostname   the hostname for the Carrot server.
    */
   public Carrot(Activity activity, String appId, String appSecret, String hostname) {
      this(activity, appId, appSecret, null, null);
   }

   /**
    * Constructor for a new Carrot instance specifying a Carrot server hostname and debug UDID.
    *
    * @param activity   the {@link Activity} to which this Carrot instance is attached.
    * @param appId      the Facebook Application Id for your application.
    * @param appSecret  the Carrot Application Secret for your application.
    * @param hostname   the hostname for the Carrot server, or null for default.
    * @param debugUDID  the debug UDID to assign, or null for normal functionality.
    */
   public Carrot(Activity activity, String appId, String appSecret, String hostname, String debugUDID) {
      mAppId = appId;
      mAppSecret = appSecret;
      mHostname = (hostname != null  && !hostname.isEmpty() ? hostname : CARROT_HOSTNAME);
      mStatus = Carrot.StatusUndetermined;
      mDebugUDID = debugUDID;

      mExecutorService = Executors.newSingleThreadExecutor();

      setActivity(activity);
   }

   protected void finalize() throws Throwable {
      try {
         close();
      }
      finally {
         super.finalize();
      }
   }

   /**
    * Closes the request cache and stops the request threads for this Carrot instance.
    * <p>
    * Once this method has been called, you should make no further calls to the instance.
    */
   public void close() {
      mExecutorService.shutdownNow();
      if(mCarrotCache != null) {
         mCarrotCache.close();
         mCarrotCache = null;
      }
   }

   public Facebook getFacebook() {
      if(mFacebook == null) {
         mFacebook = new Facebook(mAppId);
      }
      return mFacebook;
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
    * Change the {@link Activity} assigned to this instance.
    * <p>
    * The <code>Activity</code> passed in to the Carrot constructor is assigned
    * to the instance upon creation. If the <code>Activity</code> changes, call this function
    * to update the instance.
    *
    * @param activity the new <code>Activity</code> to which this instance should attach.
    */
   public void setActivity(Activity activity) {
      mHostActivity = activity;
      OpenUDID_manager.sync(activity);
      mCarrotCache = new CarrotCache(this);

      if(!hasRequiredPermissions(activity)) {
         Log.e(LOG_TAG, "Carrot in offline mode until require permissions are added.");
      }

      if(!mCarrotCache.open()) {
         Log.e(LOG_TAG, "Failed to create Carrot cache.");
      }
      else {
         Log.d(LOG_TAG, "Attached to android.app.Activity: " + mHostActivity);
         checkUDID();
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
         checkUDID();
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
    * Post an achievement to the Carrot service.
    *
    * @param achievementId the Carrot achivement id of the achievement to post.
    * @return <code>true</code> if the achievement was cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postAchievement(String achievementId) {
      HashMap<String, Object> payload = new HashMap<String, Object>();
      payload.put("achievement_id", achievementId);
      return mCarrotCache.addRequest("/me/achievements.json", payload);
   }

   /**
    * Post a high score to the Carrot service.
    *
    * @param score the high score value to post.
    * @return <code>true</code> if the score was > 0, cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postHighScore(int score) {
      return postHighScore(score, null);
   }

   /**
    * Post a high score to the Carrot service to a specific leaderboard.
    *
    * @param score the high score value to post.
    * @param leaderboardId the leaderboard to which the score should be posted.
    * @return <code>true</code> if the score was > 0, cached successfully and will be sent
    *         to the Carrot service when possible; <code>false</code> otherwise.
    */
   public boolean postHighScore(int score, String leaderboardId) {
      HashMap<String, Object> payload = new HashMap<String, Object>();
      payload.put("value", new Integer(score));
      if(leaderboardId != null && !leaderboardId.isEmpty()) payload.put("leaderboard_id", leaderboardId);
      return score > 0 && mCarrotCache.addRequest("/me/scores.json", payload);
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
    * Perform the Facebook authentication needed for Carrot.
    *
    * @return <code>true</code> if the Facebook authentication has been started successfully;
    *         <code>false</code> otherwise.
    */
   public boolean doFacebookAuth() {
      if(!getFacebook().isSessionValid()) {
         try {
            Intent intent = new Intent(mHostActivity, CarrotFacebookAuthActivity.class);
            intent.putExtra("appId", mAppId);
            intent.putExtra("appSecret", mAppSecret);
            mHostActivity.startActivityForResult(intent, 0);
         }
         catch(Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));
            return false;
         }
      }
      else {
         setAccessToken(getFacebook().getAccessToken());
      }
      return true;
   }

   /**
    * Handler class for notification of Carrot events.
    */
   public interface Handler {
      /**
       * The authentication status of the user has changed.
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
   }

   public void setUnityHandler(final String delegateObjectName) {
      try {
         // com.unity3d.player.UnityPlayer.UnitySendMessage
         Class[] params = {String.class, String.class, String.class};
         Class c = Class.forName("com.unity3d.player.UnityPlayer");
         final Method m = c.getDeclaredMethod("UnitySendMessage", params);

         setHandler(new Carrot.Handler() {
            @Override
            public void authenticationStatusChanged(int authStatus) {
               Object[] callParams = {delegateObjectName, "authenticationStatusChanged", Integer.toString(authStatus)};
               try {
                  m.invoke(null, callParams);
               }
               catch(Exception e) {
                  Log.e(LOG_TAG, Log.getStackTraceString(e));
               }
            }
         });
      }
      catch(Exception e) {
         Log.e(LOG_TAG, Log.getStackTraceString(e));
      }
   }

   public void authorizeCallback(int requestCode, int resultCode, Intent data) {
      if(mFacebook != null) {
         mFacebook.authorizeCallback(requestCode, resultCode, data);
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
         case HttpsURLConnection.HTTP_FORBIDDEN: {
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
      boolean authStatusChanged = (mStatus != status);
      mStatus = status;
      if(mStatus == StatusReady) {
         mCarrotCache.start();
      }
      else {
         mCarrotCache.stop();
      }

      if(authStatusChanged && mHandler != null) {
         mHandler.authenticationStatusChanged(mStatus);
      }
   }

   String getUDID() {
      if(mDebugUDID != null && !mDebugUDID.isEmpty()) {
         return mDebugUDID;
      }

      String ret = "";
      if(OpenUDID_manager.isInitialized()) {
         ret = OpenUDID_manager.getOpenUDID();
      }
      return ret;
   }

   String getHostname() {
      return mHostname;
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

   private void checkUDID() {
      mExecutorService.submit(new Runnable() {
         public void run() {
            HttpsURLConnection connection = null;
            try {
               while(!OpenUDID_manager.isInitialized()) {
                  java.lang.Thread.sleep(10);
               }

               URL url = new URL("https", mHostname, "/games/" + mAppId + "/users/" + getUDID() + ".json");
               Log.e(LOG_TAG, "Checking Carrot UDID " + getUDID());
               connection = (HttpsURLConnection)url.openConnection();
               connection.setRequestMethod("GET");

               switch(connection.getResponseCode())
               {
                  case HttpsURLConnection.HTTP_NOT_FOUND:
                  {
                     addUser();
                     break;
                  }
                  default:
                  {
                     if(!updateAuthenticationStatus(connection.getResponseCode())) {
                        Log.e(LOG_TAG, "Unknown error verifying Carrot user (" + connection.getResponseCode() + ").");
                        setStatus(StatusUndetermined);
                     }
                  }
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

   private void addUser() {
      mExecutorService.submit(new Runnable() {
         public void run() {
            HttpsURLConnection connection = null;
            try {
               while(!OpenUDID_manager.isInitialized()) {
                  java.lang.Thread.sleep(10);
               }

               if(mAccessToken != null && !mAccessToken.isEmpty())
               {
                  String postBody = "api_key=" + getUDID() + "&access_token=" + mAccessToken;

                  URL url = new URL("https", mHostname, "/games/" + mAppId + "/users.json");
                  connection = (HttpsURLConnection)url.openConnection();
                  connection.setRequestMethod("POST");
                  connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                  connection.setRequestProperty("Content-Length",
                     "" +  Integer.toString(postBody.getBytes().length));
                  connection.setUseCaches (false);
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
   private static final String CARROT_HOSTNAME = "gocarrot.com";

   private Activity mHostActivity;
   private String mAppId;
   private String mAppSecret;
   private String mHostname;
   private String mAccessToken;
   private String mDebugUDID;
   private int mStatus;
   private CarrotCache mCarrotCache;
   private ExecutorService mExecutorService;
   private Handler mHandler;
   private Facebook mFacebook;
}
