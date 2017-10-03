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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

import java.lang.reflect.Array;

import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;
import org.json.JSONArray;

import io.teak.sdk.Helpers._;

class Request implements Runnable {
    private final String endpoint;
    private final String hostname;
    protected final Map<String, Object> payload;
    private final Session session;
    private final String requestId;

    static Map<String, Object> dynamicCommonPayload = new HashMap<>();

    public Request(@NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session) {
        this(null, endpoint, payload, session);
    }

    public Request(@Nullable String hostname, @NonNull String endpoint, @NonNull Map<String, Object> payload, @NonNull Session session) {
        this.hostname = hostname;
        this.endpoint = endpoint;
        this.payload = payload;
        this.session = session;
        this.requestId = UUID.randomUUID().toString().replace("-", "");

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
                    Teak.log.e("request", "Value for key is null.", _.h("key", key));
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
            Teak.log.exception(e);
            return;
        }

        try {
            Teak.log.i("request.send", this.to_h());
            long startTime = System.nanoTime();

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

            Map<String, Object> h = this.to_h();
            h.remove("payload");
            h.put("response_time", (System.nanoTime() - startTime) / 1000000.0);
            try {
                h.put("payload", Helpers.jsonToMap(new JSONObject(response.toString())));
            } catch (Exception ignored) {
            }
            Teak.log.i("request.reply", h);

            // For extending classes
            done(connection.getResponseCode(), response.toString());
        } catch (UnknownHostException uh_e) {
            // Ignored, Sentry issue 'TEAK-SDK-F', 'TEAK-SDK-M', 'TEAK-SDK-X'
        } catch (SocketTimeoutException st_e) {
            // Ignored, Sentry issue 'TEAK-SDK-11'
        } catch (ConnectException c_e) {
            // Ignored, Sentry issue 'TEAK-SDK-Q', 'TEAK-SDK-K', 'TEAK-SDK-W', 'TEAK-SDK-V',
            //      'TEAK-SDK-J', 'TEAK-SDK-P'
        } catch (HttpRetryException http_e) {
            // Ignored, Sentry issue 'TEAK-SDK-N'
        } catch (SSLException ssl_e) {
            // Ignored, Sentry issue 'TEAK-SDK-T'
        } catch (SocketException sock_e) {
            // Ignored, Sentry issue 'TEAK-SDK-S'
        } catch (Exception e) {
            Teak.log.exception(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected void done(int responseCode, String responseBody) {
    }

    public Map<String, Object> to_h() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("request_id", this.requestId);
        map.put("hostname", this.hostname);
        map.put("endpoint", this.endpoint);
        map.put("session", Integer.toHexString(this.session.hashCode()));
        map.put("payload", this.payload);
        return map;
    }

    @Override
    public String toString() {
        try {
            return String.format(Locale.US, "%s: %s", super.toString(), Teak.formatJSONForLogging(new JSONObject(this.to_h())));
        } catch (Exception ignored) {
            return super.toString();
        }
    }
}
