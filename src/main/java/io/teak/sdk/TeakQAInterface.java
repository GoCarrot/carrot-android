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

import java.util.HashMap;

public abstract class TeakQAInterface {
    abstract public void identifyClient(String clientId);

    public void onCreate(HashMap<String, Object> extras) {
        reportEvent("lifecycle", "created", extras);
    }

    public void gcmIdAssigned(String gcmId) {
        HashMap<String, Object> extras = new HashMap<>();
        extras.put("gcm_push_key", gcmId);
        reportEvent("userdata", "push", extras);
    }

    public void settingsValidated(boolean valid, String gameNameOrError) {
        HashMap<String, Object> extras = new HashMap<>();
        if (valid) {
            extras.put("name", gameNameOrError);
            reportEvent("settings", "valid", extras);
        } else {
            extras.put("error", gameNameOrError);
            reportEvent("settings", "invalid", extras);
        }
    }

    abstract public void reportEvent(String eventType, String eventName, HashMap<String, Object> extras);
}
