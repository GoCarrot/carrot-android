package io.teak.sdk.wrapper.unity;

import androidx.annotation.NonNull;
import io.teak.sdk.Teak;
import io.teak.sdk.Unobfuscable;
import io.teak.sdk.core.Executors;
import io.teak.sdk.json.JSONObject;
import io.teak.sdk.raven.Raven;
import io.teak.sdk.wrapper.ISDKWrapper;
import io.teak.sdk.wrapper.TeakInterface;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class TeakUnity implements Unobfuscable {
    private static Method unitySendMessage;
    private static TeakInterface teakInterface;
    private static ExecutorService unitySendMessageExecutor = Executors.newSingleThreadExecutor();

    static {
        try {
            Class<?> unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
            TeakUnity.unitySendMessage = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);

            Teak.setLogListener(new Teak.LogListener() {
                @Override
                public void logEvent(String logEvent, String logLevel, Map<String, Object> logData) {
                    unitySendMessage("LogEvent", new JSONObject(logData).toString());
                }
            });
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
                    case ForegroundNotification: {
                        eventName = "ForegroundNotification";
                    } break;
                    case AdditionalData: {
                        eventName = "AdditionalData";
                    } break;
                }
                unitySendMessage(eventName, eventData);
            }
        });
    }

    public static boolean isAvailable() {
        return TeakUnity.unitySendMessage != null;
    }

    private static void unitySendMessage(final String method, final String message) {
        TeakUnity.unitySendMessageExecutor.submit(new Runnable() {
            @Override
            public void run() {
                if (TeakUnity.isAvailable()) {
                    try {
                        TeakUnity.unitySendMessage.invoke(null, "TeakGameObject", method, message);
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                }
            }
        });
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
