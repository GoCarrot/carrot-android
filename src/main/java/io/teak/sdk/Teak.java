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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.ads.identifier.AdvertisingIdClient.Info;

import android.net.Uri;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Allows you to interact with the Teak service from your Android application.
 */
public class Teak {
    public static final String SDKVersion = "1.2";

    /**
     * Initialize Teak and attach the {@link Activity}.
     * <p/>
     * Call this function from the <code>onCreate()</code> function of your activity.
     *
     * @param <code>Activity</code> of your app.
     */
    public static void createApp(Activity activity) {
        mHostActivity = activity;

        Boolean isDebug = (Boolean) getBuildConfigValue(mHostActivity, "DEBUG");
        mIsDebug = (isDebug == Boolean.TRUE);

        if (!hasRequiredPermissions(activity)) {
            Log.e(LOG_TAG, "Teak in offline mode until require permissions are added.");
        }

        try {
            mAppVersionCode = mHostActivity.getPackageManager().getPackageInfo(mHostActivity.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            throw new RuntimeException("Could not get version code: " + e);
        }

        // Get the API Key
        if (mAPIKey == null) {
            mAPIKey = (String) getBuildConfigValue(mHostActivity, TEAK_API_KEY);
            if (mAPIKey == null) {
                throw new RuntimeException("Failed to find BuildConfig." + TEAK_API_KEY);
            }
        }

        // Get the App Id
        if (mAppId == null) {
            mAppId = (String) getBuildConfigValue(mHostActivity, TEAK_APP_ID);
            if (mAppId == null) {
                throw new RuntimeException("Failed to find BuildConfig." + TEAK_APP_ID);
            }
        }

        mExecutorService = Executors.newSingleThreadExecutor();

        // Grab the advertising id and such
        collectAdvertisingIdEtc();

        mTeakCache = new TeakCache();
        if (!mTeakCache.open()) {
            Log.e(LOG_TAG, "Failed to create Teak cache.");
        }

        // Happy-path logging
        if (mIsDebug) {
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
        // Re-check the advertising id and such
        collectAdvertisingIdEtc();

        if (isOnline()) {
            if (mIsDebug) {
                Log.d(LOG_TAG, "Online mode.");
            }
            // Services discovery
            if (mAuthHostname == null || mPostHostname == null || mMetricsHostname == null) {
                servicesDiscovery();
            }
            mTeakCache.start();
        } else {
            if (mIsDebug) {
                Log.d(LOG_TAG, "Offline mode.");
            }
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
        if (mTeakCache != null) {
            mTeakCache.stop();
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
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
            mExecutorService = null;
        }

        if (mTeakCache != null) {
            mTeakCache.close();
            mTeakCache = null;
        }
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
     * Validate the current user with a Facebook Access Token.
     *
     * @param userId        the unique id for the current user.
     * @param fbAccessToken the Facebook access token for the current user.
     */
    public static void validateUser(String userId, String fbAccessToken) {
        mUserId = userId;
        internal_validateUser(fbAccessToken);
    }

    /**
     * Report a purchase event.
     *
     * @param amount       Amount spent on product.
     * @param currencyCode ISO 4217 currency code for amount.
     * @param purchaseId   Purchase receipt id.
     * @param purchaseTime The time the product was purchased, in milliseconds since the epoch (Jan 1, 1970).
     */
    public static void trackPurchase(float amount, String currencyCode, String purchaseId, long purchaseTime) {
        HashMap<String, Object> payload = new HashMap<String, Object>();

        // Parsnip requests user app_id/user_id not game_id/api_key
        payload.put("app_id", getAppId());
        payload.put("user_id", getUserId());

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        payload.put("happened_at", df.format(new Date(purchaseTime)));

        payload.put("amount", new Integer((int) (amount * 100)));
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
                android.Manifest.permission.ACCESS_NETWORK_STATE,
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

    /**************************************************************************/

    protected interface RequestCallback {
        void requestComplete(int responseCode, String responseBody);
    }

    static String getUserId() {
        return mUserId;
    }

    static String getHostname(String endpoint) {
        if (endpoint.equals("/purchase.json")) {
            return mMetricsHostname;
        } else if (endpoint.endsWith("/users.json")) {
            return mAuthHostname;
        }

        return mPostHostname;
    }

    static boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) mHostActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
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

    static int getAppVersion() {
        return mAppVersionCode;
    }

    static boolean isDebug() {
        return mIsDebug;
    }

    private static void collectAdvertisingIdEtc() {
        int googlePlayStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mHostActivity);

        // Facebook Attribution Id
        mExecutorService.submit(new Runnable() {
            public void run() {
                try {
                    ContentResolver contentResolver = mHostActivity.getContentResolver();
                    Uri uri = Uri.parse("content://com.facebook.katana.provider.AttributionIdProvider");
                    String columnName = "aid";
                    String[] projection = {columnName};
                    Cursor cursor = contentResolver.query(uri, projection, null, null, null);

                    if (cursor != null) {
                        if (!cursor.moveToFirst()) {
                            cursor.close();
                        } else {
                            mFbAttributionId = cursor.getString(cursor.getColumnIndex(columnName));
                            cursor.close();
                        }
                    }

                    if (mIsDebug) {
                        Log.d(LOG_TAG, "Facebook Attribution Id: " + mFbAttributionId);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Couldn't get FB Attribution Id.");
                    if (mIsDebug) {
                        e.printStackTrace();
                    }
                }
            }
        });


        // Google Play Advertising Id
        if (googlePlayStatus == ConnectionResult.SUCCESS) {
            mExecutorService.submit(new Runnable() {
                public void run() {
                    try {
                        Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(mHostActivity);
                        mGooglePlayAdId = adInfo.getId();
                        mLimitAdTracking = adInfo.isLimitAdTrackingEnabled();

                        // Happy path
                        if (mIsDebug) {
                            Log.d(LOG_TAG, "Google Play Advertising Id: " + mGooglePlayAdId);
                            Log.d(LOG_TAG, String.format("Ad tracking limited: %b", mLimitAdTracking));
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Couldn't get Google Play Advertising Id.");
                        if (mIsDebug) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    private static void servicesDiscovery() {
        mExecutorService.submit(new Runnable() {
            public void run() {
                HttpsURLConnection connection = null;

                try {
                    Gson gson = new Gson();
                    String queryString = "?sdk_version=" + URLEncoder.encode(SDKVersion, "UTF-8") +
                            "&sdk_platform=" + URLEncoder.encode("android_" + android.os.Build.VERSION.RELEASE, "UTF-8") +
                            "&game_id=" + URLEncoder.encode(mAppId, "UTF-8") +
                            "&app_version=" + URLEncoder.encode(String.valueOf(mAppVersionCode), "UTF-8");
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

                        if (mIsDebug) {
                            Log.d(LOG_TAG, "Services discovery complete.");
                            Log.d(LOG_TAG, "Using auth host: " + mAuthHostname);
                            Log.d(LOG_TAG, "Using metrics host: " + mMetricsHostname);
                            Log.d(LOG_TAG, "Using post host: " + mPostHostname);
                        }
                    } else {
                        Log.e(LOG_TAG, "Error performing services discovery: " + connection.getResponseCode());
                    }
                } catch (Exception e) {

                } finally {
                    connection.disconnect();
                    connection = null;
                }
            }
        });
    }

    private static void internal_validateUser(final String accessToken) {
        mExecutorService.submit(new Runnable() {
            public void run() {
                HashMap<String, Object> payload = new HashMap<String, Object>();

                long foo = TimeUnit.HOURS.convert(TimeZone.getDefault().getRawOffset(), TimeUnit.MILLISECONDS);
                String tzOffset = (new Long(foo)).toString();
                payload.put("timezone", tzOffset);

                if (mIsDebug) {
                    Log.d(LOG_TAG, "Valdiating user: " + getUserId());
                    Log.d(LOG_TAG, "   Timezone:     " + tzOffset);
                }

                if (mFbAttributionId != null) {
                    payload.put("fb_attribution_id", mFbAttributionId);
                }

                if (mGooglePlayAdId != null) {
                    payload.put("android_ad_id", mGooglePlayAdId);
                    payload.put("android_limit_ad_tracking", mLimitAdTracking);
                }

                if (accessToken != null) {
                    payload.put("access_token", accessToken);
                }

                mTeakCache.addRequest("/games/" + mAppId + "/users.json", payload);
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
    private static String mFbAttributionId;
    private static String mGooglePlayAdId;
    private static int mAppVersionCode;
    private static Boolean mLimitAdTracking;
    private static boolean mIsDebug;
    private static TeakCache mTeakCache;
    private static ExecutorService mExecutorService;
}
