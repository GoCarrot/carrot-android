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

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
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
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Allows you to interact with the Teak service from your Android application.
 */
public class Teak {
    public static final int StatusNotAuthorized = -1;
    public static final int StatusUndetermined = 0;
    public static final int StatusReadOnly = 1;
    public static final int StatusReady = 2;

    public static final String SDKVersion = "1.2";

    /**
     * Get the authentication status of the Teak user.
     *
     * @return the authentication status of the Teak user.
     */
    public static int getStatus() {
        return mStatus;
    }

    /**
     * Initialize Teak and attach the {@link Activity}.
     * <p/>
     * Call this function from the <code>onCreate()</code> function of your activity.
     *
     * @param <code>Activity</code> of your app.
     */
    public static void createApp(Activity activity) {
        if (!hasRequiredPermissions(activity)) {
            Log.e(LOG_TAG, "Teak in offline mode until require permissions are added.");
        }

        mHostActivity = activity;

        // Get the API Key
        if (mAPIKey == null) {
            mAPIKey = (String) getBuildConfigValue(mHostActivity, TEAK_API_KEY);
            if (mAPIKey == null) {
                Log.e(LOG_TAG, "Failed to find BuildConfig." + TEAK_API_KEY);
            }
        }

        // Get the App Id
        if (mAppId == null) {
            mAppId = (String) getBuildConfigValue(mHostActivity, TEAK_APP_ID);
            if (mAppId == null) {
                Log.e(LOG_TAG, "Failed to find BuildConfig." + TEAK_APP_ID);
            }
        }

        if (mTeakCache == null) {
            mTeakCache = new TeakCache();
            if (!mTeakCache.open()) {
                Log.e(LOG_TAG, "Failed to create Teak cache.");
            }
        }

        // Activate
        activateApp(mHostActivity);

        // Happy-path logging
        Boolean isDebug = (Boolean)getBuildConfigValue(mHostActivity, "DEBUG");
        mIsDebug = (isDebug == Boolean.TRUE);
        if(mIsDebug) {
            Log.d(LOG_TAG, "Teak attached to android.app.Activity: " + mHostActivity);
        }
    }

    /**
     * Signify that the app is active.
     * <p/>
     * Call this function from the <code>onResume()</code> function of your activity.
     *
     * @param <code>Activity</code> of your app.
     */
    public static void activateApp(Activity activity) {
        if (mExecutorService == null) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }

        // Services discovery
        if(mAuthHostname == null || mPostHostname == null || mMetricsHostname == null) {
            servicesDiscovery();
        }
    }

    /**
     * Stops the request threads for this Teak instance.
     * <p/>
     * Call this function from the <code>onPause()</code> function of your activity.
     *
     * @param <code>Activity</code> of your app.
     */
    public static void deactivateApp(Activity activity) {
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
            mExecutorService = null;
        }
    }

    /**
     * Shutdown Teak.
     * <p/>
     * Call this function from the <code>onDestroy()</code> function of your activity.
     *
     * @param <code>Activity</code> of your app.
     */
    public static void destroyApp(Activity activity) {
        if (mTeakCache != null) {
            mTeakCache.close();
            mTeakCache = null;
        }
    }

    /**
     * Assign a Facebook user token to the current user.
     *
     * @param accessToken the Facebook access token for the current user.
     */
    public static void setAccessToken(String accessToken) {
        internal_validateUser(accessToken);
    }

    /**
     * Validate the current user.
     *
     * @param userId the unique id for the current user.
     */
    public static void validateUser(String userId) {
        mUserId = userId;
        internal_validateUser(null);
    }

    /**
     * Assign a push notification key to the current Teak user.
     * <p/>
     * For Urban Airship, the device key can be obtained with:
     * <code>PushManager.shared().getPreferences().getPushId()</code>
     *
     * @param devicePushKey the push notification key for the current device.
     */
    public static void setDevicePushKey(String devicePushKey) {
        HashMap<String, Object> payload = new HashMap<String, Object>();
        payload.put("push_key", devicePushKey);
        payload.put("device_type", "android");
        mTeakCache.addRequest("/me/devices.json", payload);
    }

    /**
     * Report a purchase event.
     *
     * @param amount           Amount spent on product.
     * @param currencyCode     ISO 4217 currency code for amount.
     * @param purchaseId       Purchase receipt id.
     * @param purchaseTime     The time the product was purchased, in milliseconds since the epoch (Jan 1, 1970).
     */
    public static void trackPurchase(float amount, String currencyCode, String purchaseId, long purchaseTime) {
        HashMap<String, Object> payload = new HashMap<String, Object>();

        // Parsnip requests user app_id/user_id not game_id/api_key
        payload.put("app_id", getAppId());
        payload.put("user_id", getUserId());

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        payload.put("happened_at", df.format(new Date(purchaseTime)));

        payload.put("amount", new Integer((int)(amount * 100)));
        payload.put("currency_code", currencyCode);
        payload.put("platform_id", purchaseId);
        mTeakCache.addRequest("/purchase.json", payload);
    }

    /**
     * Post an Open Graph action with an existing object to the Teak service.
     *
     * @param actionId         the Teak action id.
     * @param objectInstanceId the instance id of the Teak object.
     * @return <code>true if the action was cached successfully and will be sent
     * to the Teak service when possible; <code>false</code> otherwise.
     */
    public static boolean postAction(String actionId, String objectInstanceId) {
        return postAction(actionId, null, objectInstanceId);
    }

    /**
     * Post an Open Graph action with an existing object to the Teak service.
     *
     * @param actionId             the Teak action id.
     * @param actionPropertiesJson the properties to be sent along with the Teak action encoded to JSON.
     * @param objectInstanceId     the instance id of the Teak object.
     * @return <code>true if the action was cached successfully and will be sent
     * to the Teak service when possible; <code>false</code> otherwise.
     */
    public static boolean postJsonAction(String actionId, String actionPropertiesJson, String objectInstanceId) {
        Map<String, Object> actionProperties = null;

        if (actionPropertiesJson != null && !actionPropertiesJson.isEmpty()) {
            Gson gson = new Gson();
            Type payloadType = new TypeToken<Map<String, Object>>() {
            }.getType();
            actionProperties = gson.fromJson(actionPropertiesJson, payloadType);
        }

        return postAction(actionId, actionProperties, objectInstanceId);
    }

    /**
     * Post an Open Graph action with an existing object to the Teak service.
     *
     * @param actionId         the Teak action id.
     * @param actionProperties the properties to be sent along with the Teak action.
     * @param objectInstanceId the instance id of the Teak object.
     * @return <code>true if the action was cached successfully and will be sent
     * to the Teak service when possible; <code>false</code> otherwise.
     */
    public static boolean postAction(String actionId, Map<String, Object> actionProperties,
                                     String objectInstanceId) {
        HashMap<String, Object> payload = new HashMap<String, Object>();
        payload.put("action_id", actionId);
        payload.put("object_instance_id", objectInstanceId);
        if (actionProperties != null) {
            payload.put("action_properties", actionProperties);
        }
        return mTeakCache.addRequest("/me/actions.json", payload);
    }

    /**
     * Post an Open Graph action to the Teak service which will create a new object.
     *
     * @param actionId         the Teak action id.
     * @param objectTypeId     the object id of the Teak object type to create.
     * @param objectProperties the properties for the new object.
     * @return <code>true if the action was cached successfully and will be sent
     * to the Teak service when possible; <code>false</code> otherwise.
     */
    public static boolean postAction(String actionId, String objectTypeId,
                                     Map<String, Object> objectProperties) {
        return postAction(actionId, null, objectTypeId, objectProperties, null);
    }

    /**
     * Post an Open Graph action to the Teak service which will create a new object or reuse an existing created object.
     *
     * @param actionId         the Teak action id.
     * @param objectTypeId     the object id of the Teak object type to create.
     * @param objectProperties the properties for the new object.
     * @param objectInstanceId the object instance id of the Teak object to create or re-use.
     * @return <code>true if the action was cached successfully and will be sent
     * to the Teak service when possible; <code>false</code> otherwise.
     */
    public static boolean postAction(String actionId, String objectTypeId,
                                     Map<String, Object> objectProperties, String objectInstanceId) {
        return postAction(actionId, null, objectTypeId, objectProperties, objectInstanceId);
    }

    /**
     * Post an Open Graph action to the Teak service which will create a new object or reuse an existing created object.
     *
     * @param actionId             the Teak action id.
     * @param actionPropertiesJson the properties to be sent along with the Teak action encoded to JSON.
     * @param objectTypeId         the object id of the Teak object type to create.
     * @param objectPropertiesJson the properties for the new object encoded as JSON.
     * @param objectInstanceId     the object instance id of the Teak object to create or re-use.
     * @return <code>true if the action was cached successfully and will be sent
     * to the Teak service when possible; <code>false</code> otherwise.
     */
    public static boolean postJsonAction(String actionId, String actionPropertiesJson, String objectTypeId,
                                         String objectPropertiesJson, String objectInstanceId) {
        Map<String, Object> actionProperties = null;
        Gson gson = new Gson();
        Type payloadType = new TypeToken<Map<String, Object>>() {
        }.getType();

        if (actionPropertiesJson != null && !actionPropertiesJson.isEmpty()) {
            actionProperties = gson.fromJson(actionPropertiesJson, payloadType);
        }
        Map<String, Object> objectProperties = gson.fromJson(objectPropertiesJson, payloadType);

        return postAction(actionId, actionProperties, objectTypeId, objectProperties, objectInstanceId);
    }

    /**
     * Post an Open Graph action to the Teak service which will create a new object or reuse an existing created object.
     *
     * @param actionId         the Teak action id.
     * @param actionProperties the properties to be sent along with the Teak action.
     * @param objectTypeId     the object id of the Teak object type to create.
     * @param objectProperties the properties for the new object.
     * @param objectInstanceId the object instance id of the Teak object to create or re-use.
     * @return <code>true if the action was cached successfully and will be sent
     * to the Teak service when possible; <code>false</code> otherwise.
     */
    public static boolean postAction(String actionId, Map<String, Object> actionProperties,
                                     String objectTypeId, Map<String, Object> objectProperties, String objectInstanceId) {
        if (objectProperties == null) {
            Log.e(LOG_TAG, "objectProperties must not be null when calling postAction to create a new object.");
            return false;
        }

        String[] requiredObjectProperties = {"title", "image", "description"};
        for (String key : requiredObjectProperties) {
            if (!objectProperties.containsKey(key)) {
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
        if (objectInstanceId != null && !objectInstanceId.isEmpty()) {
            payload.put("object_instance_id", objectInstanceId);
        }
        if (actionProperties != null) {
            payload.put("action_properties", actionProperties);
        }
        return mTeakCache.addRequest("/me/actions.json", payload);
    }

    /**
     * Check to see if your {@link Activity} has the permissions required by Teak.
     * <p/>
     * {@link Teak} requires the {@link android.Manifest.permission.INTERNET} permission.
     *
     * @param activity the <code>Activity</code> for your application.
     * @return <code>true</code> if your <code>Activity</code> has the permissions {@link Teak} requires;
     * <code>false</code> otherwise.
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

        for (String permission : requiredRermissions) {
            if (pkgMan.checkPermission(permission, packageName) != PackageManager.PERMISSION_GRANTED) {
                Log.e(LOG_TAG, "Required permission: '" + permission + "' not granted. Please add this to AndroidManifest.xml");
                ret = false;
            }
        }

        for (String permission : suggestedPermissions) {
            if (pkgMan.checkPermission(permission, packageName) != PackageManager.PERMISSION_GRANTED) {
                Log.d(LOG_TAG, "Suggested permission: '" + permission + "' not granted.");
            }
        }

        return ret;
    }

    /**
     * Handler class for notification of Teak events.
     */
    public interface Handler {
        /**
         * The authentication status of the user has changed.
         *
         * @param authStatus the updated authentication status of the current Teak user.
         */
        void authenticationStatusChanged(int authStatus);
    }

    /**
     * Assign a handler for Teak events.
     *
     * @param handler the handler you wish to be notified when Teak events occur.
     */
    public static void setHandler(Handler handler) {
        mHandler = handler;
        mLastAuthStatusReported = StatusUndetermined;
        setStatus(mStatus);
    }

    /**************************************************************************/

    protected interface RequestCallback {
        void requestComplete(int responseCode, String responseBody);
    }

    static boolean updateAuthenticationStatus(int httpStatus) {
        boolean ret = true;
        switch (httpStatus) {
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

    static void setStatus(int status) {
        mStatus = status;
        if (mStatus == StatusReady) {
            mTeakCache.start();
        } else {
            mTeakCache.stop();
        }

        if (mLastAuthStatusReported != mStatus && mHandler != null) {
            mLastAuthStatusReported = mStatus;
            mHandler.authenticationStatusChanged(mStatus);
        }
    }

    static String getUserId() {
        return mUserId;
    }

    static String getHostname(String endpoint) {
        if (endpoint.equals("/install.json") || endpoint.equals("/purchase.json")) {
            return mMetricsHostname;
        } else if (endpoint.equals("/users.json")) {
            return mAuthHostname;
        }

        return mPostHostname;
    }

    static String getAppId() {
        return mAppId;
    }

    static String getAPIKey() {
        return mAPIKey;
    }

    static Activity getHostActivity() {
        return mHostActivity;
    }

    static boolean isDebug() {
        return mIsDebug;
    }

    private static void servicesDiscovery() {
        mServicesDiscoveryFuture = new FutureTask<Boolean>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                HttpsURLConnection connection = null;
                Boolean ret = Boolean.FALSE;

                Gson gson = new Gson();
                String versionName = mHostActivity.getPackageManager().getPackageInfo(mHostActivity.getPackageName(), 0).versionName;
                String queryString = "?sdk_version=" + URLEncoder.encode(SDKVersion, "UTF-8") +
                        "&sdk_platform=" + URLEncoder.encode("android_" + android.os.Build.VERSION.RELEASE, "UTF-8") +
                        "&game_id=" + URLEncoder.encode(mAppId, "UTF-8") +
                        "&app_version=" + URLEncoder.encode(versionName, "UTF-8");
                URL url = new URL("https", TEAK_SERVICES_HOSTNAME, "/services.json" + queryString);
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestProperty("Accept-Charset", "UTF-8");
                connection.setUseCaches(false);

                // Get Response
                InputStream is = null;
                if (connection.getResponseCode() < 400) {
                    is = connection.getInputStream();
                } else {
                    is = connection.getErrorStream();
                }
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                String line;
                StringBuffer response = new StringBuffer();
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();

                Log.d(LOG_TAG, response.toString());

                // Read the JSON
                if (connection.getResponseCode() < 400) {
                    Type payloadType = new TypeToken<Map<String, String>>() {
                    }.getType();
                    Map<String, String> services = gson.fromJson(response.toString(), payloadType);

                    mAuthHostname = services.get("auth");
                    mMetricsHostname = services.get("metrics");
                    mPostHostname = services.get("post");

                    ret = Boolean.TRUE;
                } else {
                    throw new Exception("Error performing services discovery: " + connection.getResponseCode());
                }
                connection.disconnect();
                connection = null;

                return ret;
            }
        });
        mExecutorService.submit(mServicesDiscoveryFuture);
    }

    private static void internal_validateUser(final String accessToken) {
        try {
            if (mServicesDiscoveryFuture.get() == Boolean.FALSE) {
                Log.e(LOG_TAG, "Services discovery failed.");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Services discovery failed.");
        }

        mExecutorService.submit(new Runnable() {
            public void run() {
                HttpsURLConnection connection = null;
                try {
                    String versionName = mHostActivity.getPackageManager().getPackageInfo(mHostActivity.getPackageName(), 0).versionName;
                    String postBody = "api_key=" + URLEncoder.encode(getUserId(), "UTF-8")  +
                        "&sdk_version=" + URLEncoder.encode(SDKVersion, "UTF-8") +
                        "&sdk_platform=" + URLEncoder.encode("android_" + android.os.Build.VERSION.RELEASE, "UTF-8") +
                        "&game_id=" + URLEncoder.encode(mAppId, "UTF-8") +
                        "&app_version=" + URLEncoder.encode(versionName, "UTF-8");

                    if (accessToken != null) {
                        postBody += "&access_token=" + URLEncoder.encode(accessToken, "UTF-8");
                    }

                    URL url = new URL("https", getHostname("/users.json"), "/games/" + mAppId + "/users.json");
                    connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    connection.setRequestProperty("Content-Length",
                            "" + Integer.toString(postBody.getBytes().length));
                    connection.setUseCaches(false);
                    connection.setDoOutput(true);

                    // Send request
                    DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                    wr.writeBytes(postBody);
                    wr.flush();
                    wr.close();

                    if (!updateAuthenticationStatus(connection.getResponseCode())) {
                        Log.e(LOG_TAG, "Unknown error adding Teak user (" + connection.getResponseCode() + ").");
                        setStatus(StatusUndetermined);
                    }
                } catch (IOException e) {
                    // This is probably a 401
                    if (!updateAuthenticationStatus(401)) {
                        Log.e(LOG_TAG, Log.getStackTraceString(e));
                        setStatus(StatusUndetermined);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                    setStatus(StatusUndetermined);
                } finally {
                    connection.disconnect();
                    connection = null;
                }
            }
        });
    }

    protected static Object getBuildConfigValue(Context context, String fieldName) {
        try {
            Class<?> clazz = Class.forName(context.getPackageName() + ".BuildConfig");
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static final String LOG_TAG = "Teak";
    private static final String TEAK_SERVICES_HOSTNAME = "services.gocarrot.com";
    private static final String TEAK_API_KEY = "TEAK_API_KEY";
    private static final String TEAK_APP_ID = "TEAK_APP_ID";

    private static Activity mHostActivity;
    private static String mAppId;
    private static String mUserId;
    private static String mAPIKey;
    private static String mAuthHostname;
    private static String mPostHostname;
    private static String mMetricsHostname;
    private static boolean mIsDebug;
    private static FutureTask<Boolean> mServicesDiscoveryFuture;
    private static int mStatus = Teak.StatusUndetermined;
    private static int mLastAuthStatusReported;
    private static TeakCache mTeakCache;
    private static ExecutorService mExecutorService;
    private static Handler mHandler;
}
