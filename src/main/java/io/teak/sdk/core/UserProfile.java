/* Teak -- Copyright (C) 2018 GoCarrot Inc.
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

package io.teak.sdk.core;

import android.support.annotation.NonNull;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.teak.sdk.Request;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.json.JSONObject;

public class UserProfile extends Request {
    private final Map<String, Object> stringAttributes;
    private final Map<String, Object> numberAttributes;
    private final String context;

    private ScheduledFuture<?> scheduledSend;

    UserProfile(@NonNull Session session, @NonNull Map<String, Object> userProfile) {
        super("gocarrot.com", "/me/profile", new HashMap<String, Object>(), session, null, true);

        if (!(userProfile.get("context") instanceof String)) {
            throw new InvalidParameterException("User Profile value 'context' is not a String");
        }
        if (!(userProfile.get("string_attributes") instanceof Map)) {
            throw new InvalidParameterException("User Profile value 'string_attributes' is not a Map");
        }
        if (!(userProfile.get("number_attributes") instanceof Map)) {
            throw new InvalidParameterException("User Profile value 'number_attributes' is not a Map");
        }

        // Insert JSONObject.NULL so that we feed the server what it wants
        @SuppressWarnings("unchecked")
        Map<String, Object> string_attributes = (Map<String, Object>) userProfile.get("string_attributes");
        for (String key : string_attributes.keySet()) {
            if (string_attributes.get(key) == null) {
                string_attributes.put(key, JSONObject.NULL);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> number_attributes = (Map<String, Object>) userProfile.get("number_attributes");
        for (String key : number_attributes.keySet()) {
            if (number_attributes.get(key) == null) {
                number_attributes.put(key, JSONObject.NULL);
            }
        }

        this.stringAttributes = string_attributes;
        this.numberAttributes = number_attributes;
        this.context = (String) userProfile.get("context");
    }

    @Override
    public void run() {
        // If this has no scheduledSend, or if it isn't cancelable, then it has no pending updates
        if (this.scheduledSend != null && this.scheduledSend.cancel(false)) {
            this.payload.put("context", this.context);
            this.payload.put("string_attributes", this.stringAttributes);
            this.payload.put("number_attributes", this.numberAttributes);

            super.run();
        }
    }

    public void setNumericAttribute(@NonNull String key, double value) {
        if (this.numberAttributes.get(key) == null ||
            !this.numberAttributes.get(key).equals(value)) {
            setAttribute(this.numberAttributes, key, value);
        }
    }

    public void setStringAttribute(@NonNull String key, String value) {
        if (!value.equals(this.stringAttributes.get(key))) {
            setAttribute(this.stringAttributes, key, value);
        }
    }

    private void setAttribute(@NonNull final Map<String, Object> map, @NonNull final String key, @NonNull final Object value) {
        if (map.containsKey(key)) {
            TeakCore.operationQueue.execute(new Runnable() {
                @Override
                public void run() {
                    if (UserProfile.this.scheduledSend != null) {
                        UserProfile.this.scheduledSend.cancel(false);
                    }

                    // Update value
                    map.put(key, value);

                    UserProfile.this.scheduledSend = TeakCore.operationQueue.schedule(UserProfile.this,
                        (long) (UserProfile.this.batch.time * 1000.0f), TimeUnit.MILLISECONDS);
                }
            });
        }
    }

    static {
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                    RemoteConfiguration remoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;
                }
            }
        });
    }
}
