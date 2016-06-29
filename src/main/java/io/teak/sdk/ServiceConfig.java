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

import org.json.JSONObject;

import java.util.Locale;

class ServiceConfig {
    String hostname;
    String sdkSentryDsn = "";

    public void setConfig(JSONObject json) {
        this.hostname = json.optString("auth", "gocarrot.com");
        this.sdkSentryDsn = json.optString("sdk_sentry_dsn", "");
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "{\n  Hostname: %s\n   ReportSDKExceptions: %b\n}",
                this.hostname, this.sdkSentryDsn);
    }

    public String getHostname(String endpoint) {
        if (endpoint.equals("/notification_received")) {
            return "parsnip.gocarrot.com";
        }
        return this.hostname;
    }

    public String sdkSentryDSN() {
        return sdkSentryDsn;
    }
}
