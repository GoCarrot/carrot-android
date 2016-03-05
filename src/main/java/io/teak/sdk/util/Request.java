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
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.concurrent.ExecutionException;

class Request implements Runnable {
    protected String endpoint;
    protected String method;
    protected Map<String, Object> payload;

    public Request(String method, String endpoint, Map<String, Object> payload) {
        this.endpoint = endpoint;
        this.payload = payload;
        this.method = method;
    }

    protected void addCommonPayload(Map<String, Object> payload) throws InterruptedException, ExecutionException {
        payload.put("api_key", Teak.userId.get());
        payload.put("game_id", Teak.appId);
        payload.put("sdk_version", Teak.SDKVersion);
        payload.put("sdk_platform", "android_" + android.os.Build.VERSION.RELEASE);
        payload.put("app_version", String.valueOf(Teak.appVersion));
    }

    public void run() {
        HttpsURLConnection connection = null;
        SecretKeySpec keySpec = new SecretKeySpec(Teak.apiKey.getBytes(), "HmacSHA256");

        try {
            HashMap<String, Object> requestBodyObject = new HashMap<String, Object>();
            if (this.payload != null) {
                requestBodyObject.putAll(this.payload);
            }

            // Add common fields
            addCommonPayload(requestBodyObject);

            ArrayList<String> payloadKeys = new ArrayList<String>(requestBodyObject.keySet());
            Collections.sort(payloadKeys);

            StringBuilder requestBody = new StringBuilder();
            for (String key : payloadKeys) {
                Object value = requestBodyObject.get(key);
                if (value != null) {
                    String valueString = null;
                    if (value instanceof Map) {
                        valueString = new JSONObject((Map)value).toString();
                    } else if (value instanceof Array) {
                        valueString = new JSONArray(value).toString();
                    } else if (value instanceof Collection) {
                        valueString = new JSONArray((Collection)value).toString();
                    } else {
                        valueString = value.toString();
                    }
                    requestBody.append(key + "=" + valueString + "&");
                } else {
                    Log.e(Teak.LOG_TAG, "Value for key: " + key + " is NULL.");
                }
            }
            requestBody.deleteCharAt(requestBody.length() - 1);

            String stringToSign = this.method + "\n" + Teak.getHostname(this.endpoint) + "\n" + this.endpoint + "\n" + requestBody.toString();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(stringToSign.getBytes());

            requestBody = new StringBuilder();
            for (String key : payloadKeys) {
                Object value = requestBodyObject.get(key);
                String valueString = null;
                if (value instanceof Map) {
                    valueString = new JSONObject((Map)value).toString();
                } else if (value instanceof Array) {
                    valueString = new JSONArray(value).toString();
                } else if (value instanceof Collection) {
                    valueString = new JSONArray((Collection)value).toString();
                } else {
                    valueString = value.toString();
                }
                requestBody.append(key + "=" + URLEncoder.encode(valueString, "UTF-8") + "&");
            }
            requestBody.append("sig=" + URLEncoder.encode(Base64.encodeToString(result, Base64.NO_WRAP), "UTF-8"));

            if (this.method == "POST") {
                URL url = new URL("https://" + Teak.getHostname(this.endpoint) + this.endpoint);
                connection = (HttpsURLConnection) url.openConnection();

                connection.setRequestProperty("Accept-Charset", "UTF-8");
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Content-Length",
                        "" + Integer.toString(requestBody.toString().getBytes().length));

                // Send request
                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(requestBody.toString());
                wr.flush();
                wr.close();
            } else {
                URL url = new URL("https://" + Teak.getHostname(this.endpoint) + this.endpoint + "?" + requestBody.toString());
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestProperty("Accept-Charset", "UTF-8");
                connection.setUseCaches(false);
            }

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

            // For extending classes
            done(connection.getResponseCode(), response.toString());
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
        } finally {
            connection.disconnect();
            connection = null;
        }
    }

    protected void done(int responseCode, String responseBody) {}
}
