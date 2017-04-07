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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.net.ssl.HttpsURLConnection;

import java.lang.reflect.Array;

import java.net.URL;
import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.concurrent.ExecutionException;

class Request implements Runnable {
    private static final String LOG_TAG = "Teak.Request";

    private final String endpoint;
    private final String hostname;
    protected final Map<String, Object> payload;
    private final Session session;

    static Map<String, Object> dynamicCommonPayload = new HashMap<>();

    public Request(@NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session) {
        this(null, endpoint, payload, session);
    }

    public Request(@Nullable String hostname, @NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session) {
        this.hostname = hostname;
        this.endpoint = endpoint;
        this.payload = payload;
        this.session = session;

        // Add common data
        if (session.userId() != null) {
            payload.put("api_key", session.userId());
        }
        payload.put("request_date", new Date().getTime() / 1000); // Milliseconds -> Seconds
        payload.put("game_id", session.appConfiguration.appId);
        payload.put("sdk_version", Teak.SDKVersion);
        payload.put("sdk_platform", session.deviceConfiguration.platformString);
        payload.put("app_version", String.valueOf(session.appConfiguration.appVersion));
        payload.put("bundle_id", session.appConfiguration.bundleId);
        if (session.appConfiguration.installerPackage != null) {
            payload.put("appstore_name", session.appConfiguration.installerPackage);
        }
        if (session.deviceConfiguration.deviceId != null) {
            payload.put("device_id", session.deviceConfiguration.deviceId);
        }
        // Add device information to Request common payload
        payload.put("device_manufacturer", session.deviceConfiguration.deviceManufacturer);
        payload.put("device_model", session.deviceConfiguration.deviceModel);
        payload.put("device_fallback", session.deviceConfiguration.deviceFallback);

        payload.putAll(Request.dynamicCommonPayload); // TODO: I don't like this, but it seems ok for now
    }

    @Override
    public void run() {
        HttpsURLConnection connection = null;
        SecretKeySpec keySpec = new SecretKeySpec(this.session.appConfiguration.apiKey.getBytes(), "HmacSHA256");
        String requestBody;

        String hostnameForEndpoint = this.hostname;
        if (hostnameForEndpoint == null) {
            hostnameForEndpoint = this.session.remoteConfiguration.getHostnameForEndpoint(this.endpoint);
        }

        try {
            ArrayList<String> payloadKeys = new ArrayList<>(this.payload.keySet());
            Collections.sort(payloadKeys);

            StringBuilder builder = new StringBuilder();
            for (String key : payloadKeys) {
                Object value = this.payload.get(key);
                if (value != null) {
                    String valueString;
                    if (value instanceof Map) {
                        valueString = new JSONObject((Map) value).toString();
                    } else if (value instanceof Array) {
                        valueString = new JSONArray(Collections.singletonList(value)).toString();
                    } else if (value instanceof Collection) {
                        valueString = new JSONArray((Collection) value).toString();
                    } else {
                        valueString = value.toString();
                    }
                    builder.append(key).append("=").append(valueString).append("&");
                } else {
                    Log.e(LOG_TAG, "Value for key: " + key + " is null.");
                }
            }
            builder.deleteCharAt(builder.length() - 1);

            String stringToSign = "POST\n" + hostnameForEndpoint + "\n" + this.endpoint + "\n" + builder.toString();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(stringToSign.getBytes());

            builder = new StringBuilder();
            for (String key : payloadKeys) {
                Object value = this.payload.get(key);
                String valueString;
                if (value instanceof Map) {
                    valueString = new JSONObject((Map) value).toString();
                } else if (value instanceof Array) {
                    valueString = new JSONArray(Collections.singletonList(value)).toString();
                } else if (value instanceof Collection) {
                    valueString = new JSONArray((Collection) value).toString();
                } else {
                    valueString = value.toString();
                }
                builder.append(key).append("=").append(URLEncoder.encode(valueString, "UTF-8")).append("&");
            }
            builder.append("sig=").append(URLEncoder.encode(Base64.encodeToString(result, Base64.NO_WRAP), "UTF-8"));

            requestBody = builder.toString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error signing payload: " + Log.getStackTraceString(e));
            return;
        }

        try {
            if (Teak.isDebug) {
                Log.d(LOG_TAG, "Submitting request to '" + this.endpoint + "': " + Teak.formatJSONForLogging(new JSONObject(this.payload)));
            }

            URL url = new URL("https://" + hostnameForEndpoint + this.endpoint);
            connection = (HttpsURLConnection) url.openConnection();

            connection.setRequestProperty("Accept-Charset", "UTF-8");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length",
                    "" + Integer.toString(requestBody.getBytes().length));

            // Send request
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(requestBody);
            wr.flush();
            wr.close();

            // Get Response
            InputStream is;
            if (connection.getResponseCode() < 400) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
            }
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();

            if (Teak.isDebug) {
                String responseText = response.toString();
                try {
                    responseText = Teak.formatJSONForLogging(new JSONObject(response.toString()));
                } catch (Exception ignored) {
                }
                Log.d(LOG_TAG, "Reply from '" + this.endpoint + "': " + responseText);
            }

            // For extending classes
            done(connection.getResponseCode(), response.toString());
        } catch (Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected void done(int responseCode, String responseBody) {
    }
}
