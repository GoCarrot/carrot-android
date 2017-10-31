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
package io.teak.sdk.wrapper.unity;

import org.json.JSONObject;

import android.support.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Map;

import io.teak.sdk.Teak;
import io.teak.sdk.wrapper.ISDKWrapper;
import io.teak.sdk.wrapper.TeakInterface;

class TeakUnity {
    private static Method unitySendMessage;
    private static TeakInterface teakInterface;

    static {
        try {
            Class<?> unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
            TeakUnity.unitySendMessage = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static void initialize() {
        teakInterface = new TeakInterface(new ISDKWrapper() {
            @Override
            public void sdkSendMessage(@NonNull EventType eventType, @NonNull String eventData) {
                String eventName = null;
                switch (eventType) {
                    case NotificationLaunch: {
                        eventName = "NotificationLaunch";
                    } break;
                    case RewardClaim: {
                        eventName = "RewardClaimAttempt";
                    } break;
                }

                UnitySendMessage(eventName, eventData);
            }
        });
    }

    @SuppressWarnings("unused")
    public static void readyForDeepLinks() {
        teakInterface.readyForDeepLinks();
    }

    public static boolean isAvailable() {
        return TeakUnity.unitySendMessage != null;
    }

    private static void UnitySendMessage(String method, String message) {
        if (TeakUnity.isAvailable()) {
            try {
                TeakUnity.unitySendMessage.invoke(null, "TeakGameObject", method, message);
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void registerRoute(final String route, final String name, final String description) {
        try {
            Teak.registerDeepLink(route, name, description, new Teak.DeepLink() {
                @Override
                public void call(Map<String, Object> parameters) {
                    try {
                        if (TeakUnity.isAvailable()) {
                            JSONObject eventData = new JSONObject();
                            eventData.put("route", route);
                            eventData.put("parameters", new JSONObject(parameters));
                            TeakUnity.UnitySendMessage("DeepLink", eventData.toString());
                        }
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                }
            });
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }
}
