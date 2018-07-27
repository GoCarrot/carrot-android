/* Teak -- Copyright (C) 2017 GoCarrot Inc.
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
package io.teak.sdk.io;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

public class DefaultHttpsRequest implements IHttpsRequest {
    @Override
    @SuppressWarnings("TryWithIdenticalCatches")
    public Response synchronousRequest(URL url, String requestBody) throws IOException {
        Response ret = Response.ERROR_RESPONSE;

        HttpsURLConnection connection = null;
        BufferedReader rd = null;

        try {
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
            rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            ret = new Response(connection.getResponseCode(), response.toString());
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
        } catch (IOException io_e) {
            // Ignored, Sentry issue 'TEAK-ANDROID-SDK-E2'
        } finally {
            if (rd != null) {
                try {
                    rd.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }

        return ret;
    }
}
