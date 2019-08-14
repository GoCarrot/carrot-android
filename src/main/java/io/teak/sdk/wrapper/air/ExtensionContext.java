package io.teak.sdk.wrapper.air;

import androidx.annotation.NonNull;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import io.teak.sdk.Teak;
import io.teak.sdk.Unobfuscable;
import io.teak.sdk.json.JSONObject;
import io.teak.sdk.wrapper.ISDKWrapper;
import io.teak.sdk.wrapper.TeakInterface;
import java.util.HashMap;
import java.util.Map;

public class ExtensionContext extends FREContext implements Unobfuscable {
    final TeakInterface teakInterface;
    private final String initializationErrors;

    @SuppressWarnings("WeakerAccess")
    public ExtensionContext() {
        TeakInterface tempTeakInterface = null;
        String tempInitializationErrors = null;
        try {
            tempTeakInterface = new TeakInterface(new ISDKWrapper() {
                @Override
                public void sdkSendMessage(@NonNull EventType eventType, @NonNull String eventData) {
                    String eventName = null;
                    switch (eventType) {
                        case NotificationLaunch: {
                            eventName = "LAUNCHED_FROM_NOTIFICATION";
                        } break;
                        case RewardClaim: {
                            eventName = "ON_REWARD";
                        } break;
                        case ForegroundNotification: {
                            eventName = "ON_FOREGROUND_NOTIFICATION";
                        } break;
                    }
                    Extension.context.dispatchStatusEventAsync(eventName, eventData);
                }
            });
        } catch (IllegalStateException e) {
            tempInitializationErrors = e.getLocalizedMessage();
        } finally {
            this.teakInterface = tempTeakInterface;
            this.initializationErrors = tempInitializationErrors;
        }

        Teak.setLogListener(new Teak.LogListener() {
            @Override
            public void logEvent(String logEvent, String logLevel, Map<String, Object> logData) {
                Extension.context.dispatchStatusEventAsync("ON_LOG_EVENT", new JSONObject(logData).toString());
            }
        });
    }

    @Override
    public Map<String, FREFunction> getFunctions() {
        Map<String, FREFunction> functionMap = new HashMap<>();
        functionMap.put("identifyUser", new IdentifyUserFunction());
        functionMap.put("_log", new LogFunction());
        functionMap.put("scheduleNotification", new TeakNotificationFunction(TeakNotificationFunction.CallType.Schedule));
        functionMap.put("scheduleLongDistanceNotification", new TeakNotificationFunction(TeakNotificationFunction.CallType.ScheduleLongDistance));
        functionMap.put("cancelNotification", new TeakNotificationFunction(TeakNotificationFunction.CallType.Cancel));
        functionMap.put("cancelAllNotifications", new TeakNotificationFunction(TeakNotificationFunction.CallType.CancelAll));
        functionMap.put("registerRoute", new RegisterRouteFunction());
        functionMap.put("getVersion", new GetVersionFunction());
        functionMap.put("getInitializationErrors", new GetInitializationErrorsFunction(this.initializationErrors));
        functionMap.put("setNumericAttribute", new SetAttributeFunction(SetAttributeFunction.FunctionType.Numeric));
        functionMap.put("setStringAttribute", new SetAttributeFunction(SetAttributeFunction.FunctionType.String));
        functionMap.put("openSettingsAppToThisAppsSettings", new OpenSettingsAppToThisAppsSettingsFunction());
        functionMap.put("getNotificationState", new GetNotificationState());
        functionMap.put("getAppConfiguration", new GetConfigurationFunction(GetConfigurationFunction.ConfigurationType.AppConfiguration));
        functionMap.put("getDeviceConfiguration", new GetConfigurationFunction(GetConfigurationFunction.ConfigurationType.DeviceConfiguration));
        functionMap.put("_testExceptionReporting", new ExceptionReportingTestFunction());
        functionMap.put("trackEvent", new TrackEventFunction());
        functionMap.put("incrementEvent", new TrackEventFunction());
        functionMap.put("processDeepLinks", new ProcessDeepLinksFunction());
        return functionMap;
    }

    @Override
    public void dispose() {
        // None
    }
}
