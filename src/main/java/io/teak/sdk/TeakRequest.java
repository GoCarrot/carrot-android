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

import android.content.ContentValues;
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

class TeakRequest implements Runnable {
    protected String mEndpoint;
    protected String mMethod;
    protected Map<String, Object> mPayload;
    protected Teak.RequestCallback mCallback;

    public TeakRequest(String method, String endpoint,
                       Map<String, Object> payload, Teak.RequestCallback callback) {
        mEndpoint = endpoint;
        mPayload = payload;
        mMethod = method;
        mCallback = callback;
    }

    public void run() {
        HttpsURLConnection connection = null;
        SecretKeySpec keySpec = new SecretKeySpec(Teak.getAPIKey().getBytes(), "HmacSHA256");

        try {
            Gson gson = new Gson();
            HashMap<String, Object> requestBodyObject = new HashMap<String, Object>();
            if (mPayload != null) {
                requestBodyObject.putAll(mPayload);
            }
            requestBodyObject.put("api_key", Teak.getUserId());
            requestBodyObject.put("game_id", Teak.getAppId());

            ArrayList<String> payloadKeys = new ArrayList<String>(requestBodyObject.keySet());
            Collections.sort(payloadKeys);

            StringBuilder requestBody = new StringBuilder();
            for (String key : payloadKeys) {
                Object value = requestBodyObject.get(key);
                String valueString = null;
                if (!Map.class.isInstance(value) &&
                        !Array.class.isInstance(value) &&
                        !List.class.isInstance(value)) {
                    valueString = value.toString();
                } else {
                    valueString = gson.toJson(value);
                }
                requestBody.append(key + "=" + valueString + "&");
            }
            requestBody.deleteCharAt(requestBody.length() - 1);

            String stringToSign = mMethod + "\n" + Teak.getHostname(mEndpoint) + "\n" + mEndpoint + "\n" + requestBody.toString();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(stringToSign.getBytes());

            requestBody = new StringBuilder();
            for (String key : payloadKeys) {
                Object value = requestBodyObject.get(key);
                String valueString = null;
                if (!Map.class.isInstance(value) &&
                        !Array.class.isInstance(value) &&
                        !List.class.isInstance(value)) {
                    valueString = value.toString();
                } else {
                    valueString = gson.toJson(value);
                }
                requestBody.append(key + "=" + URLEncoder.encode(valueString, "UTF-8") + "&");
            }
            requestBody.append("sig=" + URLEncoder.encode(Base64.encodeToString(result, Base64.NO_WRAP), "UTF-8"));

            if (mMethod == "POST") {
                URL url = new URL("https://" + Teak.getHostname(mEndpoint) + mEndpoint);
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
                URL url = new URL("https://" + Teak.getHostname(mEndpoint) + mEndpoint + "?" + requestBody.toString());
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

            // Callback
            if (mCallback != null) {
                mCallback.requestComplete(connection.getResponseCode(), response.toString());
            }
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, Log.getStackTraceString(e));
        } finally {
            connection.disconnect();
            connection = null;
        }
    }
}
