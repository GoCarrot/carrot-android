package io.teak.sdk.wrapper.unity;

import io.teak.sdk.raven.Raven;
import io.teak.sdk.Unobfuscable;
import io.teak.sdk.json.JSONObject;

import android.support.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Map;

import io.teak.sdk.Teak;
import io.teak.sdk.wrapper.ISDKWrapper;
import io.teak.sdk.wrapper.TeakInterface;

public class TeakUnity implements Unobfuscable {
    private static Method unitySendMessage;
    private static TeakInterface teakInterface;

    static {
        try {
            Class<?> unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
            TeakUnity.unitySendMessage = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);
        } catch (Exception ignored) {
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

                unitySendMessage(eventName, eventData);
            }
        });
    }

    public static boolean isAvailable() {
        return TeakUnity.unitySendMessage != null;
    }

    private static void unitySendMessage(String method, String message) {
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
                            TeakUnity.unitySendMessage("DeepLink", eventData.toString());
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

    @SuppressWarnings({"unused", "deprecation"})
    public static void testExceptionReporting() {
        Teak.log.exception(new Raven.ReportTestException(Teak.SDKVersion));
    }
}
